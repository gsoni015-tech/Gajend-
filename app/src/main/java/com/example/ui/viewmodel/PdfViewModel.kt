package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookmarkEntity
import com.example.data.local.DocumentType
import com.example.data.local.PdfEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.PdfToolsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

enum class SortMode(val title: String) {
    DATE_DESC("Date (Newest)"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Size (Largest)")
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

    val selectedTab = MutableStateFlow(FileListTab.ALL)
    val searchQuery = MutableStateFlow("")
    val sortMode = MutableStateFlow(SortMode.DATE_DESC)
    val viewMode = MutableStateFlow(ViewMode.LIST)

    val isMultiSelectMode = MutableStateFlow(false)
    val selectedPaths = MutableStateFlow<Set<String>>(emptySet())

    val selectedPdf = MutableStateFlow<PdfEntity?>(null)
    val currentBookmarkStatus = MutableStateFlow(false)
    val currentBookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    val isScanning = MutableStateFlow(false)
    val isProcessing = MutableStateFlow(false)
    val progressMessage = MutableStateFlow("")

    val allPdfs = repository.allPdfs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Filtered documents list in pure English without any dummy items or ads
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
            FileListTab.RECENT -> pdfs.filter { it.lastOpenedTimestamp > 0 }.sortedByDescending { it.lastOpenedTimestamp }
            FileListTab.FAVORITES -> pdfs.filter { it.isFavorite }
        }

        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(query, ignoreCase = true) }
        }

        when (sort) {
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.lastModifiedTimestamp }
            SortMode.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortMode.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            isScanning.value = true
            try {
                repository.initialize()
            } finally {
                isScanning.value = false
            }
        }
    }

    fun refreshDocuments() {
        viewModelScope.launch {
            isScanning.value = true
            try {
                repository.autoFetchDeviceDocuments()
            } finally {
                isScanning.value = false
            }
        }
    }

    fun openPdf(entity: PdfEntity) {
        selectedPdf.value = entity
        viewModelScope.launch {
            repository.recordOpened(entity.path)
            checkBookmarkStatus(entity.path, 1)
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

    fun deletePdf(entity: PdfEntity) {
        viewModelScope.launch {
            repository.deletePdf(entity.path)
            Toast.makeText(getApplication(), "Deleted: ${entity.title}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteSelected() {
        viewModelScope.launch {
            for (p in selectedPaths.value) {
                repository.deletePdf(p)
            }
            selectedPaths.value = emptySet()
            isMultiSelectMode.value = false
            Toast.makeText(getApplication(), "Selected files deleted", Toast.LENGTH_SHORT).show()
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
