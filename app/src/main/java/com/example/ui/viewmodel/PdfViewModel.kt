package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cache.PdfFastCacheManager
import com.example.data.local.BookmarkEntity
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.example.data.local.SortPreferences
import com.example.data.repository.PdfRepository
import com.example.pdf.PdfToolsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class FileListTab(val title: String) {
    ALL("All Documents"),
    PDF("PDF"),
    WORD("Word"),
    EXCEL("Excel"),
    RECENT("Recent"),
    FAVORITES("Favorites")
}

enum class RecentFilterMode(val title: String) {
    ALL("All"),
    PDF("PDFs"),
    WORD("Word"),
    EXCEL("Excel")
}

enum class SortGroup(val label: String) {
    DATE("Date Modified"),
    NAME("File Name"),
    SIZE("File Size")
}

enum class SortMode(
    val title: String,
    val subtitle: String,
    val group: SortGroup
) {
    DATE_DESC("Date Modified (Newest)", "Newest first", SortGroup.DATE),
    DATE_ASC("Date Modified (Oldest)", "Oldest first", SortGroup.DATE),
    NAME_ASC("Name (A–Z)", "Alphabetical ascending", SortGroup.NAME),
    NAME_DESC("Name (Z–A)", "Alphabetical descending", SortGroup.NAME),
    SIZE_DESC("File Size (Largest)", "Largest files first", SortGroup.SIZE),
    SIZE_ASC("File Size (Smallest)", "Smallest files first", SortGroup.SIZE);

    companion object {
        fun sortEntities(
            items: List<PdfEntity>,
            mode: SortMode,
            isRecentTab: Boolean = false
        ): List<PdfEntity> {
            val comparator = when (mode) {
                DATE_DESC -> if (isRecentTab) {
                    compareByDescending<PdfEntity> { it.lastOpenedTimestamp }
                        .thenByDescending { it.lastModifiedTimestamp }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.path }
                } else {
                    compareByDescending<PdfEntity> { it.lastModifiedTimestamp }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.path }
                }
                DATE_ASC -> compareBy<PdfEntity> { it.lastModifiedTimestamp }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.path }
                NAME_ASC -> compareBy<PdfEntity> { it.title.lowercase() }
                    .thenByDescending { it.lastModifiedTimestamp }
                    .thenBy { it.path }
                NAME_DESC -> compareByDescending<PdfEntity> { it.title.lowercase() }
                    .thenByDescending { it.lastModifiedTimestamp }
                    .thenBy { it.path }
                SIZE_DESC -> compareByDescending<PdfEntity> { it.sizeBytes }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.path }
                SIZE_ASC -> compareBy<PdfEntity> { it.sizeBytes }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.path }
            }
            return items.sortedWith(comparator).distinctBy { it.path }
        }
    }
}

enum class ViewMode {
    LIST, GRID
}

enum class ReaderThemeMode(val title: String) {
    LIGHT("Day Mode"),
    SEPIA("Sepia Mode"),
    DARK("Night Mode")
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PdfRepository(application)
    private val sortPreferences = SortPreferences(application)

    val selectedTab = MutableStateFlow(FileListTab.ALL)
    val searchQuery = MutableStateFlow("")
    val sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val sortScrollTrigger = MutableStateFlow(0)
    val viewMode = MutableStateFlow(ViewMode.LIST)

    val isMultiSelectMode = MutableStateFlow(false)
    val selectedPaths = MutableStateFlow<Set<String>>(emptySet())

    val selectedPdf = MutableStateFlow<PdfEntity?>(null)
    val currentBookmarkStatus = MutableStateFlow(false)
    val currentBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    val isScanning = MutableStateFlow(false)
    val isProcessing = MutableStateFlow(false)
    val progressMessage = MutableStateFlow("")
    val pendingOpenDocument = MutableStateFlow<PdfEntity?>(null)
    private var lastScanCompletedAtMs: Long = 0L

    val deletedLocalDocsFlow = repository.deletedLocalDocsFlow

    val allPdfs = repository.allPdfs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentSearchQuery = MutableStateFlow("")
    val recentFilterMode = MutableStateFlow(RecentFilterMode.ALL)

