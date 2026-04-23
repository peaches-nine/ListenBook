package com.tz.audiobook.domain.repository

import com.tz.audiobook.domain.model.Book
import com.tz.audiobook.domain.model.Chapter
import com.tz.audiobook.parser.ParsedBook
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getBookById(id: Long): Book?
    fun getBookByIdFlow(id: Long): Flow<Book?>
    suspend fun importBook(parsedBook: ParsedBook, filePath: String, fileType: String, fileSize: Long): Long
    suspend fun deleteBook(id: Long)
    suspend fun updateLastPlayed(id: Long)
}

interface ChapterRepository {
    fun getChaptersByBook(bookId: Long): Flow<List<Chapter>>
    suspend fun getChapterById(id: Long): Chapter?
    suspend fun getChaptersByBookList(bookId: Long): List<Chapter>
    suspend fun getChapterWithContent(bookId: Long, chapterIndex: Int): Chapter?
}

interface ReadingProgressRepository {
    fun getProgress(bookId: Long): Flow<com.tz.audiobook.domain.model.ReadingProgress?>
    fun getAllProgress(): Flow<List<com.tz.audiobook.domain.model.ReadingProgress>>
    suspend fun saveProgress(progress: com.tz.audiobook.domain.model.ReadingProgress)
    suspend fun getAllProgressSnapshot(): List<com.tz.audiobook.domain.model.ReadingProgress>
    suspend fun saveAllProgress(progressList: List<com.tz.audiobook.domain.model.ReadingProgress>)
}