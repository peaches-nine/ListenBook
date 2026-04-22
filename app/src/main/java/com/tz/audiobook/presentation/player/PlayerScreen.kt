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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tz.audiobook.data.remote.edgetts.EdgeTtsConstants
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
                }
            }
            lastPlayedChapter = uiState.currentChapterIndex
            lastPlayedSentence = uiState.currentSentenceIndex
        }
    }

    // Handle initial play trigger

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
                        if (uiState.chapters.isNotEmpty() && uiState.currentChapterIndex >= 0) {
                            Text(
                                text = uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    IconButton(onClick = viewModel::toggleVoiceDialog) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = "选择配音")
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
                    // Stop current playback first before seeking
                    val pauseIntent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PAUSE
                    }
                    context.startService(pauseIntent)
                    // Then seek to the new sentence
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

            // Sentence progress bar
            if (uiState.sentences.isNotEmpty() && uiState.currentSentenceIndex >= 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // Sentence progress within chapter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "第 ${uiState.currentSentenceIndex + 1} / ${uiState.sentences.size} 句",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Cache progress text
                        if (uiState.cacheProgress < 1f) {
                            Text(
                                text = "缓存 ${((uiState.cacheProgress * 100).toInt())}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Overall progress bar (sentence-based)
                    LinearProgressIndicator(
                        progress = { (uiState.currentSentenceIndex + 1).toFloat() / uiState.sentences.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Cache progress indicator (for loading state)
            if (uiState.cacheProgress < 1f && uiState.cacheProgress > 0f && uiState.sentences.isEmpty()) {
                LinearProgressIndicator(
                    progress = { uiState.cacheProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
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
                onSpeedClick = viewModel::toggleSpeedDialog,
                onListClick = viewModel::toggleChapterList
            )
        }

        // Dialogs
        if (uiState.showChapterList) {
            ChapterListDialog(
                chapters = uiState.chapters,
                currentIndex = uiState.currentChapterIndex,
                onChapterClick = { index ->
                    viewModel.playChapter(index)
                    viewModel.hideDialogs()
                },
                onDismiss = viewModel::hideDialogs
            )
        }

        if (uiState.showSpeedDialog) {
            SpeedDialog(
                currentSpeed = uiState.speed,
                onSpeedSelected = { speed ->
                    viewModel.setSpeed(speed)
                    viewModel.hideDialogs()
                    // Seamless speed change - no restart needed
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_SET_SPEED
                        putExtra(PlaybackService.EXTRA_SPEED, speed)
                    }
                    context.startService(intent)
                },
                onDismiss = viewModel::hideDialogs
            )
        }

        if (uiState.showVoiceDialog) {
            VoiceDialog(
                currentVoice = uiState.voice,
                onVoiceSelected = { voice ->
                    viewModel.setVoice(voice)
                    viewModel.hideDialogs()
                    // Pause first, then restart with new voice from current sentence
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

    // Auto-scroll to current sentence
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
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            lineHeight = 28.sp
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
    onListClick: () -> Unit
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

            // Chapter list
            IconButton(onClick = onListClick) {
                Icon(Icons.Default.List, contentDescription = "章节列表")
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

@Composable
private fun ChapterListDialog(
    chapters: List<com.tz.audiobook.domain.model.Chapter>,
    currentIndex: Int,
    onChapterClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("章节列表") },
        text = {
            LazyColumn(state = listState) {
                itemsIndexed(chapters) { index, chapter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterClick(index) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (index == currentIndex) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = chapter.title,
                            style = if (index == currentIndex) MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun SpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放速度") },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                speeds.forEach { speed ->
                    FilterChip(selected = speed == currentSpeed, onClick = { onSpeedSelected(speed) }, label = { Text("${speed}x") })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun VoiceDialog(currentVoice: String, onVoiceSelected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择配音") },
        text = {
            LazyColumn {
                items(EdgeTtsConstants.CHINESE_VOICES.size) { index ->
                    val voice = EdgeTtsConstants.CHINESE_VOICES[index]
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onVoiceSelected(voice.name) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = voice.name == currentVoice, onClick = { onVoiceSelected(voice.name) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "${voice.displayName} (${voice.gender})", style = MaterialTheme.typography.bodyMedium)
                            Text(text = voice.style, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
