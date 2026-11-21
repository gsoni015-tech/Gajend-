package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.theme.BrandOnPrimaryContainer
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixCard
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.FileListTab
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.SortGroup
import com.example.ui.viewmodel.SortMode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Stable
class AutoHideCategoryBarController(
    initialVisible: Boolean = true
) {
    var isVisible by mutableStateOf(initialVisible)
        internal set
    var interactionTick by mutableIntStateOf(0)
        internal set

    fun bumpVisibility() {
        isVisible = true
        interactionTick += 1
    }
}

@Composable
fun rememberAutoHideCategoryBarController(
    isScrolling: Boolean,
    autoHideDelayMs: Long = 3_000L
): AutoHideCategoryBarController {
    val controller = remember { AutoHideCategoryBarController(initialVisible = true) }

    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            controller.bumpVisibility()
        }
    }

    LaunchedEffect(controller.isVisible, controller.interactionTick, isScrolling, autoHideDelayMs) {
        if (controller.isVisible && !isScrolling) {
            kotlinx.coroutines.delay(autoHideDelayMs)
            controller.isVisible = false
        }
    }

    return controller
}

fun Modifier.autoRevealCategoryOnTouch(controller: AutoHideCategoryBarController): Modifier =
    pointerInput(controller) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            controller.bumpVisibility()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            controller.bumpVisibility()
        }
    }

