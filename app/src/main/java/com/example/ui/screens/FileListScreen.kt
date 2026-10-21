package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

    var showRenameDialogFor by remember { mutableStateOf<PdfEntity?>(null) }
    var showPinDialogFor by remember { mutableStateOf<PdfEntity?>(null) }
    var showDeleteConfirmFor by remember { mutableStateOf<PdfEntity?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importPdf(it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = WhiteMixBackground,
        topBar = {
            // Compact Top Bar (size kam kar di gayi hai)
            Surface(
                color = WhiteMixSurface,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp) // Compact 48dp height
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(BrandPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = "App Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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

                        // Right actions (import)
                        IconButton(
                            onClick = { filePicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("import_document_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Import Document",
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Compact Search Box (search box chota kar diya hai)
                    CompactSearchBox(
                        query = searchQuery,
                        onQueryChange = { viewModel.searchQuery.value = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // Category Tabs (Compact)
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
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    ) { paddingValues ->
        // Document List - NO banner ads, NO ads between documents!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                            text = if (searchQuery.isNotBlank()) "No documents match \"$searchQuery\"" else "No documents found",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                filePicker.launch(arrayOf("application/pdf", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Browse Files • फाइल चुनें", fontSize = 13.sp)
                        }
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
                    label = { Text("File Name") },
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
                    Text("Rename")
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
            title = { Text(if (entity.isLocked) "Change/Remove PIN" else "Set Security PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
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
            .height(38.dp) // Compact 38dp height
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
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetPin: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val iconColor = when (entity.documentType) {
        DocumentType.PDF -> BrandCrimson
        DocumentType.WORD -> BrandPrimary
        DocumentType.EXCEL -> Color(0xFF16A34A)
        DocumentType.OTHER -> Color(0xFF6B7280)
    }

    val iconVector = when (entity.documentType) {
        DocumentType.PDF -> Icons.Default.PictureAsPdf
        DocumentType.WORD -> Icons.Default.Description
        DocumentType.EXCEL -> Icons.Default.TableChart
        DocumentType.OTHER -> Icons.Default.Description
    }

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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

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
                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(entity.lastModifiedTimestamp)),
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
                        text = { Text("Share • शेयर करें") },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename • नाम बदलें") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (entity.isLocked) "Change PIN • पिन बदलें" else "Lock PIN • लॉक करें") },
                        onClick = {
                            menuExpanded = false
                            onSetPin()
                        },
                        leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete • हटाएं", color = BrandCrimson) },
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
