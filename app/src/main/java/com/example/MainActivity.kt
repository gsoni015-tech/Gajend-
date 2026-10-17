package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ads.AdMobManager
import com.example.ui.components.AdMobInterstitialDialog
import com.example.ui.components.AdMobRewardedDialog
import com.example.ui.screens.FileListScreen
import com.example.data.local.DocumentType
import com.example.ui.screens.ExcelViewerScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PdfToolsScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.WordViewerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.PdfViewModel

enum class MainScreenNavigation {
    FILES,
    TOOLS,
    VIEWER,
    EXCEL_VIEWER,
    WORD_VIEWER,
    PDF_EDITOR
}

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming open PDF intent if launched via ACTION_VIEW
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
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri: Uri = intent.data!!
            viewModel.importPdf(uri)
        }
    }
}

@Composable
fun MainAppContent(viewModel: PdfViewModel) {
    var currentScreen by remember { mutableStateOf(MainScreenNavigation.FILES) }
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    // Ads states
    val showInterstitial by AdMobManager.showInterstitial.collectAsStateWithLifecycle()
    val showRewarded by AdMobManager.showRewarded.collectAsStateWithLifecycle()

    // Display snackbars
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentScreen == MainScreenNavigation.FILES || currentScreen == MainScreenNavigation.TOOLS) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentScreen == MainScreenNavigation.FILES,
                        onClick = { currentScreen = MainScreenNavigation.FILES },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == MainScreenNavigation.FILES)
                                    Icons.Filled.Description else Icons.Outlined.Description,
                                contentDescription = "Files"
                            )
                        },
                        label = { Text("Documents") },
                        modifier = Modifier.testTag("nav_item_files")
                    )
                    NavigationBarItem(
                        selected = currentScreen == MainScreenNavigation.TOOLS,
                        onClick = { currentScreen = MainScreenNavigation.TOOLS },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == MainScreenNavigation.TOOLS)
                                    Icons.Filled.Build else Icons.Outlined.Build,
                                contentDescription = "Tools"
                            )
                        },
                        label = { Text("PDF Tools") },
                        modifier = Modifier.testTag("nav_item_tools")
                    )
                }
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = currentScreen,
            modifier = Modifier.padding(innerPadding),
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                MainScreenNavigation.FILES -> {
                    FileListScreen(
                        viewModel = viewModel,
                        onNavigateToDocument = { type ->
                            when (type) {
                                DocumentType.EXCEL -> currentScreen = MainScreenNavigation.EXCEL_VIEWER
                                DocumentType.WORD, DocumentType.MAIN_FILE -> currentScreen = MainScreenNavigation.WORD_VIEWER
                                DocumentType.PDF -> currentScreen = MainScreenNavigation.VIEWER
                            }
                        },
                        onNavigateToEditPdf = { currentScreen = MainScreenNavigation.PDF_EDITOR }
                    )
                }
                MainScreenNavigation.TOOLS -> {
                    PdfToolsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = MainScreenNavigation.FILES },
                        onNavigateToEditPdf = { currentScreen = MainScreenNavigation.PDF_EDITOR }
                    )
                }
                MainScreenNavigation.VIEWER -> {
                    PdfViewerScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = MainScreenNavigation.FILES },
                        onNavigateToEditPdf = { currentScreen = MainScreenNavigation.PDF_EDITOR }
                    )
                }
                MainScreenNavigation.EXCEL_VIEWER -> {
                    ExcelViewerScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = MainScreenNavigation.FILES }
                    )
                }
                MainScreenNavigation.WORD_VIEWER -> {
                    WordViewerScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = MainScreenNavigation.FILES }
                    )
                }
                MainScreenNavigation.PDF_EDITOR -> {
                    PdfEditorScreen(
                        viewModel = viewModel,
                        onNavigateBack = { currentScreen = MainScreenNavigation.FILES }
                    )
                }
            }
        }
    }

    // Interstitial Ad Dialog Overlay
    if (showInterstitial) {
        AdMobInterstitialDialog(
            onDismiss = { AdMobManager.dismissInterstitialAd() }
        )
    }

    // Rewarded Video Ad Dialog Overlay
    if (showRewarded) {
        AdMobRewardedDialog(
            tool = null, // Handled internally by manager
            onRewardGranted = { AdMobManager.grantRewardAndDismiss() },
            onDismiss = { AdMobManager.dismissRewardedAdWithoutReward() }
        )
    }
}
