package com.example.pdf.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.collection.LruCache
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Singleton thumbnail loader and multi-tier cache (Memory LRU + Disk Cache)
 * for rendering pre-open previews of PDF documents across file lists, cards, and quick preview dialogs.
 */
object PdfDocumentThumbnailLoader {

    // 25MB Memory LRU Cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(10 * 1024, 25 * 1024) // in KB

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
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
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (entity.documentType != DocumentType.PDF) {
            return@withContext null
        }

        val cacheKey = buildCacheKey(entity.path, entity.lastModifiedTimestamp, targetWidth)

        // 1. Memory Cache check
        memoryCache.get(cacheKey)?.let { return@withContext it }

        // 2. Disk Cache check
        val diskCacheDir = File(context.cacheDir, "doc_thumbnails").apply { if (!exists()) mkdirs() }
        val diskFile = File(diskCacheDir, "$cacheKey.jpg")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val diskBmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (diskBmp != null) {
                    memoryCache.put(cacheKey, diskBmp)
                    return@withContext diskBmp
                }
            } catch (e: Exception) {
                diskFile.delete()
            }
        }

        // 3. Render Page 0 via native Android PdfRenderer
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
            memoryCache.put(cacheKey, bitmap)

            // Save to Disk Cache asynchronously
            try {
                FileOutputStream(diskFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
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

    private fun buildCacheKey(path: String, timestamp: Long, width: Int): String {
        val raw = "$path-$timestamp-$width"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(raw.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            raw.replace("[^a-zA-Z0-9]".toRegex(), "_")
        }
    }
}
