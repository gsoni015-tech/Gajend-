package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.PdfEntity
import com.example.pdf.PdfToolsHelper
import com.example.reader.TextToSpeechHelper
import com.example.ui.components.ComposePdfViewer
import com.example.ui.components.PdfViewerScrollMode
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.ReaderThemeMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    entity: PdfEntity,
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onNavigateToEdit: (PdfEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val file = remember(entity.path) { File(entity.path) }

    var scrollMode by remember { mutableStateOf(PdfViewerScrollMode.CONTINUOUS_VERTICAL) }
    var readerTheme by remember { mutableStateOf(ReaderThemeMode.LIGHT) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }

    var showMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var isTtsActive by remember { mutableStateOf(false) }

    val isBookmarked by viewModel.currentBookmarkStatus.collectAsState()

    val ttsHelper = remember { TextToSpeechHelper(context) }
    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = when (readerTheme) {
            ReaderThemeMode.LIGHT -> WhiteMixBackground
            ReaderThemeMode.SEPIA -> Color(0xFFFBF0D9)
            ReaderThemeMode.DARK -> Color(0xFF121212)
        },
        topBar = {
            Surface(
                color = when (readerTheme) {
                    ReaderThemeMode.DARK -> Color(0xFF1E1E1E)
                    else -> WhiteMixSurface
                },
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Compact 48dp height
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (readerTheme == ReaderThemeMode.DARK) Color.White else TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entity.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (readerTheme == ReaderThemeMode.DARK) Color.White else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (scrollMode == PdfViewerScrollMode.CONTINUOUS_VERTICAL) "Continuous Vertical • $currentPage / $totalPages" else "Paged View • $currentPage / $totalPages",
                            fontSize = 10.sp,
                            color = if (readerTheme == ReaderThemeMode.DARK) Color.LightGray else TextSecondary
                        )
                    }

                    // View Mode Toggle (Vertical Continuous vs Horizontal Paged)
                    IconButton(
                        onClick = {
                            scrollMode = if (scrollMode == PdfViewerScrollMode.CONTINUOUS_VERTICAL) {
                                PdfViewerScrollMode.PAGED_HORIZONTAL
                            } else {
                                PdfViewerScrollMode.CONTINUOUS_VERTICAL
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (scrollMode == PdfViewerScrollMode.CONTINUOUS_VERTICAL) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle Scroll Mode",
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // TTS Button
                    IconButton(
                        onClick = {
                            if (isTtsActive) {
                                ttsHelper.stop()
                                isTtsActive = false
                            } else {
                                isTtsActive = true
                                ttsHelper.speak("Narayani PDF Reader. Reading page $currentPage of ${entity.title}.")
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsActive) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = if (isTtsActive) BrandCrimson else BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Edit / Annotate Button
                    IconButton(
                        onClick = { onNavigateToEdit(entity) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("viewer_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit & Annotate",
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bookmark Button
                    IconButton(
                        onClick = {
                            viewModel.toggleCurrentBookmark(entity.path, currentPage, "Page $currentPage")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark Page",
                            tint = if (isBookmarked) BrandCrimson else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Overflow Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = if (readerTheme == ReaderThemeMode.DARK) Color.White else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reading Theme • थीम") },
                                onClick = {
                                    showMenu = false
                                    showThemeDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Palette, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Compress PDF • साइज कम करें") },
                                onClick = {
                                    showMenu = false
                                    showCompressDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Compress, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share PDF • शेयर करें") },
                                onClick = {
                                    showMenu = false
                                    sharePdf(context, file)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ComposePdfViewer(
                file = file,
                scrollMode = scrollMode,
                readerTheme = readerTheme,
                onPageChanged = { page, total ->
                    currentPage = page
                    totalPages = total
                    viewModel.checkBookmarkStatus(entity.path, page)
                }
            )
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Reading Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ReaderThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (mode == readerTheme) BrandPrimary.copy(alpha = 0.1f) else Color.Transparent)
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = (mode == readerTheme),
                                onClick = { readerTheme = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.title, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showThemeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Apply")
                }
            }
        )
    }

    // Compress Dialog
    if (showCompressDialog) {
        var selectedLevel by remember { mutableStateOf(PdfToolsHelper.CompressionLevel.MEDIUM) }
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select compression level:", fontSize = 13.sp, color = TextSecondary)
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
                        showCompressDialog = false
                        viewModel.executeCompressPdf(file, selectedLevel) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Compress Now")
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

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
