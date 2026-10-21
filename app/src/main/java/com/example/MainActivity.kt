package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.AdMobManager
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.example.ui.screens.ExcelViewerScreen
import com.example.ui.screens.FileListScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PdfToolsScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.WordViewerScreen
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.PdfViewModel
import com.example.util.PermissionUtils
import java.io.File

enum class MainNavTab(val title: String, val icon: ImageVector) {
    FILES("Documents • फाइलें", Icons.Default.Description),
    TOOLS("Tools • टूल्स", Icons.Default.Build)
}

sealed class AppScreen {
    object Main : AppScreen()
    data class Viewer(val entity: PdfEntity) : AppScreen()
    data class Editor(val file: File) : AppScreen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdMobManager.initialize(this)

        // Handle external Intent if opened with document
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            uri?.let { viewModel.importPdf(it) }
        }
    }
}

@Composable
fun MainAppContent(viewModel: PdfViewModel) {
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
    var selectedNavTab by remember { mutableStateOf(MainNavTab.FILES) }

    var pendingLockEntity by remember { mutableStateOf<PdfEntity?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    fun checkAndOpenDocument(entity: PdfEntity) {
        if (entity.isLocked && entity.password.isNotBlank()) {
            pendingLockEntity = entity
            pinInput = ""
            pinError = false
        } else {
            viewModel.openPdf(entity)
            currentScreen = AppScreen.Viewer(entity)
        }
    }

    when (val screen = currentScreen) {
        is AppScreen.Viewer -> {
            when (screen.entity.documentType) {
                DocumentType.PDF -> {
                    PdfViewerScreen(
                        entity = screen.entity,
                        viewModel = viewModel,
                        onBack = { currentScreen = AppScreen.Main },
                        onNavigateToEdit = { entity ->
                            currentScreen = AppScreen.Editor(File(entity.path))
                        }
                    )
                }
                DocumentType.WORD -> {
                    WordViewerScreen(
                        entity = screen.entity,
                        onBack = { currentScreen = AppScreen.Main }
                    )
                }
                DocumentType.EXCEL -> {
                    ExcelViewerScreen(
                        entity = screen.entity,
                        onBack = { currentScreen = AppScreen.Main }
                    )
                }
                else -> {
                    PdfViewerScreen(
                        entity = screen.entity,
                        viewModel = viewModel,
                        onBack = { currentScreen = AppScreen.Main },
                        onNavigateToEdit = { entity ->
                            currentScreen = AppScreen.Editor(File(entity.path))
                        }
                    )
                }
            }
        }

        is AppScreen.Editor -> {
            PdfEditorScreen(
                file = screen.file,
                viewModel = viewModel,
                onBack = { currentScreen = AppScreen.Main }
            )
        }

        is AppScreen.Main -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    // Compact Bottom Bar (User requested: "Top or bottom ki size kam kar do" - 54dp height)
                    Surface(
                        color = WhiteMixSurface,
                        shadowElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, WhiteMixBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp) // Compact 54dp height
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MainNavTab.entries.forEach { tab ->
                                val isSelected = tab == selectedNavTab
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedNavTab = tab }
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .testTag("nav_tab_${tab.name.lowercase()}")
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) BrandPrimary else TextSecondary,
                                        modifier = Modifier.size(20.dp) // Compact 20dp icon
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp, // Compact label
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) BrandPrimary else TextSecondary
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
                    when (selectedNavTab) {
                        MainNavTab.FILES -> {
                            FileListScreen(
                                viewModel = viewModel,
                                onOpenDocument = { checkAndOpenDocument(it) }
                            )
                        }
                        MainNavTab.TOOLS -> {
                            PdfToolsScreen(
                                viewModel = viewModel,
                                onNavigateToEditWithFile = { file ->
                                    currentScreen = AppScreen.Editor(file)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Unlock PIN Dialog
    pendingLockEntity?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingLockEntity = null },
            title = { Text("Enter Document PIN", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text("This document is protected with a security PIN.", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 6) pinInput = it
                            pinError = false
                        },
                        singleLine = true,
                        isError = pinError,
                        label = { Text("4-digit PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Incorrect PIN! Please try again.", color = BrandCrimson, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput == entity.password) {
                            val toOpen = pendingLockEntity!!
                            pendingLockEntity = null
                            viewModel.openPdf(toOpen)
                            currentScreen = AppScreen.Viewer(toOpen)
                        } else {
                            pinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLockEntity = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
