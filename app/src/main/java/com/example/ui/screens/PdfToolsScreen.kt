package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.example.data.local.PdfEntity
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
    val hindiTitle: String,
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
    val isProcessing by viewModel.isProcessing.collectAsState()
    val progressMessage by viewModel.progressMessage.collectAsState()

    var showPdfToJpgDialog by remember { mutableStateOf(false) }
    var selectedPdfForJpg by remember { mutableStateOf<File?>(null) }
    var jpgConversionResult by remember { mutableStateOf<PdfToolsHelper.PdfToJpgResult?>(null) }

    var showImageToPdfDialog by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var showCompressDialog by remember { mutableStateOf(false) }
    var selectedPdfForCompress by remember { mutableStateOf<File?>(null) }

    // File pickers
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

    val toolsList = remember {
        listOf(
            ToolItem(
                id = "pdf_to_jpg",
                title = "PDF to JPG",
                hindiTitle = "Pdf से jpg",
                description = "Convert PDF pages into high-resolution JPG images • फोटो बनाएं",
                icon = Icons.Default.PhotoLibrary,
                iconColor = Color(0xFF0D9488),
                iconBg = Color(0xFFCCFBF1)
            ),
            ToolItem(
                id = "edit_pdf",
                title = "Edit PDF",
                hindiTitle = "एडिट करें",
                description = "Draw, sign, highlight, add text, stamps, and watermarks",
                icon = Icons.Default.Edit,
                iconColor = BrandPrimary,
                iconBg = Color(0xFFDBEAFE)
            ),
            ToolItem(
                id = "image_to_pdf",
                title = "Image to PDF",
                hindiTitle = "फोटो से पीडीएफ",
                description = "Convert gallery photos and scans into a single PDF document",
                icon = Icons.Default.Image,
                iconColor = Color(0xFF2563EB),
                iconBg = Color(0xFFEFF6FF)
            ),
            ToolItem(
                id = "compress_pdf",
                title = "Compress PDF",
                hindiTitle = "साइज कम करें",
                description = "Shrink PDF file size up to 80% while retaining high readability",
                icon = Icons.Default.Compress,
                iconColor = Color(0xFFD97706),
                iconBg = Color(0xFFFEF3C7)
            ),
            ToolItem(
                id = "merge_split",
                title = "Merge & Split",
                hindiTitle = "जोड़ें एवं अलग करें",
                description = "Combine multiple PDFs into one or extract selected pages",
                icon = Icons.Default.CallMerge,
                iconColor = Color(0xFF7C3AED),
                iconBg = Color(0xFFEDE9FE)
            ),
            ToolItem(
                id = "lock_pdf",
                title = "Lock / Unlock",
                hindiTitle = "पासवर्ड लॉक",
                description = "Secure your confidential documents with 4-digit PIN lock",
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
            // Compact Top Bar (size kam kar di gayi hai - 48dp)
            Surface(
                color = WhiteMixSurface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Compact 48dp height
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PDF Tools • टूल्स",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        // Native ad in PDF tools has been completely removed per user request
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
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
                                "merge_split" -> {
                                    Toast.makeText(context, "Select PDF to split or merge", Toast.LENGTH_SHORT).show()
                                    pdfPickerForJpg.launch(arrayOf("application/pdf"))
                                }
                                "lock_pdf" -> {
                                    Toast.makeText(context, "Open any file from Home and tap 3-dots to set PIN", Toast.LENGTH_LONG).show()
                                }
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
        var quality by remember { mutableIntStateOf(90) }
        AlertDialog(
            onDismissRequest = { showPdfToJpgDialog = false },
            title = {
                Text(
                    text = "PDF to JPG (Pdf से jpg)",
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
                        text = "Image Quality: $quality%",
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
                        text = "Images will be saved in your device Gallery under Pictures/NarayaniPDF.",
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
                        text = "Location: ${result.outputDirectory.absolutePath}",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        jpgConversionResult = null
                        // Open folder / gallery
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
        var pdfTitle by remember { mutableStateOf("Images_Document_${System.currentTimeMillis() % 10000}") }
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
                        viewModel.executeImagesToPdf(context, selectedImageUris, pdfTitle) {}
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
                        viewModel.executeCompressPdf(file, selectedLevel) {}
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
}

@Composable
fun ToolGridCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .testTag("tool_card_${tool.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = WhiteMixCard,
        shadowElevation = 0.5.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhiteMixBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tool.iconBg, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.title,
                    tint = tool.iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.title,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tool.description,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
    }
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
