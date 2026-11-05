package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import com.example.pdf.text.PageTextContent
import com.example.pdf.text.PdfTextExtractor
import com.example.pdf.text.TextSearchMatch
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.FileProvider
import com.example.ads.AdMobManager
import com.example.data.local.PdfEntity
import com.example.pdf.PdfToolsHelper
import com.example.reader.TextToSpeechHelper
import com.example.ui.components.ComposePdfViewer
import com.example.ui.components.PdfViewerScrollMode
import com.example.ui.theme.BrandCrimson
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBackground
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.viewmodel.PdfViewModel
import com.example.ui.viewmodel.ReaderThemeMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    entity: PdfEntity,
    viewModel: PdfViewModel,
    onBack: () -> Unit,
    onNavigateToEdit: (PdfEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val file = remember(entity.path) { File(entity.path) }

    // Preload interstitial when screen opens
    LaunchedEffect(Unit) {
        AdMobManager.loadInterstitial(context.applicationContext)
    }

    // Interstitial Ad trigger on device Back press with cooldown protection
    val handleBackPress: () -> Unit = {
        if (activity != null) {
            AdMobManager.showBackPressInterstitial(activity) { onBack() }
        } else {
            onBack()
        }
    }

    var showThumbnailBottomSheet by remember { mutableStateOf(false) }
    var showThumbnailDrawer by remember { mutableStateOf(false) }
    var showThumbnailStrip by remember { mutableStateOf(false) }

    var scrollMode by remember { mutableStateOf(PdfViewerScrollMode.CONTINUOUS_VERTICAL) }
    var readerTheme by remember { mutableStateOf(ReaderThemeMode.LIGHT) }
    var isPageLocked by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var targetPageToJump by remember { mutableStateOf<Int?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var allPdfTextMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var searchMatches by remember { mutableStateOf<List<TextSearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Text Extraction Dialog State
    var showExtractDialog by remember { mutableStateOf(false) }
    var extractedTextContent by remember { mutableStateOf<PageTextContent?>(null) }
    var isExtractingPage by remember { mutableStateOf(false) }

    LaunchedEffect(entity.path) {
        try {
            allPdfTextMap = PdfTextExtractor.extractAllText(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(searchQuery, allPdfTextMap) {
        if (searchQuery.trim().length >= 2) {
            val matches = PdfTextExtractor.search(allPdfTextMap, searchQuery, totalPages)
            searchMatches = matches
            currentMatchIndex = 0
            if (matches.isNotEmpty()) {
                targetPageToJump = matches[0].pageNumber
                currentPage = matches[0].pageNumber
            }
        } else {
            searchMatches = emptyList()
            currentMatchIndex = 0
        }
    }

    val openExtractTextDialog: (Int) -> Unit = { pageNumber ->
        coroutineScope.launch {
            isExtractingPage = true
            try {
                val content = PdfTextExtractor.extractPageText(file, pageNumber - 1, totalPages)
                extractedTextContent = content
                showExtractDialog = true
            } catch (e: Exception) {
                Toast.makeText(context, "Could not extract text: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isExtractingPage = false
            }
        }
    }

    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            searchMatches = emptyList()
        } else if (showThumbnailBottomSheet) {
            showThumbnailBottomSheet = false
        } else if (showThumbnailDrawer) {
            showThumbnailDrawer = false
        } else if (showThumbnailStrip) {
            showThumbnailStrip = false
        } else {
            handleBackPress()
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var isTtsActive by remember { mutableStateOf(false) }

    val isBookmarked by viewModel.currentBookmarkStatus.collectAsState()

    val ttsHelper = remember { TextToSpeechHelper(context) }
    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = when (readerTheme) {
            ReaderThemeMode.LIGHT -> WhiteMixBackground
            ReaderThemeMode.SEPIA -> Color(0xFFFBF0D9)
            ReaderThemeMode.DARK -> Color(0xFF121212)
        },
        topBar = {
            Surface(
                color = when (readerTheme) {
                    ReaderThemeMode.DARK -> Color(0xFF1E1E1E)
                    else -> WhiteMixSurface
                },
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                ) {
                    if (isSearchActive) {
                        // In-PDF Search Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                    searchMatches = emptyList()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Search",
                                    tint = if (readerTheme == ReaderThemeMode.DARK) Color.White else TextPrimary
                                )
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search keyword in PDF...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .padding(horizontal = 4.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            )

                            if (searchMatches.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchIndex + 1}/${searchMatches.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                IconButton(
                                    onClick = {
                                        if (searchMatches.isNotEmpty()) {
                                            currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.size - 1
                                            val match = searchMatches[currentMatchIndex]
                                            targetPageToJump = match.pageNumber
                                            currentPage = match.pageNumber
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Previous Match",
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (searchMatches.isNotEmpty()) {
                                            currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                                            val match = searchMatches[currentMatchIndex]
                                            targetPageToJump = match.pageNumber
                                            currentPage = match.pageNumber
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Next Match",
                                        tint = BrandPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else if (searchQuery.trim().length >= 2) {
                                Text(
                                    text = "0 found",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        IconButton(
                            onClick = handleBackPress,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (readerTheme == ReaderThemeMode.DARK) Color.White else TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showGoToPageDialog = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = entity.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (readerTheme == ReaderThemeMode.DARK) Color.White else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Page $currentPage / $totalPages • Tap to Jump",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (readerTheme == ReaderThemeMode.DARK) Color(0xFF90CAF9) else BrandPrimary
                            )
                        }

                        // Search in PDF Button
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("viewer_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search in PDF",
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Extract Text Button
                        IconButton(
                            onClick = { openExtractTextDialog(currentPage) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("viewer_extract_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = "Extract Page Text",
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                    // 1-Tap Quick Reading Mode Toggle (Day / Sepia / Night)
                    IconButton(
                        onClick = {
                            readerTheme = when (readerTheme) {
                                ReaderThemeMode.LIGHT -> ReaderThemeMode.SEPIA
                                ReaderThemeMode.SEPIA -> ReaderThemeMode.DARK
                                ReaderThemeMode.DARK -> ReaderThemeMode.LIGHT
                            }
                            val themeName = when (readerTheme) {
                                ReaderThemeMode.LIGHT -> "Light Mode"
                                ReaderThemeMode.SEPIA -> "Sepia Eye-Care Mode"
                                ReaderThemeMode.DARK -> "Night Reading Mode"
                            }
                            Toast.makeText(context, themeName, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = when (readerTheme) {
                                ReaderThemeMode.LIGHT -> Icons.Default.DarkMode
                                ReaderThemeMode.SEPIA -> Icons.Default.Palette
                                ReaderThemeMode.DARK -> Icons.Default.WbSunny
                            },
                            contentDescription = "Switch Reading Mode",
                            tint = when (readerTheme) {
                                ReaderThemeMode.DARK -> Color(0xFFFFD54F)
                                ReaderThemeMode.SEPIA -> Color(0xFFD97706)
                                else -> BrandPrimary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // View Mode Toggle (Vertical Continuous vs Horizontal Paged)
                    IconButton(
                        onClick = {
                            scrollMode = if (scrollMode == PdfViewerScrollMode.CONTINUOUS_VERTICAL) {
                                PdfViewerScrollMode.PAGED_HORIZONTAL
                            } else {
                                PdfViewerScrollMode.CONTINUOUS_VERTICAL
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (scrollMode == PdfViewerScrollMode.CONTINUOUS_VERTICAL) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                            contentDescription = "Toggle Scroll Mode",
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Page Lock Toggle (Locks page in center to prevent sliding left-right off screen)
                    IconButton(
                        onClick = {
                            isPageLocked = !isPageLocked
                            Toast.makeText(
                                context,
                                if (isPageLocked) "🔒 Page Locked: Page will stay in center" else "🔓 Page Unlocked: Free pan enabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("viewer_page_lock_button")
                    ) {
                        Icon(
                            imageVector = if (isPageLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isPageLocked) "Page Locked (Tap to Unlock)" else "Page Unlocked (Tap to Lock)",
                            tint = if (isPageLocked) BrandPrimary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Page Thumbnails Navigation Sheet
                    IconButton(
                        onClick = { showThumbnailBottomSheet = !showThumbnailBottomSheet },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("viewer_thumbnails_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Page Thumbnails",
                            tint = if (showThumbnailBottomSheet) BrandCrimson else BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Text to Speech
                    IconButton(
                        onClick = {
                            if (isTtsActive) {
                                ttsHelper.stop()
                                isTtsActive = false
                            } else {
                                isTtsActive = true
                                ttsHelper.speak("Reading page $currentPage of ${entity.title}.")
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsActive) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = if (isTtsActive) BrandCrimson else BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Edit & Annotate
                    IconButton(
                        onClick = { onNavigateToEdit(entity) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("viewer_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit & Annotate",
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bookmark Button
                    IconButton(
                        onClick = {
                            viewModel.toggleCurrentBookmark(entity.path, currentPage, "Page $currentPage")
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark Page",
                            tint = if (isBookmarked) BrandCrimson else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = if (readerTheme == ReaderThemeMode.DARK) Color.White else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isPageLocked) "Unlock Horizontal Move" else "Lock Page Position (Default)") },
                                onClick = {
                                    showMenu = false
                                    isPageLocked = !isPageLocked
                                    Toast.makeText(
                                        context,
                                        if (isPageLocked) "🔒 Page Locked: Page will stay in center" else "🔓 Page Unlocked: Free pan enabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isPageLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showThumbnailStrip) "Hide Thumbnail Strip" else "Thumbnail Filmstrip") },
                                onClick = {
                                    showMenu = false
                                    showThumbnailStrip = !showThumbnailStrip
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ViewSidebar,
                                        contentDescription = "Thumbnail Filmstrip",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Page Thumbnails Grid") },
                                onClick = {
                                    showMenu = false
                                    showThumbnailDrawer = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Page Thumbnails",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Print") },
                                onClick = {
                                    showMenu = false
                                    com.example.pdf.PdfPrintHelper.printPdf(context, file, entity.uriString, entity.title)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = "Print",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Page Thumbnails (Sheet)") },
                                onClick = {
                                    showMenu = false
                                    showThumbnailBottomSheet = true
                                },
                                leadingIcon = { Icon(Icons.Default.GridView, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Side Thumbnails Drawer") },
                                onClick = {
                                    showMenu = false
                                    showThumbnailDrawer = true
                                },
                                leadingIcon = { Icon(Icons.Default.ViewSidebar, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Bottom Filmstrip") },
                                onClick = {
                                    showMenu = false
                                    showThumbnailStrip = true
                                },
                                leadingIcon = { Icon(Icons.Default.ViewAgenda, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Reading Theme") },
                                onClick = {
                                    showMenu = false
                                    showThemeDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Palette, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Compress PDF") },
                                onClick = {
                                    showMenu = false
                                    showCompressDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Compress, null, modifier = Modifier.size(18.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Document") },
                                onClick = {
                                    showMenu = false
                                    sharePdf(context, file)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            ComposePdfViewer(
                file = file,
                uriString = entity.uriString,
                scrollMode = scrollMode,
                readerTheme = readerTheme,
                targetPage = targetPageToJump,
                showThumbnailBottomSheet = showThumbnailBottomSheet,
                showThumbnailDrawer = showThumbnailDrawer,
                showThumbnailStrip = showThumbnailStrip,
                isPageLocked = isPageLocked,
                onCloseThumbnailBottomSheet = { showThumbnailBottomSheet = false },
                onCloseThumbnailDrawer = { showThumbnailDrawer = false },
                onCloseThumbnailStrip = { showThumbnailStrip = false },
                onThumbnailPageSelected = { page ->
                    targetPageToJump = page
                    currentPage = page
                },
                onPagePillClick = { showThumbnailBottomSheet = true },
                onPageLongPress = { pageNum -> openExtractTextDialog(pageNum) },
                onPageChanged = { page, total ->
                    currentPage = page
                    totalPages = total
                    viewModel.checkBookmarkStatus(entity.path, page)
                }
            )

            // Floating Search Match Snippet Indicator
            if (isSearchActive && searchMatches.isNotEmpty()) {
                val currentMatch = searchMatches.getOrNull(currentMatchIndex)
                if (currentMatch != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .widthIn(max = 600.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.95f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page ${currentMatch.pageNumber}:",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentMatch.matchSnippet,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentMatchIndex + 1}/${searchMatches.size}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Go to Page Dialog
    if (showGoToPageDialog) {
        var inputPageText by remember { mutableStateOf(currentPage.toString()) }
        var sliderValue by remember { mutableFloatStateOf(currentPage.toFloat()) }

        AlertDialog(
            onDismissRequest = { showGoToPageDialog = false },
            title = {
                Text(
                    text = "Go to Page",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Total Pages: $totalPages (Current: $currentPage)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputPageText,
                        onValueChange = { newVal ->
                            val filtered = newVal.filter { it.isDigit() }
                            inputPageText = filtered
                            val parsed = filtered.toIntOrNull()
                            if (parsed != null && parsed in 1..totalPages) {
                                sliderValue = parsed.toFloat()
                            }
                        },
                        label = { Text("Page Number (1 - $totalPages)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (totalPages > 1) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Or drag slider to jump:",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = it
                                inputPageText = it.toInt().toString()
                            },
                            valueRange = 1f..totalPages.toFloat(),
                            steps = if (totalPages > 2) totalPages - 2 else 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = inputPageText.toIntOrNull() ?: sliderValue.toInt()
                        val clamped = target.coerceIn(1, totalPages)
                        targetPageToJump = clamped
                        showGoToPageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Jump to Page")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Reading Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ReaderThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (mode == readerTheme) BrandPrimary.copy(alpha = 0.1f) else Color.Transparent)
                                .padding(8.dp)
                        ) {
                            RadioButton(
                                selected = (mode == readerTheme),
                                onClick = { readerTheme = mode }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.title, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showThemeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Apply")
                }
            }
        )
    }

    // Compress Dialog
    if (showCompressDialog) {
        var selectedLevel by remember { mutableStateOf(PdfToolsHelper.CompressionLevel.MEDIUM) }
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select compression level:", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    PdfToolsHelper.CompressionLevel.entries.forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (level == selectedLevel),
                                onClick = { selectedLevel = level }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(level.label, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompressDialog = false
                        viewModel.executeCompressPdf(file, selectedLevel) {
                            if (activity != null) {
                                AdMobManager.showTaskCompletionInterstitial(activity) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Compress Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Extracted Text Dialog (Triggered via Long-Press or Toolbar Icon)
    if (showExtractDialog && extractedTextContent != null) {
        val content = extractedTextContent!!
        AlertDialog(
            onDismissRequest = { showExtractDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Page ${content.pageNumber} Text",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEFF6FF)
                        ) {
                            Text(
                                text = "${content.wordCount} words",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "${content.characterCount} characters",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 260.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = if (content.text.isNotBlank()) content.text else "No extractable text found on this page (page may be an image or scanned document).",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = if (content.text.isNotBlank()) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (content.text.isNotBlank()) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Page ${content.pageNumber} Text", content.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }

                        TextButton(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Page ${content.pageNumber} from ${entity.title}")
                                    putExtra(Intent.EXTRA_TEXT, content.text)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Page Text"))
                            }
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
