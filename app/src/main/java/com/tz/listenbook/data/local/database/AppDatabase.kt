package com.tz.listenbook.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tz.listenbook.data.local.entity.BookEntity
import com.tz.listenbook.data.local.entity.ChapterEntity
import com.tz.listenbook.data.local.entity.ReadingProgressEntity

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