package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ReaderThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class PdfViewerScrollMode {
    CONTINUOUS_VERTICAL,
    PAGED_HORIZONTAL
}

@Composable
fun ComposePdfViewer(
    file: File,
    modifier: Modifier = Modifier,
    scrollMode: PdfViewerScrollMode = PdfViewerScrollMode.CONTINUOUS_VERTICAL,
    readerTheme: ReaderThemeMode = ReaderThemeMode.LIGHT,
    onPageChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    var pageCount by remember { mutableIntStateOf(0) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(file) {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(descriptor)
            pfd = descriptor
            renderer = pdfRenderer
            pageCount = pdfRenderer.pageCount
            isLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }

        onDispose {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
        }
    }

    if (isLoading || renderer == null || pageCount == 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val colorFilter = when (readerTheme) {
        ReaderThemeMode.LIGHT -> null
        ReaderThemeMode.SEPIA -> {
            val matrix = ColorMatrix().apply {
                setToScale(0.96f, 0.90f, 0.78f, 1f)
            }
            ColorFilter.colorMatrix(matrix)
        }
        ReaderThemeMode.DARK -> {
            // Invert colors for dark mode
            val matrix = ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ColorFilter.colorMatrix(matrix)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (scrollMode == PdfViewerScrollMode.CONTINUOUS_VERTICAL) {
            ContinuousVerticalViewer(
                renderer = renderer!!,
                pageCount = pageCount,
                colorFilter = colorFilter,
                onPageChanged = onPageChanged
            )
        } else {
            PagedHorizontalViewer(
                renderer = renderer!!,
                pageCount = pageCount,
                colorFilter = colorFilter,
                onPageChanged = onPageChanged
            )
        }
    }
}

@Composable
private fun ContinuousVerticalViewer(
    renderer: PdfRenderer,
    pageCount: Int,
    colorFilter: ColorFilter?,
    onPageChanged: (Int, Int) -> Unit
) {
    val listState = rememberLazyListState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = if (scale > 1f) {
            Offset(offset.x + panChange.x, offset.y + panChange.y)
        } else {
            Offset.Zero
        }
    }

    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(firstVisibleItemIndex) {
        val currentPage = (firstVisibleItemIndex + 1).coerceIn(1, pageCount)
        onPageChanged(currentPage, pageCount)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            items(pageCount, key = { it }) { index ->
                PdfPageItem(
                    renderer = renderer,
                    pageIndex = index,
                    colorFilter = colorFilter
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Floating Page Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = "Page ${firstVisibleItemIndex + 1} of $pageCount",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PagedHorizontalViewer(
    renderer: PdfRenderer,
    pageCount: Int,
    colorFilter: ColorFilter?,
    onPageChanged: (Int, Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage + 1, pageCount)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                PdfPageItem(
                    renderer = renderer,
                    pageIndex = pageIndex,
                    colorFilter = colorFilter
                )
            }
        }

        // Page Indicator
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / $pageCount",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun PdfPageItem(
    renderer: PdfRenderer,
    pageIndex: Int,
    colorFilter: ColorFilter?
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                val width = page.width * 2
                val height = page.height * 2
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(2.dp, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(Color.LightGray.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
