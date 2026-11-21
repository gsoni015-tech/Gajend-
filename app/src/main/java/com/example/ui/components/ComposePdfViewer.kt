package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.render.PdfRenderEngine
import com.example.ui.viewmodel.ReaderThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
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
    isPageLocked: Boolean = false,
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

internal data class VerticalPanScrollResult(
    val newOffsetY: Float,
    val leftoverPanDeltaY: Float
)

internal object PdfZoomMath {
    const val DEFAULT_MIN_SCALE = 1.0f
    const val DEFAULT_MAX_SCALE = 4.0f
    const val DEFAULT_DOUBLE_TAP_SCALE = 2.5f
    const val PINCH_ELASTIC_MIN_RATIO = 0.90f
    const val PINCH_ELASTIC_MAX_RATIO = 1.08f
    const val ZOOM_ACTIVE_EPSILON = 0.02f

    fun clampScaleResting(
        scale: Float,
        minScale: Float = DEFAULT_MIN_SCALE,
        maxScale: Float = DEFAULT_MAX_SCALE
    ): Float = scale.coerceIn(minScale, maxScale)

    fun clampScaleDuringPinch(
        scale: Float,
        minScale: Float = DEFAULT_MIN_SCALE,
        maxScale: Float = DEFAULT_MAX_SCALE
    ): Float = scale.coerceIn(minScale * PINCH_ELASTIC_MIN_RATIO, maxScale * PINCH_ELASTIC_MAX_RATIO)

    fun maxPanOffset(scale: Float, viewportSpan: Float): Float =
        (((scale - 1f).coerceAtLeast(0f)) * viewportSpan.coerceAtLeast(1f)) / 2f

    fun clampOffsets(
        scale: Float,
        viewWidth: Float,
        viewHeight: Float,
        rawX: Float,
        rawY: Float,
        isPageLocked: Boolean = false
    ): Pair<Float, Float> {
        if (scale <= 1.0f) {
            return 0f to 0f
        }
        val maxY = maxPanOffset(scale, viewHeight)
        if (isPageLocked || scale <= 1.0f + ZOOM_ACTIVE_EPSILON) {
            return 0f to rawY.coerceIn(-maxY, maxY)
        }
        val maxX = maxPanOffset(scale, viewWidth)
        return rawX.coerceIn(-maxX, maxX) to rawY.coerceIn(-maxY, maxY)
    }

    fun computePinchFocalOffsets(
        oldScale: Float,
        newScale: Float,
        oldX: Float,
        oldY: Float,
        prevCentroidX: Float,
        prevCentroidY: Float,
        currCentroidX: Float,
        currCentroidY: Float,
        viewWidth: Float,
        viewHeight: Float,
        isPageLocked: Boolean = false
    ): Pair<Float, Float> {
        if (newScale <= 1.0f || oldScale <= 0f) {
            return 0f to 0f
        }
        val halfW = viewWidth / 2f
        val halfH = viewHeight / 2f
        val cPrevX = prevCentroidX - halfW
        val cPrevY = prevCentroidY - halfH
        val cCurrX = currCentroidX - halfW
        val cCurrY = currCentroidY - halfH
        val ratio = newScale / oldScale
        val rawX = if (isPageLocked) 0f else cCurrX - (cPrevX - oldX) * ratio
        val rawY = cCurrY - (cPrevY - oldY) * ratio
        return clampOffsets(newScale, viewWidth, viewHeight, rawX, rawY, isPageLocked)
    }

    fun computeDoubleTapTargetOffsets(
        targetScale: Float,
        tapX: Float,
        tapY: Float,
        viewWidth: Float,
        viewHeight: Float,
        isPageLocked: Boolean = false
    ): Pair<Float, Float> {
        if (targetScale <= 1.0f) return 0f to 0f
        val pX = tapX - viewWidth / 2f
        val pY = tapY - viewHeight / 2f
        val rawX = if (isPageLocked) 0f else pX * (1f - targetScale)
        val rawY = pY * (1f - targetScale)
        return clampOffsets(targetScale, viewWidth, viewHeight, rawX, rawY, isPageLocked)
    }

