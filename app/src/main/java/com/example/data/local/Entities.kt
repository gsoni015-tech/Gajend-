package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DocumentType {
    PDF, WORD, EXCEL, OTHER
}

@Entity(tableName = "pdf_files")
data class PdfEntity(
    @PrimaryKey
    val path: String,
    val title: String,
    val uriString: String,
    val sizeBytes: Long,
    val lastModifiedTimestamp: Long,
    val lastOpenedTimestamp: Long = 0L,
    val isFavorite: Boolean = false,
    val pageCount: Int = 1,
    val isLocked: Boolean = false,
    val password: String = "",
    val folderName: String = "Documents"
) {
    val documentType: DocumentType
        get() = when {
            path.endsWith(".pdf", ignoreCase = true) || title.endsWith(".pdf", ignoreCase = true) -> DocumentType.PDF
            path.endsWith(".docx", ignoreCase = true) || path.endsWith(".doc", ignoreCase = true) ||
                    title.endsWith(".docx", ignoreCase = true) || title.endsWith(".doc", ignoreCase = true) -> DocumentType.WORD
            path.endsWith(".xlsx", ignoreCase = true) || path.endsWith(".xls", ignoreCase = true) ||
                    path.endsWith(".csv", ignoreCase = true) || title.endsWith(".xlsx", ignoreCase = true) ||
                    title.endsWith(".xls", ignoreCase = true) || title.endsWith(".csv", ignoreCase = true) -> DocumentType.EXCEL
            else -> DocumentType.OTHER
        }
}

@Entity(tableName = "pdf_bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pdfPath: String,
    val pageNumber: Int,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
