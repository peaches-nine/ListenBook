package com.tz.audiobook.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tz.audiobook.data.local.entity.BookEntity
import com.tz.audiobook.data.local.entity.ChapterEntity
import com.tz.audiobook.data.local.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
}