package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.example.pdf.PdfPrintHelper
import com.example.ui.components.DocumentThumbnailView
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecentDocumentCard(
    entity: PdfEntity,
    onClick: () -> Unit,
    onQuickPreview: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onSetPin: () -> Unit,
    onRemoveFromRecent: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val formattedSize = remember(entity.sizeBytes) {
        Formatter.formatFileSize(context, entity.sizeBytes)
    }
    val openedLabel = remember(entity.lastOpenedTimestamp) {
        formatOpenedTimestamp(entity.lastOpenedTimestamp)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recent_document_card_${entity.title}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(12.dp),
        color = WhiteMixCard,
        border = BorderStroke(1.dp, WhiteMixBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumbnailView(
                entity = entity,
                width = 46.dp,
                height = 58.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClick = onQuickPreview,
                        onLongClick = { menuExpanded = true }
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

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
                            tint = BrandCrimson,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = openedLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedSize,
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

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("recent_favorite_btn_${entity.title}")
            ) {
                Icon(
                    imageVector = if (entity.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (entity.isFavorite) BrandCrimson else TextTertiary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("recent_more_btn_${entity.title}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
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
                                PdfPrintHelper.printPdf(context, File(entity.path), entity.uriString, entity.title)
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
                        text = { Text("Remove from Recent") },
                        onClick = {
                            menuExpanded = false
                            onRemoveFromRecent()
                        },
                        leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null, modifier = Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete File", color = BrandCrimson) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = BrandCrimson, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecentFilesDialogs(
    showClearDialog: Boolean,
    renameEntity: PdfEntity?,
    deleteEntity: PdfEntity?,
    pinEntity: PdfEntity?,
    onDismissClearDialog: () -> Unit,
    onConfirmClearHistory: () -> Unit,
    onDismissRename: () -> Unit,
    onConfirmRename: (PdfEntity, String) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (PdfEntity) -> Unit,
    onDismissPin: () -> Unit,
    onConfirmPin: (PdfEntity, String) -> Unit
) {
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = onDismissClearDialog,
            title = {
                Text(
                    text = stringResource(R.string.recent_clear_dialog_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.recent_clear_dialog_message),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmClearHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson),
                    modifier = Modifier.testTag("confirm_clear_recent_button")
                ) {
                    Text(stringResource(R.string.recent_clear_history))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    renameEntity?.let { entity ->
        var newName by remember(entity.path) { mutableStateOf(entity.title.substringBeforeLast('.')) }
        AlertDialog(
            onDismissRequest = onDismissRename,
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
                            onConfirmRename(entity, newName)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRename) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteEntity?.let { entity ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete Document?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${entity.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { onConfirmDelete(entity) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text("Cancel")
                }
            }
        )
    }

    pinEntity?.let { entity ->
        var pin by remember(entity.path) { mutableStateOf(entity.password) }
        AlertDialog(
            onDismissRequest = onDismissPin,
            title = {
                Text(
                    text = if (entity.isLocked) "Change or Remove PIN" else "Set Security PIN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Set 4-digit PIN to protect this document. Leave blank to unlock.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
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
                    onClick = { onConfirmPin(entity, pin.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPin) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val recentDateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
}

internal fun formatOpenedTimestamp(timestamp: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0L) return "Opened recently"
    val diff = (nowMs - timestamp).coerceAtLeast(0L)
    return when {
        diff < DateUtils.MINUTE_IN_MILLIS -> "Opened just now"
        diff < DateUtils.DAY_IN_MILLIS -> {
            val relative = DateUtils.getRelativeTimeSpanString(
                timestamp,
                nowMs,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            "Opened $relative"
        }
        else -> {
            val dateText = recentDateFormat.get()?.format(Date(timestamp)).orEmpty()
            if (dateText.isNotEmpty()) "Opened $dateText" else "Opened recently"
        }
    }
}

internal fun shareRecentDocument(context: Context, entity: PdfEntity) {
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