    val allRecentFiles: StateFlow<List<PdfEntity>> = combine(
        repository.recentPdfs,
        allPdfs
    ) { recent, _ ->
        filterRecentEntities(
            items = recent,
            filterMode = RecentFilterMode.ALL,
            query = ""
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFiles: StateFlow<List<PdfEntity>> = combine(
        repository.recentPdfs,
        recentFilterMode,
        recentSearchQuery
    ) { recent, filterMode, query ->
        filterRecentEntities(
            items = recent,
            filterMode = filterMode,
            query = query
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered & sorted documents list without dummy items or ads
    val displayedItems: StateFlow<List<PdfEntity>> = combine(
        allPdfs,
        selectedTab,
        searchQuery,
        sortMode
    ) { pdfs, tab, query, sort ->
        var filtered = when (tab) {
            FileListTab.ALL -> pdfs
            FileListTab.PDF -> pdfs.filter { it.documentType == DocumentType.PDF }
            FileListTab.WORD -> pdfs.filter { it.documentType == DocumentType.WORD }
            FileListTab.EXCEL -> pdfs.filter { it.documentType == DocumentType.EXCEL }
            FileListTab.RECENT -> pdfs.filter { it.lastOpenedTimestamp > 0 }
            FileListTab.FAVORITES -> pdfs.filter { it.isFavorite }
        }

        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        }

        SortMode.sortEntities(
            items = filtered,
            mode = sort,
            isRecentTab = (tab == FileListTab.RECENT)
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            sortMode.value = sortPreferences.sortModeFlow.first()
        }
        viewModelScope.launch {
            isScanning.value = true
            try {
                repository.initialize()
                lastScanCompletedAtMs = System.currentTimeMillis()
            } finally {
                isScanning.value = false
            }
        }
    }

    fun setSortMode(mode: SortMode) {
        if (sortMode.value != mode) {
            sortMode.value = mode
            sortScrollTrigger.value += 1
        }
        viewModelScope.launch {
            sortPreferences.saveSortMode(mode)
        }
    }

    fun refreshDocuments(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (isScanning.value || (now - lastScanCompletedAtMs) < 15_000L)) {
            return
        }
        viewModelScope.launch {
            isScanning.value = true
            try {
                repository.autoFetchDeviceDocuments()
                lastScanCompletedAtMs = System.currentTimeMillis()
            } finally {
                isScanning.value = false
            }
        }
    }

    fun requestOpenDocument(entity: PdfEntity) {
        pendingOpenDocument.value = entity
    }

    fun clearPendingOpenDocument() {
        pendingOpenDocument.value = null
    }

    fun openPdf(entity: PdfEntity) {
        selectedPdf.value = entity
        PdfFastCacheManager.warmCacheAsync(getApplication(), entity)
        viewModelScope.launch {
            repository.recordOpened(entity.path)
            checkBookmarkStatus(entity.path, 1)
        }
    }

    fun deleteLocalCopyFromCloudSync(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            repository.removeDeletedCloudCopy(path)
            if (selectedPdf.value?.path == path) {
                selectedPdf.value = null
            }
            if (path in selectedPaths.value) {
                selectedPaths.value = selectedPaths.value - path
            }
        }
    }

    fun checkBookmarkStatus(path: String, page: Int) {
        viewModelScope.launch {
            currentBookmarkStatus.value = repository.isBookmarked(path, page)
        }
    }

    fun toggleCurrentBookmark(path: String, page: Int, title: String) {
        viewModelScope.launch {
            repository.toggleBookmark(path, page, title)
            checkBookmarkStatus(path, page)
        }
    }

    fun toggleFavorite(entity: PdfEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(entity.path, !entity.isFavorite)
        }
    }

