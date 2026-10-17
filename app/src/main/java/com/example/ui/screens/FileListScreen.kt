package com.example.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfEntity
import com.example.ui.components.AdMobBottomBanner
import com.example.ui.components.AdMobNativeInlineCard
import com.example.ui.viewmodel.FileListItem
import com.example.ui.viewmodel.FileListTab
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.SortMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.data.local.DocumentType
import com.example.data.local.documentType
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.TextFields

@Composable
fun FileListScreen(
    viewModel: PdfViewModel,
    onNavigateToDocument: (DocumentType) -> Unit,
    onNavigateToEditPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayedItems by viewModel.displayedItems.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    val foldersMap by viewModel.foldersWithCount.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var entityToRename by remember { mutableStateOf<PdfEntity?>(null) }
    var entityToDelete by remember { mutableStateOf<PdfEntity?>(null) }
    var entityToLock by remember { mutableStateOf<PdfEntity?>(null) }
    var entityLockedToUnlock by remember { mutableStateOf<PdfEntity?>(null) }
    var entityForDetails by remember { mutableStateOf<PdfEntity?>(null) }

    // System PDF Picker
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importPdf(it) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Search & Actions
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Narayani PDF Reader",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Sort Button
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort PDFs",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Date (Newest First)") },
                                        onClick = {
                                            viewModel.setSortMode(SortMode.DATE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Name (A to Z)") },
                                        onClick = {
                                            viewModel.setSortMode(SortMode.NAME_ASC)
                                            showSortMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("File Size (Largest First)") },
                                        onClick = {
                                            viewModel.setSortMode(SortMode.SIZE_DESC)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }

                            // Open Document Picker
                            IconButton(onClick = {
                                openDocLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/plain"
                                    )
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Import Document",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pdf_search_input"),
                        placeholder = { Text("Search PDF, Word, Excel, and Text files...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp)
                    )
                }
            }

            // Tabs Row (All, PDF, Word, Excel, Text, Recent, Favorites, Folders)
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                FileListTab.entries.forEach { tab ->
                    val tabTitle = when (tab) {
                        FileListTab.ALL -> "All Files"
                        FileListTab.PDF -> "PDFs"
                        FileListTab.WORD -> "Word"
                        FileListTab.EXCEL -> "Excel"
                        FileListTab.MAIN_TEXT -> "Text"
                        FileListTab.RECENT -> "Recent"
                        FileListTab.FAVORITES -> "Favorites"
                        FileListTab.FOLDERS -> "Folders"
                    }
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = tabTitle,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Folder Filter Chips if FOLDERS tab selected
            if (selectedTab == FileListTab.FOLDERS && foldersMap.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFolder == null,
                            onClick = { viewModel.setFolder(null) },
                            label = { Text("All Folders") }
                        )
                    }
                    items(foldersMap.keys.toList()) { folderName ->
                        val count = foldersMap[folderName] ?: 0
                        FilterChip(
                            selected = selectedFolder == folderName,
                            onClick = { viewModel.setFolder(folderName) },
                            label = { Text("$folderName ($count)") }
                        )
                    }
                }
            }

            // PDF List with Native Ads inserted
            if (displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching PDFs found" else "No PDF files here yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button below or import a PDF from storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("pdf_file_list"),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 72.dp)
                ) {
                    items(displayedItems, key = { item ->
                        when (item) {
                            is FileListItem.PdfItem -> "pdf_${item.entity.path}"
                            is FileListItem.AdItem -> "ad_${item.nativeAd.adId}"
                        }
                    }) { item ->
                        when (item) {
                            is FileListItem.PdfItem -> {
                                PdfFileRowItem(
                                    entity = item.entity,
                                    onItemClick = {
                                        if (item.entity.isLocked) {
                                            entityLockedToUnlock = item.entity
                                        } else {
                                            viewModel.openPdf(item.entity) {
                                                onNavigateToDocument(item.entity.documentType)
                                            }
                                        }
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(item.entity) },
                                    onRename = { entityToRename = item.entity },
                                    onDelete = { entityToDelete = item.entity },
                                    onShare = { viewModel.sharePdf(context, item.entity) },
                                    onLock = { entityToLock = item.entity },
                                    onDetails = { entityForDetails = item.entity },
                                    onEditPdf = {
                                        viewModel.openPdfForEdit(item.entity) {
                                            onNavigateToEditPdf()
                                        }
                                    }
                                )
                            }
                            is FileListItem.AdItem -> {
                                AdMobNativeInlineCard(adItem = item.nativeAd)
                            }
                        }
                    }
                }
            }

            // Bottom Banner Ad
            AdMobBottomBanner(modifier = Modifier.fillMaxWidth())
        }

        // Floating Action Button to pick file
        FloatingActionButton(
            onClick = {
                openDocLauncher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "text/csv",
                        "text/plain"
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 65.dp)
                .testTag("fab_add_pdf")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Document")
        }
    }

    // Rename Dialog
    entityToRename?.let { pdf ->
        var newName by remember { mutableStateOf(pdf.title) }
        AlertDialog(
            onDismissRequest = { entityToRename = null },
            title = { Text("Rename PDF") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renamePdf(pdf, newName.trim())
                        }
                        entityToRename = null
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { entityToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    entityToDelete?.let { pdf ->
        AlertDialog(
            onDismissRequest = { entityToDelete = null },
            title = { Text("Delete Document") },
            text = { Text("Are you sure you want to delete '${pdf.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePdf(pdf)
                        entityToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entityToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Set PIN Lock Dialog
    entityToLock?.let { pdf ->
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { entityToLock = null },
            title = { Text(if (pdf.isLocked) "Unlock / Remove PIN" else "Lock PDF with PIN") },
            text = {
                Column {
                    if (pdf.isLocked) {
                        Text("This document is currently locked. Would you like to remove the password protection?")
                    } else {
                        Text("Enter a 4-digit security PIN to protect this document:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6) pin = it },
                            label = { Text("Security PIN") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pdf.isLocked) {
                            viewModel.unlockPdf(pdf)
                        } else if (pin.isNotBlank()) {
                            viewModel.setLockPin(pdf, pin)
                        }
                        entityToLock = null
                    }
                ) {
                    Text(if (pdf.isLocked) "Remove Lock" else "Set Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { entityToLock = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unlock Password Check Dialog
    entityLockedToUnlock?.let { pdf ->
        var enteredPin by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { entityLockedToUnlock = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Password Protected")
                }
            },
            text = {
                Column {
                    Text("Enter the security PIN to unlock '${pdf.title}':")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            enteredPin = it
                            isError = false
                        },
                        label = { Text("PIN") },
                        isError = isError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isError) {
                        Text(
                            "Incorrect PIN. Please try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredPin == pdf.password) {
                            entityLockedToUnlock = null
                            viewModel.openPdf(pdf) {
                                onNavigateToDocument(pdf.documentType)
                            }
                        } else {
                            isError = true
                        }
                    }
                ) {
                    Text("Unlock & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { entityLockedToUnlock = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Details Dialog
    entityForDetails?.let { pdf ->
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { entityForDetails = null },
            title = { Text("Document Info") },
            text = {
                Column {
                    Text("Title: ${pdf.title}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Format: ${pdf.documentType.label} (${pdf.documentType.badge})")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pages / Size: ${pdf.pageCount} pages • ${Formatter.formatFileSize(context, pdf.sizeBytes)}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Folder: ${pdf.folderName}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Modified: ${dateFormat.format(Date(pdf.lastModifiedTimestamp))}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Location: ${pdf.path}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { entityForDetails = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun PdfFileRowItem(
    entity: PdfEntity,
    onItemClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onLock: () -> Unit,
    onDetails: () -> Unit,
    onEditPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val formattedSize = remember(entity.sizeBytes) {
        Formatter.formatFileSize(context, entity.sizeBytes)
    }
    val formattedDate = remember(entity.lastModifiedTimestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(entity.lastModifiedTimestamp))
    }

    val docType = entity.documentType
    val (typeBg, typeTint, typeIcon, typeTag) = when (docType) {
        DocumentType.PDF -> listOf(Color(0xFFFEF2F2), Color(0xFFDC2626), Icons.Default.PictureAsPdf, "PDF")
        DocumentType.EXCEL -> listOf(Color(0xFFECFDF5), Color(0xFF059669), Icons.Default.TableChart, "XLS")
        DocumentType.WORD -> listOf(Color(0xFFEFF6FF), Color(0xFF2563EB), Icons.Default.Description, "DOC")
        DocumentType.MAIN_FILE -> listOf(Color(0xFFF5F3FF), Color(0xFF7C3AED), Icons.Default.TextFields, "TXT")
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onItemClick)
            .testTag("pdf_row_${entity.path.hashCode()}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Type Icon with colored border
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (entity.isLocked) Color(0xFFFEF3C7)
                        else (typeBg as Color)
                    )
                    .border(1.dp, (typeTint as Color).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entity.isLocked) Icons.Default.Lock else (typeIcon as androidx.compose.ui.graphics.vector.ImageVector),
                    contentDescription = null,
                    tint = if (entity.isLocked) Color(0xFFD97706) else (typeTint as Color),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Metadata
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entity.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Format badge
                    Box(
                        modifier = Modifier
                            .background((typeBg as Color), RoundedCornerShape(4.dp))
                            .border(0.8.dp, (typeTint as Color).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = typeTag as String,
                            fontSize = 9.sp,
                            color = typeTint,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (entity.isLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp))
                                .border(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("Locked", fontSize = 9.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val subtitlePrefix = when (docType) {
                    DocumentType.PDF -> "${entity.pageCount} pages"
                    DocumentType.EXCEL -> "Excel Sheet"
                    DocumentType.WORD -> "Word Doc"
                    DocumentType.MAIN_FILE -> "Text File"
                }

                Text(
                    text = "$subtitlePrefix • $formattedSize • $formattedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (entity.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (entity.isFavorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // 3-dots popup menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (entity.documentType == DocumentType.PDF) {
                        DropdownMenuItem(
                            text = { Text("Edit PDF") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                onEditPdf()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (entity.isLocked) "Remove Password" else "Lock with PIN") },
                        leadingIcon = {
                            Icon(if (entity.isLocked) Icons.Default.LockOpen else Icons.Default.Lock, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onLock()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
