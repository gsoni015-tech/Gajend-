package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.example.ads.StartIoAdManager
import com.example.ads.StartIoBannerAdView
import com.example.data.local.PdfEntity
import com.example.reader.OfficeDocumentHelper
import com.example.reader.TextToSpeechHelper
import com.example.reader.WordDocument
import com.example.ui.theme.AppFonts
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordViewerScreen(
    entity: PdfEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val handleBack: () -> Unit = {
        if (activity != null) {
            StartIoAdManager.showBackPressInterstitial(activity) { onBack() }
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    // Preload interstitial so it is ready when closing the document
    LaunchedEffect(entity.path) {
        StartIoAdManager.loadInterstitial(context.applicationContext)
    }
    var doc by remember { mutableStateOf<WordDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isTtsActive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var committedScale by remember(entity.path) { mutableFloatStateOf(1.0f) }
    var liveZoomScale by remember(entity.path) { mutableFloatStateOf(1.0f) }
    var livePanX by remember(entity.path) { mutableFloatStateOf(0f) }
    var livePanY by remember(entity.path) { mutableFloatStateOf(0f) }
    val zoomAnim = remember(entity.path) { Animatable(1.0f) }
    val panXAnim = remember(entity.path) { Animatable(0f) }
    val panYAnim = remember(entity.path) { Animatable(0f) }
    val zoomScale = liveZoomScale
    val minZoomScale = 0.5f
    val maxZoomScale = 2.5f
    val wordListState = rememberLazyListState()
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }

    suspend fun commitWordZoom(settledScale: Float, finalPanY: Float) {
        val clamped = settledScale.coerceIn(minZoomScale, maxZoomScale)
        val anchorIndex = wordListState.firstVisibleItemIndex
        val factor = (clamped / committedScale).coerceAtLeast(0.1f)
        val anchorOffset = (wordListState.firstVisibleItemScrollOffset * factor - finalPanY * 0.5f)
            .roundToInt()
            .coerceAtLeast(0)
        committedScale = clamped
        liveZoomScale = clamped
        livePanX = 0f
        livePanY = 0f
        zoomAnim.snapTo(clamped)
        panXAnim.snapTo(0f)
        panYAnim.snapTo(0f)
        wordListState.scrollToItem(anchorIndex, anchorOffset)
    }

    fun animateWordZoomTo(
        target: Float,
        focalScreenPoint: Offset? = null,
        viewWidthPx: Float = 0f,
        viewHeightPx: Float = 0f
    ) {
        val clampedTarget = target.coerceIn(minZoomScale, maxZoomScale)
        zoomAnimationJob?.cancel()
        zoomAnimationJob = coroutineScope.launch {
            val startScale = liveZoomScale.coerceAtLeast(0.1f)
            val ratio = clampedTarget / startScale
            val targetVisual = clampedTarget / committedScale
            val targetPanX = if (focalScreenPoint != null && viewWidthPx > 0f && targetVisual > 1.0f) {
                val maxX = ((targetVisual - 1f) * viewWidthPx).coerceAtLeast(0f)
                (focalScreenPoint.x - (focalScreenPoint.x - livePanX) * ratio).coerceIn(-maxX, 0f)
            } else 0f
            val targetPanY = if (focalScreenPoint != null && viewHeightPx > 0f && targetVisual > 1.0f) {
                val maxY = ((targetVisual - 1f) * viewHeightPx).coerceAtLeast(0f)
                (focalScreenPoint.y - (focalScreenPoint.y - livePanY) * ratio).coerceIn(-maxY, 0f)
            } else 0f

            zoomAnim.snapTo(liveZoomScale)
            panXAnim.snapTo(livePanX)
            panYAnim.snapTo(livePanY)
            coroutineScope {
                launch {
                    zoomAnim.animateTo(clampedTarget, tween(240, easing = FastOutSlowInEasing)) {
                        liveZoomScale = value
                    }
                }
                launch {
                    panXAnim.animateTo(targetPanX, tween(240, easing = FastOutSlowInEasing)) {
                        livePanX = value
                    }
                }
                launch {
                    panYAnim.animateTo(targetPanY, tween(240, easing = FastOutSlowInEasing)) {
                        livePanY = value
                    }
                }
            }
            commitWordZoom(clampedTarget, targetPanY)
        }
    }

    // null = Auto-detect per document/paragraph/run; non-null = Manual user override from dropdown
    var selectedFontOverride by remember(entity.path) { mutableStateOf<AppFonts.SupportedFont?>(null) }
    var fontMenuExpanded by remember { mutableStateOf(false) }

    val orderedFonts = remember {
        listOf(
            AppFonts.SupportedFont.KRUTI_DEV,
            AppFonts.SupportedFont.HINDI_NOTO,
            AppFonts.SupportedFont.HINDI_TIRO,
            AppFonts.SupportedFont.HINDI_MUKTA,
            AppFonts.SupportedFont.HINDI_HIND,
            AppFonts.SupportedFont.ENGLISH_OPEN_SANS,
            AppFonts.SupportedFont.ENGLISH_NOTO_SERIF,
            AppFonts.SupportedFont.ENGLISH_POPPINS,
            AppFonts.SupportedFont.ENGLISH_ROBOTO,
            AppFonts.SupportedFont.ENGLISH_MONO,
            AppFonts.SupportedFont.SYSTEM_DEFAULT
        )
    }

    val ttsHelper = remember { TextToSpeechHelper(context) }
    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }

    LaunchedEffect(entity.path, entity.uriString) {
        isLoading = true
        doc = OfficeDocumentHelper.parseWordDocument(File(entity.path), context, entity.uriString)
        isLoading = false
    }

    val detectedPrimaryFont = doc?.primaryDetectedFont ?: AppFonts.SupportedFont.ENGLISH_OPEN_SANS
    val activeFontLabel = if (selectedFontOverride == null) {
        "Auto (${detectedPrimaryFont.displayName.substringBefore(" (")})"
    } else {
        selectedFontOverride!!.displayName.substringBefore(" (")
    }
    val isKrutiActive = (selectedFontOverride == AppFonts.SupportedFont.KRUTI_DEV) ||
            (selectedFontOverride == null && detectedPrimaryFont == AppFonts.SupportedFont.KRUTI_DEV)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = WhiteMixBackground,
        topBar = {
            Surface(
                color = WhiteMixSurface,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = handleBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = entity.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Zoom Out
                        IconButton(
                            onClick = {
                                animateWordZoomTo(liveZoomScale - 0.15f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Zoom Out",
                                tint = BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Zoom Percentage Pill (tap to reset to 100%)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    animateWordZoomTo(1.0f)
                                }
                                .padding(horizontal = 2.dp),
                            color = if (kotlin.math.abs(zoomScale - 1.0f) > 0.05f) BrandPrimary.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${(zoomScale * 100).roundToInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (kotlin.math.abs(zoomScale - 1.0f) > 0.05f) BrandPrimary else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        // Zoom In
                        IconButton(
                            onClick = {
                                animateWordZoomTo(liveZoomScale + 0.15f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Open with external Office app (Word, Docs)
                        IconButton(
                            onClick = {
                                openWordInExternalApp(context, entity)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open with external app",
                                tint = BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Read aloud / Text to Speech
                        IconButton(
                            onClick = {
                                if (isTtsActive) {
                                    ttsHelper.stop()
                                    isTtsActive = false
                                } else {
                                    doc?.let { d ->
                                        isTtsActive = true
                                        val fullText = d.paragraphs.joinToString(" ") { it.text }
                                        ttsHelper.speak(fullText.take(1000))
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isTtsActive) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = "TTS Read Aloud",
                                tint = if (isTtsActive) BrandCrimson else BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = WhiteMixBorder, thickness = 0.7.dp)

                    // Font Auto-Detection & Selection Dropdown Toolbar Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fontMenuExpanded = true }
                                    .testTag("word_font_dropdown_button"),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF3F6FC),
                                border = BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.28f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = "Select Font",
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Font: $activeFontLabel",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Open Font Menu",
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = fontMenuExpanded,
                                onDismissRequest = { fontMenuExpanded = false },
                                modifier = Modifier
                                    .widthIn(min = 240.dp, max = 320.dp)
                                    .heightIn(max = 420.dp)
                            ) {
                                // 1. Auto-Detect Document Font Option
                                DropdownMenuItem(
                                    modifier = Modifier.testTag("word_font_option_auto"),
                                    text = {
                                        Column {
                                            Text(
                                                text = "Auto (Document Font)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedFontOverride == null) BrandPrimary else TextPrimary
                                            )
                                            Text(
                                                text = "Detected: ${detectedPrimaryFont.displayName}",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = BrandPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (selectedFontOverride == null) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = BrandPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedFontOverride = null
                                        fontMenuExpanded = false
                                    }
                                )

                                HorizontalDivider(color = WhiteMixBorder)

                                // 2. All Supported Fonts (Kruti Dev 010 featured first)
                                orderedFonts.forEach { font ->
                                    val isSelected = selectedFontOverride == font
                                    DropdownMenuItem(
                                        modifier = Modifier.testTag("word_font_option_${font.id.lowercase()}"),
                                        text = {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = font.displayName,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) BrandPrimary else TextPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = font.languageCategory,
                                                        fontSize = 10.sp,
                                                        color = TextTertiary
                                                    )
                                                }
                                                Text(
                                                    text = font.sampleText,
                                                    fontFamily = font.fontFamily,
                                                    fontSize = 12.sp,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = BrandPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedFontOverride = font
                                            fontMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Quick 1-Tap Kruti Dev 010 Toggle Chip
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedFontOverride = if (selectedFontOverride == AppFonts.SupportedFont.KRUTI_DEV) {
                                        null
                                    } else {
                                        AppFonts.SupportedFont.KRUTI_DEV
                                    }
                                }
                                .testTag("word_kruti_dev_quick_chip"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isKrutiActive) BrandPrimary else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isKrutiActive) BrandPrimary else WhiteMixBorder
                            )
                        ) {
                            Text(
                                text = if (isKrutiActive) "Kruti Dev 10 ✓" else "Kruti Dev 10",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isKrutiActive) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = WhiteMixSurface,
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    StartIoBannerAdView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 728.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            } else {
                doc?.let { wordDoc ->
                    val currentFontSize = (14f * committedScale).coerceIn(7f, 35f).sp
                    val currentHeadingFontSize = (17f * committedScale).coerceIn(9f, 42f).sp
                    val currentLineHeight = (22f * committedScale).coerceIn(11f, 54f).sp
                    val currentHeadingLineHeight = (24f * committedScale).coerceIn(13f, 58f).sp
                    val currentPadding = (16.dp * committedScale).coerceIn(8.dp, 32.dp)
                    val visualMultiplier = (zoomScale / committedScale).coerceIn(0.2f, 5.0f)

                    val density = LocalDensity.current
                    val touchSlop = LocalViewConfiguration.current.touchSlop
                    val doubleTapSlopPx = with(density) { 48.dp.toPx() }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                    ) {
                        val viewWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val viewHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(viewWidthPx, viewHeightPx, committedScale) {
                                    var lastTapTime = 0L
                                    var lastTapPosition = Offset.Zero
                                    val doubleTapTimeout = 360L

                                    awaitEachGesture {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial
                                        )
                                        val downTime = SystemClock.uptimeMillis()
                                        val downPosition = down.position
                                        var isMultiTouch = false
                                        var hasMoved = false
                                        var prevDist = 0f
                                        var prevCentroid = Offset.Zero
                                        var gestureScale = liveZoomScale
                                        var gesturePanX = livePanX
                                        var gesturePanY = livePanY

                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val pointers = event.changes.filter { it.pressed }
                                            val pointerCount = pointers.size

                                            if (pointerCount >= 2) {
                                                if (!isMultiTouch) {
                                                    isMultiTouch = true
                                                    zoomAnimationJob?.cancel()
                                                }
                                                val p0 = pointers[0].position
                                                val p1 = pointers[1].position
                                                val currentDist = (p0 - p1).getDistance()
                                                val currentCentroid = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)

                                                if (prevDist > 1f && currentDist > 1f) {
                                                    val zoomStep = currentDist / prevDist
                                                    val panDelta = if (prevCentroid != Offset.Zero) {
                                                        currentCentroid - prevCentroid
                                                    } else {
                                                        Offset.Zero
                                                    }
                                                    val nextScale = (gestureScale * zoomStep)
                                                        .coerceIn(minZoomScale * 0.88f, maxZoomScale * 1.12f)
                                                    val effectiveStep = nextScale / gestureScale
                                                    gestureScale = nextScale

                                                    val nextVisual = (gestureScale / committedScale).coerceAtLeast(0.2f)
                                                    val rawX = currentCentroid.x - (currentCentroid.x - gesturePanX) * effectiveStep + panDelta.x
                                                    val rawY = currentCentroid.y - (currentCentroid.y - gesturePanY) * effectiveStep + panDelta.y
                                                    if (nextVisual >= 1f) {
                                                        val maxX = (nextVisual - 1f) * viewWidthPx
                                                        val maxY = (nextVisual - 1f) * viewHeightPx
                                                        gesturePanX = rawX.coerceIn(-maxX, 0f)
                                                        gesturePanY = rawY.coerceIn(-maxY, 0f)
                                                    } else {
                                                        val marginX = ((1f - nextVisual) * viewWidthPx) / 2f
                                                        gesturePanX = rawX.coerceIn(-marginX * 0.5f, marginX * 1.5f)
                                                        gesturePanY = rawY.coerceIn(-viewHeightPx * 0.25f, viewHeightPx * 0.25f)
                                                    }

                                                    liveZoomScale = gestureScale
                                                    livePanX = gesturePanX
                                                    livePanY = gesturePanY
                                                }
                                                prevDist = currentDist
                                                prevCentroid = currentCentroid
                                                event.changes.forEach { it.consume() }
                                            } else if (pointerCount == 1) {
                                                prevDist = 0f
                                                prevCentroid = Offset.Zero
                                                if (isMultiTouch) {
                                                    event.changes.forEach { it.consume() }
                                                } else {
                                                    val p = pointers[0]
                                                    if ((p.position - downPosition).getDistance() > touchSlop) {
                                                        hasMoved = true
                                                    }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })

                                        if (isMultiTouch) {
                                            val settledScale = gestureScale.coerceIn(minZoomScale, maxZoomScale)
                                            val currentPanY = gesturePanY
                                            zoomAnimationJob?.cancel()
                                            zoomAnimationJob = coroutineScope.launch {
                                                if (kotlin.math.abs(gestureScale - settledScale) > 0.01f) {
                                                    zoomAnim.snapTo(gestureScale)
                                                    zoomAnim.animateTo(
                                                        settledScale,
                                                        spring(stiffness = Spring.StiffnessMediumLow)
                                                    ) {
                                                        liveZoomScale = value
                                                    }
                                                }
                                                commitWordZoom(settledScale, currentPanY)
                                            }
                                        } else {
                                            val releaseTime = SystemClock.uptimeMillis()
                                            if (!hasMoved && (releaseTime - downTime < 300L)) {
                                                val timeSinceLast = releaseTime - lastTapTime
                                                val distFromLast = (downPosition - lastTapPosition).getDistance()
                                                if (timeSinceLast in 20L..doubleTapTimeout && distFromLast < doubleTapSlopPx) {
                                                    lastTapTime = 0L
                                                    val current = liveZoomScale
                                                    val target = when {
                                                        current > 1.25f -> 1.0f
                                                        current < 0.85f -> 1.0f
                                                        else -> 1.85f
                                                    }
                                                    animateWordZoomTo(
                                                        target = target,
                                                        focalScreenPoint = downPosition,
                                                        viewWidthPx = viewWidthPx,
                                                        viewHeightPx = viewHeightPx
                                                    )
                                                } else {
                                                    lastTapTime = releaseTime
                                                    lastTapPosition = downPosition
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            LazyColumn(
                                state = wordListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 760.dp)
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        scaleX = visualMultiplier
                                        scaleY = visualMultiplier
                                        translationX = livePanX
                                        translationY = livePanY
                                        transformOrigin = TransformOrigin(0f, 0f)
                                    },
                                contentPadding = PaddingValues(currentPadding)
                            ) {
                            items(wordDoc.paragraphs) { para ->
                                val paraPaddingVertical = if (para.isHeading) {
                                    (8.dp * committedScale).coerceIn(3.dp, 16.dp)
                                } else {
                                    (4.dp * committedScale).coerceIn(2.dp, 10.dp)
                                }
                                val effectiveFontSize = if (para.isHeading) currentHeadingFontSize else currentFontSize
                                val effectiveLineHeight = if (para.isHeading) currentHeadingLineHeight else currentLineHeight
                                val effectiveColor = if (para.isHeading) BrandPrimary else TextPrimary

                                if (para.runs.isNotEmpty()) {
                                    val annotated = remember(para, selectedFontOverride) {
                                        buildAnnotatedString {
                                            for (run in para.runs) {
                                                val runFont = selectedFontOverride ?: run.detectedFont
                                                val runWeight = if (para.isHeading || run.isBold) FontWeight.Bold else FontWeight.Normal
                                                val runStyle = if (run.isItalic) FontStyle.Italic else FontStyle.Normal
                                                withStyle(
                                                    SpanStyle(
                                                        fontFamily = runFont.fontFamily,
                                                        fontWeight = runWeight,
                                                        fontStyle = runStyle
                                                    )
                                                ) {
                                                    append(run.text)
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = annotated,
                                        fontSize = effectiveFontSize,
                                        color = effectiveColor,
                                        lineHeight = effectiveLineHeight,
                                        modifier = Modifier.padding(vertical = paraPaddingVertical)
                                    )
                                } else {
                                    val paraFont = selectedFontOverride ?: para.detectedFont
                                    Text(
                                        text = para.text,
                                        fontFamily = paraFont.fontFamily,
                                        fontSize = effectiveFontSize,
                                        fontWeight = if (para.isHeading || para.isBold) FontWeight.Bold else FontWeight.Normal,
                                        color = effectiveColor,
                                        lineHeight = effectiveLineHeight,
                                        modifier = Modifier.padding(vertical = paraPaddingVertical)
                                    )
                                }
                            }
                            }
                        }
                    }

                    // Floating Reset Zoom Pill when scaled
                    if (kotlin.math.abs(zoomScale - 1.0f) > 0.05f) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    animateWordZoomTo(1.0f)
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "${(zoomScale * 100).roundToInt()}% • Tap to Reset",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openWordInExternalApp(context: Context, entity: PdfEntity) {
    try {
        val file = File(entity.path)
        val uri: Uri? = if (file.exists() && file.length() > 0) {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else if (!entity.uriString.isNullOrBlank()) {
            Uri.parse(entity.uriString)
        } else {
            null
        }

        if (uri == null) {
            Toast.makeText(context, "File path not found", Toast.LENGTH_SHORT).show()
            return
        }

        val mime = if (entity.title.endsWith(".doc", ignoreCase = true)) {
            "application/msword"
        } else {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with Word or Docs"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open external app: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
