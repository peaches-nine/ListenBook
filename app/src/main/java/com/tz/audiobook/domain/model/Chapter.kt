package com.tz.audiobook.domain.model

data class Chapter(
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int,
    val title: String,
    val contentPath: String,
    val wordCount: Int,
    val content: String = ""
)