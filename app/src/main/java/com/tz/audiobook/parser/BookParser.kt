package com.tz.audiobook.parser

import android.net.Uri

interface BookParser {
    suspend fun parse(uri: Uri): ParsedBook
}

data class ParsedBook(
    val title: String,
    val author: String?,
    val chapters: List<ChapterContent>,
    val coverPath: String?
)

data class ChapterContent(
    val index: Int,
    val title: String,
    val content: String
)