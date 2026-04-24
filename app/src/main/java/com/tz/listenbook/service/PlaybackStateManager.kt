package com.tz.listenbook.service

import com.tz.listenbook.parser.Sentence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateManager @Inject constructor() {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentSentenceIndex = MutableStateFlow(-1)
    val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex.asStateFlow()

    private val _sentences = MutableStateFlow<List<Sentence>>(emptyList())
    val sentences: StateFlow<List<Sentence>> = _sentences.asStateFlow()

    private val _cacheProgress = MutableStateFlow(0f)
    val cacheProgress: StateFlow<Float> = _cacheProgress.asStateFlow()

    private val _chapterContent = MutableStateFlow("")
    val chapterContent: StateFlow<String> = _chapterContent.asStateFlow()

    fun updateState(state: PlaybackState) {
        _playbackState.value = state
    }

    fun updateState(transform: (PlaybackState) -> PlaybackState) {
        _playbackState.value = transform(_playbackState.value)
    }

    fun updatePosition(position: Long) {
        _currentPosition.value = position
    }

    fun updateCurrentSentence(index: Int) {
        _currentSentenceIndex.value = index
    }

    fun updateSentences(sentences: List<Sentence>) {
        _sentences.value = sentences
    }

    fun updateCacheProgress(progress: Float) {
        _cacheProgress.value = progress
    }

    fun updateChapterContent(content: String) {
        _chapterContent.value = content
    }
}
