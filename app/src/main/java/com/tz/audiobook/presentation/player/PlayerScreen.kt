package com.tz.audiobook.presentation.player

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tz.audiobook.data.remote.edgetts.EdgeTtsConstants
import com.tz.audiobook.data.remote.edgetts.VoiceInfo
import com.tz.audiobook.presentation.settings.SettingsPrefs
import com.tz.audiobook.service.PlaybackService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Track first play to avoid triggering on composition
    var hasTriggeredPlay by remember { mutableStateOf(false) }
    var lastPlayedChapter by remember { mutableIntStateOf(-1) }
    var lastPlayedSentence by remember { mutableIntStateOf(-1) }

    // Trigger playback when chapter or sentence changes
    LaunchedEffect(uiState.currentChapterIndex, uiState.currentSentenceIndex) {
        if (uiState.book != null) {
            val chapterChanged = lastPlayedChapter != uiState.currentChapterIndex
            val sentenceChanged = lastPlayedSentence != uiState.currentSentenceIndex

            if (chapterChanged || (sentenceChanged && !hasTriggeredPlay)) {
                if (!hasTriggeredPlay) {
                    startPlayback(context, uiState.book!!.id, uiState.currentChapterIndex, uiState.currentSentenceIndex, uiState.voice, uiState.speed)
                    hasTriggeredPlay = true
                } else if (chapterChanged) {
                    lastPlayedChapter = uiState.currentChapterIndex
                    lastPlayedSentence = 0
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY
                        putExtra(PlaybackService.EXTRA_BOOK_ID, uiState.book!!.id)
                        putExtra(PlaybackService.EXTRA_CHAPTER_INDEX, uiState.currentChapterIndex)
                        putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, 0)
                        putExtra(PlaybackService.EXTRA_VOICE, uiState.voice)
                        putExtra(PlaybackService.EXTRA_SPEED, uiState.speed)
                    }
                    context.startService(intent)
                    viewModel.checkChapterEndSleep()
                }
            }
            lastPlayedChapter = uiState.currentChapterIndex
            lastPlayedSentence = uiState.currentSentenceIndex
        }
    }

    // Handle sleep timer expiration
    LaunchedEffect(uiState.sleepTimerRemaining) {
        if (uiState.sleepTimerRemaining == 0 && uiState.sleepTimerMinutes > 0) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_PAUSE
            }
            context.startService(intent)
            viewModel.cancelSleepTimer()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.book?.title ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Current chapter name in subtitle
                        if (uiState.chapters.isNotEmpty()) {
                            Text(
                                text = uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.sleepTimerMinutes > 0 || uiState.sleepAtChapterEnd) {
                        IconButton(onClick = viewModel::toggleSleepDialog) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "睡眠定时器",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = viewModel::toggleVoiceDialog) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "选择配音")
                    }
                    // Chapter progress button - combines progress display and chapter list
                    if (uiState.chapters.isNotEmpty()) {
                        TextButton(onClick = viewModel::toggleChapterList) {
                            val progress = ((uiState.currentChapterIndex + 1).toFloat() / uiState.chapters.size * 100)
                            Text(
                                text = "${uiState.currentChapterIndex + 1}/${uiState.chapters.size} · ${"%.1f".format(progress)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sentence list with auto-scroll
            SentenceList(
                sentences = uiState.sentences,
                currentSentenceIndex = uiState.currentSentenceIndex,
                onSentenceClick = { index ->
                    val pauseIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PAUSE
                    }
                    context.startService(pauseIntent)
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SEEK_TO_SENTENCE
                        putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, index)
                    }
                    context.startService(intent)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Sentence progress section
            if (uiState.sentences.isNotEmpty() && uiState.currentSentenceIndex >= 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "第 ${uiState.currentSentenceIndex + 1} / ${uiState.sentences.size} 句",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Estimated remaining time
                            if (uiState.estimatedRemainingTime > 0) {
                                Text(
                                    text = "剩余 ${formatDuration(uiState.estimatedRemainingTime)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.sleepTimerRemaining > 0) {
                                Text(
                                    text = formatTime(uiState.sleepTimerRemaining),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (uiState.sleepAtChapterEnd) {
                                Text(
                                    text = "章末暂停",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (uiState.cacheProgress < 1f && uiState.cacheProgress > 0f) {
                                Text(
                                    text = "缓存 ${((uiState.cacheProgress * 100).toInt())}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (uiState.currentSentenceIndex + 1).toFloat() / uiState.sentences.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Control bar
            ControlBar(
                uiState = uiState,
                onPlayPause = {
                    val playAction = if (uiState.isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_RESUME
                    val intent = Intent(context, PlaybackService::class.java).apply { action = playAction }
                    context.startService(intent)
                },
                onNextChapter = viewModel::nextChapter,
                onPreviousChapter = viewModel::previousChapter,
                onSpeedClick = { viewModel.toggleSpeedDialog() },
                onSleepClick = viewModel::toggleSleepDialog
            )
        }

        // Chapter list - full screen overlay
        if (uiState.showChapterList) {
            ChapterListPage(
                chapters = uiState.chapters,
                currentIndex = uiState.currentChapterIndex,
                isPreCaching = uiState.isPreCaching,
                preCacheProgress = uiState.preCacheProgress,
                onChapterClick = { index ->
                    viewModel.playChapter(index)
                    viewModel.hideDialogs()
                },
                onPreCacheClick = { viewModel.preCacheBook() },
                onCancelPreCacheClick = { viewModel.cancelPreCache() },
                onDismiss = viewModel::hideDialogs
            )
        }

        if (uiState.showVoiceDialog) {
            VoiceDialog(
                currentVoice = uiState.voice,
                onVoiceSelected = { voice ->
                    viewModel.setVoice(voice)
                    viewModel.hideDialogs()
                    val pauseIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PAUSE
                    }
                    context.startService(pauseIntent)
                    val bookId = uiState.book?.id ?: return@VoiceDialog
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY
                        putExtra(PlaybackService.EXTRA_BOOK_ID, bookId)
                        putExtra(PlaybackService.EXTRA_CHAPTER_INDEX, uiState.currentChapterIndex)
                        putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, uiState.currentSentenceIndex)
                        putExtra(PlaybackService.EXTRA_VOICE, voice)
                        putExtra(PlaybackService.EXTRA_SPEED, uiState.speed)
                    }
                    context.startService(intent)
                },
                onDismiss = viewModel::hideDialogs
            )
        }

        if (uiState.showSleepDialog) {
            SleepTimerDialog(
                currentMinutes = uiState.sleepTimerMinutes,
                sleepAtChapterEnd = uiState.sleepAtChapterEnd,
                onSetTimer = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    viewModel.hideDialogs()
                },
                onSetChapterEnd = {
                    viewModel.setSleepAtChapterEnd()
                    viewModel.hideDialogs()
                },
                onCancel = {
                    viewModel.cancelSleepTimer()
                    viewModel.hideDialogs()
                },
                onDismiss = viewModel::hideDialogs
            )
        }

        // Speed BottomSheet
        if (uiState.showSpeedDialog) {
            SpeedBottomSheet(
                currentSpeed = uiState.speed,
                onSpeedSelected = { speed ->
                    viewModel.setSpeed(speed)
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SET_SPEED
                        putExtra(PlaybackService.EXTRA_SPEED, speed)
                    }
                    context.startService(intent)
                    scope.launch { sheetState.hide() }
                    viewModel.hideDialogs()
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }
                    viewModel.hideDialogs()
                },
                sheetState = sheetState
            )
        }
    }
}

@Composable
private fun SentenceList(
    sentences: List<com.tz.audiobook.parser.Sentence>,
    currentSentenceIndex: Int,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentSentenceIndex) {
        if (currentSentenceIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = currentSentenceIndex.coerceAtLeast(0),
                    scrollOffset = -200
                )
            }
        }
    }

    if (sentences.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "点击播放按钮开始",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            itemsIndexed(sentences) { index, sentence ->
                val isCurrent = index == currentSentenceIndex
                val isPast = index < currentSentenceIndex

                SentenceItem(
                    text = sentence.text,
                    isCurrent = isCurrent,
                    isPast = isPast,
                    onClick = { onSentenceClick(index) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SentenceItem(
    text: String,
    isCurrent: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fontSizeSetting = SettingsPrefs.getFontSize(context)
    val lineHeightSetting = SettingsPrefs.getLineHeight(context)

    val fontSize = when (fontSizeSetting) {
        0 -> 14.sp
        2 -> 18.sp
        else -> 16.sp
    }
    val lineHeight = when (lineHeightSetting) {
        0 -> fontSize * 1.3f
        2 -> fontSize * 1.8f
        else -> fontSize * 1.5f
    }
    val backgroundColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val textColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            color = textColor,
            lineHeight = lineHeight
        )
    }
}

@Composable
private fun ControlBar(
    uiState: PlayerUiState,
    onPlayPause: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onSpeedClick: () -> Unit,
    onSleepClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed button
            IconButton(onClick = onSpeedClick) {
                Text(text = "${uiState.speed}x", fontSize = 14.sp)
            }

            // Previous chapter
            IconButton(onClick = onPreviousChapter) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一章", modifier = Modifier.size(36.dp))
            }

            // Play/Pause
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp))
            } else {
                FloatingActionButton(
                    onClick = onPlayPause,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Next chapter
            IconButton(onClick = onNextChapter) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一章", modifier = Modifier.size(36.dp))
            }

            // Sleep timer
            IconButton(onClick = onSleepClick) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "睡眠定时器",
                    tint = if (uiState.sleepTimerMinutes > 0 || uiState.sleepAtChapterEnd)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun startPlayback(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int, voice: String, speed: Float) {
    val intent = Intent(context, PlaybackService::class.java).apply {
        action = PlaybackService.ACTION_PLAY
        putExtra(PlaybackService.EXTRA_BOOK_ID, bookId)
        putExtra(PlaybackService.EXTRA_CHAPTER_INDEX, chapterIndex)
        putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, sentenceIndex)
        putExtra(PlaybackService.EXTRA_VOICE, voice)
        putExtra(PlaybackService.EXTRA_SPEED, speed)
    }
    context.startService(intent)
}

