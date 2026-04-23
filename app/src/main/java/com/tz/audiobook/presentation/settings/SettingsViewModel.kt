package com.tz.audiobook.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tz.audiobook.domain.repository.BookRepository
import com.tz.audiobook.domain.repository.ChapterRepository
import com.tz.audiobook.domain.repository.ReadingProgressRepository
import com.tz.audiobook.service.AudioPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class BookCacheInfo(
    val bookId: Long,
    val bookTitle: String,
    val cacheSize: Long
)

data class SettingsUiState(
    val totalCacheSize: Long = 0L,
    val bookCaches: List<BookCacheInfo> = emptyList(),
    val isLoading: Boolean = true,
    val exportMessage: String? = null,
    val importMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val audioPipeline: AudioPipeline,
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val readingProgressRepository: ReadingProgressRepository
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

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val progressList = readingProgressRepository.getAllProgressSnapshot()
                val books = bookRepository.getAllBooks().first()

                val json = JSONObject().apply {
                    put("version", 1)
                    put("exportTime", System.currentTimeMillis())

                    // Export progress
                    val progressArray = JSONArray()
                    progressList.forEach { progress ->
                        progressArray.put(JSONObject().apply {
                            put("bookId", progress.bookId)
                            put("currentChapterIndex", progress.currentChapterIndex)
                            put("currentPosition", progress.currentPosition)
                            put("playbackSpeed", progress.playbackSpeed.toDouble())
                            put("voiceName", progress.voiceName)
                            put("lastUpdated", progress.lastUpdated)
                        })
                    }
                    put("progress", progressArray)

                    // Export settings
                    put("settings", JSONObject().apply {
                        put("darkMode", SettingsPrefs.getDarkMode(context))
                        put("fontSize", SettingsPrefs.getFontSize(context))
                        put("lineHeight", SettingsPrefs.getLineHeight(context))
                        put("backgroundPlay", SettingsPrefs.isBackgroundPlayEnabled(context))
                    })

                    // Export favorite voices
                    val voicesArray = JSONArray()
                    SettingsPrefs.getFavoriteVoices(context).forEach { voicesArray.put(it) }
                    put("favoriteVoices", voicesArray)

                    // Export bookmarks per book
                    val bookmarksObj = JSONObject()
                    books.forEach { book ->
                        val bookmarks = SettingsPrefs.getBookmarks(context, book.id)
                        if (bookmarks.isNotEmpty()) {
                            val arr = JSONArray()
                            bookmarks.forEach { arr.put(it) }
                            bookmarksObj.put(book.id.toString(), arr)
                        }
                    }
                    put("bookmarks", bookmarksObj)
                }

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toString(2).toByteArray(Charsets.UTF_8))
                }

                _uiState.value = _uiState.value.copy(exportMessage = "导出成功")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportMessage = "导出失败: ${e.message}")
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.use { input ->
                    String(input.readBytes(), Charsets.UTF_8)
                } ?: throw Exception("无法读取文件")

                val json = JSONObject(jsonStr)

                // Import settings
                json.optJSONObject("settings")?.let { settings ->
                    settings.optString("darkMode").takeIf { it.isNotEmpty() }?.let {
                        SettingsPrefs.setDarkMode(context, it)
                    }
                    settings.optInt("fontSize", -1).takeIf { it >= 0 }?.let {
                        SettingsPrefs.setFontSize(context, it)
                    }
                    settings.optInt("lineHeight", -1).takeIf { it >= 0 }?.let {
                        SettingsPrefs.setLineHeight(context, it)
                    }
                    settings.optBoolean("backgroundPlay", false).let {
                        SettingsPrefs.setBackgroundPlayEnabled(context, it)
                    }
                }

                // Import favorite voices
                json.optJSONArray("favoriteVoices")?.let { arr ->
                    val voices = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        arr.getString(i)?.let { voices.add(it) }
                    }
                    SettingsPrefs.setFavoriteVoices(context, voices)
                }

                // Import progress
                json.optJSONArray("progress")?.let { arr ->
                    val progressList = mutableListOf<com.tz.audiobook.domain.model.ReadingProgress>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        progressList.add(com.tz.audiobook.domain.model.ReadingProgress(
                            bookId = obj.getLong("bookId"),
                            currentChapterIndex = obj.getInt("currentChapterIndex"),
                            currentPosition = obj.getLong("currentPosition"),
                            playbackSpeed = obj.getDouble("playbackSpeed").toFloat(),
                            voiceName = obj.getString("voiceName"),
                            lastUpdated = obj.getLong("lastUpdated")
                        ))
                    }
                    readingProgressRepository.saveAllProgress(progressList)
                }

                // Import bookmarks
                json.optJSONObject("bookmarks")?.let { bookmarksObj ->
                    val keys = bookmarksObj.keys()
                    while (keys.hasNext()) {
                        val bookIdStr = keys.next()
                        val bookId = bookIdStr.toLongOrNull() ?: continue
                        val arr = bookmarksObj.getJSONArray(bookIdStr)
                        val bookmarks = mutableSetOf<String>()
                        for (i in 0 until arr.length()) {
                            arr.getString(i)?.let { bookmarks.add(it) }
                        }
                        SettingsPrefs.setBookmarks(context, bookId, bookmarks)
                    }
                }

                _uiState.value = _uiState.value.copy(importMessage = "导入成功")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(importMessage = "导入失败: ${e.message}")
            }
        }
    }

    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(exportMessage = null)
    }

    fun clearImportMessage() {
        _uiState.value = _uiState.value.copy(importMessage = null)
    }
}
