package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PdfEntity
import com.example.pdf.DrawingPoint
import com.example.pdf.DrawingStroke
import com.example.pdf.PageEditState
import com.example.pdf.PdfEditorHelper
import com.example.pdf.SignatureAnnotation
import com.example.pdf.StampAnnotation
import com.example.pdf.StampType
import com.example.pdf.TextAnnotation
import com.example.pdf.WatermarkConfig
import com.example.ui.theme.AppFonts
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.PdfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class EditorToolMode {
    VIEW, DRAW, HIGHLIGHT, TEXT, SIGNATURE, STAMP, WATERMARK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    file: File,
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTool by remember { mutableStateOf(EditorToolMode.VIEW) }
    var selectedColor by remember { mutableStateOf(0xFF1E3A8A) }
    var strokeWidth by remember { mutableFloatStateOf(5f) }

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPageIndex by remember { mutableIntStateOf(0) }

    val pageEditStates = remember { mutableStateMapOf<Int, PageEditState>() }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showTextDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showStampDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Drawing state for the current page
    val currentStrokes = remember { mutableStateListOf<DrawingStroke>() }
    var livePoints by remember { mutableStateOf<List<DrawingPoint>>(emptyList()) }

    DisposableEffect(file) {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(descriptor)
            pfd = descriptor
            renderer = pdfRenderer
            pageCount = pdfRenderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
        }
    }

    LaunchedEffect(currentPageIndex, renderer) {
        val ren = renderer ?: return@LaunchedEffect
        if (currentPageIndex in 0 until pageCount) {
            withContext(Dispatchers.IO) {
                try {
                    val page = ren.openPage(currentPageIndex)
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    currentBitmap = bmp
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // Load state for this page
            val existing = pageEditStates[currentPageIndex]
            currentStrokes.clear()
            if (existing != null) {
                currentStrokes.addAll(existing.strokes)
            }
        }
    }

    fun syncCurrentPageState() {
        val current = pageEditStates[currentPageIndex] ?: PageEditState(
            pageId = "page_$currentPageIndex",
            originalPageIndex = currentPageIndex
        )
        pageEditStates[currentPageIndex] = current.copy(strokes = currentStrokes.toList())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = WhiteMixBackground,
        topBar = {
            Surface(
                color = WhiteMixSurface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Compact 48dp height
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("editor_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit: ${file.nameWithoutExtension}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Page navigation
                        if (pageCount > 1) {
                            Text(
                                text = "${currentPageIndex + 1}/$pageCount",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        // Rotate button
                        IconButton(
                            onClick = {
                                val current = pageEditStates[currentPageIndex] ?: PageEditState(
                                    pageId = "page_$currentPageIndex",
                                    originalPageIndex = currentPageIndex
                                )
                                pageEditStates[currentPageIndex] = current.copy(
                                    rotationDegrees = (current.rotationDegrees + 90) % 360
                                )
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = BrandPrimary, modifier = Modifier.size(20.dp))
                        }

                        // Save button
                        IconButton(
                            onClick = {
                                syncCurrentPageState()
                                isSaving = true
                                val outDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
                                val outFile = File(outDir, "${file.nameWithoutExtension}_edited.pdf")
                                coroutineScope.launch {
                                    val success = PdfEditorHelper.saveEditedPdf(
                                        context = context,
                                        originalFile = file,
                                        pagesEditState = pageEditStates.toMap(),
                                        outputFile = outFile
                                    )
                                    isSaving = false
                                    if (success) {
                                        Toast.makeText(context, "Saved successfully: ${outFile.name}", Toast.LENGTH_LONG).show()
                                        viewModel.refreshDocuments()
                                        onBack()
                                    } else {
                                        Toast.makeText(context, "Error saving document", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("editor_save_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = BrandCrimson, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Compact Editor Toolbar (56dp)
            Surface(
                color = WhiteMixSurface,
                shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, WhiteMixBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorToolButton(
                        icon = Icons.Default.Draw,
                        label = "Draw",
                        isActive = activeTool == EditorToolMode.DRAW,
                        onClick = {
                            activeTool = if (activeTool == EditorToolMode.DRAW) EditorToolMode.VIEW else EditorToolMode.DRAW
                        }
                    )
                    EditorToolButton(
                        icon = Icons.Default.Highlight,
                        label = "Highlight",
                        isActive = activeTool == EditorToolMode.HIGHLIGHT,
                        onClick = {
                            activeTool = if (activeTool == EditorToolMode.HIGHLIGHT) EditorToolMode.VIEW else EditorToolMode.HIGHLIGHT
                        }
                    )
                    EditorToolButton(
                        icon = Icons.Default.TextFields,
                        label = "Text",
                        isActive = activeTool == EditorToolMode.TEXT,
                        onClick = {
                            activeTool = EditorToolMode.TEXT
                            showTextDialog = true
                        }
                    )
                    EditorToolButton(
                        icon = Icons.Default.Create,
                        label = "Signature",
                        isActive = activeTool == EditorToolMode.SIGNATURE,
                        onClick = {
                            activeTool = EditorToolMode.SIGNATURE
                            showSignatureDialog = true
                        }
                    )
                    EditorToolButton(
                        icon = Icons.Default.Verified,
                        label = "Stamp",
                        isActive = activeTool == EditorToolMode.STAMP,
                        onClick = {
                            activeTool = EditorToolMode.STAMP
                            showStampDialog = true
                        }
                    )
                    EditorToolButton(
                        icon = Icons.Default.FormatColorFill,
                        label = "Watermark",
                        isActive = activeTool == EditorToolMode.WATERMARK,
                        onClick = {
                            activeTool = EditorToolMode.WATERMARK
                            showWatermarkDialog = true
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            val currentPageState = pageEditStates[currentPageIndex]
            val rotationDegrees = currentPageState?.rotationDegrees ?: 0

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val boxWidth = maxWidth
                val boxHeight = maxHeight

                currentBitmap?.let { bmp ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(rotationDegrees.toFloat())
                            .pointerInput(activeTool) {
                                if (activeTool == EditorToolMode.DRAW || activeTool == EditorToolMode.HIGHLIGHT) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val pt = DrawingPoint(offset.x / size.width, offset.y / size.height)
                                            livePoints = listOf(pt)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pt = DrawingPoint(change.position.x / size.width, change.position.y / size.height)
                                            livePoints = livePoints + pt
                                        },
                                        onDragEnd = {
                                            if (livePoints.isNotEmpty()) {
                                                val stroke = DrawingStroke(
                                                    points = livePoints,
                                                    color = if (activeTool == EditorToolMode.HIGHLIGHT) 0xFFFFEE58 else selectedColor,
                                                    strokeWidth = if (activeTool == EditorToolMode.HIGHLIGHT) strokeWidth * 3f else strokeWidth,
                                                    isHighlighter = (activeTool == EditorToolMode.HIGHLIGHT)
                                                )
                                                currentStrokes.add(stroke)
                                                syncCurrentPageState()
                                                livePoints = emptyList()
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        // Canvas drawing with Multiply blend mode so background watermark stays visible
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // 1. Watermark in the BACK
                            currentPageState?.watermark?.let { wm: WatermarkConfig ->
                                val wmPaint = android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    color = wm.color.toInt()
                                    alpha = (wm.opacity * 255).toInt().coerceIn(0, 255)
                                    textSize = size.width * 0.08f
                                    isFakeBoldText = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawContext.canvas.nativeCanvas.save()
                                drawContext.canvas.nativeCanvas.rotate(wm.angle, size.width / 2f, size.height / 2f)
                                drawContext.canvas.nativeCanvas.drawText(wm.text, size.width / 2f, size.height / 2f, wmPaint)
                                drawContext.canvas.nativeCanvas.restore()
                            }

                            // 2. Draw Page Bitmap with Multiply BlendMode
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                blendMode = BlendMode.Multiply
                            )

                            // 3. Draw Completed Strokes
                            for (stroke in currentStrokes) {
                                if (stroke.points.size >= 2) {
                                    val path = Path()
                                    val first = stroke.points.first()
                                    path.moveTo(first.x * size.width, first.y * size.height)
                                    for (i in 1 until stroke.points.size) {
                                        val p = stroke.points[i]
                                        path.lineTo(p.x * size.width, p.y * size.height)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(stroke.color).copy(alpha = if (stroke.isHighlighter) 0.45f else 1f),
                                        style = Stroke(width = stroke.strokeWidth)
                                    )
                                }
                            }

                            // 4. Draw Live Stroke while dragging
                            if (livePoints.size >= 2) {
                                val path = Path()
                                val first = livePoints.first()
                                path.moveTo(first.x * size.width, first.y * size.height)
                                for (i in 1 until livePoints.size) {
                                    val p = livePoints[i]
                                    path.lineTo(p.x * size.width, p.y * size.height)
                                }
                                drawPath(
                                    path = path,
                                    color = if (activeTool == EditorToolMode.HIGHLIGHT) Color.Yellow.copy(alpha = 0.5f) else Color(selectedColor),
                                    style = Stroke(width = if (activeTool == EditorToolMode.HIGHLIGHT) strokeWidth * 3f else strokeWidth)
                                )
                            }
                        }

                        // 4. Render Text Annotations as interactive overlays
                        currentPageState?.textAnnotations?.forEach { textAnn ->
                            val font = AppFonts.SupportedFont.fromId(textAnn.fontFamilyId)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(
                                        start = (boxWidth * textAnn.xPercent),
                                        top = (boxHeight * textAnn.yPercent)
                                    )
                                    .background(
                                        color = textAnn.backgroundColor?.let { Color(it) } ?: Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = textAnn.text,
                                    color = Color(textAnn.color),
                                    fontSize = textAnn.fontSizePt.sp,
                                    fontFamily = font.fontFamily,
                                    fontWeight = if (textAnn.isBold) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // 5. Render Stamps
                        currentPageState?.stampAnnotations?.forEach { stamp ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(
                                        start = (boxWidth * stamp.xPercent),
                                        top = (boxHeight * stamp.yPercent)
                                    )
                                    .rotate(stamp.rotationAngle)
                                    .border(2.dp, Color(stamp.stampType.color), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (stamp.customText.isNotBlank()) stamp.customText else stamp.stampType.title,
                                    color = Color(stamp.stampType.color),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } ?: run {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            }

            if (isSaving) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }

    // Add Text Annotation Dialog with Hindi & English fonts
    if (showTextDialog) {
        var annotationText by remember { mutableStateOf("") }
        var selectedFontId by remember { mutableStateOf("DEFAULT") }
        var isBold by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text Annotation", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = annotationText,
                        onValueChange = { annotationText = it },
                        label = { Text("Enter text (Hindi / English)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Select Font • फॉन्ट चुनें:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AppFonts.SupportedFont.entries.toTypedArray()) { font ->
                            val isSel = font.id == selectedFontId
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedFontId = font.id },
                                label = { Text(font.displayName, fontSize = 11.sp, fontFamily = font.fontFamily) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (annotationText.isNotBlank()) {
                            val newAnn = TextAnnotation(
                                id = "text_${System.currentTimeMillis()}",
                                text = annotationText,
                                xPercent = 0.2f,
                                yPercent = 0.3f,
                                color = selectedColor,
                                fontSizePt = 16f,
                                isBold = isBold,
                                fontFamilyId = selectedFontId
                            )
                            val current = pageEditStates[currentPageIndex] ?: PageEditState(
                                pageId = "page_$currentPageIndex",
                                originalPageIndex = currentPageIndex
                            )
                            pageEditStates[currentPageIndex] = current.copy(
                                textAnnotations = current.textAnnotations + newAnn
                            )
                            showTextDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Add Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Official Stamp Dialog
    if (showStampDialog) {
        var selectedStampType by remember { mutableStateOf(StampType.APPROVED) }
        var customStampText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showStampDialog = false },
            title = { Text("Official Stamp • शासकीय सील", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    StampType.entries.forEach { st ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedStampType = st }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = st == selectedStampType,
                                onClick = { selectedStampType = st }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(st.title, color = Color(st.color), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newStamp = StampAnnotation(
                            id = "stamp_${System.currentTimeMillis()}",
                            stampType = selectedStampType,
                            customText = customStampText,
                            xPercent = 0.3f,
                            yPercent = 0.4f
                        )
                        val current = pageEditStates[currentPageIndex] ?: PageEditState(
                            pageId = "page_$currentPageIndex",
                            originalPageIndex = currentPageIndex
                        )
                        pageEditStates[currentPageIndex] = current.copy(
                            stampAnnotations = current.stampAnnotations + newStamp
                        )
                        showStampDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Apply Stamp")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStampDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Watermark Dialog
    if (showWatermarkDialog) {
        var watermarkText by remember { mutableStateOf("CONFIDENTIAL • गोपनीय") }
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text("Background Watermark • वॉटरमार्क", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Will be placed behind text content on all pages.", fontSize = 12.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val config = WatermarkConfig(text = watermarkText)
                        val current = pageEditStates[currentPageIndex] ?: PageEditState(
                            pageId = "page_$currentPageIndex",
                            originalPageIndex = currentPageIndex
                        )
                        pageEditStates[currentPageIndex] = current.copy(watermark = config)
                        showWatermarkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Apply Watermark")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatermarkDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Signature Pad Dialog
    if (showSignatureDialog) {
        val sigPoints = remember { mutableStateListOf<DrawingPoint>() }
        AlertDialog(
            onDismissRequest = { showSignatureDialog = false },
            title = { Text("Sign Document • डिजिटल हस्ताक्षर", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pt ->
                                        sigPoints.add(DrawingPoint(pt.x / size.width, pt.y / size.height))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        sigPoints.add(DrawingPoint(change.position.x / size.width, change.position.y / size.height))
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (sigPoints.size >= 2) {
                                val path = Path()
                                path.moveTo(sigPoints.first().x * size.width, sigPoints.first().y * size.height)
                                for (p in sigPoints) {
                                    path.lineTo(p.x * size.width, p.y * size.height)
                                }
                                drawPath(path, Color.Black, style = Stroke(width = 3f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { sigPoints.clear() }) {
                        Text("Clear Signature", color = BrandCrimson)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sigPoints.isNotEmpty()) {
                            val stroke = DrawingStroke(
                                points = sigPoints.toList(),
                                color = 0xFF000000,
                                strokeWidth = 3f
                            )
                            val sig = SignatureAnnotation(
                                id = "sig_${System.currentTimeMillis()}",
                                strokes = listOf(stroke),
                                xPercent = 0.2f,
                                yPercent = 0.6f
                            )
                            val current = pageEditStates[currentPageIndex] ?: PageEditState(
                                pageId = "page_$currentPageIndex",
                                originalPageIndex = currentPageIndex
                            )
                            pageEditStates[currentPageIndex] = current.copy(
                                signatureAnnotations = current.signatureAnnotations + sig
                            )
                            showSignatureDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Place Signature")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignatureDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditorToolButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) BrandPrimary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (isActive) BrandPrimary else TextSecondary,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
