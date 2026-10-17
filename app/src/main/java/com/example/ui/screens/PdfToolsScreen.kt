package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ads.AdMobManager
import com.example.ads.AdvancedTool
import com.example.data.local.PdfEntity
import com.example.pdf.PdfToolsHelper
import com.example.ui.components.AdMobBottomBanner
import com.example.ui.viewmodel.PdfViewModel
import java.io.File

enum class ToolScreenState {
    HUB,
    EDIT_PICKER,
    IMAGE_TO_PDF,
    MERGE_SPLIT,
    COMPRESS,
    PASSWORD_LOCK,
    OCR_EXTRACT
}

@Composable
fun PdfToolsScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeTool by remember { mutableStateOf(ToolScreenState.HUB) }
    val unlockedTools by AdMobManager.unlockedTools.collectAsStateWithLifecycle()

    BackHandler(enabled = activeTool != ToolScreenState.HUB) {
        activeTool = ToolScreenState.HUB
    }

    when (activeTool) {
        ToolScreenState.HUB -> {
            ToolsHubView(
                unlockedTools = unlockedTools,
                onSelectTool = { toolState, advTool ->
                    if (advTool != null && !AdMobManager.isToolUnlocked(advTool)) {
                        AdMobManager.showRewardedVideoAd(advTool) {
                            activeTool = toolState
                        }
                    } else {
                        activeTool = toolState
                    }
                },
                modifier = modifier
            )
        }
        ToolScreenState.EDIT_PICKER -> {
            EditPdfPickerView(
                viewModel = viewModel,
                onBack = { activeTool = ToolScreenState.HUB },
                onProceedToEditor = onNavigateToEditPdf,
                modifier = modifier
            )
        }
        ToolScreenState.IMAGE_TO_PDF -> {
            ImageToPdfView(
                viewModel = viewModel,
                onBack = { activeTool = ToolScreenState.HUB },
                modifier = modifier
            )
        }
        ToolScreenState.MERGE_SPLIT -> {
            MergeSplitView(
                viewModel = viewModel,
                onBack = { activeTool = ToolScreenState.HUB },
                modifier = modifier
            )
        }
        ToolScreenState.COMPRESS -> {
            CompressPdfView(
                viewModel = viewModel,
                onBack = { activeTool = ToolScreenState.HUB },
                modifier = modifier
            )
        }
        ToolScreenState.PASSWORD_LOCK -> {
            PasswordLockView(
                viewModel = viewModel,
                onBack = { activeTool = ToolScreenState.HUB },
                modifier = modifier
            )
        }
        ToolScreenState.OCR_EXTRACT -> {
            OcrExtractView(
                viewModel = viewModel,
                onBack = { activeTool = ToolScreenState.HUB },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ToolsHubView(
    unlockedTools: Set<String>,
    onSelectTool: (ToolScreenState, AdvancedTool?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Narayani PDF Tools",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "High performance converters & document utilities",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // List of tools
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ToolActionCard(
                    title = "Edit PDF",
                    description = "Draw, sign, add text annotations, official stamps, watermarks, rotate & reorder pages.",
                    icon = Icons.Default.Edit,
                    iconBg = Color(0xFF6366F1),
                    isAdvanced = false,
                    isUnlocked = true,
                    onClick = { onSelectTool(ToolScreenState.EDIT_PICKER, null) }
                )
            }

            item {
                ToolActionCard(
                    title = "Image to PDF",
                    description = "Convert photos, scans, and gallery images into crisp, formatted multi-page PDFs.",
                    icon = Icons.Default.Image,
                    iconBg = Color(0xFFEF4444),
                    isAdvanced = false,
                    isUnlocked = true,
                    onClick = { onSelectTool(ToolScreenState.IMAGE_TO_PDF, null) }
                )
            }

            item {
                val isUnlocked = unlockedTools.contains(AdvancedTool.MERGE.id)
                ToolActionCard(
                    title = "PDF Merge & Split",
                    description = "Combine multiple PDF files into one, or extract selected page ranges into separate documents.",
                    icon = Icons.Default.CallMerge,
                    iconBg = Color(0xFF8B5CF6),
                    isAdvanced = true,
                    isUnlocked = isUnlocked,
                    onClick = { onSelectTool(ToolScreenState.MERGE_SPLIT, AdvancedTool.MERGE) }
                )
            }

            item {
                val isUnlocked = unlockedTools.contains(AdvancedTool.COMPRESS.id)
                ToolActionCard(
                    title = "Compress PDF",
                    description = "Shrink PDF file size by up to 80% while retaining high readability for easy sharing.",
                    icon = Icons.Default.Compress,
                    iconBg = Color(0xFF10B981),
                    isAdvanced = true,
                    isUnlocked = isUnlocked,
                    onClick = { onSelectTool(ToolScreenState.COMPRESS, AdvancedTool.COMPRESS) }
                )
            }

            item {
                ToolActionCard(
                    title = "Lock / Unlock Password",
                    description = "Secure your confidential documents with PIN encryption and biometric access controls.",
                    icon = Icons.Default.Lock,
                    iconBg = Color(0xFFF59E0B),
                    isAdvanced = false,
                    isUnlocked = true,
                    onClick = { onSelectTool(ToolScreenState.PASSWORD_LOCK, null) }
                )
            }

            item {
                val isUnlocked = unlockedTools.contains(AdvancedTool.OCR.id)
                ToolActionCard(
                    title = "OCR Text Extract",
                    description = "Extract formatted text from PDF pages with high precision optical character recognition.",
                    icon = Icons.Default.TextFields,
                    iconBg = Color(0xFF06B6D4),
                    isAdvanced = true,
                    isUnlocked = isUnlocked,
                    onClick = { onSelectTool(ToolScreenState.OCR_EXTRACT, AdvancedTool.OCR) }
                )
            }
        }

        AdMobBottomBanner()
    }
}

@Composable
fun ToolActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconBg: Color,
    isAdvanced: Boolean,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tool_card_${title.replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBg,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAdvanced) {
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isUnlocked) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("UNLOCKED", fontSize = 10.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("WATCH AD TO UNLOCK", fontSize = 10.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isAdvanced && !isUnlocked) Icons.Default.PlayCircle else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (isAdvanced && !isUnlocked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// Sub-Tool 1: Image to PDF
// -------------------------------------------------------------
@Composable
fun ImageToPdfView(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var outputTitle by remember { mutableStateOf("Photos_Document") }
    val isProcessing by viewModel.isToolProcessing.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages = selectedImages + uris
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolSubHeader(title = "Image to PDF", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = outputTitle,
                onValueChange = { outputTitle = it },
                label = { Text("Output PDF Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("image_pdf_title_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected Images (${selectedImages.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Photos")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No images selected yet", color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text("Select Images from Gallery")
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(selectedImages) { uri ->
                        Box(
                            modifier = Modifier
                                .size(140.dp, 190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected Image",
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { selectedImages = selectedImages.filter { it != uri } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (selectedImages.isNotEmpty()) {
                        viewModel.executeImageToPdf(context, selectedImages, outputTitle) {
                            onBack()
                        }
                    }
                },
                enabled = selectedImages.isNotEmpty() && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("convert_images_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Convert to PDF (${selectedImages.size} pages)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-Tool 2: PDF Merge & Split
// -------------------------------------------------------------
@Composable
fun MergeSplitView(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val allPdfs by viewModel.allPdfs.collectAsStateWithLifecycle(emptyList())
    val isProcessing by viewModel.isToolProcessing.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolSubHeader(title = "PDF Merge & Split", onBack = onBack)

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Merge PDFs") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Split PDF") })
        }

        if (selectedTab == 0) {
            // MERGE
            var selectedPdfPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
            var mergedTitle by remember { mutableStateOf("Combined_Document") }

            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = mergedTitle,
                    onValueChange = { mergedTitle = it },
                    label = { Text("Merged PDF Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select 2 or more documents to combine:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(allPdfs) { pdf ->
                        val isChecked = selectedPdfPaths.contains(pdf.path)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedPdfPaths = if (isChecked) selectedPdfPaths - pdf.path else selectedPdfPaths + pdf.path
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = if (isChecked) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pdf.title, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text("${pdf.pageCount} pages", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val files = selectedPdfPaths.map { File(it) }
                        viewModel.executeMergePdfs(context, files, mergedTitle) {
                            onBack()
                        }
                    },
                    enabled = selectedPdfPaths.size >= 2 && !isProcessing,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Merge ${selectedPdfPaths.size} Files", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // SPLIT
            var selectedPdfForSplit by remember { mutableStateOf<PdfEntity?>(allPdfs.firstOrNull()) }
            var startPage by remember { mutableIntStateOf(1) }
            var endPage by remember { mutableIntStateOf(1) }

            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                Text("Select Document to Split:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allPdfs) { pdf ->
                        FilterChip(
                            selected = selectedPdfForSplit?.path == pdf.path,
                            onClick = {
                                selectedPdfForSplit = pdf
                                startPage = 1
                                endPage = pdf.pageCount.coerceAtLeast(1)
                            },
                            label = { Text(pdf.title, maxLines = 1) }
                        )
                    }
                }

                selectedPdfForSplit?.let { pdf ->
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Total Pages: ${pdf.pageCount}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = startPage.toString(),
                            onValueChange = { it.toIntOrNull()?.let { num -> startPage = num.coerceIn(1, pdf.pageCount) } },
                            label = { Text("Start Page") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endPage.toString(),
                            onValueChange = { it.toIntOrNull()?.let { num -> endPage = num.coerceIn(startPage, pdf.pageCount) } },
                            label = { Text("End Page") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Extracted document will contain ${endPage - startPage + 1} pages.",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        selectedPdfForSplit?.let { pdf ->
                            viewModel.executeSplitPdf(context, File(pdf.path), startPage, endPage) {
                                onBack()
                            }
                        }
                    },
                    enabled = selectedPdfForSplit != null && !isProcessing,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Extract & Save Pages", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-Tool 3: Compress PDF
// -------------------------------------------------------------
@Composable
fun CompressPdfView(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allPdfs by viewModel.allPdfs.collectAsStateWithLifecycle(emptyList())
    var selectedPdf by remember { mutableStateOf<PdfEntity?>(allPdfs.firstOrNull()) }
    var selectedLevel by remember { mutableStateOf(PdfToolsHelper.CompressionLevel.MEDIUM) }
    val isProcessing by viewModel.isToolProcessing.collectAsStateWithLifecycle()
    val result by viewModel.compressionResult.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolSubHeader(title = "Compress PDF", onBack = onBack)

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            Text("Select Document to Compress:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allPdfs) { pdf ->
                    FilterChip(
                        selected = selectedPdf?.path == pdf.path,
                        onClick = { selectedPdf = pdf },
                        label = { Text(pdf.title) }
                    )
                }
            }

            selectedPdf?.let { pdf ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(pdf.title, fontWeight = FontWeight.Bold)
                            Text("Original Size: ${Formatter.formatFileSize(context, pdf.sizeBytes)}")
                        }
                        Icon(Icons.Default.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Compression Quality:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                PdfToolsHelper.CompressionLevel.entries.forEach { level ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLevel = level }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(level.label, fontWeight = if (selectedLevel == level) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    selectedPdf?.let { pdf ->
                        viewModel.executeCompressPdf(context, File(pdf.path), selectedLevel) {
                            onBack()
                        }
                    }
                },
                enabled = selectedPdf != null && !isProcessing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Compress Document Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-Tool 4: Password Lock
// -------------------------------------------------------------
@Composable
fun PasswordLockView(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allPdfs by viewModel.allPdfs.collectAsStateWithLifecycle(emptyList())
    var selectedPdf by remember { mutableStateOf<PdfEntity?>(allPdfs.firstOrNull()) }
    var pin by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolSubHeader(title = "Lock / Unlock Password", onBack = onBack)

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            Text("Select Document:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allPdfs) { pdf ->
                    FilterChip(
                        selected = selectedPdf?.path == pdf.path,
                        onClick = {
                            selectedPdf = pdf
                            pin = pdf.password
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pdf.isLocked) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF59E0B))
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(pdf.title)
                            }
                        }
                    )
                }
            }

            selectedPdf?.let { pdf ->
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (pdf.isLocked) "Document is Currently Locked 🔒" else "Document is Unlocked 🔓",
                            fontWeight = FontWeight.Bold,
                            color = if (pdf.isLocked) Color(0xFFD97706) else Color(0xFF16A34A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (pdf.isLocked) {
                                "Anyone opening this PDF will be required to enter the security PIN below."
                            } else {
                                "Set a 4-digit security PIN to restrict unauthorized reading."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text("Security PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (pdf.isLocked) {
                        OutlinedButton(
                            onClick = {
                                viewModel.unlockPdf(pdf)
                                onBack()
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Lock")
                        }
                    }

                    Button(
                        onClick = {
                            if (pin.isNotBlank()) {
                                viewModel.setLockPin(pdf, pin)
                                onBack()
                            }
                        },
                        enabled = pin.isNotBlank(),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (pdf.isLocked) "Update PIN" else "Apply Lock")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Sub-Tool 5: OCR Text Extract
// -------------------------------------------------------------
@Composable
fun OcrExtractView(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allPdfs by viewModel.allPdfs.collectAsStateWithLifecycle(emptyList())
    var selectedPdf by remember { mutableStateOf<PdfEntity?>(allPdfs.firstOrNull()) }
    var selectedPage by remember { mutableIntStateOf(1) }
    val isProcessing by viewModel.isToolProcessing.collectAsStateWithLifecycle()
    val extractedText by viewModel.ocrResultText.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolSubHeader(title = "OCR Text Extract", onBack = onBack)

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            Text("Select Document:", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allPdfs) { pdf ->
                    FilterChip(
                        selected = selectedPdf?.path == pdf.path,
                        onClick = {
                            selectedPdf = pdf
                            selectedPage = 1
                        },
                        label = { Text(pdf.title) }
                    )
                }
            }

            selectedPdf?.let { pdf ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Page (1 of ${pdf.pageCount}):", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = selectedPage.toString(),
                        onValueChange = { it.toIntOrNull()?.let { num -> selectedPage = num.coerceIn(1, pdf.pageCount) } },
                        modifier = Modifier.width(90.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.executeOcrExtraction(File(pdf.path), selectedPage - 1)
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text("Extract Text with OCR", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (extractedText.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extracted Text Result:", fontWeight = FontWeight.Bold)
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("OCR Text", extractedText)
                            clipboard.setPrimaryClip(clip)
                            viewModel.showMessage("Copied extracted text to clipboard")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        LazyColumn(modifier = Modifier.padding(14.dp)) {
                            item {
                                Text(
                                    text = extractedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tap 'Extract Text' to read content from page $selectedPage", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun ToolSubHeader(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EditPdfPickerView(
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onProceedToEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allPdfs by viewModel.allPdfs.collectAsStateWithLifecycle(emptyList())
    val pdfOnly = remember(allPdfs) { allPdfs.filter { it.path.endsWith(".pdf", ignoreCase = true) } }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importPdf(it)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ToolSubHeader(title = "Select PDF to Edit", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        openDocLauncher.launch(arrayOf("application/pdf"))
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import PDF", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        viewModel.setPdfForEdit(null)
                        onProceedToEditor()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Blank PDF", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Choose an existing document from your library:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (pdfOnly.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No PDF documents available yet. Create a blank PDF or import one above.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pdfOnly) { pdf ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.openPdfForEdit(pdf) {
                                        onProceedToEditor()
                                    }
                                }
                                .testTag("edit_pdf_item_${pdf.path.hashCode()}"),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFEEF2FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color(0xFF6366F1),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pdf.title,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${pdf.pageCount} pages • ${Formatter.formatFileSize(context, pdf.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

