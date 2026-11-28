package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ads.StartIoAdManager
import com.example.ads.StartIoBannerAdView
import com.example.data.cache.PdfFastCacheManager
import com.example.data.cloud.GoogleDriveRepository
import com.example.data.cloud.attemptAutoSignIn
import com.example.data.cloud.authStateFlow
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.example.ui.components.AppUpdateDialog
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.ExcelViewerScreen
import com.example.ui.screens.FileListScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PdfToolsScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.RecentFilesScreen
import com.example.ui.screens.WordViewerScreen
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.CloudSyncViewModel
import com.example.ui.viewmodel.PdfViewModel
import com.example.update.AppUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.flowOf
import java.io.File

enum class MainNavTab(val title: String, val icon: ImageVector) {
    FILES("Documents", Icons.Default.Description),
    RECENT("Recent Files", Icons.Default.History),
    CLOUD("Drive", Icons.Default.AddToDrive),
    TOOLS("Tools", Icons.Default.Build)
}

sealed class AppScreen {
    object Main : AppScreen()
    data class Viewer(val entity: PdfEntity) : AppScreen()
    data class Editor(val file: File) : AppScreen()
}

class MainActivity : ComponentActivity() {
    private val viewModel: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            StartIoAdManager.initialize(this)
        } catch (_: Throwable) {
        }
        try {
            AppUpdateManager.bindActivity(this)
        } catch (_: Throwable) {
        }

        // Handle external Intent if opened with document
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            StartIoAdManager.onAppResumed(this)
        } catch (_: Throwable) {
        }
        try {
            AppUpdateManager.bindActivity(this)
            AppUpdateManager.refreshInstallPermissionState(this)
        } catch (_: Throwable) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            uri?.let {
                viewModel.importPdfAndOpen(it) { openedEntity ->
                    viewModel.requestOpenDocument(openedEntity)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: PdfViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val appScope = rememberCoroutineScope()
    val credentialManager = remember(context) {
        try {
            CredentialManager.create(context)
        } catch (_: Throwable) {
            null
        }
    }

    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
    var selectedNavTab by remember { mutableStateOf(MainNavTab.FILES) }
    val pendingOpenDocument by viewModel.pendingOpenDocument.collectAsState()
    val allDocuments by viewModel.allPdfs.collectAsState()
    val updateState by AppUpdateManager.state.collectAsState()
    val isUpdateDialogVisible by AppUpdateManager.dialogVisible.collectAsState()

    val isFirebaseReady = remember(context) {
        try {
            FirebaseApp.initializeApp(context.applicationContext) != null ||
                FirebaseApp.getApps(context.applicationContext).isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    val currentUser: FirebaseUser? by remember(isFirebaseReady) {
        if (!isFirebaseReady) {
            flowOf(null)
        } else {
            try {
                Firebase.auth.authStateFlow()
            } catch (_: Throwable) {
                flowOf(null)
            }
        }
    }.collectAsState(
        initial = if (isFirebaseReady) {
            try {
                Firebase.auth.currentUser
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    )

    LaunchedEffect(Unit) {
        try {
            PdfFastCacheManager.refreshStats(context.applicationContext)
        } catch (_: Throwable) {
        }
        if (isFirebaseReady && credentialManager != null) {
            try {
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = {},
                    onUnauthenticated = {},
                    scope = appScope
                )
            } catch (_: Throwable) {
            }
        }
        if (isFirebaseReady) {
            try {
                AppUpdateManager.startCheckForUpdates(activity ?: context, isManual = false)
            } catch (_: Throwable) {
            }
        }
    }

    val cloudViewModel: CloudSyncViewModel? = currentUser?.let { user ->
        key(user.uid) {
            val driveRepository = remember(user.uid) {
                GoogleDriveRepository(context.applicationContext)
            }
            val factory = remember(user.uid, driveRepository) {
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return CloudSyncViewModel(
                            driveRepository = driveRepository,
                            context = context.applicationContext,
                            currentUserId = user.uid
                        ) as T
                    }
                }
            }
            val vm: CloudSyncViewModel = viewModel(key = "drive_${user.uid}", factory = factory)

            LaunchedEffect(allDocuments, user.uid) {
                vm.autoSyncLocalPdfs(allDocuments)
            }
            LaunchedEffect(user.uid) {
                viewModel.deletedLocalDocsFlow.collect { deletedDocs ->
                    vm.handleLocalDeletions(deletedDocs)
                }
            }
            LaunchedEffect(user.uid) {
                vm.remoteDeletedLocalPaths.collect { path ->
                    viewModel.deleteLocalCopyFromCloudSync(path)
                }
            }
            LaunchedEffect(lifecycleOwner, user.uid) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    vm.refreshDriveInBackground()
                }
            }
            vm
        }
    }

    BackHandler(enabled = currentScreen is AppScreen.Main && selectedNavTab != MainNavTab.FILES) {
        selectedNavTab = MainNavTab.FILES
    }

    var pendingLockEntity by remember { mutableStateOf<PdfEntity?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    fun openWithInterstitialCheck(entity: PdfEntity) {
        StartIoAdManager.showOpenDocumentInterstitial(activity) {
            viewModel.openPdf(entity)
            currentScreen = AppScreen.Viewer(entity)
        }
    }

    fun checkAndOpenDocument(entity: PdfEntity) {
        if (entity.isLocked && entity.password.isNotBlank()) {
            pendingLockEntity = entity
            pinInput = ""
            pinError = false
        } else {
            openWithInterstitialCheck(entity)
        }
    }

    LaunchedEffect(pendingOpenDocument) {
        pendingOpenDocument?.let { entity ->
            viewModel.clearPendingOpenDocument()
            checkAndOpenDocument(entity)
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
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StartIoBannerAdView(modifier = Modifier.fillMaxWidth().widthIn(max = 728.dp))

                        MainBottomNavigationBar(
                            selectedNavTab = selectedNavTab,
                            onSelectTab = { selectedNavTab = it }
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 860.dp)
                    ) {
                        when (selectedNavTab) {
                            MainNavTab.FILES -> {
                                FileListScreen(
                                    viewModel = viewModel,
                                    onOpenDocument = { checkAndOpenDocument(it) }
                                )
                            }
                            MainNavTab.RECENT -> {
                                RecentFilesScreen(
                                    viewModel = viewModel,
                                    onOpenDocument = { checkAndOpenDocument(it) },
                                    onNavigateToDocuments = { selectedNavTab = MainNavTab.FILES }
                                )
                            }
                            MainNavTab.CLOUD -> {
                                CloudSyncScreen(
                                    currentUser = currentUser,
                                    cloudViewModel = cloudViewModel,
                                    allDocuments = allDocuments,
                                    onOpenDocument = { checkAndOpenDocument(it) },
                                    onDeleteLocalPath = { viewModel.deleteLocalCopyFromCloudSync(it) }
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
                            openWithInterstitialCheck(toOpen)
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

    // In-App Update Prompt & APK Installer Dialog
    AppUpdateDialog(
        state = updateState,
        visible = isUpdateDialogVisible,
        onStartDownload = { release ->
            AppUpdateManager.startDownloadAndInstall(activity ?: context, release)
        },
        onInstallApk = { readyState ->
            AppUpdateManager.launchApkInstaller(activity ?: context, readyState.apkFile)
        },
        onOpenInstallPermission = {
            AppUpdateManager.openUnknownSourcesSettings(activity ?: context)
        },
        onOpenInBrowser = { url ->
            AppUpdateManager.openDownloadInBrowser(activity ?: context, url)
        },
        onDismiss = {
            AppUpdateManager.dismissDialog()
        },
        onOpenPlayStore = {
            AppUpdateManager.openPlayStoreForUpdate(activity ?: context)
        },
        onRetryCheck = {
            AppUpdateManager.startCheckForUpdates(activity ?: context, isManual = true)
        }
    )
}

@Composable
fun MainBottomNavigationBar(
    selectedNavTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = WhiteMixSurface,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, WhiteMixBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("main_bottom_navigation_bar")
    ) {
 