    fun removeFromRecent(entity: PdfEntity) {
        viewModelScope.launch {
            repository.removeFromRecent(entity.path)
            Toast.makeText(getApplication(), "Removed from Recent Files", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            repository.clearRecentHistory()
            Toast.makeText(getApplication(), "Recent history cleared", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun filterRecentEntities(
            items: List<PdfEntity>,
            filterMode: RecentFilterMode = RecentFilterMode.ALL,
            query: String = ""
        ): List<PdfEntity> {
            return items
                .asSequence()
                .filter { it.lastOpenedTimestamp > 0L }
                .filter { entity ->
                    when (filterMode) {
                        RecentFilterMode.ALL -> true
                        RecentFilterMode.PDF -> entity.documentType == DocumentType.PDF
                        RecentFilterMode.WORD -> entity.documentType == DocumentType.WORD
                        RecentFilterMode.EXCEL -> entity.documentType == DocumentType.EXCEL
                    }
                }
                .filter { entity ->
                    query.isBlank() || entity.title.contains(query.trim(), ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<PdfEntity> { it.lastOpenedTimestamp }
                        .thenByDescending { it.lastModifiedTimestamp }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.path }
                )
                .distinctBy { it.path }
                .toList()
        }
    }

    fun setLockPin(entity: PdfEntity, pin: String) {
        viewModelScope.launch {
            repository.setLock(entity.path, pin.isNotBlank(), pin)
            Toast.makeText(getApplication(), if (pin.isNotBlank()) "PIN Locked" else "Unlocked", Toast.LENGTH_SHORT).show()
        }
    }

    fun lockOrUnlockPdfByUri(uri: Uri, pin: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isProcessing.value = true
            progressMessage.value = "Updating document PIN..."
            try {
                val entity = repository.importPdfFromUri(uri)
                if (entity != null) {
                    repository.setLock(entity.path, pin.isNotBlank(), pin)
                    Toast.makeText(
                        getApplication(),
                        if (pin.isNotBlank()) "PIN lock set for ${entity.title}" else "PIN removed for ${entity.title}",
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete(true)
                } else {
                    Toast.makeText(getApplication(), "Could not load selected document", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Security error: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete(false)
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun enterMultiSelectMode(initialPath: String? = null) {
        isMultiSelectMode.value = true
        if (initialPath != null) {
            selectedPaths.value = selectedPaths.value + initialPath
        }
    }

    fun exitMultiSelectMode() {
        isMultiSelectMode.value = false
        selectedPaths.value = emptySet()
    }

    fun toggleSelection(path: String) {
        val current = selectedPaths.value
        selectedPaths.value = if (path in current) current - path else current + path
    }

    fun toggleSelectAll(visiblePaths: List<String>) {
        if (visiblePaths.isEmpty()) return
        val current = selectedPaths.value
        val visibleSet = visiblePaths.toSet()
        val allVisibleSelected = visibleSet.all { it in current }
        selectedPaths.value = if (allVisibleSelected) {
            current - visibleSet
        } else {
            current + visibleSet
        }
    }

    fun deletePdf(entity: PdfEntity) {
        viewModelScope.launch {
            repository.deletePdf(entity.path)
            if (selectedPdf.value?.path == entity.path) {
                selectedPdf.value = null
            }
            if (entity.path in selectedPaths.value) {
                selectedPaths.value = selectedPaths.value - entity.path
            }
            Toast.makeText(getApplication(), "Deleted: ${entity.title}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSelected() {
        val pathsToDelete = selectedPaths.value.toList()
        if (pathsToDelete.isEmpty()) {
            exitMultiSelectMode()
            return
        }
        viewModelScope.launch {
            repository.deletePdfs(pathsToDelete)
            if (selectedPdf.value?.path in pathsToDelete) {
                selectedPdf.value = null
            }
            selectedPaths.value = emptySet()
            isMultiSelectMode.value = false
            val msg = if (pathsToDelete.size == 1) {
                "1 file deleted"
            } else {
                "${pathsToDelete.size} files deleted"
            }
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun renamePdf(entity: PdfEntity, newName: String) {
        viewModelScope.launch {
            repository.renamePdf(entity.path, newName)
            Toast.makeText(getApplication(), "Renamed successfully", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Imports PDF without showing any toast message and opens it immediately!
     */
    fun importPdfAndOpen(uri: Uri, onOpened: (PdfEntity) -> Unit) {
        viewModelScope.launch {
            val entity = repository.importPdfFromUri(uri)
            if (entity != null) {
                // Open immediately without showing any bottom "imported" toast
                openPdf(entity)
                onOpened(entity)
            }
        }
    }

    fun executePdfToJpg(
        context: Context,
        pdfFile: File,
        selectedPages: List<Int>? = null,
        qualityPercent: Int = 90,
        onComplete: (PdfToolsHelper.PdfToJpgResult?) -> Unit
    ) {
        viewModelScope.launch {
            isProcessing.value = true
            progressMessage.value = "Converting PDF pages to JPG images..."
            val result = PdfToolsHelper.convertPdfToJpg(context, pdfFile, selectedPages, qualityPercent)
            isProcessing.value = false
            if (result != null) {
                Toast.makeText(
                    context,
                    "Saved ${result.totalPagesConverted} JPG images in ${result.outputDirectory.name}",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "Conversion failed", Toast.LENGTH_SHORT).show()
            }
            onComplete(result)
        }
    }

    fun executeCompressPdf(
        sourceFile: File,
        level: PdfToolsHelper.CompressionLevel,
        onComplete: (PdfToolsHelper.CompressionResult?) -> Unit
    ) {
        viewModelScope.launch {
            isProcessing.value = true
            progressMessage.value = "Compressing PDF document..."
            val outDir = File(getApplication<Application>().filesDir, "NarayaniDocuments").apply { mkdirs() }
            val outFile = File(outDir, "${sourceFile.nameWithoutExtension}_compressed.pdf")

            val result = PdfToolsHelper.compressPdf(sourceFile, level, outFile)
            isProcessing.value = false
            if (result != null) {
                repository.addCreatedPdf(result.outputFile, "Compressed")
                Toast.makeText(
                    getApplication(),
                    "Reduced by ${result.percentageSaved}%! Saved to Compressed folder.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(getApplication(), "Compression failed", Toast.LENGTH_SHORT).show()
            }
            onComplete(result)
        }
    }

    fun executeImagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        title: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            isProcessing.value = true
            progressMessage.value = "Creating PDF from images..."
            val outDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
            val cleanTitle = if (title.endsWith(".pdf", true)) title else "$title.pdf"
            val outFile = File(outDir, cleanTitle)

            val success = PdfToolsHelper.convertImagesToPdf(context, imageUris, outFile)
            isProcessing.value = false
            if (success) {
                repository.addCreatedPdf(outFile, "Created from Images")
                Toast.makeText(context, "PDF Created: $cleanTitle", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
            }
            onComplete(success)
        }
    }

    fun executeMergePdfs(
        context: Context,
        pdfFiles: List<File>,
        title: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            isProcessing.value = true
            progressMessage.value = "Merging PDF files..."
            val outDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
            val cleanTitle = if (title.endsWith(".pdf", true)) title else "$title.pdf"
            val outFile = File(outDir, cleanTitle)

            val success = PdfToolsHelper.mergePdfs(pdfFiles, outFile)
            isProcessing.value = false
            if (success) {
                repository.addCreatedPdf(outFile, "Merged")
                Toast.makeText(context, "Merged PDF Created: $cleanTitle", Toast.LENGTH_SHORT).show()
            }
            onComplete(success)
        }
    }

    fun executeSplitPdf(
        context: Context,
        sourceFile: File,
        startPage: Int,
        endPage: Int,
        title: String,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            isProcessing.value = true
            progressMessage.value = "Extracting pages..."
            val outDir = File(context.filesDir, "NarayaniDocuments").apply { mkdirs() }
            val cleanTitle = if (title.endsWith(".pdf", true)) title else "$title.pdf"
            val outFile = File(outDir, cleanTitle)

            val success = PdfToolsHelper.splitPdf(sourceFile, startPage, endPage, outFile)
            isProcessing.value = false
            if (success) {
                repository.addCreatedPdf(outFile, "Split Pages")
                Toast.makeText(context, "Pages extracted to $cleanTitle", Toast.LENGTH_SHORT).show()
            }
            onComplete(success)
        }
    }
}
