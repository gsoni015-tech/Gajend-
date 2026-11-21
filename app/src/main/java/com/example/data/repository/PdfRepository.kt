package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.data.cache.PdfFastCacheManager
import com.example.data.cloud.DeletedLocalDoc
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfDao
import com.example.data.local.PdfDatabase
import com.example.data.local.PdfEntity
import com.example.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(private val context: Context) {
    private val dao: PdfDao = PdfDatabase.getInstance(context).pdfDao()
    private val scanMutex = Mutex()
    private val _deletedLocalDocsFlow = MutableSharedFlow<List<DeletedLocalDoc>>(extraBufferCapacity = 16)
    val deletedLocalDocsFlow: SharedFlow<List<DeletedLocalDoc>> = _deletedLocalDocsFlow.asSharedFlow()

    val allPdfs: Flow<List<PdfEntity>> = dao.getAllPdfs()
    val favoritePdfs: Flow<List<PdfEntity>> = dao.getFavoritePdfs()
    val recentPdfs: Flow<List<PdfEntity>> = dao.getRecentPdfs()

    private val narayaniDirCanonical: String by lazy {
        canonicalPathSafe(File(context.filesDir, "NarayaniDocuments"))
    }

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

    private fun canonicalPathSafe(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }

    private fun fileSignature(title: String, sizeBytes: Long): String? {
        val clean = title.trim().lowercase()
        return if (clean.isNotEmpty() && sizeBytes > 0L) "$clean|$sizeBytes" else null
    }

    private fun isPrivateStorageImport(entity: PdfEntity): Boolean {
        if (entity.path.startsWith("content://")) return false
        val canonical = canonicalPathSafe(File(entity.path))
        return canonical.startsWith(narayaniDirCanonical) && entity.folderName == "Storage"
    }

    private fun isEntityAccessible(entity: PdfEntity): Boolean {
        return if (entity.path.startsWith("content://")) {
            try {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(entity.path), "r")?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        } else {
            val f = File(entity.path)
            f.exists() && f.canRead() && f.length() > 0L
        }
    }

    private fun mergeEntityMetadata(preferred: PdfEntity, duplicate: PdfEntity): PdfEntity {
        val mergedFavorite = preferred.isFavorite || duplicate.isFavorite
        val mergedOpened = maxOf(preferred.lastOpenedTimestamp, duplicate.lastOpenedTimestamp)
        val mergedLocked = preferred.isLocked || duplicate.isLocked
        val mergedPassword = when {
            preferred.isLocked && preferred.password.isNotBlank() -> preferred.password
            duplicate.isLocked && duplicate.password.isNotBlank() -> duplicate.password
            else -> preferred.password.ifBlank { duplicate.password }
        }
        val mergedUri = preferred.uriString.ifBlank { duplicate.uriString }
        return preferred.copy(
            uriString = mergedUri,
            isFavorite = mergedFavorite,
            lastOpenedTimestamp = mergedOpened,
            isLocked = mergedLocked,
            password = mergedPassword
        )
    }

    /**
     * Removes duplicate rows in Room caused by canonical path aliases (/sdcard vs /storage/emulated/0),
     * content:// MediaStore rows vs real file paths, or duplicate NarayaniDocuments Storage imports.
     */
    private suspend fun deduplicateDatabaseInternal() {
        val allEntities = dao.getAllPdfsList()
        if (allEntities.size <= 1) return

        val toDeletePaths = linkedSetOf<String>()
        val toUpsert = linkedMapOf<String, PdfEntity>()

        fun entityPriority(e: PdfEntity): Int {
            return when {
                !e.path.startsWith("content://") && !isPrivateStorageImport(e) -> 3
                !e.path.startsWith("content://") -> 2
                else -> 1
            }
        }

        val keptByCanonical = linkedMapOf<String, PdfEntity>()
        val keptByUri = linkedMapOf<String, PdfEntity>()
        val keptBySignature = linkedMapOf<String, PdfEntity>()

        // Process higher-priority entries first so real external paths win over content:// & private copies
        val ordered = allEntities.sortedWith(
            compareByDescending<PdfEntity> { entityPriority(it) }
                .thenByDescending { it.lastOpenedTimestamp }
                .thenByDescending { it.lastModifiedTimestamp }
        )

        for (rawEntity in ordered) {
            val normalizedPath = if (rawEntity.path.startsWith("content://")) {
                rawEntity.path
            } else {
                canonicalPathSafe(File(rawEntity.path))
            }
            val entity = if (normalizedPath != rawEntity.path) {
                toDeletePaths.add(rawEntity.path)
                rawEntity.copy(path = normalizedPath)
            } else {
                rawEntity
            }

            // 1. Exact canonical path duplicate
            val dupByPath = keptByCanonical[entity.path]
            if (dupByPath != null) {
                val merged = mergeEntityMetadata(dupByPath, entity)
                keptByCanonical[dupByPath.path] = merged
                toUpsert[merged.path] = merged
                if (rawEntity.path != dupByPath.path) toDeletePaths.add(rawEntity.path)
                continue
            }

            // 2. Exact content URI duplicate
            val uriKey = entity.uriString.takeIf { it.isNotBlank() }
            val dupByUri = uriKey?.let { keptByUri[it] }
            if (dupByUri != null) {
                val merged = mergeEntityMetadata(dupByUri, entity)
                keptByCanonical[dupByUri.path] = merged
                toUpsert[merged.path] = merged
                toDeletePaths.add(rawEntity.path)
                if (entity.path != dupByUri.path) toDeletePaths.add(entity.path)
                continue
            }

            // 3. Same (title, sizeBytes) when one of the pair is a content:// entry or an imported Storage copy
            val sig = fileSignature(entity.title, entity.sizeBytes)
            val dupBySig = sig?.let { keptBySignature[it] }
            val isRedundantCounterpart = dupBySig != null && (
                entity.path.startsWith("content://") ||
                    dupBySig.path.startsWith("content://") ||
                    isPrivateStorageImport(entity) ||
                    isPrivateStorageImport(dupBySig)
                )
            if (dupBySig != null && isRedundantCounterpart) {
                val merged = mergeEntityMetadata(dupBySig, entity)
                keptByCanonical[dupBySig.path] = merged
                toUpsert[merged.path] = merged
                toDeletePaths.add(rawEntity.path)
                if (entity.path != dupBySig.path) toDeletePaths.add(entity.path)
                if (isPrivateStorageImport(entity)) {
                    try { File(entity.path).delete() } catch (_: Exception) {}
                }
                continue
            }

            // Keep this entity
            keptByCanonical[entity.path] = entity
            if (normalizedPath != rawEntity.path) {
                toUpsert[entity.path] = entity
            }
            if (uriKey != null) keptByUri.putIfAbsent(uriKey, entity)
            if (sig != null) keptBySignature.putIfAbsent(sig, entity)
        }

        if (toDeletePaths.isNotEmpty()) {
            toDeletePaths.chunked(500).forEach { chunk ->
                dao.deletePdfsByPaths(chunk)
            }
        }
        if (toUpsert.isNotEmpty()) {
            dao.insertPdfs(toUpsert.values.toList())
        }
    }

    suspend fun autoFetchDeviceDocuments() = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            deduplicateDatabaseInternal()

            val currentEntities = dao.getAllPdfsList()
            val existingPaths = HashSet<String>(currentEntities.size * 2)
            val existingUris = HashSet<String>(currentEntities.size * 2)
            val existingBySignature = HashMap<String, PdfEntity>(currentEntities.size * 2)

            for (entity in currentEntities) {
                existingPaths.add(entity.path)
                if (!entity.path.startsWith("content://")) {
                    existingPaths.add(canonicalPathSafe(File(entity.path)))
                }
                if (entity.uriString.isNotBlank()) {
                    existingUris.add(entity.uriString)
                }
                fileSignature(entity.title, entity.sizeBytes)?.let { sig ->
                    existingBySignature.putIfAbsent(sig, entity)
                }
            }

            val newEntities = LinkedHashMap<String, PdfEntity>()
            val pathsToDelete = linkedSetOf<String>()

            fun registerDiscoveredEntity(candidate: PdfEntity, isExternalFile: Boolean) {
                val sig = fileSignature(candidate.title, candidate.sizeBytes)
                val existingSigMatch = sig?.let { existingBySignature[it] }

                if (existingPaths.contains(candidate.path) || existingUris.contains(candidate.uriString)) {
                    return
                }

                // Upgrade legacy content:// or redundant NarayaniDocuments Storage copy to the real external path
                if (isExternalFile && existingSigMatch != null &&
                    (existingSigMatch.path.startsWith("content://") || isPrivateStorageImport(existingSigMatch))
                ) {
                    val upgraded = mergeEntityMetadata(candidate, existingSigMatch)
                    pathsToDelete.add(existingSigMatch.path)
                    if (isPrivateStorageImport(existingSigMatch)) {
                        try { File(existingSigMatch.path).delete() } catch (_: Exception) {}
                    }
                    newEntities.remove(existingSigMatch.path)
                    newEntities[upgraded.path] = upgraded
                    existingPaths.add(upgraded.path)
                    if (upgraded.uriString.isNotBlank()) existingUris.add(upgraded.uriString)
                    if (sig != null) existingBySignature[sig] = upgraded
                    return
                }

                // Skip adding content:// or NarayaniDocuments Storage duplicate if real external copy already exists
                if (!isExternalFile && existingSigMatch != null) {
                    return
                }

                newEntities[candidate.path] = candidate
                existingPaths.add(candidate.path)
                if (candidate.uriString.isNotBlank()) existingUris.add(candidate.uriString)
                if (sig != null) existingBySignature.putIfAbsent(sig, candidate)
            }

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
                                } catch (_: Exception) {}
                            }
                        }

                        if (!canAccess) continue

                        val resolvedTitle = displayName ?: file?.name ?: "Document_$id"
                        if (resolvedTitle.startsWith(".")) continue // Skip trashed/pending hidden files

                        val hasRealFile = file != null && file.exists()
                        val effectivePath = if (hasRealFile) canonicalPathSafe(file!!) else contentUri.toString()
                        val folderName = file?.parentFile?.name ?: "Storage"

                        val entity = PdfEntity(
                            path = effectivePath,
                            title = resolvedTitle,
                            uriString = contentUri.toString(),
                            sizeBytes = if (sizeBytes > 0) sizeBytes else 1024L,
                            lastModifiedTimestamp = if (modified > 0) modified else (file?.lastModified() ?: System.currentTimeMillis()),
                            folderName = folderName
                        )
                        registerDiscoveredEntity(entity, isExternalFile = hasRealFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Direct filesystem scan of key storage directories (canonicalized & visited-set deduplicated)
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
            } catch (_: Exception) {}

            val visitedDirs = HashSet<String>()
            for (dir in scanRoots) {
                scanDirectoryRecursively(
                    dir = dir,
                    currentDepth = 0,
                    maxDepth = 3,
                    visitedDirs = visitedDirs,
                    onFileFound = { entity, isExternal ->
                        registerDiscoveredEntity(entity, isExternalFile = isExternal)
                    }
                )
            }

            if (pathsToDelete.isNotEmpty()) {
                pathsToDelete.chunked(500).forEach { chunk ->
                    dao.deletePdfsByPaths(chunk)
                }
            }
            if (newEntities.isNotEmpty()) {
                dao.insertPdfs(newEntities.values.toList())
                newEntities.values.take(10).forEach { entity ->
                    PdfFastCacheManager.warmCacheAsync(context, entity)
                }
            }

            // Detect files deleted externally from Device Gallery / File Manager / Downloads
            val hasFullStorageAccess = PermissionUtils.hasStoragePermission(context)
            val appFilesCanonical = canonicalPathSafe(context.filesDir)
            val appCacheCanonical = canonicalPathSafe(context.cacheDir)
            val missingDeleted = mutableListOf<DeletedLocalDoc>()
            val missingPaths = mutableListOf<String>()

            for (existing in currentEntities) {
                if (existing.path in pathsToDelete) continue
                val canVerify = if (existing.path.startsWith("content://")) {
                    hasFullStorageAccess
                } else {
                    val canonical = canonicalPathSafe(File(existing.path))
                    canonical.startsWith(appFilesCanonical) ||
                        canonical.startsWith(appCacheCanonical) ||
                        hasFullStorageAccess ||
                        (File(existing.path).parentFile?.let { it.exists() && it.canRead() } == true)
                }
                if (canVerify && !isEntityAccessible(existing)) {
                    missingPaths.add(existing.path)
                    missingDeleted.add(
                        DeletedLocalDoc(
                            path = existing.path,
                            title = existing.title,
                            sizeBytes = existing.sizeBytes
                        )
                    )
                    PdfFastCacheManager.evictByEntity(context, existing)
                }
            }

            if (missingPaths.isNotEmpty()) {
                missingPaths.chunked(500).forEach { chunk ->
                    dao.deletePdfsByPaths(chunk)
                }
                _deletedLocalDocsFlow.tryEmit(missingDeleted)
            }
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        currentDepth: Int,
        maxDepth: Int,
        visitedDirs: HashSet<String>,
        onFileFound: (PdfEntity, Boolean) -> Unit
    ) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return
        val dirCanonical = canonicalPathSafe(dir)
        if (!visitedDirs.add(dirCanonical)) return

        val files = dir.listFiles() ?: return
        val isNarayaniDir = dirCanonical.startsWith(narayaniDirCanonical)

        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanDirectoryRecursively(f, currentDepth + 1, maxDepth, visitedDirs, onFileFound)
            } else if (f.isFile && f.length() > 0) {
                val nameLower = f.name.lowercase()
                if (nameLower.endsWith(".pdf") || nameLower.endsWith(".docx") ||
                    nameLower.endsWith(".doc") || nameLower.endsWith(".xlsx") ||
                    nameLower.endsWith(".xls") || nameLower.endsWith(".csv")
                ) {
                    val fCanonical = canonicalPathSafe(f)
                    val entity = PdfEntity(
                        path = fCanonical,
                        title = f.name,
                        uriString = Uri.fromFile(f).toString(),
                        sizeBytes = f.length(),
                        lastModifiedTimestamp = f.lastModified(),
                        folderName = dir.name
                    )
                    onFileFound(entity, !isNarayaniDir)
                }
            }
        }
    }

    private fun resolveRealFilePathFromUri(uri: Uri): String? {
        try {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val f = uri.path?.let { File(it) }
                if (f != null && f.exists() && f.canRead()) {
                    return canonicalPathSafe(f)
                }
            }
            if (uri.scheme.equals("content", ignoreCase = true)) {
                if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    if (docId.startsWith("primary:", ignoreCase = true)) {
                        val rel = docId.substringAfter(":")
                        val candidate = File(Environment.getExternalStorageDirectory(), rel)
                        if (candidate.exists() && candidate.canRead()) {
                            return canonicalPathSafe(candidate)
                        }
                    }
                }
                context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DATA),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataCol >= 0 && cursor.moveToFirst()) {
                        val path = cursor.getString(dataCol)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists() && f.canRead()) {
                                return canonicalPathSafe(f)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun importPdfFromUri(uri: Uri): PdfEntity? = withContext(Dispatchers.IO) {
        try {
            val allExisting = dao.getAllPdfsList()
            val uriStr = uri.toString()

            // 1. Return existing entity immediately if exact URI is already in DB
            val existingByUri = allExisting.firstOrNull {
                it.uriString == uriStr || it.path == uriStr
            }
            if (existingByUri != null && isEntityAccessible(existingByUri)) {
                return@withContext existingByUri
            }

            // 2. Resolve direct filesystem path without duplicating into NarayaniDocuments
            val resolvedPath = resolveRealFilePathFromUri(uri)
            if (resolvedPath != null) {
                val existingByPath = allExisting.firstOrNull {
                    it.path == resolvedPath ||
                        (!it.path.startsWith("content://") && canonicalPathSafe(File(it.path)) == resolvedPath)
                }
                if (existingByPath != null) {
                    return@withContext existingByPath
                }

                val resolvedFile = File(resolvedPath)
                val entity = PdfEntity(
                    path = resolvedPath,
                    title = resolvedFile.name,
                    uriString = uriStr,
                    sizeBytes = resolvedFile.length(),
                    lastModifiedTimestamp = resolvedFile.lastModified(),
                    folderName = resolvedFile.parentFile?.name ?: "Storage"
                )
                dao.insertPdf(entity)
                return@withContext entity
            }

            // 3. Query display name & size from ContentResolver
            var fileName = "Document_${System.currentTimeMillis()}"
            var uriSize = -1L
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { fileName = it }
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        uriSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // 4. Match existing DB entity by (title, sizeBytes) so opening an existing document never creates a duplicate
            val sig = fileSignature(fileName, uriSize)
            if (sig != null) {
                val existingBySig = allExisting.firstOrNull {
                    fileSignature(it.title, it.sizeBytes) == sig && isEntityAccessible(it)
                }
                if (existingBySig != null) {
                    return@withContext existingBySig
                }
            }

            // 5. Copy opaque content:// stream into NarayaniDocuments only when no existing match was found
            val destDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val destFile = File(destDir, safeName)
            val destCanonical = canonicalPathSafe(destFile)

            val existingAtDest = dao.getPdfByPath(destCanonical)
            if (existingAtDest != null && destFile.exists() && uriSize > 0L && destFile.length() == uriSize) {
                return@withContext existingAtDest
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val copiedSig = fileSignature(destFile.name, destFile.length())
            if (copiedSig != null) {
                val existingAfterCopy = allExisting.firstOrNull {
                    fileSignature(it.title, it.sizeBytes) == copiedSig &&
                        it.path != destCanonical &&
                        isEntityAccessible(it)
                }
                if (existingAfterCopy != null) {
                    try { destFile.delete() } catch (_: Exception) {}
                    return@withContext existingAfterCopy
                }
            }

            val preserved = dao.getPdfByPath(destCanonical)
            val entity = PdfEntity(
                path = destCanonical,
                title = destFile.name,
                uriString = Uri.fromFile(destFile).toString(),
                sizeBytes = destFile.length(),
                lastModifiedTimestamp = destFile.lastModified(),
                lastOpenedTimestamp = preserved?.lastOpenedTimestamp ?: 0L,
                isFavorite = preserved?.isFavorite ?: false,
                isLocked = preserved?.isLocked ?: false,
                password = preserved?.password ?: "",
                folderName = "Storage"
            )
            dao.insertPdf(entity)
            PdfFastCacheManager.warmCacheAsync(context, entity)
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addCreatedPdf(file: File, folderName: String = "Created"): PdfEntity = withContext(Dispatchers.IO) {
        val canonical = canonicalPathSafe(file)
        val entity = PdfEntity(
            path = canonical,
            title = file.name,
            uriString = Uri.fromFile(file).toString(),
            sizeBytes = file.length(),
            lastModifiedTimestamp = file.lastModified(),
            folderName = folderName
        )
        dao.insertPdf(entity)
        PdfFastCacheManager.warmCacheAsync(context, entity)
        entity
    }

    suspend fun recordOpened(path: String) = withContext(Dispatchers.IO) {
        dao.updateLastOpened(path, System.currentTimeMillis())
        dao.getPdfByPath(path)?.let { entity ->
            PdfFastCacheManager.warmCacheAsync(context, entity)
        }
    }

    suspend fun removeFromRecent(path: String) = withContext(Dispatchers.IO) {
        dao.updateLastOpened(path, 0L)
    }

    suspend fun clearRecentHistory() = withContext(Dispatchers.IO) {
        dao.clearAllRecentHistory()
    }

    suspend fun toggleFavorite(path: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavorite(path, isFavorite)
    }

    suspend fun setLock(path: String, isLocked: Boolean, pin: String) = withContext(Dispatchers.IO) {
        dao.updateLock(path, isLocked, pin)
    }

    suspend fun deletePdf(path: String) = withContext(Dispatchers.IO) {
        val existing = dao.getPdfByPath(path)
        deletePhysicalFiles(listOf(path))
        dao.deletePdf(path)
        PdfFastCacheManager.evictByPath(
            context = context,
            path = path,
            title = existing?.title.orEmpty(),
            sizeBytes = existing?.sizeBytes ?: 0L
        )
        _deletedLocalDocsFlow.tryEmit(
            listOf(
                DeletedLocalDoc(
                    path = path,
                    title = existing?.title ?: File(path).name,
                    sizeBytes = existing?.sizeBytes ?: 0L
                )
            )
        )
    }

    suspend fun deletePdfs(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        val allByPath = dao.getAllPdfsList().associateBy { it.path }
        deletePhysicalFiles(paths)
        paths.chunked(500).forEach { chunk ->
            dao.deletePdfsByPaths(chunk)
        }
        val deletedList = paths.map { p ->
            val entity = allByPath[p]
            PdfFastCacheManager.evictByPath(
                context = context,
                path = p,
                title = entity?.title.orEmpty(),
                sizeBytes = entity?.sizeBytes ?: 0L
            )
            DeletedLocalDoc(
                path = p,
                title = entity?.title ?: File(p).name,
                sizeBytes = entity?.sizeBytes ?: 0L
            )
        }
        _deletedLocalDocsFlow.tryEmit(deletedList)
    }

    /**
     * Used when a document is deleted from the user's Google Account / Cloud:
     * removes the local physical file, Room entry, and fast mobile cache without
     * re-emitting another cloud delete event.
     */
    suspend fun removeDeletedCloudCopy(path: String) = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        val existing = dao.getPdfByPath(path)
        deletePhysicalFiles(listOf(path))
        dao.deletePdf(path)
        PdfFastCacheManager.evictByPath(
            context = context,
            path = path,
            title = existing?.title.orEmpty(),
            sizeBytes = existing?.sizeBytes ?: 0L
        )
    }

    private fun deletePhysicalFiles(paths: List<String>) {
        for (path in paths) {
            try {
                if (path.startsWith("content://")) {
                    context.contentResolver.delete(Uri.parse(path), null, null)
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (_: Exception) {}
        }
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
        PdfFastCacheManager.evictByPath(context, oldPath)
        dao.getPdfByPath(newFile.absolutePath)?.let { renamed ->
            PdfFastCacheManager.warmCacheAsync(context, renamed)
        }
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
