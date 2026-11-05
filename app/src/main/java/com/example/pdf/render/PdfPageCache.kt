package com.example.pdf.render

import android.graphics.Bitmap
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance double-buffered memory cache for PDF page bitmaps and aspect ratios.
 * Uses system runtime memory limits to prevent OutOfMemoryError while maximizing
 * instant frame cache hits for 120Hz smooth scrolling.
 */
class PdfPageCache(
    config: PdfRenderConfig = PdfRenderConfig()
) {
    // Determine dynamic cache capacity based on actual runtime heap size
    private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val highResCacheSizeKb = (maxMemoryKb * config.maxMemoryHeapRatio).toInt().coerceAtLeast(16 * 1024)
    private val lowResCacheSizeKb = (highResCacheSizeKb / 8).coerceAtLeast(4 * 1024)

    // Full HD resolution bitmap cache for active and nearby pages
    private val highResCache = object : LruCache<Int, Bitmap>(highResCacheSizeKb) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }

        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let GC reclaim memory smoothly without manual recycle to avoid Race Conditions with Compose Image drawing
        }
    }

    // Low resolution fast-rendering thumbnails for instant display during fast flings
    private val lowResCache = object : LruCache<Int, Bitmap>(lowResCacheSizeKb) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    // Cached page aspect ratios (width / height) to completely prevent LazyColumn layout shifts
    private val aspectRatios = ConcurrentHashMap<Int, Float>()

    fun getHighRes(pageIndex: Int): Bitmap? {
        return highResCache.get(pageIndex)
    }

    fun putHighRes(pageIndex: Int, bitmap: Bitmap) {
        highResCache.put(pageIndex, bitmap)
    }

    fun getLowRes(pageIndex: Int): Bitmap? {
        return lowResCache.get(pageIndex)
    }

    fun putLowRes(pageIndex: Int, bitmap: Bitmap) {
        lowResCache.put(pageIndex, bitmap)
    }

    fun getAspectRatio(pageIndex: Int): Float {
        return aspectRatios[pageIndex] ?: 0.707f // Fallback to standard A4 ratio until measured
    }

    fun setAspectRatio(pageIndex: Int, ratio: Float) {
        aspectRatios[pageIndex] = ratio
    }

    fun clear() {
        highResCache.evictAll()
        lowResCache.evictAll()
        aspectRatios.clear()
    }
}
