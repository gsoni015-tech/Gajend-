package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRendererHelper(private val context: Context) {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentFile: File? = null

    // In-memory LRU Bitmap cache for fast scrolling (e.g. 20MB)
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    suspend fun openFile(file: File): Int = withContext(Dispatchers.IO) {
        close()
        currentFile = file
        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fileDescriptor!!)
        renderer?.pageCount ?: 0
    }

    suspend fun openUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        close()
        // If it's a file uri or content uri, copy or open directly
        fileDescriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            // Copy to temp cache
            val tempFile = File(context.cacheDir, "temp_view.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            currentFile = tempFile
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        fileDescriptor?.let {
            renderer = PdfRenderer(it)
            renderer?.pageCount ?: 0
        } ?: 0
    }

    fun getPageCount(): Int {
        return renderer?.pageCount ?: 0
    }

    suspend fun renderPage(pageIndex: Int, scale: Float = 2.0f): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${currentFile?.path}_page_${pageIndex}_scale_${scale}"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val r = renderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= r.pageCount) return@withContext null

        synchronized(r) {
            var page: PdfRenderer.Page? = null
            try {
                page = r.openPage(pageIndex)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Fill white background so transparent PDF pages render cleanly
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                memoryCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                page?.close()
            }
        }
    }

    fun close() {
        try {
            renderer?.close()
        } catch (_: Exception) {}
        renderer = null
        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
        fileDescriptor = null
    }

    companion object {
        fun getPageCountForFile(file: File): Int {
            return try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { r ->
                        r.pageCount
                    }
                }
            } catch (e: Exception) {
                1
            }
        }

        fun generateThumbnail(file: File, widthPx: Int = 180, heightPx: Int = 240): Bitmap? {
            return try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { r ->
                        if (r.pageCount > 0) {
                            r.openPage(0).use { page ->
                                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap
                            }
                        } else null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