    fun splitVerticalPanAndScroll(
        currentOffsetY: Float,
        panDeltaY: Float,
        maxY: Float
    ): VerticalPanScrollResult {
        val clampedMaxY = maxY.coerceAtLeast(0f)
        val targetOffsetY = currentOffsetY + panDeltaY
        val clampedOffsetY = targetOffsetY.coerceIn(-clampedMaxY, clampedMaxY)
        val absorbedY = clampedOffsetY - currentOffsetY
        val leftoverY = panDeltaY - absorbedY
        return VerticalPanScrollResult(clampedOffsetY, leftoverY)
    }
}

/**
 * SmoothZoomableBox provides:
 * 1. Multi-touch smooth pinch-to-zoom (1.0x minScale fit-to-width up to 4.0x maxScale) with
 *    exact center-relative focal point tracking and subtle elastic pinch bounce back to 1.0x.
 * 2. Instant double-tap to zoom in (2.5x) centered directly on the tapped area, or zoom out (1.0x).
 * 3. Full panning + seamless continuous vertical page scroll delegation when zoomed in so users
 *    can navigate across all previous/next pages without zooming back out.
 * 4. Optional horizontal edge-swipe delegation for paged horizontal viewers when zoomed in.
 * 5. Full native passthrough at 1.0x so lists and pagers scroll at 120Hz.
 * 6. Floating "Tap to Reset" badge when magnified.
 */
