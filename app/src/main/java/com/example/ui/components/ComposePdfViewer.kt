package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.render.PdfRenderEngine
import com.example.ui.viewmodel.ReaderThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

enum class PdfViewerScrollMode {
    CONTINUOUS_VERTICAL,
    PAGED_HORIZONTAL
}

/**
 * High-performance 120Hz Ultra-Smooth Jetpack Compose PDF Viewer.
 *
 * Implements:
 * 1. Asynchronous Background Rendering (Dispatchers.IO) with Mutex serialization.
 * 2. Smart Double-Buffered LruCache with dynamic memory allocation.
 * 3. Proactive Bidirectional Prefetching (±4 pages around active viewport).
 * 4. Zero-Jank Aspect Ratio preservation preventing LazyColumn layout shifts.
 * 5. Smooth multi-touch Pinch-to-Zoom (1.0x to 4.0x) with bounds clamping.
 * 6. High-speed Animated Double-Tap Zoom-In (2.5x) and Double-Tap Zoom-Out (1.0x).
 * 7. Quick 1-tap "Reset Zoom" floating pill indicator when magnified.
 */
@Composable
fun ComposePdfViewer(
    file: File,
    modifier: Modifier = Modifier,
    uriString: String? = null,
    scrollMode: PdfViewerScrollMode = PdfViewerScrollMode.CONTINUOUS_VERTICAL,
    readerTheme: ReaderThemeMode = ReaderThemeMode.LIGHT,
    targetPage: Int? = null,
    showThumbnailBottomSheet: Boolean = false,
    showThumbnailDrawer: Boolean = false,
    showThumbnailStrip: Boolean = false,
    isPageLocked: Boolean = true,
    onCloseThumbnailBottomSheet: () -> Unit = {},
    onCloseThumbnailDrawer: () -> Unit = {},
    onCloseThumbnailStrip: () -> Unit = {},
    onThumbnailPageSelected: (Int) -> Unit = {},
    onPagePillClick: () -> Unit = {},
    onPageLongPress: (Int) -> Unit = {},
    onPageChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var renderEngine by remember { mutableStateOf<PdfRenderEngine?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activePageNumber by remember { mutableIntStateOf(1) }

    DisposableEffect(file, uriString) {
        var tempFileToClean: File? = null
        try {
            val descriptor: ParcelFileDescriptor? = if (file.exists() && file.canRead()) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else if (!uriString.isNullOrBlank()) {
                try {
                    context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                } catch (e: Exception) {
                    null
                }
            } else {
                try {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e: Exception) {
                    null
                }
            } ?: run {
                val uriToTry = if (!uriString.isNullOrBlank()) Uri.parse(uriString) else Uri.fromFile(file)
                try {
                    val cacheTemp = File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uriToTry)?.use { input ->
                        cacheTemp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (cacheTemp.exists() && cacheTemp.length() > 0) {
                        tempFileToClean = cacheTemp
                        ParcelFileDescriptor.open(cacheTemp, ParcelFileDescriptor.MODE_READ_ONLY)
                    } else null
                } catch (e2: Exception) {
                    null
                }
            }

            if (descriptor != null) {
                val pdfRenderer = PdfRenderer(descriptor)
                pfd = descriptor
                val engine = PdfRenderEngine(pdfRenderer)
                renderEngine = engine
                pageCount = engine.pageCount
                isLoading = false
            } else {
                isLoading = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }

        onDispose {
            try { renderEngine?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
            try { tempFileToClean?.delete() } catch (e: Exception) {}
        }
    }

    if (isLoading || renderEngine == null || pageCount == 0) {
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

    val handlePageChanged: (Int, Int) -> Unit = { page, total ->
        activePageNumber = page
        onPageChanged(page, total)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (scrollMode == PdfViewerScrollMode.CONTINUOUS_VERTICAL) {
            ContinuousVerticalViewer(
                renderEngine = renderEngine!!,
                pageCount = pageCount,
                colorFilter = colorFilter,
                targetPage = targetPage,
                isPageLocked = isPageLocked,
                onPageLongPress = onPageLongPress,
                onPagePillClick = onPagePillClick,
                onPageChanged = handlePageChanged
            )
        } else {
            PagedHorizontalViewer(
                renderEngine = renderEngine!!,
                pageCount = pageCount,
                colorFilter = colorFilter,
                targetPage = targetPage,
                isPageLocked = isPageLocked,
                onPageLongPress = onPageLongPress,
                onPagePillClick = onPagePillClick,
                onPageChanged = handlePageChanged
            )
        }

        // Floating Bottom Thumbnail Filmstrip
        PdfThumbnailBottomStrip(
            renderEngine = renderEngine!!,
            pageCount = pageCount,
            currentPage = activePageNumber,
            isVisible = showThumbnailStrip,
            colorFilter = colorFilter,
            onSelectPage = onThumbnailPageSelected,
            onClose = onCloseThumbnailStrip,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Slide-in Side Drawer Scrim Overlay
        AnimatedVisibility(
            visible = showThumbnailDrawer,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onCloseThumbnailDrawer)
            )
        }

        // Slide-in Side Drawer Sheet
        AnimatedVisibility(
            visible = showThumbnailDrawer,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            PdfThumbnailSideDrawer(
                renderEngine = renderEngine!!,
                pageCount = pageCount,
                currentPage = activePageNumber,
                colorFilter = colorFilter,
                onSelectPage = { page ->
                    onThumbnailPageSelected(page)
                    onCloseThumbnailDrawer()
                },
                onClose = onCloseThumbnailDrawer
            )
        }

        // Modal Bottom Sheet with Low-Res Page Thumbnails Grid
        if (showThumbnailBottomSheet && renderEngine != null) {
            PdfThumbnailBottomSheet(
                renderEngine = renderEngine!!,
                pageCount = pageCount,
                currentPage = activePageNumber,
                colorFilter = colorFilter,
                onSelectPage = { page ->
                    onThumbnailPageSelected(page)
                    onCloseThumbnailBottomSheet()
                },
                onDismissRequest = onCloseThumbnailBottomSheet
            )
        }
    }
}

/**
 * SmoothZoomableBox provides:
 * 1. Multi-touch smooth pinch-to-zoom with continuous focal point tracking.
 * 2. Instant double-tap to zoom in (2.5x) centered directly on the tapped area.
 * 3. Instant double-tap to zoom out (1.0x) back to fit-to-screen with smooth animation.
 * 4. Panning when zoomed in, with boundary clamping so content never floats away.
 * 5. Full native passthrough when scale is 1.0x so lists and pagers scroll at 120Hz.
 * 6. Floating "Tap to Reset" badge when magnified for quick 1-tap overview.
 */
@Composable
fun SmoothZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 4.0f,
    doubleTapScale: Float = 2.5f,
    isPageLocked: Boolean = true,
    onLongPress: (() -> Unit)? = null,
    content: @Composable BoxScope.(currentScale: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1.0f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    LaunchedEffect(isPageLocked) {
        if (isPageLocked) {
            coroutineScope.launch {
                offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val viewWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        fun clampOffsets(targetScale: Float, rawX: Float, rawY: Float): Pair<Float, Float> {
            val maxY = ((targetScale - 1f).coerceAtLeast(0f) * viewHeight) / 2f
            if (isPageLocked || targetScale <= 1.05f) {
                // When Page Locked: strictly lock horizontal offset to 0f! Page CANNOT move left or right!
                return Pair(0f, rawY.coerceIn(-maxY, maxY))
            }
            val maxX = ((targetScale - 1f).coerceAtLeast(0f) * viewWidth) / 2f
            return Pair(rawX.coerceIn(-maxX, maxX), rawY.coerceIn(-maxY, maxY))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewWidth, viewHeight, isPageLocked) {
                    var lastTapTime = 0L
                    var lastTapPosition = Offset.Zero
                    val doubleTapTimeout = 320L
                    val doubleTapSlop = 60f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = SystemClock.uptimeMillis()
                        val downPosition = down.position
                        var isMultiTouch = false
                        var hasMoved = false

                        val longPressJob = onLongPress?.let { callback ->
                            coroutineScope.launch {
                                delay(500)
                                if (!isMultiTouch && !hasMoved) {
                                    callback()
                                }
                            }
                        }

                        var prevDist = 0f
                        var prevCentroid = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            val pointerCount = pointers.size

                            if (pointerCount >= 2) {
                                isMultiTouch = true
                                longPressJob?.cancel()
                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                val currentDist = (p0 - p1).getDistance()
                                val currentCentroid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)

                                if (prevDist > 0f) {
                                    val zoomFactor = currentDist / prevDist
                                    val panDelta = currentCentroid - prevCentroid

                                    val oldScale = scaleAnim.value
                                    val newScale = (oldScale * zoomFactor).coerceIn(0.75f, 5.0f)
                                    val rawX = if (isPageLocked) 0f else (currentCentroid.x - (currentCentroid.x - offsetXAnim.value) * zoomFactor + panDelta.x)
                                    val rawY = currentCentroid.y - (currentCentroid.y - offsetYAnim.value) * zoomFactor + panDelta.y
                                    val (clampedX, clampedY) = clampOffsets(newScale, rawX, rawY)

                                    coroutineScope.launch { scaleAnim.snapTo(newScale) }
                                    coroutineScope.launch { offsetXAnim.snapTo(clampedX) }
                                    coroutineScope.launch { offsetYAnim.snapTo(clampedY) }

                                    event.changes.forEach { it.consume() }
                                }
                                prevDist = currentDist
                                prevCentroid = currentCentroid
                            } else if (pointerCount == 1) {
                                prevDist = 0f
                                if (!isMultiTouch) {
                                    val p = pointers[0]
                                    val distFromDown = (p.position - downPosition).getDistance()
                                    if (distFromDown > 12f) {
                                        hasMoved = true
                                        longPressJob?.cancel()
                                    }

                                    // When zoomed in, pan around the magnified document
                                    if (scaleAnim.value > 1.05f) {
                                        val panDelta = p.position - p.previousPosition
                                        val rawX = if (isPageLocked) 0f else (offsetXAnim.value + panDelta.x)
                                        val rawY = offsetYAnim.value + panDelta.y
                                        val (clampedX, clampedY) = clampOffsets(
                                            scaleAnim.value,
                                            rawX,
                                            rawY
                                        )
                                        coroutineScope.launch { offsetXAnim.snapTo(clampedX) }
                                        coroutineScope.launch { offsetYAnim.snapTo(clampedY) }
                                        p.consume()
                                    }
                                    // When scale <= 1.05f, we do NOT consume, allowing natural scrolling!
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        longPressJob?.cancel()

                        val releaseTime = SystemClock.uptimeMillis()

                        // Double tap detection
                        if (!isMultiTouch && !hasMoved && (releaseTime - downTime < 280L)) {
                            val timeSinceLast = releaseTime - lastTapTime
                            val distFromLast = (downPosition - lastTapPosition).getDistance()

                            if (timeSinceLast in 30L..doubleTapTimeout && distFromLast < doubleTapSlop) {
                                // DOUBLE TAP TRIGGERED!
                                lastTapTime = 0L
                                if (scaleAnim.value > 1.15f) {
                                    // Smooth Animated Zoom Out
                                    coroutineScope.launch {
                                        scaleAnim.animateTo(1.0f, tween(280, easing = FastOutSlowInEasing))
                                    }
                                    coroutineScope.launch {
                                        offsetXAnim.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                                    }
                                    coroutineScope.launch {
                                        offsetYAnim.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
                                    }
                                } else {
                                    // Smooth Animated Zoom In centered on tapped coordinates
                                    val targetScale = doubleTapScale
                                    val rawTargetX = if (isPageLocked) 0f else ((viewWidth / 2f - downPosition.x) * targetScale)
                                    val (targetX, targetY) = clampOffsets(
                                        targetScale,
                                        rawTargetX,
                                        (viewHeight / 2f - downPosition.y) * targetScale
                                    )
                                    coroutineScope.launch {
                                        scaleAnim.animateTo(targetScale, tween(280, easing = FastOutSlowInEasing))
                                    }
                                    coroutineScope.launch {
                                        offsetXAnim.animateTo(targetX, tween(280, easing = FastOutSlowInEasing))
                                    }
                                    coroutineScope.launch {
                                        offsetYAnim.animateTo(targetY, tween(280, easing = FastOutSlowInEasing))
                                    }
                                }
                            } else {
                                lastTapTime = releaseTime
                                lastTapPosition = downPosition
                            }
                        } else {
                            // Touch released after pinch or drag - spring back to legal boundaries
                            if (scaleAnim.value < minScale) {
                                coroutineScope.launch {
                                    scaleAnim.animateTo(minScale, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                coroutineScope.launch {
                                    offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                coroutineScope.launch {
                                    offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            } else if (scaleAnim.value > maxScale) {
                                coroutineScope.launch {
                                    scaleAnim.animateTo(maxScale, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                val (cx, cy) = clampOffsets(maxScale, offsetXAnim.value, offsetYAnim.value)
                                coroutineScope.launch {
                                    offsetXAnim.animateTo(cx, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                coroutineScope.launch {
                                    offsetYAnim.animateTo(cy, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                            } else {
                                val (cx, cy) = clampOffsets(scaleAnim.value, offsetXAnim.value, offsetYAnim.value)
                                if (cx != offsetXAnim.value || isPageLocked) {
                                    coroutineScope.launch {
                                        offsetXAnim.animateTo(if (isPageLocked) 0f else cx, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                                if (cy != offsetYAnim.value) {
                                    coroutineScope.launch {
                                        offsetYAnim.animateTo(cy, spring(stiffness = Spring.StiffnessMediumLow))
                                    }
                                }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = if (isPageLocked) 0f else offsetXAnim.value
                    translationY = offsetYAnim.value
                    clip = true
                }
        ) {
            content(scaleAnim.value)
        }

        // Quick 1-Tap Reset Zoom Badge when Magnified
        if (scaleAnim.value > 1.15f) {
            val roundedScale = (scaleAnim.value * 10).roundToInt() / 10f
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        coroutineScope.launch {
                            scaleAnim.animateTo(1.0f, tween(250, easing = FastOutSlowInEasing))
                        }
                        coroutineScope.launch {
                            offsetXAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                        }
                        coroutineScope.launch {
                            offsetYAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.8f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "${roundedScale}x • Tap to Reset",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun ContinuousVerticalViewer(
    renderEngine: PdfRenderEngine,
    pageCount: Int,
    colorFilter: ColorFilter?,
    targetPage: Int? = null,
    isPageLocked: Boolean = true,
    onPageLongPress: (Int) -> Unit = {},
    onPagePillClick: () -> Unit = {},
    onPageChanged: (Int, Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(targetPage) {
        if (targetPage != null && targetPage in 1..pageCount) {
            listState.scrollToItem(targetPage - 1)
        }
    }

    var displayPageNumber by remember { mutableIntStateOf(1) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstIndex ->
                val currentPage = (firstIndex + 1).coerceIn(1, pageCount)
                displayPageNumber = currentPage
                onPageChanged(currentPage, pageCount)
                renderEngine.prefetchSurroundingPages(firstIndex)
            }
    }

    SmoothZoomableBox(
        modifier = Modifier.fillMaxSize(),
        doubleTapScale = 2.5f,
        isPageLocked = isPageLocked,
        onLongPress = { onPageLongPress(displayPageNumber) }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(pageCount, key = { it }) { index ->
                PdfPageItem(
                    renderEngine = renderEngine,
                    pageIndex = index,
                    colorFilter = colorFilter
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Floating Page Indicator Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onPagePillClick),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = if (isPageLocked) "Page $displayPageNumber of $pageCount • 🔒 Locked" else "Page $displayPageNumber of $pageCount • Tap to Jump",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PagedHorizontalViewer(
    renderEngine: PdfRenderEngine,
    pageCount: Int,
    colorFilter: ColorFilter?,
    targetPage: Int? = null,
    isPageLocked: Boolean = true,
    onPageLongPress: (Int) -> Unit = {},
    onPagePillClick: () -> Unit = {},
    onPageChanged: (Int, Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })

    LaunchedEffect(targetPage) {
        if (targetPage != null && targetPage in 1..pageCount) {
            pagerState.scrollToPage(targetPage - 1)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                onPageChanged(page + 1, pageCount)
                renderEngine.prefetchSurroundingPages(page)
            }
    }

    SmoothZoomableBox(
        modifier = Modifier.fillMaxSize(),
        doubleTapScale = 2.5f,
        isPageLocked = isPageLocked,
        onLongPress = { onPageLongPress(pagerState.currentPage + 1) }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isPageLocked,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                PdfPageItem(
                    renderEngine = renderEngine,
                    pageIndex = pageIndex,
                    colorFilter = colorFilter
                )
            }
        }

        // Page Indicator
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onPagePillClick),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shadowElevation = 4.dp
        ) {
            Text(
                text = if (isPageLocked) "Page ${pagerState.currentPage + 1} of $pageCount • 🔒 Locked" else "Page ${pagerState.currentPage + 1} of $pageCount • Tap to Jump",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun PdfPageItem(
    renderEngine: PdfRenderEngine,
    pageIndex: Int,
    colorFilter: ColorFilter?,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(pageIndex) { mutableStateOf(renderEngine.cache.getHighRes(pageIndex)) }
    var pageRatio by remember(pageIndex) { mutableFloatStateOf(renderEngine.cache.getAspectRatio(pageIndex)) }

    LaunchedEffect(pageIndex) {
        if (bitmap == null) {
            val rendered = renderEngine.getOrRenderPage(pageIndex)
            bitmap = rendered
            pageRatio = renderEngine.cache.getAspectRatio(pageIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .padding(horizontal = 10.dp)
            .aspectRatio(pageRatio) // Locked aspect ratio prevents LazyColumn relayout jank
            .shadow(1.dp, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .graphicsLayer {
                clip = true
            },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF9FAFB)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