@Composable
fun FileListCategoryTabsRow(
    selectedTab: FileListTab,
    visible: Boolean,
    onSelectTab: (FileListTab) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
        exit = shrinkVertically(animationSpec = tween(240)) + fadeOut(animationSpec = tween(180))
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("file_list_category_bar")
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
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("file_list_tab_${tab.name.lowercase()}")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: PdfViewModel,
    onOpenDocument: (PdfEntity) -> Unit,
    modifier: Modifier = Modifier,
    categoryAutoHideDelayMs: Long = 3_000L
) {
    val context = LocalContext.current
    val displayedItems by viewModel.displayedItems.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val sortScrollTrigger by viewModel.sortScrollTrigger.collectAsState()

    val isScanning by viewModel.isScanning.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    var hasPermission by remember { mutableStateOf(PermissionUtils.hasStoragePermission(context)) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isMultiSelectMode) {
        viewModel.exitMultiSelectMode()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.exitMultiSelectMode()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val wasGranted = hasPermission
                val isNowGranted = PermissionUtils.hasStoragePermission(context)
                if (isNowGranted != wasGranted) {
                    hasPermission = isNowGranted
                }
                if (isNowGranted) {
                    viewModel.refreshDocuments(force = !wasGranted)
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
            viewModel.refreshDocuments(force = true)
        }
    }

    LaunchedEffect(Unit) {
        val granted = PermissionUtils.hasStoragePermission(context)
        hasPermission = granted
        if (granted) {
            viewModel.refreshDocuments(force = false)
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
    var showMultiDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var previewEntityForSheet by remember { mutableStateOf<PdfEntity?>(null) }
    var isGridView by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val isAnyScrolling = listState.isScrollInProgress || gridState.isScrollInProgress
    val categoryBarController = rememberAutoHideCategoryBarController(
        isScrolling = isAnyScrolling,
        autoHideDelayMs = categoryAutoHideDelayMs
    )

    LaunchedEffect(sortScrollTrigger) {
        if (sortScrollTrigger > 0) {
            if (isGridView) {
                gridState.animateScrollToItem(0)
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    val onQuickPreviewAction = remember { { entity: PdfEntity -> previewEntityForSheet = entity } }
    val onToggleFavoriteAction = remember(viewModel) { { entity: PdfEntity -> viewModel.toggleFavorite(entity) } }
    val onRenameAction = remember { { entity: PdfEntity -> showRenameDialogFor = entity } }
    val onDeleteAction = remember { { entity: PdfEntity -> showDeleteConfirmFor = entity } }
    val onSetPinAction = remember { { entity: PdfEntity -> showPinDialogFor = entity } }
    val onShareAction = remember(context) { { entity: PdfEntity -> shareDocument(context, entity) } }
    val onToggleSelectAction = remember(viewModel) { { entity: PdfEntity -> viewModel.toggleSelection(entity.path) } }
    val onEnterMultiSelectAction = remember(viewModel) { { entity: PdfEntity -> viewModel.enterMultiSelectMode(entity.path) } }

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
        modifier = modifier
            .fillMaxSize()
            .autoRevealCategoryOnTouch(categoryBarController),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            if (isMultiSelectMode) {
                                val visiblePaths = remember(displayedItems) { displayedItems.map { it.path } }
                                val allVisibleSelected = visiblePaths.isNotEmpty() && visiblePaths.all { it in selectedPaths }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .background(BrandPrimary.copy(alpha = 0.08f))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.exitMultiSelectMode() },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("exit_multi_select_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel Selection",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Text(
                                            text = if (selectedPaths.isEmpty()) "Select files" else "${selectedPaths.size} selected",
                                            style = TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = TextPrimary
                                            )
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(enabled = visiblePaths.isNotEmpty()) {
                                                    viewModel.toggleSelectAll(visiblePaths)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .testTag("select_all_checkbox")
                                        ) {
                                            Checkbox(
                                                checked = allVisibleSelected,
                                                onCheckedChange = null,
                                                enabled = visiblePaths.isNotEmpty(),
                                                colors = CheckboxDefaults.colors(checkedColor = BrandPrimary),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "All",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (visiblePaths.isNotEmpty()) TextPrimary else TextTertiary
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (selectedPaths.isNotEmpty()) {
                                                    showMultiDeleteConfirmDialog = true
                                                }
                                            },
                                            enabled = selectedPaths.isNotEmpty(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = BrandCrimson,
                                                contentColor = Color.White,
                                                disabledContainerColor = WhiteMixBorder,
                                                disabledContentColor = TextTertiary
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .testTag("delete_selected_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Selected",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (selectedPaths.isEmpty()) "Delete" else "Delete (${selectedPaths.size})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            } else {
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

                                    // Right actions (Refresh, Sort, Grid/List, Open Document)
                                    Row(verticalAlignment = Alignment.CenterVertically) {

                                        IconButton(
                                            onClick = { viewModel.refreshDocuments(force = true) },
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

                                        val isCustomSortActive = sortMode != SortMode.DATE_DESC
                                        Box(contentAlignment = Alignment.Center) {
                                            IconButton(
                                                onClick = { showSortSheet = true },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isCustomSortActive) BrandPrimaryContainer else Color.Transparent
                                                    )
                                                    .testTag("sort_documents_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                                    contentDescription = "Sort Documents",
                                                    tint = BrandPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            if (isCustomSortActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(top = 6.dp, end = 6.dp)
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(BrandPrimary)
                                                )
                                            }
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
                            }

                    // Compact Search Box
                    CompactSearchBox(
                        query = searchQuery,
                        onQueryChange = {
                            categoryBarController.bumpVisibility()
                            viewModel.searchQuery.value = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // Auto In-Out Category Tabs (shows on touch/scroll, auto-hides when idle)
                    FileListCategoryTabsRow(
                        selectedTab = selectedTab,
                        visible = categoryBarController.isVisible,
                        onSelectTab = { tab ->
                            categoryBarController.bumpVisibility()
                            viewModel.selectedTab.value = tab
                        }
                    )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
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
                                        onClick = { viewModel.refreshDocuments(force = true) },
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
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = displayedItems,
                                key = { it.path },
                                contentType = { it.documentType }
                            ) { entity ->
                                DocumentGridItemCard(
                                    entity = entity,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = entity.path in selectedPaths,
                                    onClick = onOpenDocument,
                                    onToggleSelect = onToggleSelectAction,
                                    onEnterMultiSelect = onEnterMultiSelectAction,
                                    onQuickPreview = onQuickPreviewAction,
                                    onToggleFavorite = onToggleFavoriteAction,
                                    onRename = onRenameAction,
                                    onDelete = onDeleteAction,
                                    onSetPin = onSetPinAction,
                                    onShare = onShareAction
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = displayedItems,
                                key = { it.path },
                                contentType = { it.documentType }
                            ) { entity ->
                                DocumentItemCard(
                                    entity = entity,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = entity.path in selectedPaths,
                                    onClick = onOpenDocument,
                                    onToggleSelect = onToggleSelectAction,
                                    onEnterMultiSelect = onEnterMultiSelectAction,
                                    onQuickPreview = onQuickPreviewAction,
                                    onToggleFavorite = onToggleFavoriteAction,
                                    onRename = onRenameAction,
                                    onDelete = onDeleteAction,
                                    onSetPin = onSetPinAction,
                                    onShare = onShareAction
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

    if (showMultiDeleteConfirmDialog && selectedPaths.isNotEmpty()) {
        val count = selectedPaths.size
        val selectedNames = displayedItems
            .filter { it.path in selectedPaths }
            .map { it.title }
            .ifEmpty { selectedPaths.map { File(it).name } }
        val previewList = selectedNames.take(3).joinToString("\n") { "• $it" }
        val extraCount = (count - 3).coerceAtLeast(0)
        val extraLine = if (extraCount > 0) "\n...and $extraCount more" else ""
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete $count Document${if (count > 1) "s" else ""}?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to permanently delete $count selected file${if (count > 1) "s" else ""}? This action cannot be undone.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    if (previewList.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = previewList + extraLine,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMultiDeleteConfirmDialog = false
                        viewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandCrimson),
                    modifier = Modifier.testTag("confirm_multi_delete_button")
                ) {
                    Text("Delete ($count)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirmDialog = false }) {
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

    if (showSortSheet) {
        SortOptionsBottomSheet(
            currentSortMode = sortMode,
            onSelectSortMode = { selected ->
                viewModel.setSortMode(selected)
                showSortSheet = false
            },
            onDismissRequest = { showSortSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionsBottomSheet(
    currentSortMode: SortMode,
    onSelectSortMode: (SortMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = WhiteMixSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .testTag("sort_options_bottom_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Sort Documents",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Active: ${currentSortMode.title}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                if (currentSortMode != SortMode.DATE_DESC) {
                    TextButton(
                        onClick = { onSelectSortMode(SortMode.DATE_DESC) },
                        modifier = Modifier.testTag("reset_sort_button")
                    ) {
                        Text(
                            text = "Reset",
                            color = BrandPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SortGroup.entries.forEachIndexed { index, group ->
                if (index > 0) {
                    HorizontalDivider(
                        color = WhiteMixBorder,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Text(
                    text = group.label.uppercase(Locale.ENGLISH),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                )

                val groupModes = SortMode.entries.filter { it.group == group }
                groupModes.forEach { mode ->
                    val isSelected = mode == currentSortMode
                    val isDescending = mode == SortMode.DATE_DESC ||
                        mode == SortMode.NAME_DESC ||
                        mode == SortMode.SIZE_DESC
                    val groupIcon = when (group) {
                        SortGroup.DATE -> Icons.Default.CalendarToday
                        SortGroup.NAME -> Icons.Default.SortByAlpha
                        SortGroup.SIZE -> Icons.Default.Storage
                    }

                    Surface(
                        onClick = { onSelectSortMode(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) BrandPrimaryContainer else Color.Transparent,
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.35f))
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .testTag("sort_option_${mode.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = groupIcon,
                                contentDescription = null,
                                tint = if (isSelected) BrandOnPrimaryContainer else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) BrandOnPrimaryContainer else TextPrimary
                                )
                                Text(
                                    text = mode.subtitle,
                                    fontSize = 11.sp,
                                    color = if (isSelected) BrandOnPrimaryContainer.copy(alpha = 0.8f) else TextSecondary
                                )
                            }

                            Icon(
                                imageVector = if (isDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                contentDescription = if (isDescending) "Descending" else "Ascending",
                                tint = if (isSelected) BrandPrimary else TextTertiary,
                                modifier = Modifier.size(16.dp)
                            )

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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

private val threadLocalDateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
}

private fun formatListDate(timestamp: Long): String {
    return threadLocalDateFormat.get()?.format(Date(timestamp)) ?: ""
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentItemCard(
    entity: PdfEntity,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: (PdfEntity) -> Unit,
    onToggleSelect: (PdfEntity) -> Unit = {},
    onEnterMultiSelect: (PdfEntity) -> Unit = {},
    onQuickPreview: (PdfEntity) -> Unit,
    onToggleFavorite: (PdfEntity) -> Unit,
    onRename: (PdfEntity) -> Unit,
    onDelete: (PdfEntity) -> Unit,
    onSetPin: (PdfEntity) -> Unit,
    onShare: (PdfEntity) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val formattedSize = remember(entity.sizeBytes) {
        Formatter.formatFileSize(context, entity.sizeBytes)
    }
    val formattedDate = remember(entity.lastModifiedTimestamp) {
        formatListDate(entity.lastModifiedTimestamp)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("document_card_${entity.title}")
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) onToggleSelect(entity) else onClick(entity)
                },
                onLongClick = {
                    if (!isMultiSelectMode) menuExpanded = true else onToggleSelect(entity)
                }
            ),
        shape = RoundedCornerShape(10.dp),
        color = if (isMultiSelectMode && isSelected) BrandPrimary.copy(alpha = 0.08f) else WhiteMixCard,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isMultiSelectMode && isSelected) 1.5.dp else 1.dp,
            color = if (isMultiSelectMode && isSelected) BrandPrimary else WhiteMixBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = isMultiSelectMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect(entity) },
                        colors = CheckboxDefaults.colors(checkedColor = BrandPrimary),
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("checkbox_${entity.title}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            // First-Page Real Rendered Thumbnail Preview - tap to quick preview, long-press opens menu
            DocumentThumbnailView(
                entity = entity,
                width = 46.dp,
                height = 58.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClick = {
                            if (isMultiSelectMode) onToggleSelect(entity) else onQuickPreview(entity)
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) menuExpanded = true else onToggleSelect(entity)
                        }
                    )
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
                        text = formattedSize,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = " • ",
                        fontSize = 11.sp,
                        color = TextTertiary
                    )
                    Text(
                        text = formattedDate,
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

            if (!isMultiSelectMode) {
                // Quick Preview Eye Button
                IconButton(
                    onClick = { onQuickPreview(entity) },
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
                    onClick = { onToggleFavorite(entity) },
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

                    if (menuExpanded) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Quick Preview") },
                                onClick = {
                                    menuExpanded = false
                                    onQuickPreview(entity)
                                },
                                leadingIcon = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuExpanded = false
                                    onShare(entity)
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
                                    onRename(entity)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (entity.isLocked) "Change PIN" else "Lock with PIN") },
                                onClick = {
                                    menuExpanded = false
                                    onSetPin(entity)
                                },
                                leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Multiple", color = BrandCrimson) },
                                onClick = {
                                    menuExpanded = false
                                    onEnterMultiSelect(entity)
                                },
                                leadingIcon = { Icon(Icons.Default.CheckBox, null, tint = BrandCrimson, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.testTag("menu_delete_multiple_${entity.title}")
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = BrandCrimson) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete(entity)
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

/**
 * 2-Column Grid Item Card with prominent Cover Page Thumbnail Preview
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridItemCard(
    entity: PdfEntity,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: (PdfEntity) -> Unit,
    onToggleSelect: (PdfEntity) -> Unit = {},
    onEnterMultiSelect: (PdfEntity) -> Unit = {},
    onQuickPreview: (PdfEntity) -> Unit,
    onToggleFavorite: (PdfEntity) -> Unit,
    onRename: (PdfEntity) -> Unit,
    onDelete: (PdfEntity) -> Unit,
    onSetPin: (PdfEntity) -> Unit,
    onShare: (PdfEntity) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val formattedSize = remember(entity.sizeBytes) {
        Formatter.formatFileSize(context, entity.sizeBytes)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("document_grid_card_${entity.title}")
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) onToggleSelect(entity) else onClick(entity)
                },
                onLongClick = {
                    if (!isMultiSelectMode) menuExpanded = true else onToggleSelect(entity)
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isMultiSelectMode && isSelected) BrandPrimary.copy(alpha = 0.08f) else WhiteMixCard,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isMultiSelectMode && isSelected) 1.5.dp else 1.dp,
            color = if (isMultiSelectMode && isSelected) BrandPrimary else WhiteMixBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Large Thumbnail Preview - tap to quick preview, long press opens menu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3F4F6))
                    .combinedClickable(
                        onClick = {
                            if (isMultiSelectMode) onToggleSelect(entity) else onQuickPreview(entity)
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) menuExpanded = true else onToggleSelect(entity)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                DocumentThumbnailView(
                    entity = entity,
                    width = 130.dp,
                    height = 145.dp,
                    modifier = Modifier.fillMaxSize()
                )

                if (isMultiSelectMode) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.94f),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect(entity) },
                            colors = CheckboxDefaults.colors(checkedColor = BrandPrimary),
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("grid_checkbox_${entity.title}")
                        )
                    }
                } else {
                    // Quick Preview Pill on overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = formattedSize,
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                if (!isMultiSelectMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleFavorite(entity) },
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

                            if (menuExpanded) {
                                DropdownMenu(
                                    expanded = true,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Quick Preview") },
                                        onClick = {
                                            menuExpanded = false
                                            onQuickPreview(entity)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = {
                                            menuExpanded = false
                                            onShare(entity)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            menuExpanded = false
                                            onRename(entity)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (entity.isLocked) "Change PIN" else "Lock with PIN") },
                                        onClick = {
                                            menuExpanded = false
                                            onSetPin(entity)
                                        },
                                        leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Multiple", color = BrandCrimson) },
                                        onClick = {
                                            menuExpanded = false
                                            onEnterMultiSelect(entity)
                                        },
                                        leadingIcon = { Icon(Icons.Default.CheckBox, null, tint = BrandCrimson, modifier = Modifier.size(18.dp)) },
                                        modifier = Modifier.testTag("grid_menu_delete_multiple_${entity.title}")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = BrandCrimson) },
                                        onClick = {
                                            menuExpanded = false
                                            onDelete(entity)
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
