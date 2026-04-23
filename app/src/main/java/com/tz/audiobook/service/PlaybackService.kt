package com.tz.audiobook.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tz.audiobook.MainActivity
import com.tz.audiobook.domain.model.Chapter
import com.tz.audiobook.domain.model.ReadingProgress
import com.tz.audiobook.domain.repository.ChapterRepository
import com.tz.audiobook.domain.repository.ReadingProgressRepository
import com.tz.audiobook.parser.Sentence
import com.tz.audiobook.parser.SentenceSplitter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentChapterIndex: Int = -1,
    val currentSentenceIndex: Int = -1,
    val sentenceCount: Int = 0,
    val duration: Long = 0,
    val speed: Float = 1.0f,
    val voice: String = "zh-CN-XiaoxiaoNeural",
    val isLoading: Boolean = false,
    val bookId: Long = -1,
    val chapterTitle: String = "",
    val bookTitle: String = "",
    // Time estimation
    val averageSentenceDuration: Long = 0L, // milliseconds
    val estimatedRemainingTime: Long = 0L // milliseconds
)

@AndroidEntryPoint
class PlaybackService : Service() {

    companion object {
        const val TAG = "PlaybackService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "audiobook_playback"

        const val ACTION_PLAY = "com.tz.audiobook.PLAY"
        const val ACTION_RESUME = "com.tz.audiobook.RESUME"
        const val ACTION_PAUSE = "com.tz.audiobook.PAUSE"
        const val ACTION_NEXT = "com.tz.audiobook.NEXT"
        const val ACTION_PREVIOUS = "com.tz.audiobook.PREVIOUS"
        const val ACTION_STOP = "com.tz.audiobook.STOP"
        const val ACTION_SEEK_TO_SENTENCE = "com.tz.audiobook.SEEK_TO_SENTENCE"
        const val ACTION_SET_SPEED = "com.tz.audiobook.SET_SPEED"

        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_CHAPTER_INDEX = "chapter_index"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_SENTENCE_INDEX = "sentence_index"
    }

    @Inject lateinit var audioPipeline: AudioPipeline
    @Inject lateinit var chapterRepository: ChapterRepository
    @Inject lateinit var readingProgressRepository: ReadingProgressRepository
    @Inject lateinit var stateManager: PlaybackStateManager

    private var exoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var chapters: List<Chapter> = emptyList()
    private var sentences: List<Sentence> = emptyList()
    private var currentChapterId: Long = -1

