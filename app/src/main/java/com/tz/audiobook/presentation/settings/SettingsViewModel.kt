package com.tz.audiobook.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tz.audiobook.domain.repository.BookRepository
import com.tz.audiobook.domain.repository.ChapterRepository
import com.tz.audiobook.service.AudioPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookCacheInfo(
    val bookId: Long,
    val bookTitle: String,
    val cacheSize: Long
)

data class SettingsUiState(
    val totalCacheSize: Long = 0L,
    val bookCaches: List<BookCacheInfo> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val audioPipeline: AudioPipeline,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCacheInfo()
    }

    fun loadCacheInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Get cache size per chapter
                val chapterCache = audioPipeline.getCacheByChapter()

                // Get all chapter IDs that have cache
                val chapterIds = chapterCache.keys

                // Map chapter -> book
                val chapterToBook = mutableMapOf<Long, Long>()
                chapterIds.forEach { chapterId ->
                    val chapter = chapterRepository.getChapterById(chapterId)
                    if (chapter != null) {
                        chapterToBook[chapterId] = chapter.bookId
                    }
                }

                // Group cache by book
                val bookCacheMap = mutableMapOf<Long, Long>()
                chapterCache.forEach { (chapterId, size) ->
                    val bookId = chapterToBook[chapterId] ?: return@forEach
                    bookCacheMap[bookId] = (bookCacheMap[bookId] ?: 0L) + size
                }

                // Get book titles
                val bookCaches = bookCacheMap.map { (bookId, size) ->
                    val book = bookRepository.getBookById(bookId)
                    BookCacheInfo(
                        bookId = bookId,
                        bookTitle = book?.title ?: "未知书籍",
                        cacheSize = size
                    )
                }.sortedByDescending { it.cacheSize }

                val totalSize = bookCaches.sumOf { it.cacheSize }

                _uiState.value = SettingsUiState(
                    totalCacheSize = totalSize,
                    bookCaches = bookCaches,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(isLoading = false)
            }
        }
    }

    fun clearAllCache() {
        audioPipeline.clearCache()
        loadCacheInfo()
    }

    fun clearBookCache(bookId: Long) {
        viewModelScope.launch {
            val chapters = chapterRepository.getChaptersByBookList(bookId)
            val chapterIds = chapters.map { it.id }.toSet()
            audioPipeline.clearCacheForChapters(chapterIds)
            loadCacheInfo()
        }
    }
}
