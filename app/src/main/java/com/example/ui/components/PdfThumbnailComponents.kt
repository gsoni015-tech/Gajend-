package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.render.PdfRenderEngine
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface

/**
 * Individual Page Thumbnail Card
 */
@Composable
fun PdfPageThumbnailCard(
    renderEngine: PdfRenderEngine,
    pageIndex: Int,
    isSelected: Boolean,
    colorFilter: ColorFilter? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember(pageIndex) {
        mutableStateOf(renderEngine.cache.getLowRes(pageIndex) ?: renderEngine.cache.getHighRes(pageIndex))
    }
    var aspectRatio by remember(pageIndex) {
        mutableFloatStateOf(renderEngine.cache.getAspectRatio(pageIndex))
    }

    LaunchedEffect(pageIndex) {
        if (thumbnail == null) {
            val bmp = renderEngine.getOrRenderThumbnail(pageIndex)
            thumbnail = bmp
            aspectRatio = renderEngine.cache.getAspectRatio(pageIndex)
        }
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("page_thumbnail_${pageIndex + 1}"),
        shape = RoundedCornerShape(8.dp),
        color = WhiteMixCard,
        shadowElevation = if (isSelected) 4.dp else 1.dp,
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else 1.dp,
            color = if (isSelected) BrandCrimson else WhiteMixBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                thumbnail?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = colorFilter
                    )
                } ?: run {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isSelected) BrandCrimson else Color(0xFFF3F4F6),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Slide-in Side Drawer for Visual PDF Page Navigation
 */
@Composable
fun PdfThumbnailSideDrawer(
    renderEngine: PdfRenderEngine,
    pageCount: Int,
    currentPage: Int,
    colorFilter: ColorFilter? = null,
    onSelectPage: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    // Automatically scroll drawer grid to the current page on open
    LaunchedEffect(currentPage) {
        if (currentPage in 1..pageCount) {
            gridState.scrollToItem((currentPage - 1).coerceAtLeast(0))
        }
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .shadow(12.dp)
            .navigationBarsPadding(),
        color = WhiteMixSurface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Page Thumbnails",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$pageCount total pages • Current: $currentPage",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WhiteMixBorder)
            )

            // Grid of Page Thumbnails
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(pageCount, key = { it }) { index ->
                    PdfPageThumbnailCard(
                        renderEngine = renderEngine,
                        pageIndex = index,
                        isSelected = (index + 1 == currentPage),
                        colorFilter = colorFilter,
                        onClick = {
                            onSelectPage(index + 1)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Bottom Floating Thumbnail Strip for Continuous Navigation
 */
@Composable
fun PdfThumbnailBottomStrip(
    renderEngine: PdfRenderEngine,
    pageCount: Int,
    currentPage: Int,
    isVisible: Boolean,
    colorFilter: ColorFilter? = null,
    onSelectPage: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val listState = rememberLazyListState()

        // Auto-center current page in bottom filmstrip
        LaunchedEffect(currentPage) {
            if (currentPage in 1..pageCount) {
                val targetIndex = (currentPage - 2).coerceAtLeast(0)
                listState.animateScrollToItem(targetIndex)
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.88f),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                // Strip Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Page $currentPage of $pageCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )

                    Text(
                        text = "Tap any page to jump",
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hide Filmstrip",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Horizontal Filmstrip
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(pageCount, key = { it }) { index ->
                        val isSelected = (index + 1 == currentPage)
                        PdfPageThumbnailCard(
                            renderEngine = renderEngine,
                            pageIndex = index,
                            isSelected = isSelected,
                            colorFilter = colorFilter,
                            onClick = { onSelectPage(index + 1) },
                            modifier = Modifier.width(62.dp)
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Modern Material 3 Bottom Sheet displaying low-resolution thumbnails
 * of all pages for quick, responsive navigation across the PDF document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfThumbnailBottomSheet(
    renderEngine: PdfRenderEngine,
    pageCount: Int,
    currentPage: Int,
    colorFilter: ColorFilter? = null,
    onSelectPage: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val gridState = rememberLazyGridState()
    var gridColumns by remember { mutableIntStateOf(3) }
    var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }

    // Auto-scroll the grid to the current page on open
    LaunchedEffect(currentPage) {
        if (currentPage in 1..pageCount) {
            val targetIndex = (currentPage - 1).coerceAtLeast(0)
            gridState.scrollToItem(targetIndex)
        }
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
                .widthIn(max = 680.dp)
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Title, active page, density switcher, close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Page Thumbnails",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$pageCount total pages • Current: Page $currentPage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Column density toggle button (2 cols / 3 cols / 4 cols)
                    IconButton(
                        onClick = {
                            gridColumns = when (gridColumns) {
                                2 -> 3
                                3 -> 4
                                else -> 2
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("thumbnail_grid_columns_toggle")
                    ) {
                        Icon(
                            imageVector = when (gridColumns) {
                                2 -> Icons.Default.ViewList
                                4 -> Icons.Default.GridView
                                else -> Icons.Default.GridView
                            },
                            contentDescription = "Toggle Grid Columns ($gridColumns cols)",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("thumbnail_sheet_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Thumbnails",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quick Page Scrubbing Slider (if PDF has > 1 page)
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { newVal ->
                            sliderValue = newVal
                        },
                        onValueChangeFinished = {
                            val targetPage = sliderValue.toInt().coerceIn(1, pageCount)
                            if (targetPage != currentPage) {
                                onSelectPage(targetPage)
                            }
                        },
                        valueRange = 1f..pageCount.toFloat(),
                        steps = if (pageCount > 2) pageCount - 2 else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandCrimson,
                            activeTrackColor = BrandCrimson
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "$pageCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )

            // Low-Resolution Thumbnail Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(pageCount, key = { it }) { index ->
                    val pageNum = index + 1
                    val isSelected = (pageNum == currentPage)
                    PdfPageThumbnailCard(
                        renderEngine = renderEngine,
                        pageIndex = index,
                        isSelected = isSelected,
                        colorFilter = colorFilter,
                        onClick = {
                            onSelectPage(pageNum)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}
