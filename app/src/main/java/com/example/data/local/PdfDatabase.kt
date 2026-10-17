package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfEntity::class, BookmarkEntity::class], version = 1, exportSchema = false)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao

    companion object {
        @Volatile
        private var INSTANCE: PdfDatabase? = null

        fun getInstance(context: Context): PdfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PdfDatabase::class.java,
                    "pdf_master_db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
