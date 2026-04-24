package com.tz.listenbook.domain.model

data class Book(
    val id: Long = 0,
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