package com.tz.listenbook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId", unique = true)]
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Long,
    val currentChapterIndex: Int,
    val currentPosition: Long,
    val playbackSpeed: Float = 1.0f,
    val voiceName: String = "zh-CN-XiaoxiaoNeural",
    val lastUpdated: Long
)