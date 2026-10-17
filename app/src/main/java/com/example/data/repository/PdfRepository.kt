package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDao
import com.example.data.local.PdfDatabase
import com.example.data.local.PdfEntity
import com.example.pdf.PdfRendererHelper
import com.example.pdf.SamplePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(private val context: Context) {
    private val pdfDao: PdfDao = PdfDatabase.getInstance(context).pdfDao()

    val allPdfs: Flow<List<PdfEntity>> = pdfDao.getAllPdfs()
    val recentPdfs: Flow<List<PdfEntity>> = pdfDao.getRecentPdfs()
    val favoritePdfs: Flow<List<PdfEntity>> = pdfDao.getFavoritePdfs()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Create sample PDFs & Office files if needed
        val pdfSamples = SamplePdfGenerator.generateSamplePdfsIfNeeded(context)
        val officeSamples = com.example.reader.OfficeDocumentHelper.generateSampleOfficeFiles(context)
        val allSamples = pdfSamples + officeSamples

        for (file in allSamples) {
            val existing = pdfDao.getPdfByPath(file.absolutePath)
            if (existing == null) {
                val pageCount = if (file.name.endsWith(".pdf", ignoreCase = true)) {
                    PdfRendererHelper.getPageCountForFile(file)
                } else 1
                pdfDao.insertPdf(
                    PdfEntity(
                        path = file.absolutePath,
                        title = file.nameWithoutExtension.replace("_", " "),
                        uriString = Uri.fromFile(file).toString(),
                        sizeBytes = file.length(),
                        pageCount = pageCount,
                        lastModifiedTimestamp = file.lastModified(),
                        folderName = file.parentFile?.name ?: "Documents"
                    )
                )
            }
        }
        // Scan Documents directory for any new files
        scanAppDocumentsDir()
    }

    private suspend fun scanAppDocumentsDir() {
        val docsDir = File(context.filesDir, "Documents")
        val supportedExtensions = listOf(".pdf", ".docx", ".doc", ".xlsx", ".xls", ".csv", ".tsv", ".txt", ".md", ".json", ".log")
        if (docsDir.exists()) {
            docsDir.listFiles { _, name -> supportedExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) } }?.forEach { file ->
                val existing = pdfDao.getPdfByPath(file.absolutePath)
                if (existing == null) {
                    val pageCount = if (file.name.endsWith(".pdf", ignoreCase = true)) {
                        PdfRendererHelper.getPageCountForFile(file)
                    } else 1
                    pdfDao.insertPdf(
                        PdfEntity(
                            path = file.absolutePath,
                            title = file.nameWithoutExtension.replace("_", " "),
                            uriString = Uri.fromFile(file).toString(),
                            sizeBytes = file.length(),
                            pageCount = pageCount,
                            lastModifiedTimestamp = file.lastModified(),
                            folderName = "Documents"
                        )
                    )
                }
            }
        }
    }

    suspend fun importPdfFromUri(uri: Uri): PdfEntity? = withContext(Dispatchers.IO) {
        try {
            var fileName = "Imported_Document_${System.currentTimeMillis()}.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            if (!fileName.contains(".")) {
                fileName += ".pdf"
            }

            val docsDir = File(context.filesDir, "Documents")
            if (!docsDir.exists()) docsDir.mkdirs()

            val destFile = File(docsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val pageCount = if (destFile.name.endsWith(".pdf", ignoreCase = true)) {
                PdfRendererHelper.getPageCountForFile(destFile)
            } else 1

            val entity = PdfEntity(
                path = destFile.absolutePath,
                title = destFile.nameWithoutExtension.replace("_", " "),
                uriString = Uri.fromFile(destFile).toString(),
                sizeBytes = destFile.length(),
                pageCount = pageCount,
                lastModifiedTimestamp = destFile.lastModified(),
                lastOpenedTimestamp = System.currentTimeMillis(),
                folderName = "Imported"
            )
            pdfDao.insertPdf(entity)
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addCreatedPdf(file: File, folderName: String = "Tools"): PdfEntity = withContext(Dispatchers.IO) {
        val pageCount = PdfRendererHelper.getPageCountForFile(file)
        val entity = PdfEntity(
            path = file.absolutePath,
            title = file.nameWithoutExtension.replace("_", " "),
            uriString = Uri.fromFile(file).toString(),
            sizeBytes = file.length(),
            pageCount = pageCount,
            lastModifiedTimestamp = file.lastModified(),
            lastOpenedTimestamp = System.currentTimeMillis(),
            folderName = folderName
        )
        pdfDao.insertPdf(entity)
        entity
    }

    suspend fun toggleFavorite(path: String, current: Boolean) = withContext(Dispatchers.IO) {
        pdfDao.updateFavorite(path, !current)
    }

    suspend fun recordOpened(path: String) = withContext(Dispatchers.IO) {
        pdfDao.updateLastOpened(path, System.currentTimeMillis())
    }

    suspend fun renamePdf(oldPath: String, newName: String): String = withContext(Dispatchers.IO) {
        val cleanName = if (newName.endsWith(".pdf", ignoreCase = true)) newName else "$newName.pdf"
        val oldFile = File(oldPath)
        val newFile = File(oldFile.parentFile, cleanName)

        if (oldFile.renameTo(newFile)) {
            val title = newFile.nameWithoutExtension.replace("_", " ")
            pdfDao.renamePdf(oldPath, newFile.absolutePath, title)
            newFile.absolutePath
        } else {
            oldPath
        }
    }

    suspend fun deletePdf(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        pdfDao.deletePdf(path)
    }

    suspend fun setLock(path: String, pin: String) = withContext(Dispatchers.IO) {
        pdfDao.updateLock(path, isLocked = true, password = pin)
    }

    suspend fun unlockPdf(path: String) = withContext(Dispatchers.IO) {
        pdfDao.updateLock(path, isLocked = false, password = "")
    }

    // Bookmarks
    fun getBookmarks(pdfPath: String): Flow<List<BookmarkEntity>> = pdfDao.getBookmarksForPdf(pdfPath)

    suspend fun toggleBookmark(pdfPath: String, pageNumber: Int, title: String) = withContext(Dispatchers.IO) {
        if (pdfDao.isBookmarked(pdfPath, pageNumber)) {
            pdfDao.deleteBookmark(pdfPath, pageNumber)
        } else {
            pdfDao.insertBookmark(
                BookmarkEntity(
                    pdfPath = pdfPath,
                    pageNumber = pageNumber,
                    title = title
                )
            )
        }
    }

    suspend fun isPageBookmarked(pdfPath: String, pageNumber: Int): Boolean = withContext(Dispatchers.IO) {
        pdfDao.isBookmarked(pdfPath, pageNumber)
    }

    suspend fun getPdfByPath(path: String): PdfEntity? = withContext(Dispatchers.IO) {
        pdfDao.getPdfByPath(path)
    }

    suspend fun updatePdfEntity(entity: PdfEntity) = withContext(Dispatchers.IO) {
        pdfDao.insertPdf(entity)
    }
}
