package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.core.content.FileProvider
import com.example.ads.AdMobManager
import com.example.data.local.PdfEntity
import com.example.reader.OfficeDocumentHelper
import com.example.reader.TextToSpeechHelper
import com.example.reader.WordDocument
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
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
    val context = LocalContext.current
    val activity = context as? Activity
    val handleBack: () -> Unit = {
        if (activity != null) {
            AdMobManager.showBackPressInterstitial(activity) { onBack() }
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }
    var doc by remember { mutableStateOf<WordDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isTtsActive by remember { mutableStateOf(false) }

    val ttsHelper = remember { TextToSpeechHelper(context) }
    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }

    LaunchedEffect(entity.path, entity.uriString) {
        isLoading = true
        doc = OfficeDocumentHelper.parseWordDocument(File(entity.path), context, entity.uriString)
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Open with external Office app (Word, Docs)
                        IconButton(
                            onClick = {
                                openWordInExternalApp(context, entity)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open with external app",
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp)
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isTtsActive) Icons.Default.Stop else Icons.Default.VolumeUp,
                                contentDescription = "TTS Read Aloud",
                                tint = if (isTtsActive) BrandCrimson else BrandPrimary,
                                modifier = Modifier.size(20.dp)
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
                .navigationBarsPadding()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPrimary)
                }
            } else {
                doc?.let { wordDoc ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                            .align(Alignment.TopCenter),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(wordDoc.paragraphs) { para ->
                            Text(
                                text = para.text,
                                fontSize = if (para.isHeading) 17.sp else 14.sp,
                                fontWeight = if (para.isHeading || para.isBold) FontWeight.Bold else FontWeight.Normal,
                                color = if (para.isHeading) BrandPrimary else TextPrimary,
                                lineHeight = if (para.isHeading) 24.sp else 22.sp,
                                modifier = Modifier.padding(vertical = if (para.isHeading) 8.dp else 4.dp)
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
