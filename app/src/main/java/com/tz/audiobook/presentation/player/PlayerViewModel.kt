package com.tz.audiobook.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tz.audiobook.data.remote.edgetts.EdgeTtsConstants
import com.tz.audiobook.domain.model.Book
import com.tz.audiobook.domain.model.Chapter
import com.tz.audiobook.domain.model.ReadingProgress
import com.tz.audiobook.domain.repository.BookRepository
import com.tz.audiobook.domain.repository.ChapterRepository
import com.tz.audiobook.domain.repository.ReadingProgressRepository
import com.tz.audiobook.parser.Sentence
import com.tz.audiobook.service.PlaybackState
import com.tz.audiobook.service.PlaybackStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    val currentSentenceIndex: Int = -1,
    val sentences: List<Sentence> = emptyList(),
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPosition: Long = 0,
    val sentenceDuration: Long = 0,
    val speed: Float = 1.0f,
    val voice: String = EdgeTtsConstants.DEFAULT_VOICE.name,
    val cacheProgress: Float = 0f,
    val showChapterList: Boolean = false,
    val showSpeedDialog: Boolean = false,
    val showVoiceDialog: Boolean = false,
    val showSleepDialog: Boolean = false,
    // Sleep timer
    val sleepTimerMinutes: Int = 0,  // 0 means off
    val sleepTimerRemaining: Int = 0, // seconds remaining
    val sleepAtChapterEnd: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val chapterRepository: ChapterRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val playbackStateManager: PlaybackStateManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Long = savedStateHandle["bookId"] ?: -1L

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        loadBook()
        observePlaybackState()
        observeSentences()
    }

    private fun loadBook() {
        viewModelScope.launch {
            val book = bookRepository.getBookById(bookId) ?: return@launch
            val chapters = chapterRepository.getChaptersByBookList(bookId)
            val progress = readingProgressRepository.getProgress(bookId).first()

            val initialChapterIndex = progress?.currentChapterIndex ?: 0
            val initialSentenceIndex = progress?.currentPosition?.toInt() ?: 0
            val initialVoice = progress?.voiceName ?: EdgeTtsConstants.DEFAULT_VOICE.name
            val initialSpeed = progress?.playbackSpeed ?: 1.0f

            _uiState.value = _uiState.value.copy(
                book = book,
                chapters = chapters,
                currentChapterIndex = initialChapterIndex,
                currentSentenceIndex = initialSentenceIndex,
                voice = initialVoice,
                speed = initialSpeed
            )

            bookRepository.updateLastPlayed(bookId)
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            playbackStateManager.playbackState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    isPlaying = state.isPlaying,
                    isLoading = state.isLoading,
                    currentChapterIndex = state.currentChapterIndex,
                    currentSentenceIndex = state.currentSentenceIndex,
                    sentenceDuration = state.duration,
                    speed = state.speed,
                    voice = state.voice
                )
            }
        }
        viewModelScope.launch {
            playbackStateManager.currentPosition.collect { position ->
                _uiState.value = _uiState.value.copy(currentPosition = position)
            }
        }
    }

    private fun observeSentences() {
        viewModelScope.launch {
            playbackStateManager.sentences.collect { sentences ->
                _uiState.value = _uiState.value.copy(sentences = sentences)
            }
        }
        viewModelScope.launch {
            playbackStateManager.cacheProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(cacheProgress = progress)
            }
        }
    }

    fun playChapter(index: Int) {
        if (index < 0 || index >= _uiState.value.chapters.size) return
        _uiState.value = _uiState.value.copy(currentChapterIndex = index)
        saveProgress()
    }

    fun togglePlayPause() {
        // Don't toggle local state - it will be updated from Service
    }

    fun nextChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex < state.chapters.size - 1) {
            playChapter(state.currentChapterIndex + 1)
        }
    }

    fun previousChapter() {
        val state = _uiState.value
        if (state.currentChapterIndex > 0) {
            playChapter(state.currentChapterIndex - 1)
        }
    }

    fun setSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(speed = speed)
        saveProgress()
    }

    fun setVoice(voice: String) {
        _uiState.value = _uiState.value.copy(voice = voice)
        saveProgress()
    }

    fun toggleChapterList() {
        _uiState.value = _uiState.value.copy(showChapterList = !_uiState.value.showChapterList)
    }

    fun toggleSpeedDialog() {
        _uiState.value = _uiState.value.copy(showSpeedDialog = !_uiState.value.showSpeedDialog)
    }

    fun toggleVoiceDialog() {
        _uiState.value = _uiState.value.copy(showVoiceDialog = !_uiState.value.showVoiceDialog)
    }

    fun toggleSleepDialog() {
        _uiState.value = _uiState.value.copy(showSleepDialog = !_uiState.value.showSleepDialog)
    }

    fun hideDialogs() {
        _uiState.value = _uiState.value.copy(
            showChapterList = false,
            showSpeedDialog = false,
            showVoiceDialog = false,
            showSleepDialog = false
        )
    }

    // Sleep timer functions
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.value = _uiState.value.copy(
                sleepTimerMinutes = 0,
                sleepTimerRemaining = 0,
                sleepAtChapterEnd = false
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = minutes,
            sleepTimerRemaining = minutes * 60,
            sleepAtChapterEnd = false
        )
        startSleepCountdown()
    }

    fun setSleepAtChapterEnd() {
        sleepTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = 0,
            sleepTimerRemaining = 0,
            sleepAtChapterEnd = true
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            sleepTimerMinutes = 0,
            sleepTimerRemaining = 0,
            sleepAtChapterEnd = false
        )
    }

    private fun startSleepCountdown() {
        sleepTimerJob = viewModelScope.launch {
            while (_uiState.value.sleepTimerRemaining > 0) {
                delay(1000)
                val remaining = _uiState.value.sleepTimerRemaining - 1
                _uiState.value = _uiState.value.copy(sleepTimerRemaining = remaining)
                if (remaining <= 0) {
                    // Timer expired, pause playback
                    playbackStateManager.updateState { it.copy(isPlaying = false) }
                }
            }
        }
    }

    // Check if should sleep at chapter end (called when chapter changes)
    fun checkChapterEndSleep() {
        if (_uiState.value.sleepAtChapterEnd) {
            playbackStateManager.updateState { it.copy(isPlaying = false) }
            cancelSleepTimer()
        }
    }

    private fun saveProgress() {
        viewModelScope.launch {
            val state = _uiState.value
            readingProgressRepository.saveProgress(
                ReadingProgress(
                    bookId = bookId,
                    currentChapterIndex = state.currentChapterIndex,
                    currentPosition = state.currentSentenceIndex.toLong(),
                    playbackSpeed = state.speed,
                    voiceName = state.voice,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }
}
