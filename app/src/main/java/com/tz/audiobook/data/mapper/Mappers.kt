package com.tz.audiobook.data.mapper

import com.tz.audiobook.data.local.entity.BookEntity
import com.tz.audiobook.data.local.entity.ChapterEntity
import com.tz.audiobook.data.local.entity.ReadingProgressEntity
import com.tz.audiobook.domain.model.Book
import com.tz.audiobook.domain.model.Chapter
import com.tz.audiobook.domain.model.ReadingProgress

fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    filePath = filePath,
    fileType = fileType,
    coverPath = coverPath,
    totalChapters = totalChapters,
    totalDuration = totalDuration,
    fileSize = fileSize,
    addedAt = addedAt,
    lastPlayedAt = lastPlayedAt
)

fun Book.toEntity() = BookEntity(
    id = id,
    title = title,
    author = author,
    filePath = filePath,
    fileType = fileType,
    coverPath = coverPath,
    totalChapters = totalChapters,
    totalDuration = totalDuration,
    fileSize = fileSize,
    addedAt = addedAt,
    lastPlayedAt = lastPlayedAt
)

fun ChapterEntity.toDomain() = Chapter(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    title = title,
    contentPath = contentPath,
    wordCount = wordCount
)

fun Chapter.toEntity() = ChapterEntity(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    title = title,
    contentPath = contentPath,
    wordCount = wordCount
)

fun ReadingProgressEntity.toDomain() = ReadingProgress(
    bookId = bookId,
    currentChapterIndex = currentChapterIndex,
    currentPosition = currentPosition,
    playbackSpeed = playbackSpeed,
    voiceName = voiceName,
    lastUpdated = lastUpdated
)

fun ReadingProgress.toEntity() = ReadingProgressEntity(
    bookId = bookId,
    currentChapterIndex = currentChapterIndex,
    currentPosition = currentPosition,
    playbackSpeed = playbackSpeed,
    voiceName = voiceName,
    lastUpdated = lastUpdated
)