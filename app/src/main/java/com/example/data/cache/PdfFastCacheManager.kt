package com.example.data.cache

import android.content.Context
import android.net.Uri
import com.example.data.cloud.CloudDocumentIdentity
import com.example.data.local.PdfEntity
import com.example.pdf.thumbnail.PdfDocumentThumbnailLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class CacheStats(
    val fileCount: Int = 0,
    val totalBytes: Long = 0L
)

/**
 * High-speed local mobile cache for PDF & document files.
 * - Keeps recently opened / synced PDFs in app cacheDir for instant zero-lag opening.
 * - Enforces an LRU storage budget (250 MB).
 * - Automatically evicts cached files and thumbnails whenever deleted from PDF Reader,
 *   device gallery/storage, or Google Cloud account.
 */
object PdfFastCacheManager {
    private const val CACHE_DIR_NAME = "fast_pdf_cache"
    private const val PREFS_NAME = "fast_pdf_cache_index"
    private const val MAX_CACHE_BYTES = 250L * 1024L * 1024L // 250 MB

    private val lock = Any()
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _cacheStats = MutableStateFlow(CacheStats())
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    private fun cacheDir(context: Context): File {
        return File(context.applicationContext.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun refreshStats(context: Context) {
        val dir = cacheDir(context)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        _cacheStats.value = CacheStats(
            fileCount = files.size,
            totalBytes = files.sumOf { it.length() }
        )
    }

    fun warmCacheAsync(context: Context, entity: PdfEntity) {
        val appCtx = context.applicationContext
        backgroundScope.launch {
            getOrWarmFastFile(appCtx, entity)
        }
    }

    suspend fun getOrWarmFastFile(context: Context, entity: PdfEntity): File? = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val cloudId = CloudDocumentIdentity.buildDocId(entity)
        val cached = getCachedFileForEntity(appCtx, entity, cloudId)
        if (cached != null) {
            touchIndex(appCtx, cloudId, entity.path, cached.name, cached.length(), entity.lastModifiedTimestamp)
            refreshStats(appCtx)
            return@withContext cached
        }

        val ext = entity.title.substringAfterLast('.', "pdf").lowercase().ifBlank { "pdf" }
        val targetFile = File(cacheDir(appCtx), "$cloudId.$ext")
        val tempFile = File(cacheDir(appCtx), "$cloudId.$ext.tmp")

        try {
            val copied = if (!entity.path.startsWith("content://")) {
                val src = File(entity.path)
                if (src.exists() && src.canRead() && src.length() > 0L) {
                    FileInputStream(src).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output, bufferSize = 65_536)
                        }
                    }
                    true
                } else {
                    false
                }
            } else if (entity.uriString.isNotBlank()) {
                appCtx.contentResolver.openInputStream(Uri.parse(entity.uriString))?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, bufferSize = 65_536)
                    }
                    true
                } ?: false
            } else {
                false
            }

            if (copied && tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                touchIndex(
                    appCtx,
                    cloudId,
                    entity.path,
                    targetFile.name,
                    targetFile.length(),
                    entity.lastModifiedTimestamp
                )
                enforceLruBudget(appCtx, protectedFileName = targetFile.name)
                refreshStats(appCtx)
                return@withContext targetFile
            } else {
                tempFile.delete()
            }
        } catch (_: Exception) {
            tempFile.delete()
        }

        val fallback = File(entity.path)
        if (fallback.exists() && fallback.canRead()) fallback else null
    }

    fun getFastReadableFileIfPresent(context: Context, entity: PdfEntity): File? {
        val cloudId = CloudDocumentIdentity.buildDocId(entity)
        val cached = getCachedFileForEntity(context.applicationContext, entity, cloudId)
        if (cached != null) return cached
        val direct = File(entity.path)
        return if (!entity.path.startsWith("content://") && direct.exists() && direct.canRead()) direct else null
    }

    fun getCachedFileByCloudId(context: Context, cloudId: String, titleHint: String = ""): File? {
        val appCtx = context.applicationContext
        val dir = cacheDir(appCtx)
        val indexedName = readEntry(appCtx, cloudId)?.optString("fileName").orEmpty()
        if (indexedName.isNotBlank()) {
            val f = File(dir, indexedName)
            if (f.exists() && f.length() > 0L) return f
        }
        val ext = titleHint.substringAfterLast('.', "pdf").lowercase().ifBlank { "pdf" }
        val candidate = File(dir, "$cloudId.$ext")
        if (candidate.exists() && candidate.length() > 0L) return candidate
        return dir.listFiles()?.firstOrNull { it.isFile && it.name.startsWith("$cloudId.") && it.length() > 0L }
    }

    fun createCacheTargetFile(context: Context, cloudId: String, title: String): File {
        val ext = title.substringAfterLast('.', "pdf").lowercase().ifBlank { "pdf" }
        return File(cacheDir(context), "$cloudId.$ext")
    }

    fun registerCloudCachedFile(
        context: Context,
        cloudId: String,
        file: File,
        localPath: String = ""
    ) {
        if (!file.exists() || file.length() <= 0L) return
        val appCtx = context.applicationContext
        touchIndex(appCtx, cloudId, localPath, file.name, file.length(), file.lastModified())
        enforceLruBudget(appCtx, protectedFileName = file.name)
        refreshStats(appCtx)
    }

    fun evictByEntity(context: Context, entity: PdfEntity) {
        val cloudId = CloudDocumentIdentity.buildDocId(entity)
        evict(context, cloudId = cloudId, localPath = entity.path)
    }

    fun evictByPath(context: Context, path: String, title: String = "", sizeBytes: Long = 0L) {
        val cloudId = if (title.isNotBlank() && sizeBytes > 0L) {
            CloudDocumentIdentity.buildDocId(title = title, sizeBytes = sizeBytes, path = path)
        } else {
            findCloudIdByPath(context, path)
        }
        evict(context, cloudId = cloudId, localPath = path)
    }

    fun evictByCloudId(context: Context, cloudId: String) {
        evict(context, cloudId = cloudId, localPath = null)
    }

    fun evict(context: Context, cloudId: String?, localPath: String?) {
        val appCtx = context.applicationContext
        val dir = cacheDir(appCtx)
        val p = prefs(appCtx)

        synchronized(lock) {
            val resolvedCloudIds = mutableSetOf<String>()
            if (!cloudId.isNullOrBlank()) resolvedCloudIds.add(cloudId)
            if (!localPath.isNullOrBlank()) {
                p.all.forEach { (key, value) ->
                    val jsonStr = value as? String ?: return@forEach
                    try {
                        val obj = JSONObject(jsonStr)
                        if (obj.optString("localPath") == localPath) {
                            resolvedCloudIds.add(key)
                        }
                    } catch (_: Exception) {}
                }
            }

            val editor = p.edit()
            resolvedCloudIds.forEach { id ->
                val obj = readEntry(appCtx, id)
                val fileName = obj?.optString("fileName").orEmpty()
                if (fileName.isNotBlank()) {
                    File(dir, fileName).delete()
                }
                dir.listFiles()?.forEach { f ->
                    if (f.name.startsWith("$id.")) {
                        f.delete()
                    }
                }
                val indexedPath = obj?.optString("localPath").orEmpty()
                if (indexedPath.isNotBlank()) {
                    PdfDocumentThumbnailLoader.evictByPath(appCtx, indexedPath)
                }
                editor.remove(id)
            }
            editor.apply()
        }

        if (!localPath.isNullOrBlank()) {
            PdfDocumentThumbnailLoader.evictByPath(appCtx, localPath)
        }
        refreshStats(appCtx)
    }

    fun clearAllCache(context: Context) {
        val appCtx = context.applicationContext
        synchronized(lock) {
            cacheDir(appCtx).deleteRecursively()
            cacheDir(appCtx)
            prefs(appCtx).edit().clear().apply()
        }
        PdfDocumentThumbnailLoader.clearCache(appCtx)
        refreshStats(appCtx)
    }

    private fun getCachedFileForEntity(context: Context, entity: PdfEntity, cloudId: String): File? {
        val dir = cacheDir(context)
        val entry = readEntry(context, cloudId)
        val expectedFileName = entry?.optString("fileName").orEmpty()
        val candidate = if (expectedFileName.isNotBlank()) {
            File(dir, expectedFileName)
        } else {
            val ext = entity.title.substringAfterLast('.', "pdf").lowercase().ifBlank { "pdf" }
            File(dir, "$cloudId.$ext")
        }
        if (!candidate.exists() || candidate.length() <= 0L) return null
        if (entity.sizeBytes > 0L && candidate.length() != entity.sizeBytes) return null
        return candidate
    }

    private fun findCloudIdByPath(context: Context, path: String): String? {
        val p = prefs(context)
        p.all.forEach { (key, value) ->
            val str = value as? String ?: return@forEach
            try {
                if (JSONObject(str).optString("localPath") == path) return key
            } catch (_: Exception) {}
        }
        return null
    }

    private fun readEntry(context: Context, cloudId: String): JSONObject? {
        val raw = prefs(context).getString(cloudId, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun touchIndex(
        context: Context,
        cloudId: String,
        localPath: String,
        fileName: String,
        sizeBytes: Long,
        lastModifiedMs: Long
    ) {
        synchronized(lock) {
            val json = JSONObject().apply {
                put("localPath", localPath)
                put("fileName", fileName)
                put("sizeBytes", sizeBytes)
                put("lastModifiedMs", lastModifiedMs)
                put("lastAccessedMs", System.currentTimeMillis())
            }
            prefs(context).edit().putString(cloudId, json.toString()).apply()
        }
    }

    private fun enforceLruBudget(context: Context, protectedFileName: String) {
        val dir = cacheDir(context)
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return

        val p = prefs(context)
        val accessMap = mutableMapOf<String, Long>()
        p.all.forEach { (_, value) ->
            val str = value as? String ?: return@forEach
            try {
                val obj = JSONObject(str)
                val name = obj.optString("fileName")
                val accessed = obj.optLong("lastAccessedMs", 0L)
                if (name.isNotBlank()) accessMap[name] = accessed
            } catch (_: Exception) {}
        }

        val sortedOldestFirst = files
            .filter { it.name != protectedFileName }
            .sortedBy { accessMap[it.name] ?: it.lastModified() }

        for (file in sortedOldestFirst) {
            if (total <= MAX_CACHE_BYTES) break
            val len = file.length()
            if (file.delete()) {
                total -= len
                val cloudId = file.nameWithoutExtension
                p.edit().remove(cloudId).apply()
            }
        }
    }
}