    private var progressJob: Job? = null
    private var positionJob: Job? = null
    private var pregenerateJob: Job? = null
    private var isTransitioning = false
    private val sentenceDurations = mutableListOf<Long>()
    private var currentSentenceStartTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                true // Handle audio focus automatically - will pause on calls
            )
        }
        setupPlayerListeners()
        startForegroundNotification()
        Log.d(TAG, "PlaybackService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_PLAY -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)
                val chapterIndex = intent.getIntExtra(EXTRA_CHAPTER_INDEX, 0)
                val sentenceIndex = intent.getIntExtra(EXTRA_SENTENCE_INDEX, 0)
                val voice = intent.getStringExtra(EXTRA_VOICE) ?: "zh-CN-XiaoxiaoNeural"
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)

                Log.d(TAG, "PLAY: bookId=$bookId, chapter=$chapterIndex, sentence=$sentenceIndex, voice=$voice, speed=$speed")

                if (bookId >= 0) {
                    playBook(bookId, chapterIndex, voice, speed, sentenceIndex)
                }
            }
            ACTION_RESUME -> {
                Log.d(TAG, "RESUME")
                exoPlayer?.play()
            }
            ACTION_PAUSE -> {
                Log.d(TAG, "PAUSE")
                exoPlayer?.pause()
            }
            ACTION_NEXT -> nextChapter()
            ACTION_PREVIOUS -> previousChapter()
            ACTION_STOP -> stopPlayback()
            ACTION_SEEK_TO_SENTENCE -> {
                val sentenceIndex = intent.getIntExtra(EXTRA_SENTENCE_INDEX, 0)
                playSentence(sentenceIndex)
            }
            ACTION_SET_SPEED -> {
                val newSpeed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                Log.d(TAG, "SET_SPEED: $newSpeed")
                exoPlayer?.setPlaybackSpeed(newSpeed)
                stateManager.updateState { it.copy(speed = newSpeed) }
                // Regenerate cache for upcoming sentences at new speed
                pregenerateJob?.cancel()
                val currentIndex = stateManager.playbackState.value.currentSentenceIndex
                if (currentIndex >= 0 && sentences.isNotEmpty()) {
                    pregenerateJob = serviceScope.launch(Dispatchers.IO) {
                        audioPipeline.preGenerateSentences(
                            chapterId = currentChapterId,
                            sentences = sentences,
                            startIndex = currentIndex + 1,
                            count = 5,
                            voice = stateManager.playbackState.value.voice,
                            speed = newSpeed
                        )
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun playBook(bookId: Long, chapterIndex: Int, voice: String, speed: Float, sentenceIndex: Int = 0) {
        serviceScope.launch {
            try {
                chapters = chapterRepository.getChaptersByBookList(bookId)
                Log.d(TAG, "Loaded ${chapters.size} chapters for book $bookId")
                stateManager.updateState {
                    it.copy(
                        bookId = bookId,
                        voice = voice,
                        speed = speed
                    )
                }
                playChapter(chapterIndex, sentenceIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load chapters", e)
            }
        }
    }

    private fun playChapter(chapterIndex: Int, sentenceIndex: Int = 0) {
        if (chapterIndex < 0 || chapterIndex >= chapters.size) {
            Log.w(TAG, "Invalid chapter index: $chapterIndex, total: ${chapters.size}")
            return
        }

        val chapterMeta = chapters[chapterIndex]
        Log.d(TAG, "Playing chapter $chapterIndex: ${chapterMeta.title}")

        // Stop current playback immediately when switching chapters
        exoPlayer?.stop()

        // Reset time estimation for new chapter
        sentenceDurations.clear()
        currentSentenceStartTime = 0L

        stateManager.updateState {
            it.copy(
                isLoading = true,
                currentChapterIndex = chapterIndex,
                chapterTitle = chapterMeta.title,
                currentSentenceIndex = sentenceIndex
            )
        }
        updateNotification()

        serviceScope.launch {
            try {
                val currentState = stateManager.playbackState.value
                val chapter = chapterRepository.getChapterWithContent(currentState.bookId, chapterIndex)
                if (chapter == null || chapter.content.isEmpty()) {
                    Log.e(TAG, "Failed to load chapter content for index $chapterIndex")
                    stateManager.updateState { it.copy(isLoading = false) }
                    return@launch
                }

                currentChapterId = chapter.id

                // Split into sentences
                sentences = SentenceSplitter.split(chapter.content)
                stateManager.updateSentences(sentences)
                stateManager.updateChapterContent(chapter.content)
                stateManager.updateState { it.copy(sentenceCount = sentences.size) }

                Log.d(TAG, "Chapter split into ${sentences.size} sentences")

                if (sentences.isEmpty()) {
                    stateManager.updateState { it.copy(isLoading = false) }
                    return@launch
                }

                // Start playing from specified sentence
                playSentence(sentenceIndex)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to play chapter index $chapterIndex", e)
                stateManager.updateState { it.copy(isLoading = false) }
            }
        }
    }

private fun playSentence(sentenceIndex: Int) {
        // Stop current audio immediately before switching
        exoPlayer?.stop()

        if (sentenceIndex < 0 || sentenceIndex >= sentences.size) {
            Log.w(TAG, "Invalid sentence index: $sentenceIndex, total: ${sentences.size}")
            // Move to next chapter if at end
            if (sentenceIndex >= sentences.size && sentences.isNotEmpty()) {
                val currentChapter = stateManager.playbackState.value.currentChapterIndex
                if (currentChapter + 1 < chapters.size) {
                    playChapter(currentChapter + 1, 0)
                }
            }
            return
        }

        // Save progress immediately on sentence change
        saveProgressNow()

        // Record previous sentence duration for time estimation
        if (currentSentenceStartTime > 0) {
            val elapsed = System.currentTimeMillis() - currentSentenceStartTime
            sentenceDurations.add(elapsed)
        }
        currentSentenceStartTime = System.currentTimeMillis()

        val sentence = sentences[sentenceIndex]
        Log.d(TAG, "Playing sentence $sentenceIndex: ${sentence.text.take(30)}...")

        stateManager.updateCurrentSentence(sentenceIndex)
        stateManager.updateState { it.copy(currentSentenceIndex = sentenceIndex) }
        updateNotification()

        serviceScope.launch(Dispatchers.IO) {
            try {
                val currentState = stateManager.playbackState.value
                val audioUri = audioPipeline.getOrCreateAudioForSentence(
                    chapterId = currentChapterId,
                    sentence = sentence,
                    voice = currentState.voice,
                    speed = currentState.speed
                )

                // Update cache progress
                val cachedCount = audioPipeline.getCachedSentenceCount(
                    currentChapterId,
                    sentences.size,
                    currentState.voice,
                    currentState.speed
                )
                stateManager.updateCacheProgress(cachedCount.toFloat() / sentences.size)

                withContext(Dispatchers.Main) {
                    val player = exoPlayer ?: return@withContext
                    // Must stop before setting new media when in ENDED/IDLE state
                    if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
                        player.stop()
                    }
                    player.setMediaItem(MediaItem.fromUri(audioUri))
                    // Apply current speed since audio is always generated at 1.0x
                    player.setPlaybackSpeed(currentState.speed)
                    player.prepare()
                    player.playWhenReady = true
                    isTransitioning = false
                    Log.d(TAG, "Playing sentence $sentenceIndex, playWhenReady=true")
                    stateManager.updateState { it.copy(isLoading = false) }
                }

                // Pre-generate next sentences (cache next 5 sentences)
                pregenerateJob?.cancel()
                pregenerateJob = launch(Dispatchers.IO) {
                    audioPipeline.preGenerateSentences(
                        chapterId = currentChapterId,
                        sentences = sentences,
                        startIndex = sentenceIndex + 1,
                        count = 5,
                        voice = currentState.voice,
                        speed = currentState.speed
                    )
                    // Update progress after pre-generation
                    val newCachedCount = audioPipeline.getCachedSentenceCount(
                        currentChapterId,
                        sentences.size,
                        currentState.voice,
                        currentState.speed
                    )
                    stateManager.updateCacheProgress(newCachedCount.toFloat() / sentences.size)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to play sentence $sentenceIndex", e)
            }
        }

        startProgressTracking()
        startPositionTracking()
    }

    private fun nextSentence() {
        val currentIndex = stateManager.playbackState.value.currentSentenceIndex
        playSentence(currentIndex + 1)
    }

    private fun previousSentence() {
        val currentIndex = stateManager.playbackState.value.currentSentenceIndex
        if (currentIndex > 0) {
            playSentence(currentIndex - 1)
        } else {
            // Go to previous chapter's last sentence
            val currentChapter = stateManager.playbackState.value.currentChapterIndex
            if (currentChapter > 0) {
                previousChapter()
            }
        }
    }

    private fun nextChapter() {
        val currentChapter = stateManager.playbackState.value.currentChapterIndex
        if (currentChapter + 1 < chapters.size) {
            playChapter(currentChapter + 1)
        }
    }

    private fun previousChapter() {
        val currentChapter = stateManager.playbackState.value.currentChapterIndex
        if (currentChapter > 0) {
            playChapter(currentChapter - 1)
        }
    }

    private fun setupPlayerListeners() {
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "isPlaying changed: $isPlaying")
                stateManager.updateState { it.copy(isPlaying = isPlaying) }
                updateNotification()
            }

            override fun onPlaybackStateChanged(state: Int) {
                Log.d(TAG, "Playback state changed: $state")
                when (state) {
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Sentence ended, moving to next, transitioning=$isTransitioning")
                        if (!isTransitioning) {
                            isTransitioning = true
                            nextSentence()
                        }
                    }
                    Player.STATE_READY -> {
                        stateManager.updateState {
                            it.copy(
                                isLoading = false,
                                duration = exoPlayer?.duration ?: 0
                            )
                        }
                        Log.d(TAG, "Player ready, duration=${exoPlayer?.duration}")
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "Player buffering")
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "Player idle")
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}", error)
                stateManager.updateState {
                    it.copy(isPlaying = false, isLoading = false)
                }
            }
        })
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = serviceScope.launch {
            while (true) {
                delay(200)
                exoPlayer?.let { player ->
                    val position = player.currentPosition
                    val duration = player.duration.takeIf { it > 0 } ?: 1
                    stateManager.updatePosition(position)

                    // Update time estimation
                    updateTimeEstimation()
                }
            }
        }
    }

    private fun updateTimeEstimation() {
        val currentState = stateManager.playbackState.value
        val currentIndex = currentState.currentSentenceIndex
        val totalSentences = sentences.size

        if (currentIndex >= 0 && totalSentences > 0 && sentenceDurations.isNotEmpty()) {
            val avgDuration = sentenceDurations.average().toLong()
            val remainingSentences = totalSentences - currentIndex - 1
            val speed = currentState.speed
            val estimatedRemaining = ((avgDuration * remainingSentences) / speed).toLong()

            stateManager.updateState {
                it.copy(
                    averageSentenceDuration = avgDuration,
                    estimatedRemainingTime = estimatedRemaining
                )
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (true) {
                delay(5000)
                saveProgressNow()
            }
        }
    }

    private fun saveProgressNow() {
        val state = stateManager.playbackState.value
        if (state.bookId >= 0) {
            serviceScope.launch {
                readingProgressRepository.saveProgress(
                    ReadingProgress(
                        bookId = state.bookId,
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "有声小说播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "音频播放控制"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundNotification() {
        val notification = buildNotification(stateManager.playbackState.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        try {
            val notification = buildNotification(stateManager.playbackState.value)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification", e)
        }
    }

    private fun buildNotification(state: PlaybackState): Notification {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("bookId", state.bookId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, PlaybackService::class.java).apply {
            action = if (state.isPlaying) ACTION_PAUSE else ACTION_RESUME
        }

        val nextIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_NEXT
        }

        val prevIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_PREVIOUS
        }

        val sentenceInfo = if (state.sentenceCount > 0) {
            " (${state.currentSentenceIndex + 1}/${state.sentenceCount})"
        } else ""

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(state.chapterTitle.ifEmpty { "有声小说" })
            .setContentText(state.chapterTitle + sentenceInfo)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_previous,
                "上一章",
                PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "暂停" else "播放",
                PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "下一章",
                PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE)
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun stopPlayback() {
        exoPlayer?.stop()
        stateManager.updateState { PlaybackState() }
        stateManager.updateSentences(emptyList())
        stateManager.updateCurrentSentence(-1)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        positionJob?.cancel()
        pregenerateJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        Log.d(TAG, "PlaybackService destroyed")
    }

    override fun onBind(intent: Intent?) = null
}
