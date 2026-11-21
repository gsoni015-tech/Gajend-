package com.example.ui.components

import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.example.pdf.thumbnail.PdfDocumentThumbnailLoader
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a high-speed pre-open thumbnail preview of Page 1 for any document.
 * If the document is a PDF, renders the actual cover page.
 * If non-PDF or still loading, displays a clean styled document badge.
 */
@Composable
fun DocumentThumbnailView(
    entity: PdfEntity,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 62.dp,
    showFormatBadge: Boolean = true
) {
    val context = LocalContext.current
    val targetW = remember(width) { width.value.toInt() * 3 }
    val targetH = remember(height) { height.value.toInt() * 3 }

    // Instant synchronous memory-cache hit avoids flicker & spinning progress bars during scroll
    var thumbnailImage by remember(entity.path, entity.lastModifiedTimestamp, targetW) {
        mutableStateOf<ImageBitmap?>(PdfDocumentThumbnailLoader.getCachedImageBitmap(entity, targetW))
    }

    LaunchedEffect(entity.path, entity.lastModifiedTimestamp, targetW) {
        if (thumbnailImage == null && entity.documentType == DocumentType.PDF) {
            val loaded = PdfDocumentThumbnailLoader.loadThumbnailImageBitmap(
                context = context,
                entity = entity,
                targetWidth = targetW,
                targetHeight = targetH,
                delayBeforeNativeRenderMs = 50L
            )
            if (loaded != null) {
                thumbnailImage = loaded
            }
        }
    }

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

    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF9FAFB))
            .border(1.dp, WhiteMixBorder, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnailImage
        if (image != null) {
            // Actual First-Page Rendered Preview
            Image(
                bitmap = image,
                contentDescription = "Preview of ${entity.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Static lightweight placeholder / fallback icon (no continuous spinner animation while scrolling)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(iconColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size((width.value * 0.45f).dp)
                )
            }
        }

        // Tiny format chip on bottom-left corner
        if (showFormatBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(2.dp)
                    .background(iconColor.copy(alpha = 0.92f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
            ) {
                Text(
                    text = entity.documentType.name,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Lock badge if PIN locked
        if (entity.isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(Color(0xFFEA580C), RoundedCornerShape(3.dp))
                    .padding(1.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Protected",
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                )
            }
        }
    }
}

/**
 * Pre-Open Document Quick Preview Bottom Sheet.
 * Allows users to inspect Page 1 at high resolution, examine document metadata,
 * and choose how to open, edit, or share before entering the full reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentQuickPreviewBottomSheet(
    entity: PdfEntity,
    onDismissRequest: () -> Unit,
    onOpenDocument: (PdfEntity) -> Unit,
    onEditDocument: ((PdfEntity) -> Unit)? = null,
    onToggleFavorite: (PdfEntity) -> Unit,
    onShareDocument: (PdfEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var previewBitmap by remember(entity.path) { mutableStateOf<Bitmap?>(null) }
    var isRenderingPreview by remember(entity.path) { mutableStateOf(entity.documentType == DocumentType.PDF) }

    LaunchedEffect(entity.path) {
        if (entity.documentType == DocumentType.PDF) {
            val bmp = PdfDocumentThumbnailLoader.loadThumbnail(context, entity, 450, 600)
            previewBitmap = bmp
            isRenderingPreview = false
        } else {
            isRenderingPreview = false
        }
    }

    val iconColor = when (entity.documentType) {
        DocumentType.PDF -> BrandCrimson
        DocumentType.WORD -> BrandPrimary
        DocumentType.EXCEL -> Color(0xFF16A34A)
        DocumentType.OTHER -> Color(0xFF6B7280)
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(entity.lastModifiedTimestamp) {
        dateFormatter.format(Date(entity.lastModifiedTimestamp))
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Document Quick Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Inspect before opening",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large First-Page Visual Preview with Pinch-to-Zoom & Tap-to-Open
            var previewScale by remember { androidx.compose.runtime.mutableFloatStateOf(1.0f) }
            var previewOffset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .widthIn(max = 340.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, WhiteMixBorder), RoundedCornerShape(8.dp)),
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(previewScale) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startTime = System.currentTimeMillis()
                                    val startPos = down.position
                                    var maxDistance = 0f
                                    var isTransforming = false

                                    do {
                                        val event = awaitPointerEvent()
                                        val pointerCount = event.changes.size

                                        if (pointerCount >= 2) {
                                            isTransforming = true
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()

                                            val newScale = (previewScale * zoomChange).coerceIn(0.85f, 4.5f)
                                            val maxPanX = if (newScale > 1f) (newScale - 1f) * size.width / 2f else 0f
                                            val maxPanY = if (newScale > 1f) (newScale - 1f) * size.height / 2f else 0f

                                            previewScale = newScale
                                            previewOffset = Offset(
                                                x = (previewOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                y = (previewOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                            )
                                            event.changes.forEach { it.consume() }
                                        } else if (pointerCount == 1 && previewScale > 1.05f) {
                                            val panChange = event.calculatePan()
                                            if (panChange != Offset.Zero) {
                                                isTransforming = true
                                                val maxPanX = (previewScale - 1f) * size.width / 2f
                                                val maxPanY = (previewScale - 1f) * size.height / 2f
                                                previewOffset = Offset(
                                                    x = (previewOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (previewOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                                event.changes.forEach { it.consume() }
                                            }
                                        } else if (pointerCount == 1) {
                                            val currentChange = event.changes.firstOrNull()
                                            if (currentChange != null) {
                                                val dist = (currentChange.position - startPos).getDistance()
                                                if (dist > maxDistance) {
                                                    maxDistance = dist
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })

                                    val duration = System.currentTimeMillis() - startTime
                                    // If not zoomed/pinched and short clean tap with minimal movement: open document directly!
                                    if (!isTransforming && duration < 350 && maxDistance < 18f) {
                                        onDismissRequest()
                                        onOpenDocument(entity)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = previewScale,
                                    scaleY = previewScale,
                                    translationX = previewOffset.x,
                                    translationY = previewOffset.y
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = "Cover preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else if (isRenderingPreview) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = BrandCrimson,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Rendering Page 1 Preview...",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            } else {
                                // Icon fallback
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = when (entity.documentType) {
                                            DocumentType.PDF -> Icons.Default.PictureAsPdf
                                            DocumentType.WORD -> Icons.Default.Description
                                            DocumentType.EXCEL -> Icons.Default.TableChart
                                            else -> Icons.Default.Description
                                        },
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${entity.documentType.name} Document",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }

                        // Floating Zoom Indicator & Reset Badge or Pinch/Tap hint
                        if (previewScale > 1.05f || previewScale < 0.95f) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clickable {
                                        previewScale = 1.0f
                                        previewOffset = Offset.Zero
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.75f)
                            ) {
                                Text(
                                    text = "${(previewScale * 100).roundToInt()}% • Tap to Reset",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .clickable {
                                        onDismissRequest()
                                        onOpenDocument(entity)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "🔍 Pinch to Zoom • Tap to Open",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Document Details Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = WhiteMixCard,
                border = BorderStroke(1.dp, WhiteMixBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = entity.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailChip(label = "Format", value = entity.documentType.name)
                        DetailChip(label = "Size", value = Formatter.formatFileSize(context, entity.sizeBytes))
                        if (entity.documentType == DocumentType.PDF && entity.pageCount > 0) {
                            DetailChip(label = "Pages", value = "${entity.pageCount} pages")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Modified: $formattedDate",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    if (entity.isLocked) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PIN Protected Document",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Action: Open Full Reader
            Button(
                onClick = {
                    onDismissRequest()
                    onOpenDocument(entity)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("preview_open_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Full Document",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Quick Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (entity.documentType == DocumentType.PDF && onEditDocument != null) {
                    OutlinedButton(
                        onClick = {
                            onDismissRequest()
                            onEditDocument(entity)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Annotate", fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = { onShareDocument(entity) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onToggleFavorite(entity) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (entity.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = if (entity.isFavorite) BrandCrimson else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (entity.isFavorite) "Saved" else "Favorite", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailChip(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
