package com.example.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.cache.CacheStats
import com.example.data.cache.PdfFastCacheManager
import com.example.data.cloud.CloudDocument
import com.example.data.cloud.CloudSyncState
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandOnPrimaryContainer
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface

@Composable
fun CloudSignInSection(
    isSigningIn: Boolean,
    authErrorMessage: String?,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("cloud_sign_in_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Surface(
            shape = CircleShape,
            color = BrandPrimaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AddToDrive,
                    contentDescription = stringResource(R.string.cloud_sync_title),
                    tint = BrandPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.cloud_sign_in_title),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.cloud_sign_in_subtitle),
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = WhiteMixSurface,
            border = BorderStroke(1.dp, WhiteMixBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CloudFeatureRow(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.cloud_feature_auto_upload_title),
                    subtitle = stringResource(R.string.cloud_feature_auto_upload_desc)
                )
                CloudFeatureRow(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.cloud_feature_three_way_delete_title),
                    subtitle = stringResource(R.string.cloud_feature_three_way_delete_desc)
                )
                CloudFeatureRow(
                    icon = Icons.Default.Bolt,
                    title = stringResource(R.string.cloud_feature_fast_cache_title),
                    subtitle = stringResource(R.string.cloud_feature_fast_cache_desc)
                )
            }
        }

        if (!authErrorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = authErrorMessage,
                color = BrandCrimson,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSignInClick,
            enabled = !isSigningIn,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .testTag("cloud_google_sign_in_button")
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Signing in...", fontWeight = FontWeight.SemiBold)
            } else {
                Text(
                    text = stringResource(R.string.cloud_sign_in_button),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CloudFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BrandPrimaryContainer,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
            Text(text = subtitle, fontSize = 11.5.sp, color = TextSecondary, lineHeight = 16.sp)
        }
    }
}

@Composable
fun CloudFastCacheCard(
    cacheStats: CacheStats,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formattedSize = Formatter.formatShortFileSize(context, cacheStats.totalBytes)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WhiteMixSurface,
        border = BorderStroke(1.dp, WhiteMixBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_fast_cache_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE0F2FE),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Fast Mobile Cache",
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.cloud_fast_cache_label),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${cacheStats.fileCount} file(s) • $formattedSize ready for instant open",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (cacheStats.fileCount > 0) {
                TextButton(
                    onClick = onClearCache,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("cloud_clear_cache_button")
                ) {
                    Text(
                        text = stringResource(R.string.cloud_clear_cache),
                        color = BrandCrimson,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CloudStorageSetupBanner(
    onGrantAccessClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("drive_permission_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cloud_storage_setup_notice),
                    fontSize = 11.5.sp,
                    color = Color(0xFF92400E),
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onGrantAccessClick,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("grant_drive_access_button")
            ) {
                Text(
                    text = stringResource(R.string.cloud_grant_drive_access),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun CloudDocumentCard(
    doc: CloudDocument,
    isOpening: Boolean,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formattedSize = Formatter.formatShortFileSize(context, doc.sizeBytes)
    val isBinarySynced = doc.resolvedSyncState == CloudSyncState.SYNCED
    val isFastCached = PdfFastCacheManager.getCachedFileByCloudId(context, doc.id, doc.title) != null

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WhiteMixSurface,
        border = BorderStroke(1.dp, WhiteMixBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_document_card_${doc.title}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEE2E2),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = doc.title,
                        tint = BrandCrimson,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title.ifBlank { "Document.pdf" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formattedSize,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isBinarySynced) Color(0xFFD1FAE5) else BrandPrimaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isBinarySynced) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isBinarySynced) BrandEmerald else BrandOnPrimaryContainer,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isBinarySynced) "Drive Synced" else "Pending Drive",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isBinarySynced) BrandEmerald else BrandOnPrimaryContainer
                            )
                        }
                    }
                    if (isFastCached) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFE0F2FE)
                        ) {
                            Text(
                                text = "Cached",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (doc.folderName.isNotBlank()) {
                    Text(
                        text = doc.folderName,
                        fontSize = 10.5.sp,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onOpenClick,
                enabled = !isOpening,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("cloud_open_${doc.title}")
            ) {
                if (isOpening) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = BrandPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open ${doc.title}",
                        tint = BrandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("cloud_delete_${doc.title}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ${doc.title}",
                    tint = BrandCrimson,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CloudDeleteConfirmDialog(
    doc: CloudDocument,
    onDeleteCloudAndCache: () -> Unit,
    onDeleteEverywhere: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete '${doc.title}'?",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Removing this PDF from Google Drive will also automatically clear its fast mobile cache.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                OutlinedButton(
                    onClick = onDeleteCloudAndCache,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Delete from Drive & Cache Only", color = TextPrimary)
                }
                Button(
                    onClick = onDeleteEverywhere,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_cloud_delete_everywhere")
                ) {
                    Text("Delete Everywhere (Drive, Cache & Device)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
