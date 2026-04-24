package com.tz.listenbook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String?,
    val filePath: String,
    val fileType: String,
    val coverPath: String?,
    val totalChapters: Int,
    val totalDuration: Long?,
    val fileSize: Long,
    val addedAt: Long,
    val lastPlayedAt: Long?
)