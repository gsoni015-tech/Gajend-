package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentType
import com.example.data.local.documentType
import com.example.reader.OfficeDocumentHelper
import com.example.reader.WordDocument
import com.example.ui.components.AdMobBottomBanner
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.ReaderThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordViewerScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPdf by viewModel.currentPdf.collectAsStateWithLifecycle()

    var wordDoc by remember { mutableStateOf<WordDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var fontScale by remember { mutableFloatStateOf(1f) }
    var readerTheme by remember { mutableStateOf(ReaderThemeMode.LIGHT) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(currentPdf) {
        val pdf = currentPdf
        if (pdf != null) {
            isLoading = true
            withContext(Dispatchers.IO) {
                val file = File(pdf.path)
                wordDoc = OfficeDocumentHelper.parseWord(file)
            }
            isLoading = false
        }
    }

    val pageBg = when (readerTheme) {
        ReaderThemeMode.LIGHT -> Color(0xFFFFFFFF)
        ReaderThemeMode.DARK_INVERT -> Color(0xFF1E293B)
        ReaderThemeMode.SEPIA -> Color(0xFFFBF0D9)
    }

    val screenBg = when (readerTheme) {
        ReaderThemeMode.LIGHT -> MaterialTheme.colorScheme.background
        ReaderThemeMode.DARK_INVERT -> Color(0xFF0F172A)
        ReaderThemeMode.SEPIA -> Color(0xFFF4ECD8)
    }

    val textColor = when (readerTheme) {
        ReaderThemeMode.LIGHT -> Color(0xFF0F172A)
        ReaderThemeMode.DARK_INVERT -> Color(0xFFF1F5F9)
        ReaderThemeMode.SEPIA -> Color(0xFF451A03)
    }

    val isWord = currentPdf?.documentType == DocumentType.WORD

    Surface(
        modifier = modifier.fillMaxSize(),
        color = screenBg
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isWord) Color(0xFF2563EB) else Color(0xFF8B5CF6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isWord) Icons.Default.Description else Icons.Default.TextFields,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = currentPdf?.title ?: "Document",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isWord) "Word Document Reader" else "Main Text Reader",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            // Font Size Controls (A- / A+)
                            IconButton(onClick = { if (fontScale > 0.8f) fontScale -= 0.1f }) {
                                Text("A-", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            IconButton(onClick = { if (fontScale < 1.6f) fontScale += 0.1f }) {
                                Text("A+", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            // Theme Toggle
                            IconButton(onClick = {
                                readerTheme = when (readerTheme) {
                                    ReaderThemeMode.LIGHT -> ReaderThemeMode.SEPIA
                                    ReaderThemeMode.SEPIA -> ReaderThemeMode.DARK_INVERT
                                    ReaderThemeMode.DARK_INVERT -> ReaderThemeMode.LIGHT
                                }
                            }) {
                                Icon(Icons.Default.Brightness4, contentDescription = "Toggle Reading Theme")
                            }

                            // Search
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(Icons.Default.Search, contentDescription = "Search text")
                            }

                            // Copy All Text
                            IconButton(onClick = {
                                val allText = wordDoc?.paragraphs?.joinToString("\n\n") { it.text } ?: ""
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Document Text", allText)
                                clipboard.setPrimaryClip(clip)
                                viewModel.showMessage("Copied full document text")
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                            }

                            // Share
                            IconButton(onClick = { currentPdf?.let { viewModel.sharePdf(context, it) } }) {
                                Icon(Icons.Default.Share, contentDescription = "Share Document")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    // Search row if active
                    if (isSearchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Find text in document...", fontSize = 13.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        }
                    }
                }
            }

            // Reading Stats Bar
            wordDoc?.let { doc ->
                val readingMinutes = maxOf(1, doc.wordCount / 200)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${doc.wordCount} words • ${doc.charCount} chars • ~$readingMinutes min read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isWord) ".DOCX Formatted" else ".TXT Formatted",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isWord) Color(0xFF2563EB) else Color(0xFF8B5CF6)
                        )
                    }
                }
            }

            // Main Document Content Box with prominent border
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val doc = wordDoc
                if (doc == null || doc.paragraphs.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No text content available in this file", color = Color.Gray)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // A4 paper card with crisp 1.5dp border and elevation
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("word_document_canvas"),
                            shape = RoundedCornerShape(12.dp),
                            color = pageBg,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                            shadowElevation = 3.dp,
                            tonalElevation = 2.dp
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 20.dp, vertical = 18.dp)
                            ) {
                                items(doc.paragraphs) { p ->
                                    val paragraphText = p.text
                                    val isMatch = searchQuery.isNotBlank() && paragraphText.contains(searchQuery, ignoreCase = true)

                                    if (p.isHeading) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = paragraphText,
                                            fontSize = (18f * fontScale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMatch) MaterialTheme.colorScheme.primary else textColor,
                                            modifier = if (isMatch) Modifier.background(Color(0xFFFEF08A)) else Modifier
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    } else if (p.isBullet) {
                                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                            Text(
                                                text = "• ",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = (14f * fontScale).sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            val annotated = buildAnnotatedString {
                                                if (searchQuery.isNotBlank() && isMatch) {
                                                    val parts = paragraphText.split(searchQuery, ignoreCase = true)
                                                    append(paragraphText)
                                                } else {
                                                    append(paragraphText)
                                                }
                                            }
                                            Text(
                                                text = annotated,
                                                fontSize = (14f * fontScale).sp,
                                                lineHeight = (22f * fontScale).sp,
                                                color = textColor,
                                                modifier = if (isMatch) Modifier.background(Color(0xFFFEF08A)) else Modifier
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = paragraphText,
                                            fontSize = (14.5f * fontScale).sp,
                                            lineHeight = (23f * fontScale).sp,
                                            color = textColor,
                                            modifier = Modifier
                                                .padding(vertical = 5.dp)
                                                .then(if (isMatch) Modifier.background(Color(0xFFFEF08A)) else Modifier)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AdMob Bottom Banner
            AdMobBottomBanner()
        }
    }
}
