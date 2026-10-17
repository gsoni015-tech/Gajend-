package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ads.AdMobManager
import com.example.ads.AdvancedTool
import com.example.ads.NativeAdItem
import com.example.data.local.BookmarkEntity
import com.example.data.local.PdfEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.PageEditState
import com.example.pdf.PdfEditorHelper
import com.example.pdf.PdfRendererHelper
import com.example.pdf.PdfToolsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

import com.example.data.local.DocumentType
import com.example.data.local.documentType

enum class FileListTab {
    ALL, PDF, WORD, EXCEL, MAIN_TEXT, RECENT, FAVORITES, FOLDERS
}

enum class SortMode {
    DATE_DESC, NAME_ASC, SIZE_DESC
}

enum class ViewMode {
    LIST, GRID
}

enum class ReaderThemeMode {
    LIGHT, DARK_INVERT, SEPIA
}

// Sealed class for items in file list to support Native Ads every 6 items
sealed class FileListItem {
    data class PdfItem(val entity: PdfEntity) : FileListItem()
    data class AdItem(val nativeAd: NativeAdItem) : FileListItem()
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)

    // File List UI State
    private val _selectedTab = MutableStateFlow(FileListTab.ALL)
    val selectedTab: StateFlow<FileListTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    // Status snackbar / toast message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val allPdfs = repository.allPdfs
    val recentPdfs = repository.recentPdfs
    val favoritePdfs = repository.favoritePdfs

    // Combined list with Ads inserted every 6 items
    val displayedItems: StateFlow<List<FileListItem>> = combine(
        allPdfs,
        _selectedTab,
        _searchQuery,
        _sortMode,
        _selectedFolder
    ) { all: List<PdfEntity>, tab: FileListTab, query: String, sort: SortMode, folder: String? ->
        val rawList = when (tab) {
            FileListTab.ALL -> all
            FileListTab.PDF -> all.filter { it.documentType == DocumentType.PDF }
            FileListTab.WORD -> all.filter { it.documentType == DocumentType.WORD }
            FileListTab.EXCEL -> all.filter { it.documentType == DocumentType.EXCEL }
            FileListTab.MAIN_TEXT -> all.filter { it.documentType == DocumentType.MAIN_FILE }
            FileListTab.RECENT -> all.filter { it.lastOpenedTimestamp > 0L }.sortedByDescending { it.lastOpenedTimestamp }
            FileListTab.FAVORITES -> all.filter { it.isFavorite }
            FileListTab.FOLDERS -> {
                if (folder != null) all.filter { it.folderName == folder }
                else all
            }
        }

        val filtered = if (query.isBlank()) {
            rawList
        } else {
            rawList.filter { it.title.contains(query, ignoreCase = true) }
        }

        val sorted = when (sort) {
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.lastModifiedTimestamp }
            SortMode.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortMode.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }

        // Interleave Native Ads every 6 items
        val result = mutableListOf<FileListItem>()
        val ads = AdMobManager.sampleNativeAds
        var adIdx = 0

        for (i in sorted.indices) {
            result.add(FileListItem.PdfItem(sorted[i]))
            if ((i + 1) % 6 == 0 && ads.isNotEmpty()) {
                result.add(FileListItem.AdItem(ads[adIdx % ads.size]))
                adIdx++
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Folders aggregation
    val foldersWithCount: StateFlow<Map<String, Int>> = allPdfs.combine(_searchQuery) { all, _ ->
        all.groupBy { it.folderName }.mapValues { it.value.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Viewer State
    private val _currentPdf = MutableStateFlow<PdfEntity?>(null)
    val currentPdf: StateFlow<PdfEntity?> = _currentPdf.asStateFlow()

    private val _readerTheme = MutableStateFlow(ReaderThemeMode.LIGHT)
    val readerTheme: StateFlow<ReaderThemeMode> = _readerTheme.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _currentBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val currentBookmarks: StateFlow<List<BookmarkEntity>> = _currentBookmarks.asStateFlow()

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    // Tools execution states
    private val _isToolProcessing = MutableStateFlow(false)
    val isToolProcessing: StateFlow<Boolean> = _isToolProcessing.asStateFlow()

    private val _ocrResultText = MutableStateFlow("")
    val ocrResultText: StateFlow<String> = _ocrResultText.asStateFlow()

    private val _compressionResult = MutableStateFlow<PdfToolsHelper.CompressionResult?>(null)
    val compressionResult: StateFlow<PdfToolsHelper.CompressionResult?> = _compressionResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initialize()
        }
    }

    fun selectTab(tab: FileListTab) {
        _selectedTab.value = tab
        if (tab != FileListTab.FOLDERS) {
            _selectedFolder.value = null
        }
    }

    fun setFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun toggleFavorite(entity: PdfEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(entity.path, entity.isFavorite)
        }
    }

    fun deletePdf(entity: PdfEntity) {
        viewModelScope.launch {
            repository.deletePdf(entity.path)
            _userMessage.value = "Deleted '${entity.title}'"
        }
    }

    fun renamePdf(entity: PdfEntity, newName: String) {
        viewModelScope.launch {
            val newPath = repository.renamePdf(entity.path, newName)
            _userMessage.value = "Renamed to '$newName'"
            if (_currentPdf.value?.path == entity.path) {
                _currentPdf.value = repository.getPdfByPath(newPath)
            }
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importPdfFromUri(uri)
            if (imported != null) {
                _userMessage.value = "Imported '${imported.title}' successfully"
            } else {
                _userMessage.value = "Failed to import selected file"
            }
        }
    }

    fun openPdf(entity: PdfEntity, onOpenSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.recordOpened(entity.path)
            _currentPdf.value = entity
            _totalPages.value = entity.pageCount.coerceAtLeast(1)
            _currentPage.value = 0
            refreshBookmarks(entity.path)
            checkBookmarkStatus(entity.path, 0)
            onOpenSuccess()
        }
    }

    fun setPage(page: Int) {
        _currentPage.value = page
        _currentPdf.value?.let { checkBookmarkStatus(it.path, page) }
    }

    fun setTotalPages(count: Int) {
        _totalPages.value = count
    }

    fun toggleReaderTheme() {
        _readerTheme.value = when (_readerTheme.value) {
            ReaderThemeMode.LIGHT -> ReaderThemeMode.DARK_INVERT
            ReaderThemeMode.DARK_INVERT -> ReaderThemeMode.SEPIA
            ReaderThemeMode.SEPIA -> ReaderThemeMode.LIGHT
        }
    }

    fun setReaderTheme(mode: ReaderThemeMode) {
        _readerTheme.value = mode
    }

    private fun refreshBookmarks(path: String) {
        viewModelScope.launch {
            repository.getBookmarks(path).collect {
                _currentBookmarks.value = it
                checkBookmarkStatus(path, _currentPage.value)
            }
        }
    }

    private fun checkBookmarkStatus(path: String, page: Int) {
        viewModelScope.launch {
            _isCurrentPageBookmarked.value = repository.isPageBookmarked(path, page + 1)
        }
    }

    fun toggleCurrentBookmark() {
        val pdf = _currentPdf.value ?: return
        val pageNumber = _currentPage.value + 1
        viewModelScope.launch {
            repository.toggleBookmark(pdf.path, pageNumber, "${pdf.title} - Page $pageNumber")
            _isCurrentPageBookmarked.value = !_isCurrentPageBookmarked.value
            _userMessage.value = if (_isCurrentPageBookmarked.value) "Page $pageNumber bookmarked" else "Bookmark removed"
        }
    }

    fun sharePdf(context: Context, entity: PdfEntity) {
        try {
            val file = File(entity.path)
            if (!file.exists()) {
                _userMessage.value = "File does not exist"
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/pdf"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share ${entity.title}")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            _userMessage.value = "Share failed: ${e.localizedMessage}"
        }
    }

    // PDF Tools Handlers
    fun executeImageToPdf(context: Context, imageUris: List<Uri>, outputTitle: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isToolProcessing.value = true
            val toolsDir = File(context.filesDir, "Tools").apply { if (!exists()) mkdirs() }
            val cleanTitle = if (outputTitle.isBlank()) "Images_Converted_${System.currentTimeMillis()}" else outputTitle
            val outputFile = File(toolsDir, "$cleanTitle.pdf")

            val success = PdfToolsHelper.convertImagesToPdf(context, imageUris, outputFile)
            _isToolProcessing.value = false

            if (success) {
                repository.addCreatedPdf(outputFile, "Converted")
                // Trigger interstitial ad as required by brief
                AdMobManager.showInterstitialAd {
                    _userMessage.value = "Images converted to PDF successfully!"
                    onComplete()
                }
            } else {
                _userMessage.value = "Failed to convert images to PDF"
            }
        }
    }

    fun executeMergePdfs(context: Context, files: List<File>, outputTitle: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isToolProcessing.value = true
            val toolsDir = File(context.filesDir, "Tools").apply { if (!exists()) mkdirs() }
            val cleanTitle = if (outputTitle.isBlank()) "Merged_Doc_${System.currentTimeMillis()}" else outputTitle
            val outputFile = File(toolsDir, "$cleanTitle.pdf")

            val success = PdfToolsHelper.mergePdfs(files, outputFile)
            _isToolProcessing.value = false

            if (success) {
                repository.addCreatedPdf(outputFile, "Merged")
                AdMobManager.showInterstitialAd {
                    _userMessage.value = "PDFs merged successfully!"
                    onComplete()
                }
            } else {
                _userMessage.value = "Failed to merge PDFs"
            }
        }
    }

    fun executeSplitPdf(context: Context, sourceFile: File, startPage: Int, endPage: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isToolProcessing.value = true
            val toolsDir = File(context.filesDir, "Tools").apply { if (!exists()) mkdirs() }
            val outputFile = File(toolsDir, "${sourceFile.nameWithoutExtension}_split_p${startPage}_p${endPage}.pdf")

            val success = PdfToolsHelper.splitPdf(sourceFile, startPage, endPage, outputFile)
            _isToolProcessing.value = false

            if (success) {
                repository.addCreatedPdf(outputFile, "Split")
                AdMobManager.showInterstitialAd {
                    _userMessage.value = "PDF split successfully (Pages $startPage - $endPage)!"
                    onComplete()
                }
            } else {
                _userMessage.value = "Failed to split PDF"
            }
        }
    }

    fun executeCompressPdf(context: Context, sourceFile: File, level: PdfToolsHelper.CompressionLevel, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isToolProcessing.value = true
            val toolsDir = File(context.filesDir, "Tools").apply { if (!exists()) mkdirs() }
            val outputFile = File(toolsDir, "${sourceFile.nameWithoutExtension}_compressed.pdf")

            val result = PdfToolsHelper.compressPdf(sourceFile, level, outputFile)
            _isToolProcessing.value = false
            _compressionResult.value = result

            if (result != null) {
                repository.addCreatedPdf(outputFile, "Compressed")
                AdMobManager.showInterstitialAd {
                    _userMessage.value = "Saved ${result.percentageSaved}% file size!"
                    onComplete()
                }
            } else {
                _userMessage.value = "Failed to compress PDF"
            }
        }
    }

    fun executeOcrExtraction(file: File, pageIndex: Int) {
        viewModelScope.launch {
            _isToolProcessing.value = true
            val extracted = PdfToolsHelper.extractTextFromPage(file, pageIndex)
            _isToolProcessing.value = false
            _ocrResultText.value = extracted
        }
    }

    fun setLockPin(entity: PdfEntity, pin: String) {
        viewModelScope.launch {
            repository.setLock(entity.path, pin)
            _userMessage.value = "'${entity.title}' locked with PIN"
            if (_currentPdf.value?.path == entity.path) {
                _currentPdf.value = repository.getPdfByPath(entity.path)
            }
        }
    }

    fun unlockPdf(entity: PdfEntity) {
        viewModelScope.launch {
            repository.unlockPdf(entity.path)
            _userMessage.value = "Lock removed from '${entity.title}'"
            if (_currentPdf.value?.path == entity.path) {
                _currentPdf.value = repository.getPdfByPath(entity.path)
            }
        }
    }

    // PDF Editing Support
    private val _pdfForEditing = MutableStateFlow<PdfEntity?>(null)
    val pdfForEditing: StateFlow<PdfEntity?> = _pdfForEditing.asStateFlow()

    fun openPdfForEdit(entity: PdfEntity, onReady: () -> Unit) {
        _pdfForEditing.value = entity
        onReady()
    }

    fun setPdfForEdit(entity: PdfEntity?) {
        _pdfForEditing.value = entity
    }

    fun executeSaveEditedPdf(
        context: Context,
        sourcePdf: PdfEntity,
        pageStates: List<PageEditState>,
        outputTitle: String,
        overwriteOriginal: Boolean,
        onComplete: (PdfEntity) -> Unit
    ) {
        viewModelScope.launch {
            _isToolProcessing.value = true
            val sourceFile = File(sourcePdf.path)

            if (overwriteOriginal) {
                val tempFile = File(context.cacheDir, "edited_temp_${System.currentTimeMillis()}.pdf")
                val success = PdfEditorHelper.saveEditedPdf(context, sourceFile, pageStates, tempFile)
                _isToolProcessing.value = false

                if (success && tempFile.exists()) {
                    sourceFile.delete()
                    tempFile.copyTo(sourceFile, overwrite = true)
                    tempFile.delete()

                    val pageCount = PdfRendererHelper.getPageCountForFile(sourceFile)
                    val updatedEntity = sourcePdf.copy(
                        sizeBytes = sourceFile.length(),
                        pageCount = pageCount,
                        lastModifiedTimestamp = System.currentTimeMillis()
                    )
                    repository.updatePdfEntity(updatedEntity)
                    _currentPdf.value = updatedEntity
                    _pdfForEditing.value = updatedEntity

                    AdMobManager.showInterstitialAd {
                        _userMessage.value = "Document edited & saved successfully!"
                        onComplete(updatedEntity)
                    }
                } else {
                    _userMessage.value = "Failed to save edited document"
                }
            } else {
                val editedDir = File(context.filesDir, "Edited").apply { if (!exists()) mkdirs() }
                val cleanTitle = if (outputTitle.isBlank()) "${sourcePdf.title}_Edited" else outputTitle.trim()
                val outputFile = File(editedDir, "$cleanTitle.pdf")

                val success = PdfEditorHelper.saveEditedPdf(context, sourceFile, pageStates, outputFile)
                _isToolProcessing.value = false

                if (success && outputFile.exists()) {
                    val savedEntity = repository.addCreatedPdf(outputFile, "Edited")
                    _currentPdf.value = savedEntity
                    _pdfForEditing.value = savedEntity

                    AdMobManager.showInterstitialAd {
                        _userMessage.value = "Edited PDF saved as '$cleanTitle'!"
                        onComplete(savedEntity)
                    }
                } else {
                    _userMessage.value = "Failed to save edited PDF"
                }
            }
        }
    }
}
