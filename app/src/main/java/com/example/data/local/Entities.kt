package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DocumentType(val label: String, val badge: String) {
    PDF("PDF", "PDF"),
    EXCEL("Excel", "XLS"),
    WORD("Word", "DOC"),
    MAIN_FILE("Main Text", "TXT")
}

@Entity(tableName = "pdf_files")
data class PdfEntity(
    @PrimaryKey
    val path: String,
    val title: String,
    val uriString: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastOpenedTimestamp: Long = 0L,
    val lastModifiedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val password: String = "",
    val folderName: String = "Documents"
)

val PdfEntity.documentType: DocumentType
    get() = when {
        path.endsWith(".xlsx", ignoreCase = true) ||
        path.endsWith(".xls", ignoreCase = true) ||
        path.endsWith(".csv", ignoreCase = true) ||
        path.endsWith(".tsv", ignoreCase = true) -> DocumentType.EXCEL

        path.endsWith(".docx", ignoreCase = true) ||
        path.endsWith(".doc", ignoreCase = true) ||
        path.endsWith(".rtf", ignoreCase = true) -> DocumentType.WORD

        path.endsWith(".txt", ignoreCase = true) ||
        path.endsWith(".md", ignoreCase = true) ||
        path.endsWith(".json", ignoreCase = true) ||
        path.endsWith(".log", ignoreCase = true) -> DocumentType.MAIN_FILE

        else -> DocumentType.PDF
    }

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pdfPath: String,
    val pageNumber: Int,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
