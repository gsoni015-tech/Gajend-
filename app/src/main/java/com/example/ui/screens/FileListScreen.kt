package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import com.example.ui.components.DocumentThumbnailView
import com.example.ui.components.DocumentQuickPreviewBottomSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.util.PermissionUtils
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.FileListTab
import com.example.ui.viewmodel.PdfViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: PdfViewModel,
    onOpenDocument: (PdfEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayedItems by viewModel.displayedItems.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val isScanning by viewModel.isScanning.collectAsState()
    var hasPermission by remember { mutableStateOf(PermissionUtils.hasStoragePermission(context)) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isNowGranted = PermissionUtils.hasStoragePermission(context)
                if (isNowGranted != hasPermission) {
                    hasPermission = isNowGranted
                }
                if (isNowGranted) {
                    viewModel.refreshDocuments()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val anyGranted = permissionsMap.values.any { it }
        val isGranted = anyGranted || PermissionUtils.hasStoragePermission(context)
        hasPermission = isGranted
        if (isGranted) {
            viewModel.refreshDocuments()
        }
    }

    LaunchedEffect(Unit) {
        val granted = PermissionUtils.hasStoragePermission(context)
        hasPermission = granted
        if (granted) {
            viewModel.refreshDocuments()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                showPermissionRationaleDialog = true
            } else {
                permissionLauncher.launch(PermissionUtils.getStandardPermissions())
            }
        }
    }

    var showRenameDialogFor by remember { mutableStateOf<PdfEntity?>(null) }
    var showPinDialogFor by remember { mutableStateOf<PdfEntity?>(null) }
    var showDeleteConfirmFor by remember { mutableStateOf<PdfEntity?>(null) }
    var previewEntityForSheet by remember { mutableStateOf<PdfEntity?>(null) }
    var isGridView by remember { mutableStateOf(false) }

    // Folder picker: loads file and directly opens it without bottom "imported" toast
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importPdfAndOpen(it) { openedEntity ->
                onOpenDocument(openedEntity)
            }
        }
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
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Narayani PDF",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary
                                )
                            )
                        }

                        // Right actions (Auto-fetch refresh & Open Document from folder)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.refreshDocuments() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("refresh_documents_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Scan Storage",
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { isGridView = !isGridView },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("toggle_grid_list_view_button")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    filePicker.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "text/csv"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("open_folder_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Open File",
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Compact Search Box
                    CompactSearchBox(
                        query = searchQuery,
                        onQueryChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // English Category Tabs
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(FileListTab.entries.toTypedArray()) { tab ->
                            val isSelected = tab == selectedTab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) BrandPrimary else WhiteMixSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) BrandPrimary else WhiteMixBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { viewModel.selectedTab.value = tab }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                // Permission Banner (if not granted)
                if (!hasPermission) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF2F2),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = BrandCrimson,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Storage Permission Needed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BrandCrimson
                            )
                            Text(
                                text = "Allow storage permission to automatically scan all documents from memory.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    PermissionUtils.openStorageAccessSettings(context)
                                } else {
                                    permissionLauncher.launch(PermissionUtils.getStandardPermissions())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Scanning indicator
            if (isScanning) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = BrandPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scanning device memory for documents...",
                            fontSize = 12.sp,
                            color = BrandPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (displayedItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(color = BrandPrimary)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Scanning device memory for documents...",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (!hasPermission) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = BrandCrimson,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Permission Required to Auto-Scan",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Please allow storage permission to automatically scan and load files from device memory.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            PermissionUtils.openStorageAccessSettings(context)
                                        } else {
                                            permissionLauncher.launch(PermissionUtils.getStandardPermissions())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Grant Permission & Auto Scan", fontSize = 13.sp)
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No files match \"$searchQuery\"" else "No documents found on device",
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.refreshDocuments() },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Scan Storage", fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = {
                                            filePicker.launch(
                                                arrayOf(
                                                    "application/pdf",
                                                    "application/msword",
                                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                    "application/vnd.ms-excel",
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                    "text/csv"
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Browse Folder", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(displayedItems, key = { it.path }) { entity ->
                                DocumentGridItemCard(
                                    entity = entity,
                                    onClick = { onOpenDocument(entity) },
                                    onQuickPreview = { previewEntityForSheet = entity },
                                    onToggleFavorite = { viewModel.toggleFavorite(entity) },
                                    onRename = { showRenameDialogFor = entity },
                                    onDelete = { showDeleteConfirmFor = entity },
                                    onSetPin = { showPinDialogFor = entity },
                                    onShare = { shareDocument(context, entity) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedItems, key = { it.path }) { entity ->
                                DocumentItemCard(
                                    entity = entity,
                                    onClick = { onOpenDocument(entity) },
                                    onQuickPreview = { previewEntityForSheet = entity },
                                    onToggleFavorite = { viewModel.toggleFavorite(entity) },
                                    onRename = { showRenameDialogFor = entity },
                                    onDelete = { showDeleteConfirmFor = entity },
                                    onSetPin = { showPinDialogFor = entity },
                                    onShare = { shareDocument(context, entity) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }

    // Permission Rationale Dialog for Android 11+
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = {
                Text(
                    text = "Storage Permission Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Narayani PDF Reader requires storage access to automatically scan and load all PDF, Word, and Excel documents saved on your device.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationaleDialog = false
                        PermissionUtils.openStorageAccessSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationaleDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    // Dialogs
    showRenameDialogFor?.let { entity ->
        var newName by remember { mutableStateOf(entity.title.substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            title = { Text("Rename Document", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Document Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renamePdf(entity, newName)
                            showRenameDialogFor = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showDeleteConfirmFor?.let { entity ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmFor = null },
            title = { Text("Delete Document?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${entity.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePdf(entity)
                        showDeleteConfirmFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showPinDialogFor?.let { entity ->
        var pin by remember { mutableStateOf(entity.password) }
        AlertDialog(
            onDismissRequest = { showPinDialogFor = null },
            title = { Text(if (entity.isLocked) "Change or Remove PIN" else "Set Security PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Set 4-digit PIN to protect this document. Leave blank to unlock.", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6) pin = it },
                        singleLine = true,
                        label = { Text("PIN Code") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setLockPin(entity, pin.trim())
                        showPinDialogFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialogFor = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Document Quick Preview Sheet before opening
    previewEntityForSheet?.let { entity ->
        DocumentQuickPreviewBottomSheet(
            entity = entity,
            onDismissRequest = { previewEntityForSheet = null },
            onOpenDocument = { doc ->
                previewEntityForSheet = null
                onOpenDocument(doc)
            },
            onToggleFavorite = { doc -> viewModel.toggleFavorite(doc) },
            onShareDocument = { doc -> shareDocument(context, doc) }
        )
    }
}

@Composable
fun CompactSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier
            .height(38.dp)
            .testTag("compact_search_box"),
        shape = RoundedCornerShape(20.dp),
        color = WhiteMixBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhiteMixBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    color = TextPrimary
                ),
                cursorBrush = SolidColor(BrandPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search documents by name...",
                            fontSize = 13.sp,
                            color = TextTertiary
                        )
                    }
                    innerTextField()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentItemCard(
    entity: PdfEntity,
    onClick: () -> Unit,
    onQuickPreview: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetPin: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("document_card_${entity.title}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = WhiteMixCard,
        shadowElevation = 0.5.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhiteMixBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // First-Page Real Rendered Thumbnail Preview
            DocumentThumbnailView(
                entity = entity,
                width = 46.dp,
                height = 58.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onQuickPreview)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.title,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entity.isLocked) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Formatter.formatFileSize(LocalContext.current, entity.sizeBytes),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = " • ",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                    Text(
                        text = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(entity.lastModifiedTimestamp)),
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = " • ${entity.folderName}",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Preview Eye Button
            IconButton(
                onClick = onQuickPreview,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("preview_btn_${entity.title}")
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Quick Preview",
                    tint = BrandPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Favorite Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (entity.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (entity.isFavorite) BrandCrimson else TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // More Menu
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Quick Preview") },
                        onClick = {
                            menuExpanded = false
                            onQuickPreview()
                        },
                        leadingIcon = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) }
                    )
                    if (entity.documentType == DocumentType.PDF) {
                        DropdownMenuItem(
                            text = { Text("Print") },
                            onClick = {
                                menuExpanded = false
                                com.example.pdf.PdfPrintHelper.printPdf(context, File(entity.path), entity.uriString, entity.title)
                            },
                            leadingIcon = { Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (entity.isLocked) "Change PIN" else "Lock with PIN") },
                        onClick = {
                            menuExpanded = false
                            onSetPin()
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = BrandCrimson) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = BrandCrimson, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

/**
 * 2-Column Grid Item Card with prominent Cover Page Thumbnail Preview
 */
@Composable
fun DocumentGridItemCard(
    entity: PdfEntity,
    onClick: () -> Unit,
    onQuickPreview: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetPin: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("document_grid_card_${entity.title}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = WhiteMixCard,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, WhiteMixBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Large Thumbnail Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3F4F6))
                    .clickable(onClick = onQuickPreview),
                contentAlignment = Alignment.Center
            ) {
                DocumentThumbnailView(
                    entity = entity,
                    width = 130.dp,
                    height = 145.dp,
                    modifier = Modifier.fillMaxSize()
                )

                // Quick Preview Pill on overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Quick Preview",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Preview",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Document Title
            Text(
                text = entity.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Meta & Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Formatter.formatFileSize(context, entity.sizeBytes),
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (entity.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (entity.isFavorite) BrandCrimson else TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Quick Preview") },
                                onClick = {
                                    menuExpanded = false
                                    onQuickPreview()
                                },
                                leadingIcon = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (entity.isLocked) "Change PIN" else "Lock with PIN") },
                                onClick = {
                                    menuExpanded = false
                                    onSetPin()
                                },
                                leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = BrandCrimson) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = BrandCrimson, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shareDocument(context: Context, entity: PdfEntity) {
    try {
        val file = File(entity.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = when (entity.documentType) {
                DocumentType.PDF -> "application/pdf"
                DocumentType.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                DocumentType.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