@Composable
fun SmoothZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = PdfZoomMath.DEFAULT_MIN_SCALE,
    maxScale: Float = PdfZoomMath.DEFAULT_MAX_SCALE,
    doubleTapScale: Float = PdfZoomMath.DEFAULT_DOUBLE_TAP_SCALE,
    isPageLocked: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onScrollVerticalDelta: ((rawDeltaPx: Float) -> Float)? = null,
    onHorizontalEdgeSwipe: ((direction: Int) -> Boolean)? = null,
    content: @Composable BoxScope.(currentScale: Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val effectiveMinScale = minScale.coerceAtLeast(1.0f)
    val effectiveMaxScale = maxScale.coerceAtLeast(effectiveMinScale)
    val effectiveDoubleTapScale = doubleTapScale.coerceIn(effectiveMinScale, effectiveMaxScale)
    val doubleTapSlopPx = remember(density) { with(density) { 48.dp.toPx() } }
    val edgeSwipeThresholdPx = remember(density) { with(density) { 44.dp.toPx() } }

    var scaleState by remember { mutableFloatStateOf(effectiveMinScale) }
    var offsetXState by remember { mutableFloatStateOf(0f) }
    var offsetYState by remember { mutableFloatStateOf(0f) }
    var activeAnimJob by remember { mutableStateOf<Job?>(null) }

    val isZoomed by remember(effectiveMinScale) {
        derivedStateOf { scaleState > effectiveMinScale + 0.04f }
    }

    fun animateScaleAndOffsets(
        targetScale: Float,
        targetX: Float,
        targetY: Float,
        useTween: Boolean = false
    ) {
        activeAnimJob?.cancel()
        val startScale = scaleState
        val startX = offsetXState
        val startY = offsetYState
        activeAnimJob = coroutineScope.launch {
            val anim = androidx.compose.animation.core.Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = if (useTween) {
                    tween(240, easing = FastOutSlowInEasing)
                } else {
                    spring(stiffness = Spring.StiffnessMediumLow)
                }
            ) {
                val fraction = value
                scaleState = startScale + (targetScale - startScale) * fraction
                offsetXState = startX + (targetX - startX) * fraction
                offsetYState = startY + (targetY - startY) * fraction
            }
            scaleState = targetScale
            offsetXState = targetX
            offsetYState = targetY
        }
    }

    LaunchedEffect(isPageLocked) {
        if (isPageLocked && offsetXState != 0f) {
            animateScaleAndOffsets(
                targetScale = PdfZoomMath.clampScaleResting(scaleState, effectiveMinScale, effectiveMaxScale),
                targetX = 0f,
                targetY = offsetYState
            )
        }
    }

    BoxWithConstraints(modifier = modifier.testTag("pdf_zoomable_container")) {
        val viewWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        fun applyVerticalPanAndScroll(panDeltaY: Float): Boolean {
            val currentScale = scaleState.coerceAtLeast(1.0f)
            val maxY = PdfZoomMath.maxPanOffset(currentScale, viewHeight)
            val split = PdfZoomMath.splitVerticalPanAndScroll(offsetYState, panDeltaY, maxY)
            offsetYState = split.newOffsetY
            val absorbedByOffset = abs(split.leftoverPanDeltaY - panDeltaY) > 0.01f
            var consumedByList = 0f
            if (abs(split.leftoverPanDeltaY) > 0.01f && onScrollVerticalDelta != null) {
                // Dragging finger UP (leftoverPanDeltaY < 0) scrolls LazyColumn forward (+delta)
                val scrollRequest = -split.leftoverPanDeltaY / currentScale
                consumedByList = onScrollVerticalDelta.invoke(scrollRequest)
            }
            return absorbedByOffset || abs(consumedByList) > 0.01f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(viewWidth, viewHeight, isPageLocked, effectiveMinScale, effectiveMaxScale) {
                    var lastTapTime = 0L
                    var lastTapPosition = Offset.Zero
                    val doubleTapTimeout = 320L

                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        activeAnimJob?.cancel()
                        val downTime = down.uptimeMillis
                        var releaseTime = downTime
                        val downPosition = down.position
                        var isMultiTouch = false
                        var hasMoved = false
                        var horizontalEdgeOverscroll = 0f
                        val velocityTracker = VelocityTracker()

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
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            releaseTime = event.changes.maxOfOrNull { it.uptimeMillis } ?: releaseTime
                            val pointers = event.changes.filter { it.pressed }
                            val pointerCount = pointers.size

                            if (pointerCount >= 2) {
                                isMultiTouch = true
                                longPressJob?.cancel()
                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                val currentDist = (p0 - p1).getDistance()
                                val currentCentroid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)

                                if (prevDist > 1f && currentDist > 1f) {
                                    val zoomFactor = currentDist / prevDist
                                    val oldScale = scaleState
                                    val newScale = PdfZoomMath.clampScaleDuringPinch(
                                        oldScale * zoomFactor,
                                        effectiveMinScale,
                                        effectiveMaxScale
                                    )
                                    val (clampedX, clampedY) = PdfZoomMath.computePinchFocalOffsets(
                                        oldScale = oldScale,
                                        newScale = newScale,
                                        oldX = offsetXState,
                                        oldY = offsetYState,
                                        prevCentroidX = prevCentroid.x,
                                        prevCentroidY = prevCentroid.y,
                                        currCentroidX = currentCentroid.x,
                                        currCentroidY = currentCentroid.y,
                                        viewWidth = viewWidth,
                                        viewHeight = viewHeight,
                                        isPageLocked = isPageLocked
                                    )
                                    scaleState = newScale
                                    offsetXState = clampedX
                                    offsetYState = clampedY
                                    event.changes.forEach { it.consume() }
                                }
                                prevDist = currentDist
                                prevCentroid = currentCentroid
                            } else if (pointerCount == 1) {
                                prevDist = 0f
                                if (!isMultiTouch) {
                                    val p = pointers[0]
                                    val distFromDown = (p.position - downPosition).getDistance()
                                    if (!hasMoved && distFromDown > viewConfiguration.touchSlop) {
                                        hasMoved = true
                                        longPressJob?.cancel()
                                    }

                                    // When zoomed in above 1.0x, coordinate X pan + Y pan/page scrolling
                                    if (hasMoved && scaleState > effectiveMinScale + PdfZoomMath.ZOOM_ACTIVE_EPSILON) {
                                        velocityTracker.addPosition(p.uptimeMillis, p.position)
                                        val panDelta = p.position - p.previousPosition

                                        if (!isPageLocked) {
                                            val maxX = PdfZoomMath.maxPanOffset(scaleState, viewWidth)
                                            val nextX = offsetXState + panDelta.x
                                            val clampedX = nextX.coerceIn(-maxX, maxX)
                                            val leftoverX = nextX - clampedX
                                            offsetXState = clampedX

                                            if (onHorizontalEdgeSwipe != null && abs(panDelta.x) > abs(panDelta.y) && abs(leftoverX) > 0.1f) {
                                                horizontalEdgeOverscroll += leftoverX
                                            } else if (abs(panDelta.y) > abs(panDelta.x) * 1.5f) {
                                                horizontalEdgeOverscroll = 0f
                                            }
                                        } else {
                                            offsetXState = 0f
                                        }

                                        applyVerticalPanAndScroll(panDelta.y)
                                        p.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        longPressJob?.cancel()

                        if (!isMultiTouch && !hasMoved && (releaseTime - downTime < 280L)) {
                            val timeSinceLast = releaseTime - lastTapTime
                            val distFromLast = (downPosition - lastTapPosition).getDistance()

                            if (lastTapTime > 0L && timeSinceLast in 0L..doubleTapTimeout && distFromLast < doubleTapSlopPx) {
                                lastTapTime = 0L
                                if (scaleState > effectiveMinScale + 0.06f) {
                                    animateScaleAndOffsets(
                                        targetScale = effectiveMinScale,
                                        targetX = 0f,
                                        targetY = 0f,
                                        useTween = true
                                    )
                                } else {
                                    val (targetX, targetY) = PdfZoomMath.computeDoubleTapTargetOffsets(
                                        targetScale = effectiveDoubleTapScale,
                                        tapX = downPosition.x,
                                        tapY = downPosition.y,
                                        viewWidth = viewWidth,
                                        viewHeight = viewHeight,
                                        isPageLocked = isPageLocked
                                    )
                                    animateScaleAndOffsets(
                                        targetScale = effectiveDoubleTapScale,
                                        targetX = targetX,
                                        targetY = targetY,
                                        useTween = true
                                    )
                                }
                            } else {
                                lastTapTime = releaseTime
                                lastTapPosition = downPosition
                            }
                        } else {
                            val restingScale = PdfZoomMath.clampScaleResting(
                                scaleState,
                                effectiveMinScale,
                                effectiveMaxScale
                            )
                            if (scaleState < effectiveMinScale) {
                                animateScaleAndOffsets(effectiveMinScale, 0f, 0f)
                            } else if (scaleState > effectiveMaxScale) {
                                val (cx, cy) = PdfZoomMath.clampOffsets(
                                    restingScale,
                                    viewWidth,
                                    viewHeight,
                                    offsetXState,
                                    offsetYState,
                                    isPageLocked
                                )
                                animateScaleAndOffsets(restingScale, cx, cy)
                            } else {
                                val (cx, cy) = PdfZoomMath.clampOffsets(
                                    scaleState,
                                    viewWidth,
                                    viewHeight,
                                    offsetXState,
                                    offsetYState,
                                    isPageLocked
                                )
                                if (cx != offsetXState || cy != offsetYState) {
                                    offsetXState = cx
                                    offsetYState = cy
                                }

                                val switchedPage = if (
                                    !isMultiTouch &&
                                    onHorizontalEdgeSwipe != null &&
                                    abs(horizontalEdgeOverscroll) > edgeSwipeThresholdPx
                                ) {
                                    // Swiping left (overscroll < 0) goes forward (+1); swiping right goes back (-1)
                                    val direction = if (horizontalEdgeOverscroll < 0f) 1 else -1
                                    val turned = onHorizontalEdgeSwipe.invoke(direction)
                                    if (turned) {
                                        val edgeX = PdfZoomMath.maxPanOffset(scaleState, viewWidth)
                                        val targetEdgeX = if (direction > 0) edgeX else -edgeX
                                        animateScaleAndOffsets(scaleState, targetEdgeX, offsetYState, useTween = true)
                                    }
                                    turned
                                } else {
                                    false
                                }

                                if (
                                    !switchedPage &&
                                    !isMultiTouch &&
                                    hasMoved &&
                                    scaleState > effectiveMinScale + PdfZoomMath.ZOOM_ACTIVE_EPSILON &&
                                    onScrollVerticalDelta != null
                                ) {
                                    val vy = velocityTracker.calculateVelocity().y
                                    if (abs(vy) > 150f) {
                                        activeAnimJob = coroutineScope.launch {
                                            var prevVal = 0f
                                            AnimationState(
                                                initialValue = 0f,
                                                initialVelocity = vy
                                            ).animateDecay(
                                                animationSpec = exponentialDecay(frictionMultiplier = 1.6f)
                                            ) {
                                                val delta = value - prevVal
                                                prevVal = value
                                                val moved = applyVerticalPanAndScroll(delta)
                                                if (!moved) {
                                                    cancelAnimation()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scaleState
                    scaleY = scaleState
                    translationX = if (isPageLocked) 0f else offsetXState
                    translationY = offsetYState
                    clip = true
                }
        ) {
            content(scaleState)
        }

        // Quick 1-Tap Reset Zoom Badge when Magnified
        if (isZoomed) {
            val percentage = (scaleState * 100).roundToInt()
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .testTag("pdf_reset_zoom_badge")
                    .clickable {
                        animateScaleAndOffsets(
                            targetScale = effectiveMinScale,
                            targetX = 0f,
                            targetY = 0f,
                            useTween = true
                        )
                    },
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.82f),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "$percentage% • Reset",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
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
    isPageLocked: Boolean = false,
    onPageLongPress: (Int) -> Unit = {},
    onPagePillClick: () -> Unit = {},
    onPageChanged: (Int, Int) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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

    Box(modifier = Modifier.fillMaxSize()) {
        SmoothZoomableBox(
            modifier = Modifier.fillMaxSize(),
            minScale = PdfZoomMath.DEFAULT_MIN_SCALE,
            maxScale = PdfZoomMath.DEFAULT_MAX_SCALE,
            doubleTapScale = PdfZoomMath.DEFAULT_DOUBLE_TAP_SCALE,
            isPageLocked = isPageLocked,
            onLongPress = { onPageLongPress(displayPageNumber) },
            onScrollVerticalDelta = { rawDeltaPx ->
                listState.dispatchRawDelta(rawDeltaPx)
            }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pdf_continuous_list"),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 8.dp, bottom = 68.dp)
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
        }

        // Fixed unscaled floating Page Navigation Pill at bottom center
        PdfPageNavigationPill(
            currentPage = displayPageNumber,
            pageCount = pageCount,
            onPrevPage = {
                if (displayPageNumber > 1) {
                    coroutineScope.launch {
                        listState.animateScrollToItem((displayPageNumber - 2).coerceAtLeast(0))
                    }
                }
            },
            onNextPage = {
                if (displayPageNumber < pageCount) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(displayPageNumber.coerceAtMost(pageCount - 1))
                    }
                }
            },
            onPillClick = onPagePillClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun PagedHorizontalViewer(
    renderEngine: PdfRenderEngine,
    pageCount: Int,
    colorFilter: ColorFilter?,
    targetPage: Int? = null,
    isPageLocked: Boolean = false,
    onPageLongPress: (Int) -> Unit = {},
    onPagePillClick: () -> Unit = {},
    onPageChanged: (Int, Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

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

    Box(modifier = Modifier.fillMaxSize()) {
        SmoothZoomableBox(
            modifier = Modifier.fillMaxSize(),
            minScale = PdfZoomMath.DEFAULT_MIN_SCALE,
            maxScale = PdfZoomMath.DEFAULT_MAX_SCALE,
            doubleTapScale = PdfZoomMath.DEFAULT_DOUBLE_TAP_SCALE,
            isPageLocked = isPageLocked,
            onLongPress = { onPageLongPress(pagerState.currentPage + 1) },
            onHorizontalEdgeSwipe = { direction ->
                val targetIndex = pagerState.currentPage + direction
                if (targetIndex in 0 until pageCount) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                    true
                } else {
                    false
                }
            }
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
        }

        val currentPageNumber = pagerState.currentPage + 1
        PdfPageNavigationPill(
            currentPage = currentPageNumber,
            pageCount = pageCount,
            onPrevPage = {
                if (pagerState.currentPage > 0) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            },
            onNextPage = {
                if (pagerState.currentPage < pageCount - 1) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            onPillClick = onPagePillClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun PdfPageNavigationPill(
    currentPage: Int,
    pageCount: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onPillClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.82f),
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (pageCount > 1) {
                IconButton(
                    onClick = onPrevPage,
                    enabled = currentPage > 1,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_prev_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous page",
                        tint = if (currentPage > 1) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onPillClick)
                    .testTag("pdf_page_indicator_pill")
                    .padding(horizontal = if (pageCount > 1) 8.dp else 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Page $currentPage of $pageCount",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (pageCount > 1) {
                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < pageCount,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("pdf_next_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next page",
                        tint = if (currentPage < pageCount) Color.White else Color.White.copy(alpha = 0.35f)
                    )
                }
            }
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
