package com.tz.listenbook.presentation.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tz.listenbook.domain.model.Book
import com.tz.listenbook.domain.model.ReadingProgress
import com.tz.listenbook.domain.repository.BookRepository
import com.tz.listenbook.domain.repository.ReadingProgressRepository
import com.tz.listenbook.parser.EpubParser
import com.tz.listenbook.parser.ParsedBook
import com.tz.listenbook.parser.TxtParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookWithProgress(
    val book: Book,
    val progress: ReadingProgress?
)

data class BookShelfUiState(
    val books: List<BookWithProgress> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val importProgress: Float = 0f,
    val isImporting: Boolean = false
)

@HiltViewModel
class BookShelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val txtParser: TxtParser,
    private val epubParser: EpubParser
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookShelfUiState())
    val uiState: StateFlow<BookShelfUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            combine(
                bookRepository.getAllBooks(),
                readingProgressRepository.getAllProgress()
            ) { books, progress ->
                books.map { book ->
                    BookWithProgress(
                        book = book,
                        progress = progress.find { it.bookId == book.id }
                    )
                }.sortedByDescending { it.book.lastPlayedAt ?: it.book.addedAt }
            }.collect { books ->
                _uiState.value = _uiState.value.copy(
                    books = books,
                    isLoading = false
                )
            }
        }
    }

    fun importBook(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                importProgress = 0f
            )

            try {
                val fileType = when (fileName.substringAfterLast(".").lowercase()) {
                    "txt" -> "TXT"
                    "epub" -> "EPUB"
                    else -> throw IllegalArgumentException("不支持的文件格式: $fileName")
                }

                _uiState.value = _uiState.value.copy(importProgress = 0.3f)

                val parsedBook: ParsedBook = when (fileType) {
                    "TXT" -> txtParser.parse(uri)
                    "EPUB" -> epubParser.parse(uri)
                    else -> throw IllegalArgumentException("不支持的文件格式")
                }

                _uiState.value = _uiState.value.copy(importProgress = 0.7f)

                bookRepository.importBook(
                    parsedBook = parsedBook,
                    filePath = uri.toString(),
                    fileType = fileType,
                    fileSize = 0L
                )

                _uiState.value = _uiState.value.copy(
                    importProgress = 1f,
                    isImporting = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = "导入失败: ${e.message}"
                )
            }
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}