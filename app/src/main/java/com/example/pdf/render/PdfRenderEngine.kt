package com.example.pdf.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 120Hz Ultra-Smooth Asynchronous Background PDF Rendering Engine.
 * 
 * Features:
 * - Offloads all decoding & native rasterization strictly to Dispatchers.IO
 * - Thread-safe Mutex serialization for Android native PdfRenderer
 * - Predictive bidirectional prefetching (±4 pages) around visible viewport
 * - Zero Main-Thread contention, enabling 120fps jank-free scrolling
 */
class PdfRenderEngine(
    private val renderer: PdfRenderer,
    val cache: PdfPageCache = PdfPageCache(),
    private val config: PdfRenderConfig = PdfRenderConfig()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rendererMutex = Mutex()
    private var prefetchJob: Job? = null

    val pageCount: Int
        get() = renderer.pageCount

    init {
        // Immediately inspect the first page on background thread to establish standard aspect ratio
        scope.launch {
            try {
                rendererMutex.withLock {
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val ratio = if (page.height > 0) page.width.toFloat() / page.height.toFloat() else 0.707f
                        cache.setAspectRatio(0, ratio)
                        page.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Renders a specific page at HD resolution.
     * Checks LruCache first. If missing, renders asynchronously on Dispatchers.IO.
     */
    suspend fun getOrRenderPage(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until pageCount) return@withContext null

        // 1. Fast cache hit
        cache.getHighRes(pageIndex)?.let { return@withContext it }

        // 2. Render from native engine under Mutex lock
        try {
            rendererMutex.withLock {
                // Double check cache under lock
                cache.getHighRes(pageIndex)?.let { return@withContext it }

                val page = renderer.openPage(pageIndex)
                val originalW = page.width
                val originalH = page.height
                val aspectRatio = if (originalH > 0) originalW.toFloat() / originalH.toFloat() else 0.707f
                cache.setAspectRatio(pageIndex, aspectRatio)

                val targetW = (originalW * config.scaleFactor).toInt().coerceAtLeast(100)
                val targetH = (originalH * config.scaleFactor).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                cache.putHighRes(pageIndex, bitmap)
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fast lightweight thumbnail rendering for the side drawer or bottom thumbnail strip.
     * Uses low resolution scaling (0.4x) and lowResCache for minimal memory and instant display.
     */
    suspend fun getOrRenderThumbnail(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until pageCount) return@withContext null

        cache.getLowRes(pageIndex)?.let { return@withContext it }
        cache.getHighRes(pageIndex)?.let { return@withContext it }

        try {
            rendererMutex.withLock {
                cache.getLowRes(pageIndex)?.let { return@withContext it }
                cache.getHighRes(pageIndex)?.let { return@withContext it }

                val page = renderer.openPage(pageIndex)
                val originalW = page.width
                val originalH = page.height
                val aspectRatio = if (originalH > 0) originalW.toFloat() / originalH.toFloat() else 0.707f
                cache.setAspectRatio(pageIndex, aspectRatio)

                val targetW = (originalW * config.lowResScaleFactor).toInt().coerceAtLeast(60)
                val targetH = (originalH * config.lowResScaleFactor).toInt().coerceAtLeast(80)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                cache.putLowRes(pageIndex, bitmap)
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Proactively prefetches pages around the visible range (e.g. current page ± 4)
     * Ordered by proximity so next visible pages are rendered first.
     */
    fun prefetchSurroundingPages(visibleCenterIndex: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val total = pageCount
            val window = config.prefetchWindow
            
            // Generate priority list: center, center+1, center-1, center+2, center-2, ...
            val prefetchList = mutableListOf<Int>()
            for (step in 1..window) {
                val next = visibleCenterIndex + step
                val prev = visibleCenterIndex - step
                if (next in 0 until total) prefetchList.add(next)
                if (prev in 0 until total) prefetchList.add(prev)
            }

            for (pageIndex in prefetchList) {
                if (cache.getHighRes(pageIndex) == null) {
                    try {
                        rendererMutex.withLock {
                            if (cache.getHighRes(pageIndex) == null) {
                                val page = renderer.openPage(pageIndex)
                                val originalW = page.width
                                val originalH = page.height
                                val aspectRatio = if (originalH > 0) originalW.toFloat() / originalH.toFloat() else 0.707f
                                cache.setAspectRatio(pageIndex, aspectRatio)

                                val targetW = (originalW * config.scaleFactor).toInt().coerceAtLeast(100)
                                val targetH = (originalH * config.scaleFactor).toInt().coerceAtLeast(100)

                                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)

                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()

                                cache.putHighRes(pageIndex, bitmap)
                            }
                        }
                    } catch (e: Exception) {
                        // Prefetch failures are non-critical
                    }
                }
            }
        }
    }

    fun close() {
        scope.cancel()
        cache.clear()
    }
}
