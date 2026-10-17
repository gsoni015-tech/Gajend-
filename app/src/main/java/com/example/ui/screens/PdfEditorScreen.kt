package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfEntity
import com.example.pdf.DrawingPoint
import com.example.pdf.DrawingStroke
import com.example.pdf.PageEditState
import com.example.pdf.PdfEditorHelper
import com.example.pdf.PdfRendererHelper
import com.example.pdf.SignatureAnnotation
import com.example.pdf.StampAnnotation
import com.example.pdf.StampType
import com.example.pdf.TextAnnotation
import com.example.pdf.WatermarkConfig
import com.example.ui.components.AdMobBottomBanner
import com.example.ui.viewmodel.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

enum class EditorToolMode {
    PAN_ZOOM,
    DRAW_PEN,
    TEXT_ANNOTATION,
    SIGNATURE,
    STAMP,
    WATERMARK,
    PAGE_ORGANIZER
}

val EDITOR_PALETTE_COLORS = listOf(
    0xFF0F172A, // Black / Charcoal
    0xFF1E3A8A, // Deep Navy
    0xFF2563EB, // Royal Blue
    0xFFDC2626, // Crimson Red
    0xFF16A34A, // Emerald Green
    0xFFD97706, // Amber
    0xFF7C3AED, // Purple
    0xFFFACC15  // Yellow Highlighter
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPdf by viewModel.pdfForEditing.collectAsStateWithLifecycle()
    val isSaving by viewModel.isToolProcessing.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val pdfRenderer = remember { PdfRendererHelper(context) }

    // Pages list state
    val pages = remember { mutableStateListOf<PageEditState>() }
    var currentPageIndex by remember { mutableIntStateOf(0) }

    // Undo / Redo history stack: stores snapshots of pages list
    val undoStack = remember { mutableListOf<List<PageEditState>>() }
    val redoStack = remember { mutableListOf<List<PageEditState>>() }

    fun saveSnapshot() {
        // Deep copy of current pages list
        val snapshot = pages.map { it.copy() }
        undoStack.add(snapshot)
        if (undoStack.size > 20) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentSnapshot = pages.map { it.copy() }
            redoStack.add(currentSnapshot)
            val previous = undoStack.removeAt(undoStack.size - 1)
            pages.clear()
            pages.addAll(previous)
            if (currentPageIndex >= pages.size) {
                currentPageIndex = (pages.size - 1).coerceAtLeast(0)
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentSnapshot = pages.map { it.copy() }
            undoStack.add(currentSnapshot)
            val next = redoStack.removeAt(redoStack.size - 1)
            pages.clear()
            pages.addAll(next)
            if (currentPageIndex >= pages.size) {
                currentPageIndex = (pages.size - 1).coerceAtLeast(0)
            }
        }
    }

    // Active tool state
    var activeTool by remember { mutableStateOf(EditorToolMode.DRAW_PEN) }

    // Drawing Tool Settings
    var strokeColor by remember { mutableStateOf(0xFF0F172A) }
    var strokeWidth by remember { mutableFloatStateOf(4f) }
    var isHighlighterMode by remember { mutableStateOf(false) }
    val currentPoints = remember { mutableStateListOf<DrawingPoint>() }

    // Text Annotation Dialog state
    var showAddTextDialog by remember { mutableStateOf(false) }
    var textToEdit by remember { mutableStateOf<TextAnnotation?>(null) }

    // Signature Pad Dialog state
    var showSignatureDialog by remember { mutableStateOf(false) }

    // Stamp Sheet state
    var showStampSheet by remember { mutableStateOf(false) }

    // Watermark Dialog state
    var showWatermarkDialog by remember { mutableStateOf(false) }

    // Save Dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    // Canvas Zoom & Pan (when PAN_ZOOM mode is selected)
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Current page rendered bitmap
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    // Initialize document pages
    LaunchedEffect(currentPdf) {
        val pdf = currentPdf
        if (pdf != null) {
            val file = File(pdf.path)
            if (file.exists()) {
                val count = pdfRenderer.openFile(file)
                pages.clear()
                for (i in 0 until count) {
                    pages.add(PageEditState(originalPageIndex = i))
                }
                currentPageIndex = 0
                isLoadingPage = true
                currentBitmap = pdfRenderer.renderPage(0)
                isLoadingPage = false
            }
        } else {
            // New blank document
            pages.clear()
            pages.add(PageEditState(originalPageIndex = -1))
            currentPageIndex = 0
            isLoadingPage = false
        }
    }

    // Re-render bitmap when page index or source changes
    LaunchedEffect(currentPageIndex, pages.size) {
        if (currentPageIndex in pages.indices) {
            val pageState = pages[currentPageIndex]
            if (pageState.originalPageIndex >= 0) {
                isLoadingPage = true
                currentBitmap = pdfRenderer.renderPage(pageState.originalPageIndex)
                isLoadingPage = false
            } else {
                currentBitmap = null
                isLoadingPage = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer.close()
        }
    }

    BackHandler {
        if (undoStack.isNotEmpty()) {
            showDiscardConfirmDialog = true
        } else {
            onNavigateBack()
        }
    }

    val currentPageState = pages.getOrNull(currentPageIndex)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentPdf?.title ?: "PDF Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Page ${currentPageIndex + 1} of ${pages.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                showDiscardConfirmDialog = true
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undo
                    IconButton(
                        onClick = { undo() },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = { redo() },
                        enabled = redoStack.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                        )
                    }

                    // Save Button
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.testTag("save_edited_pdf_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save PDF",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Primary Feature Mode Navigation Bar
            ScrollableTabRow(
                selectedTabIndex = activeTool.ordinal,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                EditorToolMode.entries.forEach { mode ->
                    val (label, icon) = when (mode) {
                        EditorToolMode.DRAW_PEN -> Pair("Pen & Draw", Icons.Default.Draw)
                        EditorToolMode.TEXT_ANNOTATION -> Pair("Add Text", Icons.Default.TextFields)
                        EditorToolMode.SIGNATURE -> Pair("Signature", Icons.Default.Gesture)
                        EditorToolMode.STAMP -> Pair("Stamps", Icons.Default.AutoAwesome)
                        EditorToolMode.WATERMARK -> Pair("Watermark", Icons.Default.WaterDrop)
                        EditorToolMode.PAGE_ORGANIZER -> Pair("Pages", Icons.Default.Layers)
                        EditorToolMode.PAN_ZOOM -> Pair("Zoom / Move", Icons.Default.PanTool)
                    }
                    Tab(
                        selected = activeTool == mode,
                        onClick = { activeTool = mode },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // Sub-Toolbar depending on active tool
            AnimatedVisibility(visible = activeTool == EditorToolMode.DRAW_PEN) {
                DrawSubToolbar(
                    selectedColor = strokeColor,
                    strokeWidth = strokeWidth,
                    isHighlighter = isHighlighterMode,
                    onSelectColor = { strokeColor = it },
                    onChangeStrokeWidth = { strokeWidth = it },
                    onToggleHighlighter = { isHighlighterMode = it },
                    onClearDrawings = {
                        if (currentPageIndex in pages.indices) {
                            saveSnapshot()
                            pages[currentPageIndex] = pages[currentPageIndex].copy(strokes = emptyList())
                        }
                    }
                )
            }

            AnimatedVisibility(visible = activeTool == EditorToolMode.TEXT_ANNOTATION) {
                TextSubToolbar(
                    onAddTextClick = {
                        textToEdit = null
                        showAddTextDialog = true
                    }
                )
            }

            AnimatedVisibility(visible = activeTool == EditorToolMode.SIGNATURE) {
                SignatureSubToolbar(
                    onOpenSignaturePad = { showSignatureDialog = true }
                )
            }

            AnimatedVisibility(visible = activeTool == EditorToolMode.STAMP) {
                StampSubToolbar(
                    onOpenStamps = { showStampSheet = true }
                )
            }

            AnimatedVisibility(visible = activeTool == EditorToolMode.WATERMARK) {
                WatermarkSubToolbar(
                    currentWatermark = currentPageState?.watermark,
                    onOpenConfig = { showWatermarkDialog = true },
                    onRemoveWatermark = {
                        saveSnapshot()
                        pages[currentPageIndex] = pages[currentPageIndex].copy(watermark = null)
                    }
                )
            }

            // Main Editor Canvas or Page Organizer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (activeTool == EditorToolMode.PAGE_ORGANIZER) {
                    PageOrganizerView(
                        pages = pages,
                        currentPageIndex = currentPageIndex,
                        onSelectPage = {
                            currentPageIndex = it
                            activeTool = EditorToolMode.DRAW_PEN
                        },
                        onRotatePage = { idx ->
                            saveSnapshot()
                            val cur = pages[idx]
                            val newRot = (cur.rotationDegrees + 90) % 360
                            pages[idx] = cur.copy(rotationDegrees = newRot)
                        },
                        onDeletePage = { idx ->
                            if (pages.size > 1) {
                                saveSnapshot()
                                pages.removeAt(idx)
                                if (currentPageIndex >= pages.size) {
                                    currentPageIndex = pages.size - 1
                                }
                            }
                        },
                        onDuplicatePage = { idx ->
                            saveSnapshot()
                            val cur = pages[idx]
                            pages.add(idx + 1, cur.copy(pageId = UUID.randomUUID().toString()))
                        },
                        onMovePage = { from, to ->
                            if (from in pages.indices && to in pages.indices) {
                                saveSnapshot()
                                val item = pages.removeAt(from)
                                pages.add(to, item)
                                currentPageIndex = to
                            }
                        },
                        onAddBlankPage = {
                            saveSnapshot()
                            pages.add(PageEditState(originalPageIndex = -1))
                            currentPageIndex = pages.size - 1
                        }
                    )
                } else {
                    // Document Page Canvas with zoom/pan and annotations
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .then(
                                if (activeTool == EditorToolMode.PAN_ZOOM) {
                                    Modifier.pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 4f)
                                            if (scale > 1f) {
                                                val maxOffset = (scale - 1f) * 600f
                                                offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                                offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                        }
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val canvasWidth = maxWidth
                        val canvasHeight = maxHeight

                        // Standard A4 aspect ratio 1 : 1.414
                        val rot = currentPageState?.rotationDegrees ?: 0
                        val isRotatedSideways = (rot == 90 || rot == 270)
                        val pageAspect = if (isRotatedSideways) 1.414f else 0.707f

                        Box(
                            modifier = Modifier
                                .aspectRatio(pageAspect, matchHeightConstraintsFirst = true)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        ) {
                            if (isLoadingPage) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                // 1. Base rendered PDF page bitmap
                                if (currentBitmap != null) {
                                    Image(
                                        bitmap = currentBitmap!!.asImageBitmap(),
                                        contentDescription = "PDF Page",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rotate(rot.toFloat())
                                    )
                                } else {
                                    // Blank Page Indicator
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Blank Page", color = Color.LightGray, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 2. Watermark Overlay
                                currentPageState?.watermark?.let { wm ->
                                    WatermarkOverlay(watermark = wm)
                                }

                                // 3. Canvas for Drawing Strokes & Current Live Stroke
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (activeTool == EditorToolMode.DRAW_PEN) {
                                                Modifier.pointerInput(Unit) {
                                                    detectDragGestures(
                                                        onDragStart = { offset ->
                                                            currentPoints.clear()
                                                            currentPoints.add(
                                                                DrawingPoint(
                                                                    offset.x / size.width.toFloat(),
                                                                    offset.y / size.height.toFloat()
                                                                )
                                                            )
                                                        },
                                                        onDrag = { change, _ ->
                                                            change.consume()
                                                            currentPoints.add(
                                                                DrawingPoint(
                                                                    change.position.x / size.width.toFloat(),
                                                                    change.position.y / size.height.toFloat()
                                                                )
                                                            )
                                                        },
                                                        onDragEnd = {
                                                            if (currentPoints.size >= 2 && currentPageIndex in pages.indices) {
                                                                saveSnapshot()
                                                                val newStroke = DrawingStroke(
                                                                    points = currentPoints.toList(),
                                                                    color = strokeColor,
                                                                    strokeWidth = strokeWidth,
                                                                    isHighlighter = isHighlighterMode
                                                                )
                                                                val updatedStrokes = pages[currentPageIndex].strokes + newStroke
                                                                pages[currentPageIndex] = pages[currentPageIndex].copy(strokes = updatedStrokes)
                                                            }
                                                            currentPoints.clear()
                                                        },
                                                        onDragCancel = {
                                                            currentPoints.clear()
                                                        }
                                                    )
                                                }
                                            } else Modifier
                                        )
                                ) {
                                    val w = size.width
                                    val h = size.height

                                    // Render saved strokes for current page
                                    currentPageState?.strokes?.forEach { stroke ->
                                        if (stroke.points.size >= 2) {
                                            val path = Path()
                                            path.moveTo(stroke.points[0].x * w, stroke.points[0].y * h)
                                            for (i in 1 until stroke.points.size) {
                                                path.lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
                                            }
                                            val strokePaintColor = Color(stroke.color)
                                            val finalColor = if (stroke.isHighlighter) {
                                                strokePaintColor.copy(alpha = 0.45f)
                                            } else {
                                                strokePaintColor
                                            }
                                            val finalWidth = if (stroke.isHighlighter) stroke.strokeWidth * 3.5f else stroke.strokeWidth

                                            drawPath(
                                                path = path,
                                                color = finalColor,
                                                style = Stroke(
                                                    width = finalWidth,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }

                                    // Render active drawing stroke
                                    if (currentPoints.size >= 2) {
                                        val activePath = Path()
                                        activePath.moveTo(currentPoints[0].x * w, currentPoints[0].y * h)
                                        for (i in 1 until currentPoints.size) {
                                            activePath.lineTo(currentPoints[i].x * w, currentPoints[i].y * h)
                                        }
                                        val activeColor = Color(strokeColor)
                                        val finalColor = if (isHighlighterMode) activeColor.copy(alpha = 0.45f) else activeColor
                                        val finalWidth = if (isHighlighterMode) strokeWidth * 3.5f else strokeWidth

                                        drawPath(
                                            path = activePath,
                                            color = finalColor,
                                            style = Stroke(
                                                width = finalWidth,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }

                                // 4. Text Annotations Layer (Draggable & Selectable)
                                currentPageState?.textAnnotations?.forEach { textAnn ->
                                    DraggableTextAnnotationView(
                                        annotation = textAnn,
                                        onPositionChanged = { newX, newY ->
                                            saveSnapshot()
                                            val updated = pages[currentPageIndex].textAnnotations.map {
                                                if (it.id == textAnn.id) it.copy(xPercent = newX, yPercent = newY) else it
                                            }
                                            pages[currentPageIndex] = pages[currentPageIndex].copy(textAnnotations = updated)
                                        },
                                        onEdit = {
                                            textToEdit = textAnn
                                            showAddTextDialog = true
                                        },
                                        onDelete = {
                                            saveSnapshot()
                                            val updated = pages[currentPageIndex].textAnnotations.filter { it.id != textAnn.id }
                                            pages[currentPageIndex] = pages[currentPageIndex].copy(textAnnotations = updated)
                                        }
                                    )
                                }

                                // 5. Signatures Layer
                                currentPageState?.signatureAnnotations?.forEach { sig ->
                                    DraggableSignatureView(
                                        signature = sig,
                                        onPositionChanged = { newX, newY ->
                                            saveSnapshot()
                                            val updated = pages[currentPageIndex].signatureAnnotations.map {
                                                if (it.id == sig.id) it.copy(xPercent = newX, yPercent = newY) else it
                                            }
                                            pages[currentPageIndex] = pages[currentPageIndex].copy(signatureAnnotations = updated)
                                        },
                                        onDelete = {
                                            saveSnapshot()
                                            val updated = pages[currentPageIndex].signatureAnnotations.filter { it.id != sig.id }
                                            pages[currentPageIndex] = pages[currentPageIndex].copy(signatureAnnotations = updated)
                                        }
                                    )
                                }

                                // 6. Stamp Annotations Layer
                                currentPageState?.stampAnnotations?.forEach { stamp ->
                                    DraggableStampView(
                                        stamp = stamp,
                                        onPositionChanged = { newX, newY ->
                                            saveSnapshot()
                                            val updated = pages[currentPageIndex].stampAnnotations.map {
                                                if (it.id == stamp.id) it.copy(xPercent = newX, yPercent = newY) else it
                                            }
                                            pages[currentPageIndex] = pages[currentPageIndex].copy(stampAnnotations = updated)
                                        },
                                        onDelete = {
                                            saveSnapshot()
                                            val updated = pages[currentPageIndex].stampAnnotations.filter { it.id != stamp.id }
                                            pages[currentPageIndex] = pages[currentPageIndex].copy(stampAnnotations = updated)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation & Page Controls
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Previous page
                        IconButton(
                            onClick = {
                                if (currentPageIndex > 0) currentPageIndex--
                            },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Page")
                        }

                        // Page indicator
                        Text(
                            text = "Page ${currentPageIndex + 1} of ${pages.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Next page
                        IconButton(
                            onClick = {
                                if (currentPageIndex < pages.size - 1) currentPageIndex++
                            },
                            enabled = currentPageIndex < pages.size - 1
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Page")
                        }
                    }

                    AdMobBottomBanner()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // DIALOGS & SHEETS
    // -------------------------------------------------------------------------

    // 1. Text Annotation Dialog
    if (showAddTextDialog) {
        AddTextAnnotationDialog(
            initialText = textToEdit?.text ?: "",
            initialColor = textToEdit?.color ?: 0xFF0F172A,
            initialSize = textToEdit?.fontSizePt ?: 16f,
            initialBg = textToEdit?.backgroundColor,
            initialBold = textToEdit?.isBold ?: false,
            initialItalic = textToEdit?.isItalic ?: false,
            onDismiss = { showAddTextDialog = false },
            onSave = { text, color, size, bg, bold, italic ->
                saveSnapshot()
                if (textToEdit != null) {
                    val updated = pages[currentPageIndex].textAnnotations.map {
                        if (it.id == textToEdit!!.id) {
                            it.copy(
                                text = text,
                                color = color,
                                fontSizePt = size,
                                backgroundColor = bg,
                                isBold = bold,
                                isItalic = italic
                            )
                        } else it
                    }
                    pages[currentPageIndex] = pages[currentPageIndex].copy(textAnnotations = updated)
                } else {
                    val newAnnotation = TextAnnotation(
                        text = text,
                        xPercent = 0.25f,
                        yPercent = 0.35f,
                        fontSizePt = size,
                        color = color,
                        backgroundColor = bg,
                        isBold = bold,
                        isItalic = italic
                    )
                    pages[currentPageIndex] = pages[currentPageIndex].copy(
                        textAnnotations = pages[currentPageIndex].textAnnotations + newAnnotation
                    )
                }
                showAddTextDialog = false
            }
        )
    }

    // 2. Signature Pad Dialog
    if (showSignatureDialog) {
        SignaturePadDialog(
            onDismiss = { showSignatureDialog = false },
            onSaveSignature = { strokes ->
                if (strokes.isNotEmpty()) {
                    saveSnapshot()
                    val newSig = SignatureAnnotation(
                        strokes = strokes,
                        xPercent = 0.3f,
                        yPercent = 0.6f,
                        scale = 1.0f
                    )
                    pages[currentPageIndex] = pages[currentPageIndex].copy(
                        signatureAnnotations = pages[currentPageIndex].signatureAnnotations + newSig
                    )
                }
                showSignatureDialog = false
            }
        )
    }

    // 3. Stamps Bottom Sheet
    if (showStampSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showStampSheet = false },
            sheetState = sheetState
        ) {
            StampSelectionSheet(
                onSelectStamp = { stampType ->
                    saveSnapshot()
                    val newStamp = StampAnnotation(
                        stampType = stampType,
                        xPercent = 0.5f,
                        yPercent = 0.45f
                    )
                    pages[currentPageIndex] = pages[currentPageIndex].copy(
                        stampAnnotations = pages[currentPageIndex].stampAnnotations + newStamp
                    )
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showStampSheet = false }
                }
            )
        }
    }

    // 4. Watermark Configuration Dialog
    if (showWatermarkDialog) {
        WatermarkConfigDialog(
            initial = currentPageState?.watermark ?: WatermarkConfig(),
            onDismiss = { showWatermarkDialog = false },
            onApply = { wm ->
                saveSnapshot()
                if (wm.applyToAllPages) {
                    for (i in pages.indices) {
                        pages[i] = pages[i].copy(watermark = wm)
                    }
                } else {
                    pages[currentPageIndex] = pages[currentPageIndex].copy(watermark = wm)
                }
                showWatermarkDialog = false
            }
        )
    }

    // 5. Save Document Dialog
    if (showSaveDialog) {
        val originalTitle = currentPdf?.title ?: "Document_Edited"
        var outputTitle by remember { mutableStateOf("${originalTitle}_Edited") }
        var overwriteMode by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveDialog = false },
            title = { Text("Save Edited PDF") },
            text = {
                Column {
                    Text("Choose how you want to save this document:")
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { overwriteMode = false }
                    ) {
                        RadioButton(selected = !overwriteMode, onClick = { overwriteMode = false })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as a new copy", fontWeight = if (!overwriteMode) FontWeight.Bold else FontWeight.Normal)
                    }

                    if (currentPdf != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { overwriteMode = true }
                        ) {
                            RadioButton(selected = overwriteMode, onClick = { overwriteMode = true })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Overwrite original file", fontWeight = if (overwriteMode) FontWeight.Bold else FontWeight.Normal)
                        }
                    }

                    if (!overwriteMode) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = outputTitle,
                            onValueChange = { outputTitle = it },
                            label = { Text("Document Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pdf = currentPdf ?: return@Button
                        viewModel.executeSaveEditedPdf(
                            context = context,
                            sourcePdf = pdf,
                            pageStates = pages.toList(),
                            outputTitle = outputTitle,
                            overwriteOriginal = overwriteMode,
                            onComplete = {
                                showSaveDialog = false
                                onNavigateBack()
                            }
                        )
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Save Document")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveDialog = false },
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 6. Discard Changes Confirmation
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved edits. Are you sure you want to exit without saving?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Discard & Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// SUB-TOOLBARS
// -----------------------------------------------------------------------------

@Composable
fun DrawSubToolbar(
    selectedColor: Long,
    strokeWidth: Float,
    isHighlighter: Boolean,
    onSelectColor: (Long) -> Unit,
    onChangeStrokeWidth: (Float) -> Unit,
    onToggleHighlighter: (Boolean) -> Unit,
    onClearDrawings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Color circles
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EDITOR_PALETTE_COLORS.take(5).forEach { colorVal ->
                    val isSelected = selectedColor == colorVal && !isHighlighter
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(colorVal))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable {
                                onSelectColor(colorVal)
                                onToggleHighlighter(false)
                            }
                    )
                }
            }

            // Highlighter toggle
            FilterChip(
                selected = isHighlighter,
                onClick = {
                    onToggleHighlighter(!isHighlighter)
                    if (!isHighlighter) onSelectColor(0xFFFACC15)
                },
                label = { Text("Highlighter", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(Icons.Default.FormatPaint, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            )

            // Clear page drawing
            IconButton(
                onClick = onClearDrawings,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Clear, contentDescription = "Clear Page Drawings", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun TextSubToolbar(onAddTextClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Tap to place customized text notes or captions", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onAddTextClick,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Text", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SignatureSubToolbar(onOpenSignaturePad: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sign document with finger or stylus", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onOpenSignaturePad,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign Here", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StampSubToolbar(onOpenStamps: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Add Approved, Confidential & Draft stamps", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onOpenStamps,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Choose Stamp", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun WatermarkSubToolbar(
    currentWatermark: WatermarkConfig?,
    onOpenConfig: () -> Unit,
    onRemoveWatermark: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentWatermark != null) {
                Text("Watermark: '${currentWatermark.text}' active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row {
                    OutlinedButton(onClick = onOpenConfig) { Text("Edit") }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onRemoveWatermark) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove Watermark", tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Text("Protect PDF with diagonal watermark", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = onOpenConfig,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Watermark", fontSize = 12.sp)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// OVERLAY ANNOTATION VIEWS (Text, Signature, Stamp, Watermark)
// -----------------------------------------------------------------------------

@Composable
fun WatermarkOverlay(watermark: WatermarkConfig) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = watermark.text.uppercase(),
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(watermark.color).copy(alpha = watermark.opacity),
            modifier = Modifier.rotate(watermark.angle),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DraggableTextAnnotationView(
    annotation: TextAnnotation,
    onPositionChanged: (Float, Float) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var isSelected by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentW = maxWidth
        val parentH = maxHeight

        val xPx = annotation.xPercent * parentW.value
        val yPx = annotation.yPercent * parentH.value

        Box(
            modifier = Modifier
                .offset(x = xPx.dp, y = yPx.dp)
                .pointerInput(annotation.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = ((xPx + dragAmount.x / density) / parentW.value).coerceIn(0.05f, 0.9f)
                        val newY = ((yPx + dragAmount.y / density) / parentH.value).coerceIn(0.05f, 0.9f)
                        onPositionChanged(newX, newY)
                    }
                }
                .pointerInput(annotation.id) {
                    detectTapGestures(
                        onTap = { isSelected = !isSelected }
                    )
                }
                .clip(RoundedCornerShape(4.dp))
                .background(
                    annotation.backgroundColor?.let { Color(it) } ?: Color.Transparent
                )
                .border(
                    width = if (isSelected) 1.5.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Column {
                Text(
                    text = annotation.text,
                    color = Color(annotation.color),
                    fontSize = annotation.fontSizePt.sp,
                    fontWeight = if (annotation.isBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (annotation.isItalic) FontStyle.Italic else FontStyle.Normal
                )

                if (isSelected) {
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(2.dp)
                    ) {
                        Text(
                            text = "Edit",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onEdit() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "Delete",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable { onDelete() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DraggableSignatureView(
    signature: SignatureAnnotation,
    onPositionChanged: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    var isSelected by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentW = maxWidth
        val parentH = maxHeight

        val xPx = signature.xPercent * parentW.value
        val yPx = signature.yPercent * parentH.value

        Box(
            modifier = Modifier
                .offset(x = xPx.dp, y = yPx.dp)
                .size(130.dp, 60.dp)
                .pointerInput(signature.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = ((xPx + dragAmount.x / density) / parentW.value).coerceIn(0.05f, 0.85f)
                        val newY = ((yPx + dragAmount.y / density) / parentH.value).coerceIn(0.05f, 0.85f)
                        onPositionChanged(newX, newY)
                    }
                }
                .pointerInput(signature.id) {
                    detectTapGestures(onTap = { isSelected = !isSelected })
                }
                .border(
                    width = if (isSelected) 1.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scale = signature.scale
                signature.strokes.forEach { stroke ->
                    if (stroke.points.size >= 2) {
                        val path = Path()
                        path.moveTo(stroke.points[0].x * scale, stroke.points[0].y * scale)
                        for (i in 1 until stroke.points.size) {
                            path.lineTo(stroke.points[i].x * scale, stroke.points[i].y * scale)
                        }
                        drawPath(
                            path = path,
                            color = Color(stroke.color),
                            style = Stroke(width = stroke.strokeWidth * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            if (isSelected) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Delete Signature", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun DraggableStampView(
    stamp: StampAnnotation,
    onPositionChanged: (Float, Float) -> Unit,
    onDelete: () -> Unit
) {
    var isSelected by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val parentW = maxWidth
        val parentH = maxHeight

        val xPx = stamp.xPercent * parentW.value
        val yPx = stamp.yPercent * parentH.value

        val stampColor = Color(stamp.stampType.color)
        val title = stamp.customText?.takeIf { it.isNotBlank() } ?: stamp.stampType.title

        Box(
            modifier = Modifier
                .offset(x = xPx.dp, y = yPx.dp)
                .rotate(stamp.rotationAngle)
                .pointerInput(stamp.id) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = ((xPx + dragAmount.x / density) / parentW.value).coerceIn(0.05f, 0.85f)
                        val newY = ((yPx + dragAmount.y / density) / parentH.value).coerceIn(0.05f, 0.85f)
                        onPositionChanged(newX, newY)
                    }
                }
                .pointerInput(stamp.id) {
                    detectTapGestures(onTap = { isSelected = !isSelected })
                }
                .clip(RoundedCornerShape(6.dp))
                .background(stampColor.copy(alpha = 0.12f))
                .border(2.dp, stampColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = stampColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp
                )

                if (isSelected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(20.dp).background(Color.Red, CircleShape)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// PAGE ORGANIZER GRID
// -----------------------------------------------------------------------------

@Composable
fun PageOrganizerView(
    pages: List<PageEditState>,
    currentPageIndex: Int,
    onSelectPage: (Int) -> Unit,
    onRotatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onDuplicatePage: (Int) -> Unit,
    onMovePage: (from: Int, to: Int) -> Unit,
    onAddBlankPage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Page Organizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${pages.size} Total Pages", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Button(
                onClick = onAddBlankPage,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Blank Page", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(pages) { index, pageState ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPage(index) }
                        .border(
                            width = if (index == currentPageIndex) 2.5.dp else 1.dp,
                            color = if (index == currentPageIndex) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // Page thumbnail box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (pageState.originalPageIndex >= 0) "Page ${pageState.originalPageIndex + 1}" else "Blank Page",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (pageState.rotationDegrees != 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Rotated ${pageState.rotationDegrees}°", fontSize = 11.sp, color = Color(0xFFD97706))
                                }
                                if (pageState.strokes.isNotEmpty() || pageState.textAnnotations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${pageState.strokes.size + pageState.textAnnotations.size} Edits", fontSize = 11.sp, color = Color(0xFF16A34A))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Controls row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("P${index + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            Row {
                                // Rotate 90
                                IconButton(onClick = { onRotatePage(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                                }
                                // Duplicate
                                IconButton(onClick = { onDuplicatePage(index) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                                }
                                // Delete
                                if (pages.size > 1) {
                                    IconButton(onClick = { onDeletePage(index) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
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

// -----------------------------------------------------------------------------
// TEXT CUSTOMIZATION DIALOG
// -----------------------------------------------------------------------------

@Composable
fun AddTextAnnotationDialog(
    initialText: String,
    initialColor: Long,
    initialSize: Float,
    initialBg: Long?,
    initialBold: Boolean,
    initialItalic: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Long, Float, Long?, Boolean, Boolean) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var fontSize by remember { mutableFloatStateOf(initialSize) }
    var bgOption by remember { mutableStateOf(initialBg) }
    var isBold by remember { mutableStateOf(initialBold) }
    var isItalic by remember { mutableStateOf(initialItalic) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialText.isNotBlank()) "Edit Text" else "Add Text Note") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Enter text") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Font size slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Size: ${fontSize.roundToInt()} pt", fontSize = 12.sp, modifier = Modifier.width(75.dp))
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 12f..36f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Style toggles
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = isBold,
                        onClick = { isBold = !isBold },
                        label = { Text("Bold", fontWeight = FontWeight.Bold) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = isItalic,
                        onClick = { isItalic = !isItalic },
                        label = { Text("Italic", fontStyle = FontStyle.Italic) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Colors
                Text("Text Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EDITOR_PALETTE_COLORS.take(5).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (selectedColor == c) 2.5.dp else 1.dp,
                                    color = if (selectedColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Background Style
                Text("Background Style:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = bgOption == null,
                        onClick = { bgOption = null },
                        label = { Text("None") }
                    )
                    FilterChip(
                        selected = bgOption == 0xFFFEF08A,
                        onClick = { bgOption = 0xFFFEF08A },
                        label = { Text("Sticky Yellow") }
                    )
                    FilterChip(
                        selected = bgOption == 0xFFFFFFFF,
                        onClick = { bgOption = 0xFFFFFFFF },
                        label = { Text("White Card") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSave(text.trim(), selectedColor, fontSize, bgOption, isBold, isItalic)
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -----------------------------------------------------------------------------
// SIGNATURE PAD DIALOG
// -----------------------------------------------------------------------------

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (List<DrawingStroke>) -> Unit
) {
    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    val currentPoints = remember { mutableStateListOf<DrawingPoint>() }
    var strokeColor by remember { mutableStateOf(0xFF1E3A8A) } // Default blue ink

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Draw Signature")
                IconButton(onClick = { strokes.clear(); currentPoints.clear() }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear Pad", tint = Color.Red)
                }
            }
        },
        text = {
            Column {
                Text("Use finger to sign on the pad below:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.5.dp, Color(0xFF94A3B8), RoundedCornerShape(8.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints.clear()
                                        currentPoints.add(DrawingPoint(offset.x, offset.y))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints.add(DrawingPoint(change.position.x, change.position.y))
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size >= 2) {
                                            strokes.add(
                                                DrawingStroke(
                                                    points = currentPoints.toList(),
                                                    color = strokeColor,
                                                    strokeWidth = 3f
                                                )
                                            )
                                        }
                                        currentPoints.clear()
                                    },
                                    onDragCancel = { currentPoints.clear() }
                                )
                            }
                    ) {
                        // Base guideline
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(20f, size.height * 0.75f),
                            end = Offset(size.width - 20f, size.height * 0.75f),
                            strokeWidth = 1f
                        )

                        // Render strokes
                        strokes.forEach { stroke ->
                            if (stroke.points.size >= 2) {
                                val path = Path()
                                path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                for (i in 1 until stroke.points.size) {
                                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                                drawPath(path, Color(stroke.color), style = Stroke(stroke.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }

                        // Live current stroke
                        if (currentPoints.size >= 2) {
                            val active = Path()
                            active.moveTo(currentPoints[0].x, currentPoints[0].y)
                            for (i in 1 until currentPoints.size) {
                                active.lineTo(currentPoints[i].x, currentPoints[i].y)
                            }
                            drawPath(active, Color(strokeColor), style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ink Color:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                    listOf(0xFF1E3A8A to "Blue", 0xFF0F172A to "Black").forEach { (col, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { strokeColor = col }
                                .padding(end = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(col))
                                    .border(if (strokeColor == col) 2.dp else 0.dp, Color.Gray, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSignature(strokes.toList()) },
                enabled = strokes.isNotEmpty()
            ) {
                Text("Insert Signature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// -----------------------------------------------------------------------------
// STAMP SELECTION SHEET
// -----------------------------------------------------------------------------

@Composable
fun StampSelectionSheet(onSelectStamp: (StampType) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Text("Select Document Stamp", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Stamps can be repositioned anywhere on the page", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(280.dp)
        ) {
            items(StampType.entries) { stampType ->
                val color = Color(stampType.color)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectStamp(stampType) },
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, color)
                ) {
                    Box(
                        modifier = Modifier.padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stampType.title,
                            color = color,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// WATERMARK CONFIG DIALOG
// -----------------------------------------------------------------------------

@Composable
fun WatermarkConfigDialog(
    initial: WatermarkConfig,
    onDismiss: () -> Unit,
    onApply: (WatermarkConfig) -> Unit
) {
    var text by remember { mutableStateOf(initial.text) }
    var opacity by remember { mutableFloatStateOf(initial.opacity) }
    var isDiagonal by remember { mutableStateOf(initial.angle != 0f) }
    var applyToAll by remember { mutableStateOf(initial.applyToAllPages) }
    var selectedColor by remember { mutableStateOf(initial.color) }

    val presetTexts = listOf("CONFIDENTIAL", "DO NOT COPY", "DRAFT", "ORIGINAL")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watermark Settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Watermark Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Presets
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetTexts) { preset ->
                        FilterChip(
                            selected = text == preset,
                            onClick = { text = preset },
                            label = { Text(preset, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Opacity slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity: ${(opacity * 100).roundToInt()}%", fontSize = 12.sp, modifier = Modifier.width(90.dp))
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.1f..0.6f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Orientation & Scope
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = isDiagonal,
                        onClick = { isDiagonal = true },
                        label = { Text("Diagonal (-45°)") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !isDiagonal,
                        onClick = { isDiagonal = false },
                        label = { Text("Horizontal") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = applyToAll,
                        onClick = { applyToAll = true },
                        label = { Text("All Pages") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !applyToAll,
                        onClick = { applyToAll = false },
                        label = { Text("This Page Only") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onApply(
                            WatermarkConfig(
                                text = text.trim(),
                                opacity = opacity,
                                angle = if (isDiagonal) -45f else 0f,
                                color = selectedColor,
                                applyToAllPages = applyToAll
                            )
                        )
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text("Apply Watermark")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
