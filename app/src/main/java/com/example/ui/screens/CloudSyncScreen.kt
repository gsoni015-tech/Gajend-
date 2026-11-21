package com.example.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.cache.CacheStats
import com.example.data.cloud.CloudDocument
import com.example.data.cloud.CloudUiState
import com.example.data.cloud.onGoogleSignInClicked
import com.example.data.cloud.signOutGoogleAccount
import com.example.data.local.PdfEntity
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandOnPrimaryContainer
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.CloudSyncViewModel
import com.google.firebase.auth.FirebaseUser

@Composable
fun CloudSyncScreen(
    currentUser: FirebaseUser?,
    cloudViewModel: CloudSyncViewModel?,
    allDocuments: List<PdfEntity>,
    onOpenDocument: (PdfEntity) -> Unit,
    onDeleteLocalPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    if (currentUser == null || cloudViewModel == null) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("cloud_sync_screen"),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = WhiteMixBackground,
            topBar = {
                CloudTopBar(
                    docCount = 0,
                    isSignedIn = false,
                    isSyncing = false,
                    onSyncNow = {},
                    onSignOut = {}
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val viewportMinHeight = maxHeight
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .testTag("cloud_sign_in_scroll_container"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = viewportMinHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        CloudSignInSection(
                            isSigningIn = isSigningIn,
                            authErrorMessage = authErrorMessage,
                            onSignInClick = {
                                isSigningIn = true
                                authErrorMessage = null
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isSigningIn = false
                                        authErrorMessage = null
                                    },
                                    onAuthError = { msg ->
                                        isSigningIn = false
                                        authErrorMessage = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        isSigningIn = false
                                    }
                                )
                            },
                            modifier = Modifier.widthIn(max = 560.dp)
                        )
                    }
                }
            }
        }
        return
    }

    val cloudDocsState by cloudViewModel.cloudDocsState.collectAsState()
    val isSyncing by cloudViewModel.isSyncing.collectAsState()
    val activeSyncTitle by cloudViewModel.activeSyncTitle.collectAsState()
    val storageSetupNeeded by cloudViewModel.storageSetupNeeded.collectAsState()
    val pendingConsentIntent by cloudViewModel.pendingConsentIntent.collectAsState()
    val cacheStats by cloudViewModel.cacheStats.collectAsState()
    val openingDocId by cloudViewModel.openingDocId.collectAsState()
    val actionMessage by cloudViewModel.actionMessage.collectAsState()

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
            cloudViewModel.onDriveConsentResult(activityResult.data, allDocuments)
        } else {
            cloudViewModel.onDriveConsentDismissed()
        }
    }

    LaunchedEffect(pendingConsentIntent) {
        pendingConsentIntent?.let { intent ->
            try {
                driveConsentLauncher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
            } catch (_: Exception) {
                cloudViewModel.onDriveConsentDismissed()
            }
        }
    }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            cloudViewModel.clearActionMessage()
        }
    }

    CloudSyncAuthenticatedContent(
        userEmail = currentUser.email ?: currentUser.displayName ?: "Google Account",
        userName = currentUser.displayName ?: "Connected Google Drive",
        cloudDocsState = cloudDocsState,
        isSyncing = isSyncing,
        activeSyncTitle = activeSyncTitle,
        storageSetupNeeded = storageSetupNeeded,
        cacheStats = cacheStats,
        openingDocId = openingDocId,
        onSyncNow = { cloudViewModel.syncAllNow(allDocuments) },
        onGrantDriveAccess = { cloudViewModel.requestDrivePermission(allDocuments) },
        onSignOut = {
            signOutGoogleAccount(
                credentialManager = credentialManager,
                scope = scope
            )
        },
        onClearCache = { cloudViewModel.clearMobileCache() },
        onOpenCloudDoc = { doc ->
            cloudViewModel.openCloudDocument(doc, onOpenEntity = onOpenDocument)
        },
        onDeleteCloudDoc = { doc, alsoDeleteLocal ->
            cloudViewModel.deleteFromCloudAccount(
                doc = doc,
                alsoDeleteLocal = alsoDeleteLocal,
                onDeleteLocalPath = onDeleteLocalPath
            )
        },
        modifier = modifier
    )
}

