package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDao
import com.example.data.local.PdfDatabase
import com.example.data.local.PdfEntity
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
        // Clean up any old demo files per user request
        cleanupDemoFiles()
        // Automatically fetch real documents from device memory
        autoFetchDeviceDocuments()
    }

    private suspend fun cleanupDemoFiles() = withContext(Dispatchers.IO) {
        val demoNames = listOf(
            "Narayani_User_Guide.pdf",
            "Service_Agreement_Sample.pdf",
            "Monthly_Financial_Summary.pdf"
        )
        for (name in demoNames) {
            val file = File(File(context.filesDir, "NarayaniDocuments"), name)
            if (file.exists()) {
                file.delete()
            }
        }
        // Remove demo entities from database
        val allPaths = dao.getAllPaths()
        for (p in allPaths) {
            if (demoNames.any { p.endsWith(it) } || p.contains("Demo Documents")) {
                dao.deletePdf(p)
            }
        }
    }

    suspend fun autoFetchDeviceDocuments() = withContext(Dispatchers.IO) {
        val existingPaths = dao.getAllPaths().toHashSet()
        val newEntities = mutableListOf<PdfEntity>()

        // 1. Query Android MediaStore for all document types (PDF, Word, Excel, CSV)
        try {
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            val selection = "(${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.docx' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.doc' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xlsx' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xls' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.csv' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/pdf' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/msword' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.ms-excel' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' OR " +
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = 'text/csv' OR " +
                    "${MediaStore.Files.FileColumns.DATA} LIKE '%.pdf' OR " +
                    "${MediaStore.Files.FileColumns.DATA} LIKE '%.docx' OR " +
                    "${MediaStore.Files.FileColumns.DATA} LIKE '%.xlsx')"

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val modCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)
                    val filePath = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val cursorSize = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val modified = if (modCol >= 0) cursor.getLong(modCol) * 1000L else 0L

                    val file = if (filePath.isNotBlank()) File(filePath) else null
                    var canAccess = false
                    var sizeBytes = cursorSize

                    if (file != null && file.exists() && file.canRead()) {
                        canAccess = true
                        if (sizeBytes <= 0L) sizeBytes = file.length()
                    } else {
                        // Check if accessible via ContentResolver
                        try {
                            context.contentResolver.openAssetFileDescriptor(contentUri, "r")?.use { afd ->
                                canAccess = true
                                if (sizeBytes <= 0L) sizeBytes = afd.length
                            }
                        } catch (e: Exception) {
                            try {
                                context.contentResolver.openInputStream(contentUri)?.use {
                                    canAccess = true
                                }
                            } catch (e2: Exception) {}
                        }
                    }

                    if (!canAccess) continue

                    val effectivePath = if (file != null && file.exists()) filePath else contentUri.toString()
                    if (existingPaths.contains(effectivePath)) continue

                    val resolvedTitle = displayName ?: file?.name ?: "Document_${id}"
                    val folderName = file?.parentFile?.name ?: "Storage"

                    val entity = PdfEntity(
                        path = effectivePath,
                        title = resolvedTitle,
                        uriString = contentUri.toString(),
                        sizeBytes = if (sizeBytes > 0) sizeBytes else 1024L,
                        lastModifiedTimestamp = if (modified > 0) modified else (file?.lastModified() ?: System.currentTimeMillis()),
                        folderName = folderName
                    )
                    newEntities.add(entity)
                    existingPaths.add(effectivePath)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Direct filesystem scan of key storage directories
        val scanRoots = mutableListOf<File>()
        try {
            scanRoots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            scanRoots.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            scanRoots.add(File(Environment.getExternalStorageDirectory(), "Download"))
            scanRoots.add(File(Environment.getExternalStorageDirectory(), "Documents"))
            scanRoots.add(File(Environment.getExternalStorageDirectory(), "DCIM"))
            scanRoots.add(File(Environment.getExternalStorageDirectory(), "WhatsApp/Media/WhatsApp Documents"))
            scanRoots.add(File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"))
            scanRoots.add(File(Environment.getExternalStorageDirectory(), "Telegram/Telegram Documents"))
            scanRoots.add(File(context.filesDir, "NarayaniDocuments"))
            scanRoots.add(Environment.getExternalStorageDirectory())
        } catch (e: Exception) {}

        for (dir in scanRoots.distinct()) {
            scanDirectoryRecursively(dir, 0, 3, existingPaths, newEntities)
        }

        if (newEntities.isNotEmpty()) {
            dao.insertPdfs(newEntities)
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        currentDepth: Int,
        maxDepth: Int,
        existingPaths: HashSet<String>,
        outList: MutableList<PdfEntity>
    ) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    scanDirectoryRecursively(f, currentDepth + 1, maxDepth, existingPaths, outList)
                }
            } else if (f.isFile && f.length() > 0) {
                val nameLower = f.name.lowercase()
                if (nameLower.endsWith(".pdf") || nameLower.endsWith(".docx") ||
                    nameLower.endsWith(".doc") || nameLower.endsWith(".xlsx") ||
                    nameLower.endsWith(".xls") || nameLower.endsWith(".csv")
                ) {
                    if (!existingPaths.contains(f.absolutePath)) {
                        val entity = PdfEntity(
                            path = f.absolutePath,
                            title = f.name,
                            uriString = Uri.fromFile(f).toString(),
                            sizeBytes = f.length(),
                            lastModifiedTimestamp = f.lastModified(),
                            folderName = dir.name
                        )
                        outList.add(entity)
                        existingPaths.add(f.absolutePath)
                    }
                }
            }
        }
    }

    suspend fun importPdfFromUri(uri: Uri): PdfEntity? = withContext(Dispatchers.IO) {
        try {
            var fileName = "Document_${System.currentTimeMillis()}"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }

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
                folderName = "Storage"
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