private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return if (min > 0) "${min}分${sec}秒" else "${sec}秒"
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    if (seconds < 60) return "${seconds}秒"
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (secs > 0) "${minutes}分${secs}秒" else "${minutes}分钟"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListPage(
    chapters: List<com.tz.audiobook.domain.model.Chapter>,
    currentIndex: Int,
    isPreCaching: Boolean,
    preCacheProgress: Float,
    onChapterClick: (Int) -> Unit,
    onPreCacheClick: () -> Unit,
    onCancelPreCacheClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
    val coroutineScope = rememberCoroutineScope()

    val filteredChapters = remember(chapters, searchQuery) {
        if (searchQuery.isBlank()) chapters.mapIndexed { index, chapter -> index to chapter }
        else chapters.mapIndexed { index, chapter -> index to chapter }
            .filter { (_, chapter) -> chapter.title.contains(searchQuery, ignoreCase = true) }
    }

    val progress = ((currentIndex + 1).toFloat() / chapters.size.coerceAtLeast(1))

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Top bar
            TopAppBar(
                title = {
                    Column {
                        Text("目录", style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentIndex + 1}/${chapters.size}章",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${"%.1f".format(progress * 100)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Jump to current chapter
                    IconButton(onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
                        }
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "定位当前章节")
                    }
                }
            )

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索章节") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Pre-cache section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPreCaching) {
                    Text(
                        text = "缓存中 ${"%.0f".format(preCacheProgress * 100)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    TextButton(onClick = onCancelPreCacheClick) {
                        Text("取消", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    TextButton(onClick = onPreCacheClick) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("预缓存全书", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (isPreCaching) {
                LinearProgressIndicator(
                    progress = { preCacheProgress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Chapter list
            if (filteredChapters.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "未找到匹配章节",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(filteredChapters.size) { idx ->
                        val (index, chapter) = filteredChapters[idx]
                        val isCurrent = index == currentIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChapterClick(index) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = chapter.title,
                                style = if (isCurrent) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                                else MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (chapter.wordCount > 0) {
                                Text(
                                    text = formatWordCount(chapter.wordCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        if (idx < filteredChapters.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatWordCount(count: Int): String {
    return when {
        count >= 10000 -> "${count / 10000}万字"
        count >= 1000 -> "${count / 1000}千字"
        else -> "${count}字"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedBottomSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "播放速度",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                speeds.forEach { speed ->
                    FilterChip(
                        selected = speed == currentSpeed,
                        onClick = { onSpeedSelected(speed) },
                        label = { Text("${speed}x") },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceDialog(
    currentVoice: String,
    onVoiceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var favoriteVoices by remember { mutableStateOf(SettingsPrefs.getFavoriteVoices(context)) }

    // Sort voices: favorites first, then the rest
    val sortedVoices = remember(favoriteVoices) {
        val favorites = EdgeTtsConstants.CHINESE_VOICES.filter { it.name in favoriteVoices }
        val others = EdgeTtsConstants.CHINESE_VOICES.filter { it.name !in favoriteVoices }
        favorites + others
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择配音") },
        text = {
            LazyColumn {
                if (favoriteVoices.isNotEmpty() && favoriteVoices.size < EdgeTtsConstants.CHINESE_VOICES.size) {
                    item {
                        Text(
                            text = "收藏",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(sortedVoices.take(favoriteVoices.size).size) { idx ->
                        val voice = sortedVoices[idx]
                        VoiceItem(
                            voice = voice,
                            isCurrent = voice.name == currentVoice,
                            isFavorite = voice.name in favoriteVoices,
                            onSelect = { onVoiceSelected(voice.name) },
                            onToggleFavorite = {
                                SettingsPrefs.toggleFavoriteVoice(context, voice.name)
                                favoriteVoices = SettingsPrefs.getFavoriteVoices(context)
                            }
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "全部音色",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                    items(sortedVoices.drop(favoriteVoices.size).size) { idx ->
                        val voice = sortedVoices[favoriteVoices.size + idx]
                        VoiceItem(
                            voice = voice,
                            isCurrent = voice.name == currentVoice,
                            isFavorite = false,
                            onSelect = { onVoiceSelected(voice.name) },
                            onToggleFavorite = {
                                SettingsPrefs.toggleFavoriteVoice(context, voice.name)
                                favoriteVoices = SettingsPrefs.getFavoriteVoices(context)
                            }
                        )
                    }
                } else {
                    items(sortedVoices.size) { idx ->
                        val voice = sortedVoices[idx]
                        VoiceItem(
                            voice = voice,
                            isCurrent = voice.name == currentVoice,
                            isFavorite = voice.name in favoriteVoices,
                            onSelect = { onVoiceSelected(voice.name) },
                            onToggleFavorite = {
                                SettingsPrefs.toggleFavoriteVoice(context, voice.name)
                                favoriteVoices = SettingsPrefs.getFavoriteVoices(context)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun VoiceItem(
    voice: VoiceInfo,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isCurrent,
            onClick = { onSelect() }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${voice.displayName} (${voice.gender})",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = voice.style,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(
    currentMinutes: Int,
    sleepAtChapterEnd: Boolean,
    onSetTimer: (Int) -> Unit,
    onSetChapterEnd: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        15 to "15分钟后",
        30 to "30分钟后",
        45 to "45分钟后",
        60 to "60分钟后"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("睡眠定时器") },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetTimer(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMinutes == minutes && !sleepAtChapterEnd,
                            onClick = { onSetTimer(minutes) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetChapterEnd() }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = sleepAtChapterEnd,
                        onClick = { onSetChapterEnd() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "读完当前章节后暂停", style = MaterialTheme.typography.bodyLarge)
                }
                if (currentMinutes > 0 || sleepAtChapterEnd) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCancel() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "关闭定时器",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