@Composable
fun CloudSyncAuthenticatedContent(
    userEmail: String,
    userName: String,
    cloudDocsState: CloudUiState<List<CloudDocument>>,
    isSyncing: Boolean,
    activeSyncTitle: String?,
    storageSetupNeeded: Boolean,
    cacheStats: CacheStats,
    openingDocId: String?,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onClearCache: () -> Unit,
    onOpenCloudDoc: (CloudDocument) -> Unit,
    onDeleteCloudDoc: (CloudDocument, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onGrantDriveAccess: () -> Unit = onSyncNow
) {
    var pendingDeleteDoc by remember { mutableStateOf<CloudDocument?>(null) }
    val docsCount = (cloudDocsState as? CloudUiState.Success)?.data?.size ?: 0

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("cloud_sync_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = WhiteMixBackground,
        topBar = {
            CloudTopBar(
                docCount = docsCount,
                isSignedIn = true,
                isSyncing = isSyncing,
                onSyncNow = onSyncNow,
                onSignOut = onSignOut
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WhiteMixSurface,
                    border = BorderStroke(1.dp, WhiteMixBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 860.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFD1FAE5),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Google Drive Active",
                                        tint = BrandEmerald,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = userEmail,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (storageSetupNeeded) Color(0xFFFEF3C7) else Color(0xFFD1FAE5)
                            ) {
                                Text(
                                    text = when {
                                        storageSetupNeeded -> "Permission Needed"
                                        isSyncing -> "Syncing"
                                        else -> "Drive Sync On"
                                    },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (storageSetupNeeded) Color(0xFF92400E) else BrandEmerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (isSyncing) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                color = BrandPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            if (!activeSyncTitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Uploading $activeSyncTitle to Google Drive…",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (storageSetupNeeded) {
                item {
                    CloudStorageSetupBanner(
                        onGrantAccessClick = onGrantDriveAccess,
                        modifier = Modifier.widthIn(max = 860.dp)
                    )
                }
            }

            item {
                CloudFastCacheCard(
                    cacheStats = cacheStats,
                    onClearCache = onClearCache,
                    modifier = Modifier.widthIn(max = 860.dp)
                )
            }

            when (cloudDocsState) {
                is CloudUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BrandPrimary)
                        }
                    }
                }

                is CloudUiState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = WhiteMixSurface,
                            border = BorderStroke(1.dp, WhiteMixBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 860.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Google Drive Sync Issue",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = BrandCrimson
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = cloudDocsState.message,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onSyncNow,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Retry Sync")
                                }
                            }
                        }
                    }
                }

                is CloudUiState.Success -> {
                    val docs = cloudDocsState.data
                    if (docs.isEmpty() && !storageSetupNeeded) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = WhiteMixSurface,
                                border = BorderStroke(1.dp, WhiteMixBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 860.dp)
                                    .testTag("cloud_empty_state")
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddToDrive,
                                        contentDescription = null,
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.cloud_empty_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.cloud_empty_subtitle),
                                        fontSize = 12.5.sp,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = onSyncNow,
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("cloud_sync_now_empty_button")
                                    ) {
                                        Text("Upload Local PDFs Now")
                                    }
                                }
                            }
                        }
                    } else if (docs.isNotEmpty()) {
                        items(docs, key = { it.id }) { doc ->
                            CloudDocumentCard(
                                doc = doc,
                                isOpening = openingDocId == doc.id,
                                onOpenClick = { onOpenCloudDoc(doc) },
                                onDeleteClick = { pendingDeleteDoc = doc },
                                modifier = Modifier.widthIn(max = 860.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeleteDoc?.let { doc ->
        CloudDeleteConfirmDialog(
            doc = doc,
            onDeleteCloudAndCache = {
                pendingDeleteDoc = null
                onDeleteCloudDoc(doc, false)
            },
            onDeleteEverywhere = {
                pendingDeleteDoc = null
                onDeleteCloudDoc(doc, true)
            },
            onDismiss = { pendingDeleteDoc = null }
        )
    }
}

@Composable
private fun CloudTopBar(
    docCount: Int,
    isSignedIn: Boolean,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(color = WhiteMixSurface, shadowElevation = 1.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .displayCutoutPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp)
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.narayani_pdf_logo_1793504887925),
                        contentDescription = "Narayani Logo",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.cloud_sync_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    if (docCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandPrimaryContainer
                        ) {
                            Text(
                                text = "$docCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandOnPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (isSignedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onSyncNow,
                            enabled = !isSyncing,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("cloud_sync_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync PDFs to Google Drive",
                                tint = BrandPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("cloud_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = BrandCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
