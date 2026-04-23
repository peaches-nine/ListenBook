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

data class BookCacheInfo(val bookId: Long, val bookTitle: String, val cacheSize: Long)

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

    init { loadCacheInfo() }

    fun loadCacheInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val chapterCache = audioPipeline.getCacheByChapter()
                val chapterIds = chapterCache.keys
                val chapterToBook = mutableMapOf<Long, Long>()
                chapterIds.forEach { cid -> chapterRepository.getChapterById(cid)?.let { chapterToBook[cid] = it.bookId } }
                val bookCacheMap = mutableMapOf<Long, Long>()
                chapterCache.forEach { (cid, size) -> chapterToBook[cid]?.let { bookCacheMap[it] = (bookCacheMap[it] ?: 0L) + size } }
                val bookCaches = bookCacheMap.map { (bid, size) ->
                    val book = bookRepository.getBookById(bid)
                    BookCacheInfo(bid, book?.title ?: "未知书籍", size)
                }.sortedByDescending { it.cacheSize }
                _uiState.value = SettingsUiState(totalCacheSize = bookCaches.sumOf { it.cacheSize }, bookCaches = bookCaches, isLoading = false)
            } catch (_: Exception) { _uiState.value = SettingsUiState(isLoading = false) }
        }
    }

    fun clearAllCache() { audioPipeline.clearCache(); loadCacheInfo() }

    fun clearBookCache(bookId: Long) {
        viewModelScope.launch {
            val chapters = chapterRepository.getChaptersByBookList(bookId)
            audioPipeline.clearCacheForChapters(chapters.map { it.id }.toSet())
            loadCacheInfo()
        }
    }

    fun exportData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val progressList = readingProgressRepository.getAllProgressSnapshot()
                val books = bookRepository.getAllBooks().first()
                val json = JSONObject().apply {
                    put("version", 2)
                    put("exportTime", System.currentTimeMillis())
                    val progressArray = JSONArray()
                    progressList.forEach { p ->
                        progressArray.put(JSONObject().apply {
                            put("bookId", p.bookId); put("currentChapterIndex", p.currentChapterIndex)
                            put("currentPosition", p.currentPosition); put("playbackSpeed", p.playbackSpeed.toDouble())
                            put("voiceName", p.voiceName); put("lastUpdated", p.lastUpdated)
                        })
                    }
                    put("progress", progressArray)
                    put("settings", JSONObject().apply {
                        put("darkMode", SettingsPrefs.getDarkMode(context))
                        put("fontSizeSp", SettingsPrefs.getFontSizeSp(context))
                        put("lineHeightMult", SettingsPrefs.getLineHeightMult(context))
                        put("backgroundPlay", SettingsPrefs.isBackgroundPlayEnabled(context))
                    })
                    val voicesArray = JSONArray()
                    SettingsPrefs.getFavoriteVoices(context).forEach { voicesArray.put(it) }
                    put("favoriteVoices", voicesArray)
                    val bookmarksObj = JSONObject()
                    books.forEach { b ->
                        val bm = SettingsPrefs.getBookmarks(context, b.id)
                        if (bm.isNotEmpty()) { val arr = JSONArray(); bm.forEach { arr.put(it) }; bookmarksObj.put(b.id.toString(), arr) }
                    }
                    put("bookmarks", bookmarksObj)
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray(Charsets.UTF_8)) }
                _uiState.value = _uiState.value.copy(exportMessage = "导出成功")
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(exportMessage = "导出失败: ${e.message}") }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val jsonStr = context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: throw Exception("无法读取文件")
                val json = JSONObject(jsonStr)
                json.optJSONObject("settings")?.let { s ->
                    s.optString("darkMode").takeIf { it.isNotEmpty() }?.let { SettingsPrefs.setDarkMode(context, it) }
                    s.optInt("fontSizeSp", -1).takeIf { it >= 0 }?.let { SettingsPrefs.setFontSizeSp(context, it) }
                    s.optDouble("lineHeightMult", -1.0).takeIf { it >= 0 }?.let { SettingsPrefs.setLineHeightMult(context, it.toFloat()) }
                    s.optBoolean("backgroundPlay", false).let { SettingsPrefs.setBackgroundPlayEnabled(context, it) }
                }
                json.optJSONArray("favoriteVoices")?.let { arr ->
                    val voices = mutableSetOf<String>()
                    for (i in 0 until arr.length()) arr.getString(i)?.let { voices.add(it) }
                    SettingsPrefs.setFavoriteVoices(context, voices)
                }
                json.optJSONArray("progress")?.let { arr ->
                    val list = mutableListOf<com.tz.audiobook.domain.model.ReadingProgress>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(com.tz.audiobook.domain.model.ReadingProgress(
                            bookId = o.getLong("bookId"), currentChapterIndex = o.getInt("currentChapterIndex"),
                            currentPosition = o.getLong("currentPosition"), playbackSpeed = o.getDouble("playbackSpeed").toFloat(),
                            voiceName = o.getString("voiceName"), lastUpdated = o.getLong("lastUpdated")
                        ))
                    }
                    readingProgressRepository.saveAllProgress(list)
                }
                json.optJSONObject("bookmarks")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val bidStr = keys.next()
                        val bid = bidStr.toLongOrNull() ?: continue
                        val arr = obj.getJSONArray(bidStr)
                        val bm = mutableSetOf<String>()
                        for (i in 0 until arr.length()) arr.getString(i)?.let { bm.add(it) }
                        SettingsPrefs.setBookmarks(context, bid, bm)
                    }
                }
                _uiState.value = _uiState.value.copy(importMessage = "导入成功")
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(importMessage = "导入失败: ${e.message}") }
        }
    }

    fun clearExportMessage() { _uiState.value = _uiState.value.copy(exportMessage = null) }
    fun clearImportMessage() { _uiState.value = _uiState.value.copy(importMessage = null) }
}
