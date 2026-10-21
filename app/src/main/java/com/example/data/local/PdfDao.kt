package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_files ORDER BY lastModifiedTimestamp DESC")
    fun getAllPdfs(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_files WHERE isFavorite = 1 ORDER BY lastModifiedTimestamp DESC")
    fun getFavoritePdfs(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_files WHERE lastOpenedTimestamp > 0 ORDER BY lastOpenedTimestamp DESC")
    fun getRecentPdfs(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdf_files WHERE path = :path LIMIT 1")
    suspend fun getPdfByPath(path: String): PdfEntity?

    @Query("SELECT path FROM pdf_files")
    suspend fun getAllPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfs(pdfs: List<PdfEntity>)

    @Update
    suspend fun updatePdf(pdf: PdfEntity)

    @Query("UPDATE pdf_files SET isFavorite = :isFavorite WHERE path = :path")
    suspend fun updateFavorite(path: String, isFavorite: Boolean)

    @Query("UPDATE pdf_files SET lastOpenedTimestamp = :timestamp WHERE path = :path")
    suspend fun updateLastOpened(path: String, timestamp: Long)

    @Query("UPDATE pdf_files SET isLocked = :isLocked, password = :password WHERE path = :path")
    suspend fun updateLock(path: String, isLocked: Boolean, password: String)

    @Query("UPDATE pdf_files SET path = :newPath, title = :newTitle, uriString = :newUri WHERE path = :oldPath")
    suspend fun renamePdf(oldPath: String, newPath: String, newTitle: String, newUri: String)

    @Query("DELETE FROM pdf_files WHERE path = :path")
    suspend fun deletePdf(path: String)

    // Bookmarks
    @Query("SELECT * FROM pdf_bookmarks WHERE pdfPath = :pdfPath ORDER BY pageNumber ASC")
    fun getBookmarksForPdf(pdfPath: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM pdf_bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM pdf_bookmarks WHERE pdfPath = :pdfPath AND pageNumber = :page")
    suspend fun deleteBookmark(pdfPath: String, page: Int)

    @Query("SELECT COUNT(*) > 0 FROM pdf_bookmarks WHERE pdfPath = :pdfPath AND pageNumber = :page")
    suspend fun isBookmarked(pdfPath: String, page: Int): Boolean
}
