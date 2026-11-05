package com.example.pdf.render

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/**
 * Immutable representation of a single rendered PDF page to allow
 * Compose compiler to skip redundant recompositions during high refresh rate (120Hz) scrolling.
 */
@Immutable
data class PdfPageRenderState(
    val pageIndex: Int,
    val bitmap: Bitmap? = null,
    val previewBitmap: Bitmap? = null,
    val aspectRatio: Float = 0.707f, // Standard A4 (595 / 842) default to avoid layout jump
    val isLoading: Boolean = false,
    val isError: Boolean = false
)

/**
 * Immutable configuration for rendering quality, scaling, and prefetching.
 */
@Immutable
data class PdfRenderConfig(
    val scaleFactor: Float = 2.2f, // Crisp Ultra-HD rendering on modern density displays and high zoom
    val prefetchWindow: Int = 4,   // ±4 pages ahead/behind active viewport for 0-delay instant display
    val maxMemoryHeapRatio: Float = 0.25f, // Use up to 25% of app heap for bitmap cache
    val lowResScaleFactor: Float = 0.4f    // Fast blur-up thumbnail for rapid flings
)
