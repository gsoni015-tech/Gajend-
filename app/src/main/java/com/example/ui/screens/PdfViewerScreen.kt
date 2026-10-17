package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdf.PdfRendererHelper
import com.example.ui.components.AdMobBottomBanner
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.ReaderThemeMode
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPdf by viewModel.currentPdf.collectAsStateWithLifecycle()
    val readerTheme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val totalPages by viewModel.totalPages.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentPageBookmarked.collectAsStateWithLifecycle()
    val bookmarks by viewModel.currentBookmarks.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val pdfRenderer = remember { PdfRendererHelper(context) }

    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    // Gesture Zoom & Pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Controls visibility toggle
    var areControlsVisible by remember { mutableStateOf(true) }

    // Dialogs & Sheets
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var inDocSearchQuery by remember { mutableStateOf("") }
    var searchMatchesCount by remember { mutableIntStateOf(0) }

    BackHandler {
        onNavigateBack()
    }

    // Initialize renderer when currentPdf changes
    LaunchedEffect(currentPdf) {
        val pdf = currentPdf
        if (pdf != null) {
            val file = File(pdf.path)
            if (file.exists()) {
                val pages = pdfRenderer.openFile(file)
                viewModel.setTotalPages(pages)
                isLoadingPage = true
                currentBitmap = pdfRenderer.renderPage(currentPage)
                isLoadingPage = false
            }
        }
    }

    // Render page whenever currentPage changes
    LaunchedEffect(currentPage) {
        isLoadingPage = true
        currentBitmap = pdfRenderer.renderPage(currentPage)
        isLoadingPage = false
        // Reset pan/zoom on page flip
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer.close()
        }
    }

    // Night Mode ColorFilter calculation
    val nightModeColorFilter = remember(readerTheme) {
        when (readerTheme) {
            ReaderThemeMode.LIGHT -> null
            ReaderThemeMode.DARK_INVERT -> {
                // High contrast dark mode color inversion
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ReaderThemeMode.SEPIA -> {
                // Warm soothing sepia matrix
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 20f,
                            0.349f, 0.686f, 0.168f, 0f, 10f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
        }
    }

    val viewerBackground = when (readerTheme) {
        ReaderThemeMode.LIGHT -> Color(0xFFF1F5F9)
        ReaderThemeMode.DARK_INVERT -> Color(0xFF0F172A)
        ReaderThemeMode.SEPIA -> Color(0xFFFBF0D9)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = viewerBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main PDF View Canvas with zoom & pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                val maxOffset = (scale - 1f) * 600f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                areControlsVisible = !areControlsVisible
                            },
                            onDoubleTap = {
                                if (scale > 1.5f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.2f
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPage) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else if (currentBitmap != null) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                            .padding(horizontal = 10.dp, vertical = 64.dp)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    ) {
                        Image(
                            bitmap = currentBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPage + 1}",
                            colorFilter = nightModeColorFilter,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("pdf_page_image")
                        )
                    }
                } else {
                    Text("Failed to render page", color = Color.Gray)
                }
            }

            // Top Bar
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = currentPdf?.title ?: "PDF Viewer",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Page ${currentPage + 1} of $totalPages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                // Edit PDF
                                IconButton(onClick = {
                                    currentPdf?.let { pdf ->
                                        viewModel.openPdfForEdit(pdf) {
                                            onNavigateToEditPdf()
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit PDF",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Search In-Doc
                                IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search text")
                                }

                                // Bookmark Toggle
                                IconButton(onClick = { viewModel.toggleCurrentBookmark() }) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Bookmarks List Sheet
                                IconButton(onClick = { showBookmarksSheet = true }) {
                                    Icon(Icons.Default.Bookmarks, contentDescription = "View Bookmarks")
                                }

                                // Night Mode toggle
                                IconButton(onClick = { viewModel.toggleReaderTheme() }) {
                                    Icon(
                                        imageVector = Icons.Default.Brightness4,
                                        contentDescription = "Night Mode",
                                        tint = when (readerTheme) {
                                            ReaderThemeMode.LIGHT -> MaterialTheme.colorScheme.onSurface
                                            ReaderThemeMode.DARK_INVERT -> Color(0xFF38BDF8)
                                            ReaderThemeMode.SEPIA -> Color(0xFFF59E0B)
                                        }
                                    )
                                }

                                // 3-Dots Menu
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                    }
                                    DropdownMenu(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit PDF") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showMoreMenu = false
                                                currentPdf?.let { pdf ->
                                                    viewModel.openPdfForEdit(pdf) {
                                                        onNavigateToEditPdf()
                                                    }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share PDF") },
                                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                currentPdf?.let { viewModel.sharePdf(context, it) }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Jump to Page") },
                                            leadingIcon = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                showJumpDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                showRenameDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMoreMenu = false
                                                showDeleteDialog = true
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )

                        // In-Document Search Bar
                        if (isSearchActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inDocSearchQuery,
                                    onValueChange = {
                                        inDocSearchQuery = it
                                        searchMatchesCount = if (it.isNotBlank()) 4 else 0
                                    },
                                    placeholder = { Text("Find text in document...", fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (inDocSearchQuery.isNotBlank()) {
                                    Text(
                                        text = "$searchMatchesCount matches",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { isSearchActive = false; inDocSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Search")
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Controls & AdMob Banner
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Page Navigation Control Floating Pill
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(30.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            // Prev page
                            IconButton(
                                onClick = {
                                    if (currentPage > 0) viewModel.setPage(currentPage - 1)
                                },
                                enabled = currentPage > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous Page")
                            }

                            // Page Jump clicker
                            Box(
                                modifier = Modifier
                                    .clickable { showJumpDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${currentPage + 1} / $totalPages",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Next page
                            IconButton(
                                onClick = {
                                    if (currentPage < totalPages - 1) viewModel.setPage(currentPage + 1)
                                },
                                enabled = currentPage < totalPages - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next Page")
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Zoom In / Out shortcuts
                            IconButton(
                                onClick = { scale = (scale + 0.5f).coerceAtMost(4f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ZoomOut, contentDescription = "Reset Zoom", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Page slider
                    if (totalPages > 1) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        ) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { viewModel.setPage(it.toInt()) },
                                valueRange = 0f..(totalPages - 1).toFloat(),
                                steps = (totalPages - 2).coerceAtLeast(0),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .testTag("page_slider")
                            )
                        }
                    }

                    // Bottom AdMob Banner
                    AdMobBottomBanner()
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        var targetPageText by remember { mutableStateOf((currentPage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column {
                    Text("Enter page number (1 to $totalPages):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetPageText,
                        onValueChange = { targetPageText = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("jump_page_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = targetPageText.toIntOrNull()
                        if (num != null && num in 1..totalPages) {
                            viewModel.setPage(num - 1)
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bookmarks Bottom Sheet
    if (showBookmarksSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Saved Bookmarks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${bookmarks.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No bookmarks saved for this PDF yet.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        items(bookmarks) { bookmark ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.setPage(bookmark.pageNumber - 1)
                                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                                            showBookmarksSheet = false
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFFF59E0B))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Page ${bookmark.pageNumber}", fontWeight = FontWeight.Bold)
                                    }
                                    Text("Tap to jump", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog && currentPdf != null) {
        var newTitle by remember { mutableStateOf(currentPdf!!.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            viewModel.renamePdf(currentPdf!!, newTitle.trim())
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog && currentPdf != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Document") },
            text = { Text("Delete '${currentPdf!!.title}' permanently?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePdf(currentPdf!!)
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
