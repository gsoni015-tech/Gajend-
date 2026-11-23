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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ads.StartIoAdManager
import com.example.ads.StartIoBannerAdView
import com.example.data.local.PdfEntity
import com.example.reader.ExcelDocument
import com.example.reader.OfficeDocumentHelper
import com.example.ui.theme.AppFonts
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelViewerScreen(
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

    // Preload interstitial so it is ready when closing the spreadsheet
    LaunchedEffect(entity.path) {
        StartIoAdManager.loadInterstitial(context.applicationContext)
    }

    var excelDoc by remember { mutableStateOf<ExcelDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var isListView by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var committedScale by remember { mutableFloatStateOf(1.0f) }
    var liveZoomScale by remember { mutableFloatStateOf(1.0f) }
    var livePanX by remember { mutableFloatStateOf(0f) }
    var livePanY by remember { mutableFloatStateOf(0f) }
    val zoomAnim = remember { Animatable(1.0f) }
    val panXAnim = remember { Animatable(0f) }
    val panYAnim = remember { Animatable(0f) }
    val zoomScale = liveZoomScale
    var currentFitPageScale by remember { mutableFloatStateOf(0.5f) }
    var currentMinZoomScale by remember { mutableFloatStateOf(0.12f) }
    val maxZoomScale = 3.0f
    var zoomAnimationJob by remember { mutableStateOf<Job?>(null) }

    val horizontalScrollState = rememberScrollState()
    val gridListState = rememberLazyListState()
    val cardListState = rememberLazyListState()
    var pendingCommitScrollX by remember { mutableStateOf<Int?>(null) }
    var lastViewportWidthPx by remember { mutableFloatStateOf(0f) }
    var lastViewportHeightPx by remember { mutableFloatStateOf(0f) }

    suspend fun commitExcelZoom(
        settledScale: Float,
        currentPanX: Float,
        currentPanY: Float,
        resetHorizontalScroll: Boolean = false
    ) {
        val prevCommitted = committedScale.coerceAtLeast(0.05f)
        val ratio = settledScale / prevCommitted
        val targetScrollX = if (resetHorizontalScroll || isListView) {
            0
        } else {
            (horizontalScrollState.value * ratio - currentPanX).roundToInt().coerceAtLeast(0)
        }
        committedScale = settledScale
        liveZoomScale = settledScale
        livePanX = 0f
        livePanY = 0f
        zoomAnim.snapTo(settledScale)
        panXAnim.snapTo(0f)
        panYAnim.snapTo(0f)
        if (!isListView) {
            pendingCommitScrollX = targetScrollX
            if (currentPanY < -0.5f) {
                gridListState.scrollBy(-currentPanY)
            }
        } else if (currentPanY < -0.5f) {
            cardListState.scrollBy(-currentPanY)
        }
    }

    fun animateExcelZoomTo(
        target: Float,
        focalScreenPoint: Offset? = null,
        viewWidthPx: Float = lastViewportWidthPx,
        viewHeightPx: Float = lastViewportHeightPx,
        resetHorizontalScroll: Boolean = false
    ) {
        zoomAnimationJob?.cancel()
        zoomAnimationJob = coroutineScope.launch {
            val startScale = liveZoomScale.coerceAtLeast(0.05f)
            val clampedTarget = target.coerceIn(currentMinZoomScale, maxZoomScale)
            if (abs(startScale - clampedTarget) < 0.005f) {
                commitExcelZoom(clampedTarget, livePanX, livePanY, resetHorizontalScroll)
                return@launch
            }
            val startPanX = livePanX
            val startPanY = livePanY
            val focus = focalScreenPoint ?: Offset(
                (viewWidthPx / 2f).coerceAtLeast(0f),
                (viewHeightPx / 3f).coerceAtLeast(0f)
            )
            val endVisual = (clampedTarget / committedScale).coerceAtLeast(0.15f)
            val factor = clampedTarget / startScale
            val rawEndPanX = if (resetHorizontalScroll) 0f else focus.x - (focus.x - startPanX) * factor
            val rawEndPanY = focus.y - (focus.y - startPanY) * factor
            val (endPanX, endPanY) = if (endVisual >= 1f && viewWidthPx > 0f && viewHeightPx > 0f) {
                val maxX = (endVisual - 1f) * viewWidthPx
                val maxY = (endVisual - 1f) * viewHeightPx
                rawEndPanX.coerceIn(-maxX, 0f) to rawEndPanY.coerceIn(-maxY, 0f)
            } else {
                0f to 0f
            }

            zoomAnim.snapTo(startScale)
            panXAnim.snapTo(startPanX)
            panYAnim.snapTo(startPanY)
            val animSpec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
            val j1 = launch {
                zoomAnim.animateTo(clampedTarget, animSpec) { liveZoomScale = value }
            }
            val j2 = launch {
                panXAnim.animateTo(endPanX, animSpec) { livePanX = value }
            }
            val j3 = launch {
                panYAnim.animateTo(endPanY, animSpec) { livePanY = value }
            }
            j1.join()
            j2.join()
            j3.join()
            commitExcelZoom(clampedTarget, livePanX, livePanY, resetHorizontalScroll)
        }
    }

    // null = Auto-detect per workbook/cell; non-null = Manual user override from dropdown
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

    LaunchedEffect(entity.path, entity.uriString) {
        isLoading = true
        excelDoc = OfficeDocumentHelper.parseExcelDocument(File(entity.path), context, entity.uriString)
        selectedSheetIndex = 0
        isLoading = false
    }

    LaunchedEffect(selectedSheetIndex) {
        zoomAnimationJob?.cancel()
        livePanX = 0f
        livePanY = 0f
        panXAnim.snapTo(0f)
        panYAnim.snapTo(0f)
        pendingCommitScrollX = null
        horizontalScrollState.scrollTo(0)
        gridListState.scrollToItem(0)
        cardListState.scrollToItem(0)
    }

    LaunchedEffect(committedScale, pendingCommitScrollX) {
        val targetX = pendingCommitScrollX ?: return@LaunchedEffect
        horizontalScrollState.scrollTo(targetX.coerceAtLeast(0))
        pendingCommitScrollX = null
    }

    val detectedPrimaryFont = excelDoc?.primaryDetectedFont ?: AppFonts.SupportedFont.ENGLISH_OPEN_SANS
    val activeFontLabel = if (selectedFontOverride == null) {
        "Auto (${detectedPrimaryFont.displayName.substringBefore(" (")})"
    } else {
        selectedFontOverride!!.displayName.substringBefore(" (")
    }
    val isKrutiActive = (selectedFontOverride == AppFonts.SupportedFont.KRUTI_DEV) ||
        (selectedFontOverride == null && detectedPrimaryFont == AppFonts.SupportedFont.KRUTI_DEV)
    val isFitPageActive = abs(zoomScale - currentFitPageScale) < 0.04f && currentFitPageScale < 0.96f

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
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
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
                                val step = if (liveZoomScale <= 0.6f) 0.1f else 0.2f
                                val target = (liveZoomScale - step).coerceIn(currentMinZoomScale, maxZoomScale)
                                animateExcelZoomTo(target)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("excel_zoom_out_button")
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
                                    animateExcelZoomTo(1.0f)
                                }
                                .padding(horizontal = 2.dp),
                            color = if (abs(zoomScale - 1.0f) > 0.05f) BrandPrimary.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${(zoomScale * 100).roundToInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (abs(zoomScale - 1.0f) > 0.05f) BrandPrimary else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        // Zoom In
                        IconButton(
                            onClick = {
                                val step = if (liveZoomScale < 0.6f) 0.1f else 0.2f
                                val target = (liveZoomScale + step).coerceIn(currentMinZoomScale, maxZoomScale)
                                animateExcelZoomTo(target)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("excel_zoom_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Open with external app (Excel, Sheets)
                        IconButton(
                            onClick = { openExcelInExternalApp(context, entity) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open with Excel or Sheets",
                                tint = BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // View Mode Toggle (Table Grid vs Vertical Card List)
                        IconButton(
                            onClick = { isListView = !isListView },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isListView) Icons.Default.TableChart else Icons.Default.ViewAgenda,
                                contentDescription = if (isListView) "Switch to Table Grid" else "Switch to Vertical List",
                                tint = BrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = WhiteMixBorder, thickness = 0.7.dp)

                    // Font Auto-Detection, Kruti Dev Quick Chip & Fit Full Sheet Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { fontMenuExpanded = true }
                                    .testTag("excel_font_dropdown_button"),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF3F6FC),
                                border = BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.28f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TextFields,
                                        contentDescription = "Select Font",
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Font: $activeFontLabel",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
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
                                // 1. Auto-Detect Workbook Font Option
                                DropdownMenuItem(
                                    modifier = Modifier.testTag("excel_font_option_auto"),
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
                                        modifier = Modifier.testTag("excel_font_option_${font.id.lowercase()}"),
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
                                .testTag("excel_kruti_dev_quick_chip"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isKrutiActive) BrandPrimary else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isKrutiActive) BrandPrimary else WhiteMixBorder
                            )
                        ) {
                            Text(
                                text = if (isKrutiActive) "Kruti 10 ✓" else "Kruti 10",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isKrutiActive) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        // Quick 1-Tap Fit Whole Sheet / Page Overview Chip
                        if (!isListView) {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val target = if (isFitPageActive) 1.0f else currentFitPageScale
                                        animateExcelZoomTo(
                                            target = target,
                                            resetHorizontalScroll = !isFitPageActive
                                        )
                                    }
                                    .testTag("excel_fit_page_chip"),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFitPageActive) BrandPrimary else Color(0xFFF3F6FC),
                                border = BorderStroke(
                                    1.dp,
                                    if (isFitPageActive) BrandPrimary else BrandPrimary.copy(alpha = 0.28f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FitScreen,
                                        contentDescription = "Fit Full Sheet",
                                        tint = if (isFitPageActive) Color.White else BrandPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isFitPageActive) "100%" else "Fit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isFitPageActive) Color.White else BrandPrimary
                                    )
                                }
                            }
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
                excelDoc?.let { doc ->
                    val sheetNames = doc.sheets.keys.toList()
                    val safeIndex = selectedSheetIndex.coerceIn(0, (sheetNames.size - 1).coerceAtLeast(0))
                    val currentSheetData = if (sheetNames.isNotEmpty()) doc.sheets[sheetNames[safeIndex]] else null

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sheet Tabs
                        if (sheetNames.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = safeIndex,
                                containerColor = WhiteMixSurface,
                                edgePadding = 8.dp,
                                modifier = Modifier.height(40.dp)
                            ) {
                                sheetNames.forEachIndexed { index, name ->
                                    Tab(
                                        selected = safeIndex == index,
                                        onClick = { selectedSheetIndex = index },
                                        text = {
                                            Text(
                                                text = name,
                                                fontSize = 12.sp,
                                                fontWeight = if (safeIndex == index) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        currentSheetData?.let { sheet ->
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                            ) {
                                val viewportWidthDp = maxWidth.value.coerceAtLeast(240f)
                                val viewportHeightDp = maxHeight.value.coerceAtLeast(300f)
                                val colCount = maxOf(
                                    sheet.headers.size,
                                    sheet.rows.maxOfOrNull { it.size } ?: 0
                                ).coerceAtLeast(1)

                                val fitWidthScale = ((viewportWidthDp - 12f) / (colCount * 130f))
                                    .coerceIn(0.10f, 1.0f)
                                val totalRows = (sheet.rows.size + 1).coerceAtLeast(1)
                                val fitHeightScale = ((viewportHeightDp - 24f) / (totalRows * 34f))
                                    .coerceIn(0.15f, 1.0f)
                                val fitPageScale = minOf(fitWidthScale, fitHeightScale.coerceAtLeast(0.32f))
                                    .coerceIn(0.10f, 1.0f)
                                val minZoomScale = minOf(0.22f, fitPageScale * 0.85f).coerceAtLeast(0.08f)

                                val density = LocalDensity.current
                                val touchSlop = LocalViewConfiguration.current.touchSlop
                                val doubleTapSlopPx = with(density) { 48.dp.toPx() }
                                val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                                val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

                                SideEffect {
                                    currentFitPageScale = fitPageScale
                                    currentMinZoomScale = minZoomScale
                                    lastViewportWidthPx = viewportWidthPx
                                    lastViewportHeightPx = viewportHeightPx
                                }

                                // Layout scales bind strictly to committedScale so pinch/animations run at pure 60/120Hz GPU speed
                                val cellWidth = (130.dp * committedScale).coerceIn(10.dp, 390.dp)
                                val headerFontSize = (12f * committedScale).coerceIn(3.5f, 28f).sp
                                val dataFontSize = (12f * committedScale).coerceIn(3.5f, 28f).sp
                                val cellLineHeight = (15f * committedScale).coerceIn(4.5f, 34f).sp
                                val cellPaddingH = (8.dp * committedScale).coerceIn(1.dp, 18.dp)
                                val cellPaddingV = (7.dp * committedScale).coerceIn(1.dp, 16.dp)
                                val cellMaxLines = when {
                                    committedScale < 0.35f -> 1
                                    committedScale < 0.65f -> 2
                                    else -> Int.MAX_VALUE
                                }
                                val visualMultiplier = (zoomScale / committedScale).coerceIn(0.15f, 5.0f)

                                val gestureModifier = Modifier.pointerInput(
                                    minZoomScale,
                                    maxZoomScale,
                                    isListView,
                                    viewportWidthPx,
                                    viewportHeightPx,
                                    committedScale
                                ) {
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

                                                    val nextVisual = (gestureScale / committedScale).coerceAtLeast(0.15f)
                                                    val rawX = currentCentroid.x - (currentCentroid.x - gesturePanX) * effectiveStep + panDelta.x
                                                    val rawY = currentCentroid.y - (currentCentroid.y - gesturePanY) * effectiveStep + panDelta.y
                                                    if (nextVisual >= 1f) {
                                                        val maxX = (nextVisual - 1f) * viewportWidthPx
                                                        val maxY = (nextVisual - 1f) * viewportHeightPx
                                                        gesturePanX = rawX.coerceIn(-maxX, 0f)
                                                        gesturePanY = rawY.coerceIn(-maxY, 0f)
                                                    } else {
                                                        val marginX = ((1f - nextVisual) * viewportWidthPx) / 2f
                                                        gesturePanX = rawX.coerceIn(-marginX * 0.5f, marginX * 1.5f)
                                                        gesturePanY = rawY.coerceIn(-viewportHeightPx * 0.25f, viewportHeightPx * 0.25f)
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
                                            val currentPanX = gesturePanX
                                            val currentPanY = gesturePanY
                                            zoomAnimationJob?.cancel()
                                            zoomAnimationJob = coroutineScope.launch {
                                                if (abs(gestureScale - settledScale) > 0.01f) {
                                                    zoomAnim.snapTo(gestureScale)
                                                    zoomAnim.animateTo(
                                                        settledScale,
                                                        spring(stiffness = Spring.StiffnessMediumLow)
                                                    ) {
                                                        liveZoomScale = value
                                                    }
                                                }
                                                commitExcelZoom(settledScale, currentPanX, currentPanY)
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
                                                    animateExcelZoomTo(
                                                        target = target,
                                                        focalScreenPoint = downPosition,
                                                        viewWidthPx = viewportWidthPx,
                                                        viewHeightPx = viewportHeightPx
                                                    )
                                                } else {
                                                    lastTapTime = releaseTime
                                                    lastTapPosition = downPosition
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isListView) {
                                    // Vertical Card List View for mobile reading
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(gestureModifier)
                                    ) {
                                        LazyColumn(
                                            state = cardListState,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = visualMultiplier
                                                    scaleY = visualMultiplier
                                                    translationX = livePanX
                                                    translationY = livePanY
                                                    transformOrigin = TransformOrigin(0f, 0f)
                                                },
                                            contentPadding = PaddingValues((12.dp * committedScale).coerceIn(4.dp, 20.dp)),
                                            verticalArrangement = Arrangement.spacedBy((8.dp * committedScale).coerceIn(3.dp, 16.dp))
                                        ) {
                                            itemsIndexed(sheet.rows) { rowIndex, rowCells ->
                                                val rowFonts = sheet.rowFonts.getOrNull(rowIndex)
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = WhiteMixCard,
                                                    border = BorderStroke(1.dp, WhiteMixBorder),
                                                    shadowElevation = 0.5.dp
                                                ) {
                                                    Column(modifier = Modifier.padding((12.dp * committedScale).coerceIn(4.dp, 18.dp))) {
                                                        Text(
                                                            text = "Row #${rowIndex + 1}",
                                                            fontSize = (11f * committedScale).coerceIn(6f, 22f).sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = BrandPrimary
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        rowCells.forEachIndexed { colIndex, cellVal ->
                                                            val headerName = sheet.headers.getOrNull(colIndex)
                                                                ?: "Column ${('A' + colIndex)}"
                                                            val headerFont = selectedFontOverride
                                                                ?: sheet.headerFonts.getOrNull(colIndex)
                                                                ?: detectedPrimaryFont
                                                            val cellFont = selectedFontOverride
                                                                ?: rowFonts?.getOrNull(colIndex)
                                                                ?: detectedPrimaryFont
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(vertical = 2.dp),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    text = headerName,
                                                                    fontFamily = headerFont.fontFamily,
                                                                    fontSize = (11f * committedScale).coerceIn(6f, 22f).sp,
                                                                    color = TextSecondary,
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                                Text(
                                                                    text = cellVal,
                                                                    fontFamily = cellFont.fontFamily,
                                                                    fontSize = (12f * committedScale).coerceIn(6f, 24f).sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = TextPrimary,
                                                                    modifier = Modifier.weight(1.5f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Horizontal & Vertical Table Grid with full A4 zoom-out & double-tap
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(gestureModifier)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer {
                                                    scaleX = visualMultiplier
                                                    scaleY = visualMultiplier
                                                    translationX = livePanX
                                                    translationY = livePanY
                                                    transformOrigin = TransformOrigin(0f, 0f)
                                                }
                                                .horizontalScroll(horizontalScrollState)
                                        ) {
                                            LazyColumn(
                                                state = gridListState,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(
                                                        horizontal = if (committedScale < 0.7f) 4.dp else 0.dp,
                                                        vertical = if (committedScale < 0.7f) 4.dp else 0.dp
                                                    )
                                            ) {
                                                // Header Row
                                                item {
                                                    Row(
                                                        modifier = Modifier
                                                            .background(Color(0xFFF3F4F6))
                                                            .border(0.5.dp, WhiteMixBorder)
                                                    ) {
                                                        for (colIdx in 0 until colCount) {
                                                            val header = sheet.headers.getOrNull(colIdx) ?: ""
                                                            val headerFont = selectedFontOverride
                                                                ?: sheet.headerFonts.getOrNull(colIdx)
                                                                ?: detectedPrimaryFont
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(cellWidth)
                                                                    .border(0.5.dp, WhiteMixBorder)
                                                                    .padding(
                                                                        horizontal = cellPaddingH,
                                                                        vertical = cellPaddingV + (1.dp * committedScale).coerceAtLeast(0.5.dp)
                                                                    )
                                                            ) {
                                                                Text(
                                                                    text = header,
                                                                    fontFamily = headerFont.fontFamily,
                                                                    fontSize = headerFontSize,
                                                                    lineHeight = cellLineHeight,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = TextPrimary,
                                                                    maxLines = cellMaxLines,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Data Rows
                                                itemsIndexed(sheet.rows) { rowIndex, row ->
                                                    val rowFonts = sheet.rowFonts.getOrNull(rowIndex)
                                                    Row(
                                                        modifier = Modifier
                                                            .background(
                                                                if (rowIndex % 2 == 0) WhiteMixSurface else Color(0xFFFAFAFA)
                                                            )
                                                            .border(0.5.dp, WhiteMixBorder)
                                                    ) {
                                                        for (colIdx in 0 until colCount) {
                                                            val cell = row.getOrNull(colIdx) ?: ""
                                                            val cellFont = selectedFontOverride
                                                                ?: rowFonts?.getOrNull(colIdx)
                                                                ?: detectedPrimaryFont
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(cellWidth)
                                                                    .border(0.5.dp, WhiteMixBorder)
                                                                    .padding(
                                                                        horizontal = cellPaddingH,
                                                                        vertical = cellPaddingV
                                                                    )
                                                            ) {
                                                                Text(
                                                                    text = cell,
                                                                    fontFamily = cellFont.fontFamily,
                                                                    fontSize = dataFontSize,
                                                                    lineHeight = cellLineHeight,
                                                                    color = TextPrimary,
                                                                    maxLines = cellMaxLines,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Reset Zoom Pill when scaled
                    if (abs(zoomScale - 1.0f) > 0.05f) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    animateExcelZoomTo(1.0f)
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

private fun openExcelInExternalApp(context: Context, entity: PdfEntity) {
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

        val mime = when {
            entity.title.endsWith(".csv", ignoreCase = true) -> "text/csv"
            entity.title.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
            else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with Excel or Sheets"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open external app: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
