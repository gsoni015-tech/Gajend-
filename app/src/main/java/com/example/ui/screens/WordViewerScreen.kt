package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PdfEntity
import com.example.reader.OfficeDocumentHelper
import com.example.reader.TextToSpeechHelper
import com.example.reader.WordDocument
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixSurface
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordViewerScreen(
    entity: PdfEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var doc by remember { mutableStateOf<WordDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isTtsActive by remember { mutableStateOf(false) }

    val ttsHelper = remember { TextToSpeechHelper(context) }
    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }

    LaunchedEffect(entity.path) {
        isLoading = true
        doc = OfficeDocumentHelper.parseWordDocument(File(entity.path))
        isLoading = false
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entity.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (isTtsActive) {
                                ttsHelper.stop()
                                isTtsActive = false
                            } else {
                                doc?.let { d ->
                                    isTtsActive = true
                                    val fullText = d.paragraphs.joinToString(" ") { it.text }
                                    ttsHelper.speak(fullText.take(500))
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsActive) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "TTS",
                            tint = if (isTtsActive) BrandCrimson else BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
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
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            } else {
                doc?.let { wordDoc ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(wordDoc.paragraphs) { para ->
                            Text(
                                text = para.text,
                                fontSize = if (para.isHeading) 17.sp else 14.sp,
                                fontWeight = if (para.isHeading || para.isBold) FontWeight.Bold else FontWeight.Normal,
                                color = if (para.isHeading) BrandPrimary else TextPrimary,
                                lineHeight = if (para.isHeading) 24.sp else 20.sp,
                                modifier = Modifier.padding(vertical = if (para.isHeading) 8.dp else 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
