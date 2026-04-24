package com.tz.listenbook.data.repository

import com.tz.listenbook.data.local.database.BookDao
import com.tz.listenbook.data.local.database.ChapterDao
import com.tz.listenbook.data.local.entity.BookEntity
import com.tz.listenbook.data.local.entity.ChapterEntity
import com.tz.listenbook.data.mapper.toDomain
import com.tz.listenbook.data.mapper.toEntity
import com.tz.listenbook.data.storage.ContentStorage
import com.tz.listenbook.domain.model.Book
import com.tz.listenbook.domain.repository.BookRepository
import com.tz.listenbook.domain.repository.ChapterRepository
import com.tz.listenbook.parser.ParsedBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val contentStorage: ContentStorage
) : BookRepository, ChapterRepository {

    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getBookById(id: Long): Book? {
        return bookDao.getBookById(id)?.toDomain()
    }

    override fun getBookByIdFlow(id: Long): Flow<Book?> {
        return bookDao.getBookByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun importBook(
        parsedBook: ParsedBook,
        filePath: String,
        fileType: String,
        fileSize: Long
    ): Long {
        val now = System.currentTimeMillis()
        val book = BookEntity(
            title = parsedBook.title,
            author = parsedBook.author,
            filePath = filePath,
            fileType = fileType,
            coverPath = parsedBook.coverPath,
            totalChapters = parsedBook.chapters.size,
            totalDuration = null,
            fileSize = fileSize,
            addedAt = now,
            lastPlayedAt = null
        )

        val bookId = bookDao.insert(book)

        val chapters = parsedBook.chapters.map { chapterContent ->
            val contentPath = contentStorage.saveChapterContent(bookId, chapterContent.index, chapterContent.content)
            ChapterEntity(
                bookId = bookId,
                chapterIndex = chapterContent.index,
                title = chapterContent.title,
                contentPath = contentPath,
                wordCount = chapterContent.content.length
            )
        }

        chapterDao.insertAll(chapters)

        return bookId
    }

    override suspend fun deleteBook(id: Long) {
        contentStorage.deleteBookContent(id)
        bookDao.deleteById(id)
    }

    override suspend fun updateLastPlayed(id: Long) {
        bookDao.updateLastPlayed(id, System.currentTimeMillis())
    }

    override fun getChaptersByBook(bookId: Long): Flow<List<com.tz.listenbook.domain.model.Chapter>> {
        return chapterDao.getChaptersByBook(bookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getChapterById(id: Long): com.tz.listenbook.domain.model.Chapter? {
        return chapterDao.getChapterById(id)?.toDomain()
    }

    override suspend fun getChaptersByBookList(bookId: Long): List<com.tz.listenbook.domain.model.Chapter> {
        return chapterDao.getChaptersByBookList(bookId).map { it.toDomain() }
    }

    override suspend fun getChapterWithContent(bookId: Long, chapterIndex: Int): com.tz.listenbook.domain.model.Chapter? {
        val entity = chapterDao.getChapterByBookAndIndex(bookId, chapterIndex) ?: return null
        val content = contentStorage.loadChapterContent(entity.contentPath)
        return entity.toDomain().copy(content = content)
    }
}