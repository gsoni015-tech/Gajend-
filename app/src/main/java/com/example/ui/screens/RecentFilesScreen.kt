package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.PdfEntity
import com.example.ui.components.DocumentQuickPreviewBottomSheet
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandOnPrimaryContainer
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.RecentFilterMode

@Composable
fun RecentFilesScreen(
    viewModel: PdfViewModel,
    onOpenDocument: (PdfEntity) -> Unit,
    onNavigateToDocuments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentFiles by viewModel.recentFiles.collectAsState()
    val allRecentFiles by viewModel.allRecentFiles.collectAsState()
    val searchQuery by viewModel.recentSearchQuery.collectAsState()
    val filterMode by viewModel.recentFilterMode.collectAsState()

    RecentFilesScreenContent(
        recentFiles = recentFiles,
        totalRecentCount = allRecentFiles.size,
        searchQuery = searchQuery,
        filterMode = filterMode,
        onSearchQueryChange = { viewModel.recentSearchQuery.value = it },
        onFilterModeChange = { viewModel.recentFilterMode.value = it },
        onOpenDocument = onOpenDocument,
        onNavigateToDocuments = onNavigateToDocuments,
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onRemoveFromRecent = { viewModel.removeFromRecent(it) },
        onClearRecentHistory = { viewModel.clearRecentHistory() },
        onRenameDocument = { entity, newName -> viewModel.renamePdf(entity, newName) },
        onDeleteDocument = { viewModel.deletePdf(it) },
        onSetPin = { entity, pin -> viewModel.setLockPin(entity, pin) },
        onShareDocument = { shareRecentDocument(context, it) },
        modifier = modifier
    )
}

@Composable
fun RecentFilesScreenContent(
    recentFiles: List<PdfEntity>,
    totalRecentCount: Int,
    searchQuery: String,
    filterMode: RecentFilterMode,
    onSearchQueryChange: (String) -> Unit,
    onFilterModeChange: (RecentFilterMode) -> Unit,
    onOpenDocument: (PdfEntity) -> Unit,
    onNavigateToDocuments: () -> Unit,
    onToggleFavorite: (PdfEntity) -> Unit,
    onRemoveFromRecent: (PdfEntity) -> Unit,
    onClearRecentHistory: () -> Unit,
    onRenameDocument: (PdfEntity, String) -> Unit,
    onDeleteDocument: (PdfEntity) -> Unit,
    onSetPin: (PdfEntity, String) -> Unit,
    onShareDocument: (PdfEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var previewEntity by remember { mutableStateOf<PdfEntity?>(null) }
    var renameEntity by remember { mutableStateOf<PdfEntity?>(null) }
    var deleteEntity by remember { mutableStateOf<PdfEntity?>(null) }
    var pinEntity by remember { mutableStateOf<PdfEntity?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("recent_files_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = WhiteMixBackground,
        topBar = {
            Surface(color = WhiteMixSurface, shadowElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 860.dp)
                    ) {
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
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.recent_files_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = TextPrimary
                                )
                                if (recentFiles.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BrandPrimaryContainer
                                    ) {
                                        Text(
                                            text = "${recentFiles.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandOnPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (totalRecentCount > 0) {
                                TextButton(
                                    onClick = { showClearDialog = true },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("clear_recent_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = stringResource(R.string.recent_clear_history),
                                        tint = BrandCrimson,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.recent_clear_history),
                                        color = BrandCrimson,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        RecentSearchBox(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(RecentFilterMode.entries) { mode ->
                                val isSelected = mode == filterMode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) BrandPrimary else WhiteMixSurface)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) BrandPrimary else WhiteMixBorder,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onFilterModeChange(mode) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                        .testTag("recent_filter_${mode.name.lowercase()}")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (mode == RecentFilterMode.PDF) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else BrandCrimson,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = mode.title,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                if (recentFiles.isEmpty()) {
                    RecentEmptyState(
                        hasAnyRecent = totalRecentCount > 0,
                        onBrowseDocuments = onNavigateToDocuments,
                        onResetFilters = {
                            onSearchQueryChange("")
                            onFilterModeChange(RecentFilterMode.ALL)
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("recent_files_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = recentFiles,
                            key = { it.path },
                            contentType = { it.documentType }
                        ) { entity ->
                            RecentDocumentCard(
                                entity = entity,
                                onClick = { onOpenDocument(entity) },
                                onQuickPreview = { previewEntity = entity },
                                onToggleFavorite = { onToggleFavorite(entity) },
                                onShare = { onShareDocument(entity) },
                                onRename = { renameEntity = entity },
                                onSetPin = { pinEntity = entity },
                                onRemoveFromRecent = { onRemoveFromRecent(entity) },
                                onDelete = { deleteEntity = entity }
                            )
                        }
                    }
                }
            }
        }
    }

    RecentFilesDialogs(
        showClearDialog = showClearDialog,
        renameEntity = renameEntity,
        deleteEntity = deleteEntity,
        pinEntity = pinEntity,
        onDismissClearDialog = { showClearDialog = false },
        onConfirmClearHistory = {
            showClearDialog = false
            onClearRecentHistory()
        },
        onDismissRename = { renameEntity = null },
        onConfirmRename = { entity, newName ->
            onRenameDocument(entity, newName)
            renameEntity = null
        },
        onDismissDelete = { deleteEntity = null },
        onConfirmDelete = { entity ->
            onDeleteDocument(entity)
            deleteEntity = null
        },
        onDismissPin = { pinEntity = null },
        onConfirmPin = { entity, pin ->
            onSetPin(entity, pin)
            pinEntity = null
        }
    )

    previewEntity?.let { entity ->
        DocumentQuickPreviewBottomSheet(
            entity = entity,
            onDismissRequest = { previewEntity = null },
            onOpenDocument = { doc ->
                previewEntity = null
                onOpenDocument(doc)
            },
            onToggleFavorite = onToggleFavorite,
            onShareDocument = onShareDocument
        )
    }
}

@Composable
private fun RecentEmptyState(
    hasAnyRecent: Boolean,
    onBrowseDocuments: () -> Unit,
    onResetFilters: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("recent_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = BrandPrimaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    if (hasAnyRecent) R.string.recent_empty_filtered_title else R.string.recent_empty_title
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(
                    if (hasAnyRecent) R.string.recent_empty_filtered_subtitle else R.string.recent_empty_subtitle
                ),
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (hasAnyRecent) {
                OutlinedButton(
                    onClick = onResetFilters,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("recent_reset_filters_button")
                ) {
                    Text(
                        text = stringResource(R.string.recent_reset_filters),
                        color = BrandPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Button(
                    onClick = onBrowseDocuments,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("recent_browse_documents_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.recent_browse_documents),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier
            .height(40.dp)
            .testTag("recent_search_box"),
        shape = RoundedCornerShape(20.dp),
        color = WhiteMixBackground,
        border = BorderStroke(1.dp, WhiteMixBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search recent files",
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = TextPrimary),
                cursorBrush = SolidColor(BrandPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.recent_search_placeholder),
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
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
