package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.AdMobManager
import com.example.pdf.PdfToolsHelper
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.PdfViewModel
import java.io.File

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBg: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    viewModel: PdfViewModel,
    onNavigateToEditWithFile: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isProcessing by viewModel.isProcessing.collectAsState()
    val progressMessage by viewModel.progressMessage.collectAsState()

    var isProUnlocked by remember { mutableStateOf(false) }

    var showPdfToJpgDialog by remember { mutableStateOf(false) }
    var selectedPdfForJpg by remember { mutableStateOf<File?>(null) }
    var jpgConversionResult by remember { mutableStateOf<PdfToolsHelper.PdfToJpgResult?>(null) }

    var showImageToPdfDialog by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var showCompressDialog by remember { mutableStateOf(false) }
    var selectedPdfForCompress by remember { mutableStateOf<File?>(null) }

    var showMergeDialog by remember { mutableStateOf(false) }
    var selectedPdfsForMerge by remember { mutableStateOf<List<File>>(emptyList()) }

    var showSplitDialog by remember { mutableStateOf(false) }
    var selectedPdfForSplit by remember { mutableStateOf<File?>(null) }

    var showLockDialog by remember { mutableStateOf(false) }
    var selectedUriForLock by remember { mutableStateOf<Uri?>(null) }
    var selectedDocNameForLock by remember { mutableStateOf("") }
    var isDocCurrentlyLocked by remember { mutableStateOf(false) }
    var currentPinForDoc by remember { mutableStateOf("") }
    var lockPinInput by remember { mutableStateOf("") }
    var lockPinConfirmInput by remember { mutableStateOf("") }
    var lockPinError by remember { mutableStateOf<String?>(null) }

    val allPdfsList by viewModel.allPdfs.collectAsState()

    val pdfPickerForLock = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = getFileNameFromUri(context, it)
            selectedUriForLock = it
            selectedDocNameForLock = name
            val matchingEntity = allPdfsList.find { entity ->
                entity.title.equals(name, ignoreCase = true) || entity.uriString == it.toString()
            }
            isDocCurrentlyLocked = matchingEntity?.isLocked == true && matchingEntity.password.isNotBlank()
            currentPinForDoc = matchingEntity?.password ?: ""
            lockPinInput = ""
            lockPinConfirmInput = ""
            lockPinError = null
            showLockDialog = true
        }
    }

    // Pickers
    val pdfPickerForJpg = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToTempFile(context, it, "temp_for_jpg.pdf")
            if (file != null) {
                selectedPdfForJpg = file
                showPdfToJpgDialog = true
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris
            showImageToPdfDialog = true
        }
    }

    val pdfPickerForEdit = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToTempFile(context, it, "temp_for_edit.pdf")
            if (file != null) {
                onNavigateToEditWithFile(file)
            }
        }
    }

    val pdfPickerForCompress = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToTempFile(context, it, "temp_for_compress.pdf")
            if (file != null) {
                selectedPdfForCompress = file
                showCompressDialog = true
            }
        }
    }

    val pdfPickerForMerge = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.size < 2) {
            Toast.makeText(context, "Select at least 2 PDF files to merge", Toast.LENGTH_SHORT).show()
        } else {
            val copiedFiles = uris.mapIndexedNotNull { index, uri ->
                copyUriToTempFile(context, uri, "merge_input_${index}_${System.currentTimeMillis() % 1000}.pdf")
            }
            if (copiedFiles.size >= 2) {
                selectedPdfsForMerge = copiedFiles
                showMergeDialog = true
            } else {
                Toast.makeText(context, "Could not load selected files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pdfPickerForSplit = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToTempFile(context, it, "split_source_${System.currentTimeMillis() % 1000}.pdf")
            if (file != null) {
                selectedPdfForSplit = file
                showSplitDialog = true
            }
        }
    }

    val toolsList = remember {
        listOf(
            ToolItem(
                id = "pdf_to_jpg",
                title = "PDF to JPG",
                description = "Convert PDF document pages into high-resolution JPG images",
                icon = Icons.Default.PhotoLibrary,
                iconColor = Color(0xFF0D9488),
                iconBg = Color(0xFFCCFBF1)
            ),
            ToolItem(
                id = "edit_pdf",
                title = "Edit PDF",
                description = "Draw, sign, highlight, add text, stamps, and watermarks",
                icon = Icons.Default.Edit,
                iconColor = BrandPrimary,
                iconBg = Color(0xFFDBEAFE)
            ),
            ToolItem(
                id = "image_to_pdf",
                title = "Image to PDF",
                description = "Convert photos and gallery images into a formatted PDF document",
                icon = Icons.Default.Image,
                iconColor = Color(0xFF2563EB),
                iconBg = Color(0xFFEFF6FF)
            ),
            ToolItem(
                id = "compress_pdf",
                title = "Compress PDF",
                description = "Reduce PDF file size up to 80% while retaining high clarity",
                icon = Icons.Default.Compress,
                iconColor = Color(0xFFD97706),
                iconBg = Color(0xFFFEF3C7)
            ),
            ToolItem(
                id = "merge_pdf",
                title = "Merge PDFs",
                description = "Combine multiple PDF documents into a single organized file",
                icon = Icons.Default.CallMerge,
                iconColor = Color(0xFF7C3AED),
                iconBg = Color(0xFFEDE9FE)
            ),
            ToolItem(
                id = "split_pdf",
                title = "Split PDF",
                description = "Extract selected page ranges or sheets into a new PDF",
                icon = Icons.Default.CallSplit,
                iconColor = Color(0xFF0284C7),
                iconBg = Color(0xFFE0F2FE)
            ),
            ToolItem(
                id = "lock_pdf",
                title = "Lock / Unlock",
                description = "Protect your confidential documents with 4-digit PIN security",
                icon = Icons.Default.Lock,
                iconColor = BrandCrimson,
                iconBg = Color(0xFFFEE2E2)
            )
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                        .displayCutoutPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 860.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.narayani_pdf_logo_1793504887925),
                                    contentDescription = "Narayani Logo",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PDF Tools",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = TextPrimary
                                    )
                                )
                            }

                            // Rewarded Ad trigger to unlock Pro tools
                            if (!isProUnlocked) {
                                OutlinedButton(
                                    onClick = {
                                        if (activity != null) {
                                            AdMobManager.showRewardedAd(
                                                activity = activity,
                                                onUserEarnedReward = {
                                                    isProUnlocked = true
                                                    Toast.makeText(context, "Ultra HD & Pro Features Unlocked!", Toast.LENGTH_LONG).show()
                                                },
                                                onAdClosed = {}
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unlock Pro (Ad)", fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        "PRO Active",
                                        color = Color(0xFFD97706),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                items(toolsList, key = { it.id }) { tool ->
                    ToolGridCard(
                        tool = tool,
                        onClick = {
                            when (tool.id) {
                                "pdf_to_jpg" -> pdfPickerForJpg.launch(arrayOf("application/pdf"))
                                "edit_pdf" -> pdfPickerForEdit.launch(arrayOf("application/pdf"))
                                "image_to_pdf" -> imagePicker.launch("image/*")
                                "compress_pdf" -> pdfPickerForCompress.launch(arrayOf("application/pdf"))
                                "merge_pdf" -> pdfPickerForMerge.launch("application/pdf")
                                "split_pdf" -> pdfPickerForSplit.launch(arrayOf("application/pdf"))
                                "lock_pdf" -> pdfPickerForLock.launch(arrayOf("application/pdf"))
                            }
                        }
                    )
                }
            }

            // Progress overlay
            if (isProcessing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 8.dp,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = BrandPrimary)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(progressMessage, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // PDF to JPG Dialog
    if (showPdfToJpgDialog && selectedPdfForJpg != null) {
        var quality by remember { mutableIntStateOf(if (isProUnlocked) 100 else 90) }
        AlertDialog(
            onDismissRequest = { showPdfToJpgDialog = false },
            title = {
                Text(
                    text = "PDF to JPG Converter",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "File: ${selectedPdfForJpg!!.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Image Quality: $quality% ${if (quality == 100) "(Ultra HD)" else ""}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 50f..100f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Images will be saved to your device Pictures/NarayaniPDF folder.",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = selectedPdfForJpg!!
                        showPdfToJpgDialog = false
                        viewModel.executePdfToJpg(
                            context = context,
                            pdfFile = file,
                            qualityPercent = quality
                        ) { result ->
                            jpgConversionResult = result
                            // Interstitial on task completion
                            if (activity != null) {
                                AdMobManager.showTaskCompletionInterstitial(activity) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Convert to JPG")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPdfToJpgDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // JPG Result Success Dialog
    jpgConversionResult?.let { result ->
        AlertDialog(
            onDismissRequest = { jpgConversionResult = null },
            title = { Text("Conversion Complete! 🎉", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Successfully converted ${result.totalPagesConverted} pages into high-resolution JPG images.",
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Saved in: ${result.outputDirectory.name}",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        jpgConversionResult = null
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.fromFile(result.outputDirectory), "*/*")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Images saved in Pictures/NarayaniPDF", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Done")
                }
            }
        )
    }

    // Image to PDF Dialog
    if (showImageToPdfDialog && selectedImageUris.isNotEmpty()) {
        var pdfTitle by remember { mutableStateOf("Document_${System.currentTimeMillis() % 10000}") }
        AlertDialog(
            onDismissRequest = { showImageToPdfDialog = false },
            title = { Text("Image to PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Selected: ${selectedImageUris.size} images", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pdfTitle,
                        onValueChange = { pdfTitle = it },
                        singleLine = true,
                        label = { Text("Output PDF Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImageToPdfDialog = false
                        viewModel.executeImagesToPdf(context, selectedImageUris, pdfTitle) {
                            if (activity != null) {
                                AdMobManager.showTaskCompletionInterstitial(activity) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Create PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImageToPdfDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Compress Dialog
    if (showCompressDialog && selectedPdfForCompress != null) {
        var selectedLevel by remember { mutableStateOf(PdfToolsHelper.CompressionLevel.MEDIUM) }
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("File: ${selectedPdfForCompress!!.name}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    PdfToolsHelper.CompressionLevel.entries.forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (level == selectedLevel),
                                onClick = { selectedLevel = level }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(level.label, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = selectedPdfForCompress!!
                        showCompressDialog = false
                        viewModel.executeCompressPdf(file, selectedLevel) {
                            if (activity != null) {
                                AdMobManager.showTaskCompletionInterstitial(activity) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Compress")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Merge Dialog
    if (showMergeDialog && selectedPdfsForMerge.isNotEmpty()) {
        var mergeTitle by remember { mutableStateOf("Merged_Document_${System.currentTimeMillis() % 10000}") }
        var currentMergeFiles by remember { mutableStateOf(selectedPdfsForMerge) }

        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = {
                Text(
                    text = "Merge PDFs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "${currentMergeFiles.size} PDFs selected in merge order:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                    ) {
                        currentMergeFiles.forEachIndexed { idx, file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${file.name}",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (currentMergeFiles.size > 2) {
                                    IconButton(
                                        onClick = {
                                            currentMergeFiles = currentMergeFiles.toMutableList().also { it.removeAt(idx) }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = BrandCrimson, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mergeTitle,
                        onValueChange = { mergeTitle = it },
                        singleLine = true,
                        label = { Text("Output PDF Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val filesToMerge = currentMergeFiles
                        showMergeDialog = false
                        viewModel.executeMergePdfs(context, filesToMerge, mergeTitle) { success ->
                            if (success && activity != null) {
                                AdMobManager.showTaskCompletionInterstitial(activity) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Merge Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Split Dialog
    if (showSplitDialog && selectedPdfForSplit != null) {
        val file = selectedPdfForSplit!!
        var totalPagesCount by remember(file) {
            mutableIntStateOf(
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(pfd)
                    val count = r.pageCount
                    r.close()
                    pfd.close()
                    count
                } catch (e: Exception) {
                    1
                }
            )
        }
        var fromPageStr by remember { mutableStateOf("1") }
        var toPageStr by remember(totalPagesCount) { mutableStateOf(minOf(totalPagesCount, 5).toString()) }
        var splitTitle by remember { mutableStateOf("${file.nameWithoutExtension}_part") }

        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = {
                Text(
                    text = "Split / Extract PDF",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "File: ${file.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Total Pages: $totalPagesCount",
                        fontSize = 12.sp,
                        color = BrandPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fromPageStr,
                            onValueChange = { if (it.all { ch -> ch.isDigit() }) fromPageStr = it },
                            label = { Text("From Page") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toPageStr,
                            onValueChange = { if (it.all { ch -> ch.isDigit() }) toPageStr = it },
                            label = { Text("To Page") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = splitTitle,
                        onValueChange = { splitTitle = it },
                        label = { Text("Output PDF Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val from = fromPageStr.toIntOrNull() ?: 1
                        val to = toPageStr.toIntOrNull() ?: totalPagesCount
                        if (from < 1 || to > totalPagesCount || from > to) {
                            Toast.makeText(context, "Invalid page range (1 to $totalPagesCount)", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        showSplitDialog = false
                        viewModel.executeSplitPdf(context, file, from, to, splitTitle) { success ->
                            if (success && activity != null) {
                                AdMobManager.showTaskCompletionInterstitial(activity) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Extract Pages")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Interactive Document PIN Lock / Unlock Dialog
    if (showLockDialog && selectedUriForLock != null) {
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isDocCurrentlyLocked) BrandCrimson else BrandPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDocCurrentlyLocked) "Unlock / Manage PIN" else "Protect with PIN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = selectedDocNameForLock,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDocCurrentlyLocked)
                            "This document is currently PIN protected. Enter current PIN to remove protection, or enter a new PIN to change it."
                        else
                            "Set a 4-6 digit security PIN. This document will require PIN authentication whenever opened in Narayani PDF Reader.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = lockPinInput,
                        onValueChange = {
                            if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                lockPinInput = it
                                lockPinError = null
                            }
                        },
                        label = { Text(if (isDocCurrentlyLocked) "Current / New PIN" else "4-6 Digit PIN") },
                        placeholder = { Text("e.g. 1234") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = lockPinError != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!isDocCurrentlyLocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = lockPinConfirmInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                    lockPinConfirmInput = it
                                    lockPinError = null
                                }
                            },
                            label = { Text("Confirm PIN") },
                            placeholder = { Text("Re-enter PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            isError = lockPinError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (lockPinError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lockPinError!!,
                            color = BrandCrimson,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    if (isDocCurrentlyLocked) {
                        TextButton(
                            onClick = {
                                if (lockPinInput == currentPinForDoc || currentPinForDoc.isBlank()) {
                                    viewModel.lockOrUnlockPdfByUri(selectedUriForLock!!, "")
                                    showLockDialog = false
                                } else {
                                    lockPinError = "Incorrect current PIN. Cannot unlock."
                                }
                            }
                        ) {
                            Text("Remove Lock", color = BrandCrimson)
                        }
                    }
                    Button(
                        onClick = {
                            if (!isDocCurrentlyLocked) {
                                if (lockPinInput.length < 4) {
                                    lockPinError = "PIN must be at least 4 digits"
                                } else if (lockPinInput != lockPinConfirmInput) {
                                    lockPinError = "PINs do not match"
                                } else {
                                    viewModel.lockOrUnlockPdfByUri(selectedUriForLock!!, lockPinInput)
                                    showLockDialog = false
                                }
                            } else {
                                if (lockPinInput.length < 4) {
                                    lockPinError = "New PIN must be at least 4 digits"
                                } else {
                                    viewModel.lockOrUnlockPdfByUri(selectedUriForLock!!, lockPinInput)
                                    showLockDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                    ) {
                        Text(if (isDocCurrentlyLocked) "Update PIN" else "Set Lock")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {}
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "Document.pdf"
}

private fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File? {
    return try {
        val destFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun ToolGridCard(
    tool: ToolItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.id}"),
        shape = RoundedCornerShape(12.dp),
        color = WhiteMixCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhiteMixBorder),
        shadowElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tool.iconBg, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.title,
                    tint = tool.iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tool.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.description,
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

