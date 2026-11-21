package com.example.pdf.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Singleton thumbnail loader and multi-tier cache (Memory LRU + Disk Cache)
 * for rendering pre-open previews of PDF documents across file lists, cards, and quick preview dialogs.
 */
object PdfDocumentThumbnailLoader {

    private data class CachedEntry(
        val bitmap: Bitmap,
        val imageBitmap: ImageBitmap
    )

    // 25MB Memory LRU Cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(10 * 1024, 25 * 1024) // in KB

    private val memoryCache = object : LruCache<String, CachedEntry>(cacheSize) {
        override fun sizeOf(key: String, value: CachedEntry): Int {
            return (value.bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    // Bound native PdfRenderer concurrency to prevent thread/RAM thrashing during fast flings
    private val renderSemaphore = Semaphore(permits = 2)

    private val diskDecodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    /**
     * Synchronous zero-allocation memory cache lookup for instant Compose rendering without flicker.
     */
    fun getCachedImageBitmap(
        entity: PdfEntity,
        targetWidth: Int = 180
    ): ImageBitmap? {
        if (entity.documentType != DocumentType.PDF) return null
        val cacheKey = buildCacheKey(entity.path, entity.lastModifiedTimestamp, targetWidth)
        return memoryCache.get(cacheKey)?.imageBitmap
    }

    /**
     * Retrieves or asynchronously renders a thumbnail as a Compose ImageBitmap.
     * Checks memory cache -> disk cache -> debounced native PdfRenderer.
     */
    suspend fun loadThumbnailImageBitmap(
        context: Context,
        entity: PdfEntity,
        targetWidth: Int = 180,
        targetHeight: Int = 240,
        delayBeforeNativeRenderMs: Long = 50L
    ): ImageBitmap? {
        if (entity.documentType != DocumentType.PDF) return null
        val cacheKey = buildCacheKey(entity.path, entity.lastModifiedTimestamp, targetWidth)
        memoryCache.get(cacheKey)?.let { return it.imageBitmap }
        val bmp = loadThumbnailInternal(
            context = context,
            entity = entity,
            cacheKey = cacheKey,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            delayBeforeNativeRenderMs = delayBeforeNativeRenderMs
        ) ?: return null
        return memoryCache.get(cacheKey)?.imageBitmap ?: bmp.asImageBitmap()
    }

    /**
     * Retrieves or asynchronously renders a low-resolution thumbnail of the document's first page.
     * High-speed lookup: Memory -> Disk -> Native PdfRenderer.
     */
    suspend fun loadThumbnail(
        context: Context,
        entity: PdfEntity,
        targetWidth: Int = 180,
        targetHeight: Int = 240
    ): Bitmap? {
        if (entity.documentType != DocumentType.PDF) return null
        val cacheKey = buildCacheKey(entity.path, entity.lastModifiedTimestamp, targetWidth)
        memoryCache.get(cacheKey)?.let { return it.bitmap }
        return loadThumbnailInternal(
            context = context,
            entity = entity,
            cacheKey = cacheKey,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            delayBeforeNativeRenderMs = 0L
        )
    }

    private suspend fun loadThumbnailInternal(
        context: Context,
        entity: PdfEntity,
        cacheKey: String,
        targetWidth: Int,
        targetHeight: Int,
        delayBeforeNativeRenderMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        // 1. Memory Cache check
        memoryCache.get(cacheKey)?.let { return@withContext it.bitmap }

        // 2. Disk Cache check (fast, no fling delay needed)
        val diskCacheDir = File(context.cacheDir, "doc_thumbnails").apply { if (!exists()) mkdirs() }
        val diskFile = File(diskCacheDir, "$cacheKey.jpg")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val diskBmp = BitmapFactory.decodeFile(diskFile.absolutePath, diskDecodeOptions)
                if (diskBmp != null) {
                    memoryCache.put(cacheKey, CachedEntry(diskBmp, diskBmp.asImageBitmap()))
                    return@withContext diskBmp
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        // Skip native PdfRenderer work if item scrolls off-screen immediately during a fast fling
        if (delayBeforeNativeRenderMs > 0L) {
            delay(delayBeforeNativeRenderMs)
        }

        // 3. Render Page 0 via native Android PdfRenderer (concurrency-bounded)
        renderSemaphore.withPermit {
            // Double-check cache in case another coroutine just populated it
            memoryCache.get(cacheKey)?.let { return@withContext it.bitmap }

            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var page: PdfRenderer.Page? = null

            try {
                val file = File(entity.path)
                pfd = if (file.exists() && file.canRead()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else if (entity.uriString.isNotBlank()) {
                    try {
                        context.contentResolver.openFileDescriptor(Uri.parse(entity.uriString), "r")
                    } catch (e: Exception) {
                        null
                    }
                } else null

                if (pfd == null) return@withContext null

                renderer = PdfRenderer(pfd)
                if (renderer.pageCount == 0) return@withContext null

                page = renderer.openPage(0)

                val pageW = page.width
                val pageH = page.height
                val scale = (targetWidth.toFloat() / pageW.toFloat()).coerceAtMost(targetHeight.toFloat() / pageH.toFloat())
                val renderW = (pageW * scale).toInt().coerceIn(80, 480)
                val renderH = (pageH * scale).toInt().coerceIn(100, 640)

                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Cache in Memory
                memoryCache.put(cacheKey, CachedEntry(bitmap, bitmap.asImageBitmap()))

                // Save to Disk Cache
                try {
                    FileOutputStream(diskFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                } catch (e: Exception) {
                    // Ignore disk write errors
                }

                return@withContext bitmap
            } catch (e: Exception) {
                // Document might be password-protected or encrypted
                return@withContext null
            } finally {
                try { page?.close() } catch (e: Exception) {}
                try { renderer?.close() } catch (e: Exception) {}
                try { pfd?.close() } catch (e: Exception) {}
            }
        }
    }

    /**
     * Evicts cached memory and disk thumbnails associated with a deleted file path.
     */
    fun evictByPath(context: Context, path: String) {
        if (path.isBlank()) return
        val snapshot = memoryCache.snapshot()
        snapshot.keys.forEach { key ->
            if (key.contains(path.hashCode().toString()) || key.startsWith(buildPathPrefix(path))) {
                memoryCache.remove(key)
            }
        }
        try {
            val prefix = buildPathPrefix(path)
            val diskCacheDir = File(context.applicationContext.cacheDir, "doc_thumbnails")
            diskCacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(prefix)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Clears all memory and disk thumbnail caches.
     */
    fun clearCache(context: Context) {
        memoryCache.evictAll()
        try {
            val diskCacheDir = File(context.cacheDir, "doc_thumbnails")
            diskCacheDir.deleteRecursively()
        } catch (e: Exception) {}
    }

    private fun buildPathPrefix(path: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(path.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(12)
        } catch (_: Exception) {
            path.hashCode().toString()
        }
    }

    private fun buildCacheKey(path: String, timestamp: Long, width: Int): String {
        val prefix = buildPathPrefix(path)
        val raw = "$path-$timestamp-$width"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(raw.toByteArray())
            val suffix = bytes.joinToString("") { "%02x".format(it) }.take(16)
            "${prefix}_$suffix"
        } catch (e: Exception) {
            "${prefix}_${timestamp}_$width"
        }
    }
}
