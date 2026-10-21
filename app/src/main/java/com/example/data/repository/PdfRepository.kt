package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDao
import com.example.data.local.PdfDatabase
import com.example.data.local.PdfEntity
import com.example.pdf.SamplePdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(private val context: Context) {
    private val dao: PdfDao = PdfDatabase.getInstance(context).pdfDao()

    val allPdfs: Flow<List<PdfEntity>> = dao.getAllPdfs()
    val favoritePdfs: Flow<List<PdfEntity>> = dao.getFavoritePdfs()
    val recentPdfs: Flow<List<PdfEntity>> = dao.getRecentPdfs()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Ensure sample documents are generated on first launch
        val demoFiles = SamplePdfGenerator.generateInitialDocuments(context)
        for (f in demoFiles) {
            val entity = PdfEntity(
                path = f.absolutePath,
                title = f.name,
                uriString = Uri.fromFile(f).toString(),
                sizeBytes = f.length(),
                lastModifiedTimestamp = f.lastModified(),
                folderName = "Demo Documents"
            )
            dao.insertPdf(entity)
        }
    }

    suspend fun autoFetchDeviceDocuments() = withContext(Dispatchers.IO) {
        val searchDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File(context.filesDir, "NarayaniDocuments")
        )

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (f.isFile && (f.name.endsWith(".pdf", true) || f.name.endsWith(".docx", true) || f.name.endsWith(".xlsx", true))) {
                    val existing = dao.getPdfByPath(f.absolutePath)
                    if (existing == null) {
                        val entity = PdfEntity(
                            path = f.absolutePath,
                            title = f.name,
                            uriString = Uri.fromFile(f).toString(),
                            sizeBytes = f.length(),
                            lastModifiedTimestamp = f.lastModified(),
                            folderName = dir.name
                        )
                        dao.insertPdf(entity)
                    }
                }
            }
        }
    }

    suspend fun importPdfFromUri(uri: Uri): PdfEntity? = withContext(Dispatchers.IO) {
        try {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported_Doc_${System.currentTimeMillis()}.pdf"
            val destDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
            val destFile = File(destDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val entity = PdfEntity(
                path = destFile.absolutePath,
                title = destFile.name,
                uriString = Uri.fromFile(destFile).toString(),
                sizeBytes = destFile.length(),
                lastModifiedTimestamp = destFile.lastModified(),
                folderName = "Imported"
            )
            dao.insertPdf(entity)
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addCreatedPdf(file: File, folderName: String = "Created"): PdfEntity = withContext(Dispatchers.IO) {
        val entity = PdfEntity(
            path = file.absolutePath,
            title = file.name,
            uriString = Uri.fromFile(file).toString(),
            sizeBytes = file.length(),
            lastModifiedTimestamp = file.lastModified(),
            folderName = folderName
        )
        dao.insertPdf(entity)
        entity
    }

    suspend fun recordOpened(path: String) = withContext(Dispatchers.IO) {
        dao.updateLastOpened(path, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(path: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavorite(path, isFavorite)
    }

    suspend fun setLock(path: String, isLocked: Boolean, pin: String) = withContext(Dispatchers.IO) {
        dao.updateLock(path, isLocked, pin)
    }

    suspend fun deletePdf(path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {}
        dao.deletePdf(path)
    }

    suspend fun renamePdf(oldPath: String, newName: String): String = withContext(Dispatchers.IO) {
        val oldFile = File(oldPath)
        val ext = oldFile.extension
        val cleanName = if (newName.endsWith(".$ext", true)) newName else "$newName.$ext"
        val newFile = File(oldFile.parentFile, cleanName)

        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        }

        val newUri = Uri.fromFile(newFile).toString()
        dao.renamePdf(oldPath, newFile.absolutePath, cleanName, newUri)
        newFile.absolutePath
    }

    suspend fun getPdfByPath(path: String): PdfEntity? = withContext(Dispatchers.IO) {
        dao.getPdfByPath(path)
    }

    fun getBookmarksForPdf(path: String): Flow<List<BookmarkEntity>> = dao.getBookmarksForPdf(path)

    suspend fun toggleBookmark(path: String, page: Int, title: String) = withContext(Dispatchers.IO) {
        if (dao.isBookmarked(path, page)) {
            dao.deleteBookmark(path, page)
        } else {
            dao.insertBookmark(BookmarkEntity(pdfPath = path, pageNumber = page, title = title))
        }
    }

    suspend fun isBookmarked(path: String, page: Int): Boolean = withContext(Dispatchers.IO) {
        dao.isBookmarked(path, page)
    }
}
