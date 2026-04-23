package com.tz.audiobook.presentation.player

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tz.audiobook.data.remote.edgetts.EdgeTtsConstants
import com.tz.audiobook.data.remote.edgetts.VoiceInfo
import com.tz.audiobook.presentation.settings.SettingsPrefs
import com.tz.audiobook.service.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Fullscreen mode: auto-hide bars after 3s of playing
    var isFullscreen by remember { mutableStateOf(false) }
    var showBars by remember { mutableStateOf(true) }

    // Auto-hide logic
    LaunchedEffect(uiState.isPlaying, isFullscreen) {
        if (uiState.isPlaying && !isFullscreen) {
            delay(3000)
            isFullscreen = true
            showBars = false
        }
    }

    // When paused, show bars
    LaunchedEffect(uiState.isPlaying) {
        if (!uiState.isPlaying) {
            showBars = true
            isFullscreen = false
        }
    }

    // Track first play
    var hasTriggeredPlay by remember { mutableStateOf(false) }
    var lastPlayedChapter by remember { mutableIntStateOf(-1) }
    var lastPlayedSentence by remember { mutableIntStateOf(-1) }

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

    LaunchedEffect(uiState.sleepTimerRemaining) {
        if (uiState.sleepTimerRemaining == 0 && uiState.sleepTimerMinutes > 0) {
            context.startService(Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_PAUSE })
            viewModel.cancelSleepTimer()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar (animated)
            AnimatedVisibility(visible = showBars, enter = fadeIn(), exit =fadeOut()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(uiState.book?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (uiState.chapters.isNotEmpty()) {
                                Text(
                                    uiState.chapters.getOrNull(uiState.currentChapterIndex)?.title ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        if (uiState.sleepTimerMinutes > 0 || uiState.sleepAtChapterEnd) {
                            IconButton(onClick = viewModel::toggleSleepDialog) {
                                Icon(Icons.Default.Timer, "睡眠定时器", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = viewModel::toggleVoiceDialog) { Icon(Icons.Default.RecordVoiceOver, "选择配音") }
                        IconButton(onClick = viewModel::toggleBookmarkList) {
                            Icon(Icons.Default.Bookmark, "书签", tint = if (uiState.showBookmarkList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (uiState.chapters.isNotEmpty()) {
                            TextButton(onClick = viewModel::toggleChapterList) {
                                val progress = ((uiState.currentChapterIndex + 1).toFloat() / uiState.chapters.size * 100)
                                Text("${uiState.currentChapterIndex + 1}/${uiState.chapters.size} · ${"%.1f".format(progress)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                    }
                )
            }

            // Pinned chapter title (small, always visible)
            if (uiState.chapters.isNotEmpty() && uiState.chapters.getOrNull(uiState.currentChapterIndex) != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = uiState.chapters[uiState.currentChapterIndex].title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Sentence list
            SentenceList(
                sentences = uiState.sentences,
                currentSentenceIndex = uiState.currentSentenceIndex,
                currentChapterIndex = uiState.currentChapterIndex,
                totalChapters = uiState.chapters.size,
                bookId = uiState.book?.id ?: -1L,
                isFullscreen = isFullscreen,
                onToggleBars = { showBars = !showBars; if (showBars) isFullscreen = false },
                onSentenceClick = { index ->
                    if (!showBars) { showBars = true; isFullscreen = false }
                    else {
                        context.startService(Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_PAUSE })
                        context.startService(Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_SEEK_TO_SENTENCE
                            putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, index)
                        })
                    }
                },
                onSwipeLeft = viewModel::nextChapter,
                onSwipeRight = viewModel::previousChapter,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            // Bottom area (animated)
            AnimatedVisibility(visible = showBars, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    if (uiState.sentences.isNotEmpty() && uiState.currentSentenceIndex >= 0) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("第 ${uiState.currentSentenceIndex + 1} / ${uiState.sentences.size} 句", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (uiState.estimatedRemainingTime > 0) Text("剩余 ${formatDuration(uiState.estimatedRemainingTime)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (uiState.sleepTimerRemaining > 0) Text(formatTime(uiState.sleepTimerRemaining), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    else if (uiState.sleepAtChapterEnd) Text("章末暂停", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    if (uiState.cacheProgress < 1f && uiState.cacheProgress > 0f) Text("缓存 ${(uiState.cacheProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (uiState.currentSentenceIndex + 1).toFloat() / uiState.sentences.size.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    ControlBar(
                        uiState = uiState,
                        onPlayPause = {
                            val actionType = if (uiState.isPlaying) PlaybackService.ACTION_PAUSE else PlaybackService.ACTION_RESUME
                            context.startService(Intent(context, PlaybackService::class.java).apply { action = actionType })
                        },
                        onNextChapter = viewModel::nextChapter,
                        onPreviousChapter = viewModel::previousChapter,
                        onSpeedClick = viewModel::toggleSpeedDialog,
                        onSleepClick = viewModel::toggleSleepDialog
                    )
                }
            }
        }

        // Dialogs
        if (uiState.showChapterList) {
            ChapterListPage(
                chapters = uiState.chapters, currentIndex = uiState.currentChapterIndex,
                isPreCaching = uiState.isPreCaching, preCacheProgress = uiState.preCacheProgress,
                onChapterClick = { viewModel.playChapter(it); viewModel.hideDialogs() },
                onPreCacheClick = viewModel::preCacheBook, onCancelPreCacheClick = viewModel::cancelPreCache,
                onDismiss = viewModel::hideDialogs
            )
        }
        if (uiState.showVoiceDialog) {
            VoiceDialog(uiState.voice, { v -> viewModel.setVoice(v); viewModel.hideDialogs()
                context.startService(Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_PAUSE })
                uiState.book?.id?.let { bid ->
                    context.startService(Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY
                        putExtra(PlaybackService.EXTRA_BOOK_ID, bid)
                        putExtra(PlaybackService.EXTRA_CHAPTER_INDEX, uiState.currentChapterIndex)
                        putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, uiState.currentSentenceIndex)
                        putExtra(PlaybackService.EXTRA_VOICE, v)
                        putExtra(PlaybackService.EXTRA_SPEED, uiState.speed)
                    })
                }
            }, viewModel::hideDialogs)
        }
        if (uiState.showSleepDialog) {
            SleepTimerDialog(uiState.sleepTimerMinutes, uiState.sleepAtChapterEnd,
                { viewModel.setSleepTimer(it); viewModel.hideDialogs() },
                { viewModel.setSleepAtChapterEnd(); viewModel.hideDialogs() },
                { viewModel.cancelSleepTimer(); viewModel.hideDialogs() }, viewModel::hideDialogs)
        }
        if (uiState.showBookmarkList) {
            BookmarkListDialog(
                bookId = uiState.book?.id ?: -1L, chapters = uiState.chapters,
                currentChapterIndex = uiState.currentChapterIndex,
                onJumpToBookmark = { ci, si ->
                    context.startService(Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_PLAY
                        putExtra(PlaybackService.EXTRA_BOOK_ID, uiState.book!!.id)
                        putExtra(PlaybackService.EXTRA_CHAPTER_INDEX, ci)
                        putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, si)
                        putExtra(PlaybackService.EXTRA_VOICE, uiState.voice)
                        putExtra(PlaybackService.EXTRA_SPEED, uiState.speed)
                    })
                    viewModel.playChapter(ci); viewModel.hideDialogs()
                }, onDismiss = viewModel::hideDialogs
            )
        }
        if (uiState.showSpeedDialog) {
            SpeedBottomSheet(uiState.speed, { s ->
                viewModel.setSpeed(s)
                context.startService(Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_SET_SPEED
                    putExtra(PlaybackService.EXTRA_SPEED, s)
                })
                scope.launch { sheetState.hide() }; viewModel.hideDialogs()
            }, { scope.launch { sheetState.hide() }; viewModel.hideDialogs() }, sheetState)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SentenceList(
    sentences: List<com.tz.audiobook.parser.Sentence>,
    currentSentenceIndex: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    bookId: Long,
    isFullscreen: Boolean,
    onToggleBars: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var fontSizeSp by remember { mutableIntStateOf(SettingsPrefs.getFontSizeSp(context)) }
    var lineHeightMult by remember { mutableFloatStateOf(SettingsPrefs.getLineHeightMult(context)) }

    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("audiobook_settings", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "font_size_sp" -> fontSizeSp = SettingsPrefs.getFontSizeSp(context)
                "line_height_mult_x10" -> lineHeightMult = SettingsPrefs.getLineHeightMult(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(currentSentenceIndex) {
        if (currentSentenceIndex >= 0) {
            coroutineScope.launch { listState.animateScrollToItem(currentSentenceIndex.coerceAtLeast(0), -200) }
        }
    }

    if (sentences.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("点击播放按钮开始", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.pointerInput(Unit) {
                detectHorizontalDragGestures { _, drag -> if (drag < -150f) onSwipeLeft() else if (drag > 150f) onSwipeRight() }
            },
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            itemsIndexed(sentences, key = { i, _ -> "$i-$fontSizeSp-$lineHeightMult" }) { index, sentence ->
                val isCurrent = index == currentSentenceIndex
                val isPast = index < currentSentenceIndex
                val isBookmarked = SettingsPrefs.isBookmarked(context, bookId, currentChapterIndex, index)

                SentenceItem(
                    text = sentence.text, isCurrent = isCurrent, isPast = isPast, isBookmarked = isBookmarked,
                    fontSizeSp = fontSizeSp, lineHeightMult = lineHeightMult,
                    onClick = { onSentenceClick(index) },
                    onLongClick = { /* context menu handled inside */ },
                    onShare = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sentence.text) }, "分享句子"))
                    },
                    onToggleBookmark = {
                        if (isBookmarked) SettingsPrefs.removeBookmark(context, bookId, currentChapterIndex, index)
                        else SettingsPrefs.addBookmark(context, bookId, currentChapterIndex, index, sentence.text.take(20).let { if (sentence.text.length > 20) "$it..." else it })
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SentenceItem(
    text: String, isCurrent: Boolean, isPast: Boolean, isBookmarked: Boolean,
    fontSizeSp: Int, lineHeightMult: Float,
    onClick: () -> Unit, onLongClick: () -> Unit, onShare: () -> Unit, onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val fontSize = fontSizeSp.sp
    val lineHeight = fontSize * lineHeightMult
    val bgColor = when {
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
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(bgColor)
            .combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
            .padding(12.dp)
    ) {
        if (isBookmarked) Icon(Icons.Default.Bookmark, "已加书签", modifier = Modifier.size(14.dp).align(Alignment.TopEnd), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Text(text, fontSize = fontSize, color = textColor, lineHeight = lineHeight)
        DropdownMenu(showMenu, { showMenu = false }) {
            DropdownMenuItem(text = { Text(if (isBookmarked) "移除书签" else "添加书签") }, onClick = { showMenu = false; onToggleBookmark() }, leadingIcon = { Icon(if (isBookmarked) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, null) })
            DropdownMenuItem(text = { Text("分享") }, onClick = { showMenu = false; onShare() }, leadingIcon = { Icon(Icons.Default.Share, null) })
        }
    }
}

@Composable
private fun ControlBar(uiState: PlayerUiState, onPlayPause: () -> Unit, onNextChapter: () -> Unit, onPreviousChapter: () -> Unit, onSpeedClick: () -> Unit, onSleepClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onSpeedClick) { Text("${uiState.speed}x", fontSize = 14.sp) }
            IconButton(onPreviousChapter) { Icon(Icons.Default.SkipPrevious, "上一章", modifier = Modifier.size(36.dp)) }
            if (uiState.isLoading) CircularProgressIndicator(Modifier.size(56.dp))
            else FloatingActionButton(onPlayPause, shape = CircleShape) { Icon(if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (uiState.isPlaying) "暂停" else "播放", Modifier.size(32.dp)) }
            IconButton(onNextChapter) { Icon(Icons.Default.SkipNext, "下一章", Modifier.size(36.dp)) }
            IconButton(onSleepClick) { Icon(Icons.Default.Timer, "睡眠定时器", tint = if (uiState.sleepTimerMinutes > 0 || uiState.sleepAtChapterEnd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun startPlayback(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int, voice: String, speed: Float) {
    context.startService(Intent(context, PlaybackService::class.java).apply {
        action = PlaybackService.ACTION_PLAY
        putExtra(PlaybackService.EXTRA_BOOK_ID, bookId); putExtra(PlaybackService.EXTRA_CHAPTER_INDEX, chapterIndex)
        putExtra(PlaybackService.EXTRA_SENTENCE_INDEX, sentenceIndex); putExtra(PlaybackService.EXTRA_VOICE, voice); putExtra(PlaybackService.EXTRA_SPEED, speed)
    })
}

private fun formatTime(seconds: Int) = if (seconds / 60 > 0) "${seconds / 60}分${seconds % 60}秒" else "${seconds}秒"
private fun formatDuration(ms: Long): String { val s = (ms / 1000).toInt(); return if (s < 60) "${s}秒" else if (s % 60 > 0) "${s / 60}分${s % 60}秒" else "${s / 60}分钟" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterListPage(chapters: List<com.tz.audiobook.domain.model.Chapter>, currentIndex: Int, isPreCaching: Boolean, preCacheProgress: Float, onChapterClick: (Int) -> Unit, onPreCacheClick: () -> Unit, onCancelPreCacheClick: () -> Unit, onDismiss: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState((currentIndex - 3).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val filtered = if (searchQuery.isBlank()) chapters.mapIndexed { i, c -> i to c } else chapters.mapIndexed { i, c -> i to c }.filter { it.second.title.contains(searchQuery, true) }
    val progress = (currentIndex + 1).toFloat() / chapters.size.coerceAtLeast(1)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            TopAppBar(
                title = { Column { Text("目录"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("${currentIndex + 1}/${chapters.size}章", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("%.1f%%".format(progress * 100), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) } } },
                navigationIcon = { IconButton(onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton({ scope.launch { listState.animateScrollToItem(currentIndex.coerceAtLeast(0)) } }) { Icon(Icons.Default.MyLocation, "定位当前") } }
            )
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(2.dp), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant)
            OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp), placeholder = { Text("搜索章节") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (searchQuery.isNotEmpty()) IconButton({ searchQuery = "" }) { Icon(Icons.Default.Close, "清空") } }, singleLine = true, shape = MaterialTheme.shapes.medium)
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                if (isPreCaching) { Text("缓存中 ${"%.0f".format(preCacheProgress * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary); TextButton(onCancelPreCacheClick) { Text("取消", style = MaterialTheme.typography.labelSmall) } }
                else TextButton(onPreCacheClick) { Icon(Icons.Default.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("预缓存全书", style = MaterialTheme.typography.labelMedium) }
            }
            if (isPreCaching) LinearProgressIndicator({ preCacheProgress }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp), MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.surfaceVariant)
            if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("未找到匹配章节", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filtered.size) { idx ->
                    val (i, c) = filtered[idx]
                    val cur = i == currentIndex
                    Row(Modifier.fillMaxWidth().clickable { onChapterClick(i) }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (cur) { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                        Text(c.title, style = if (cur) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (c.wordCount > 0) Text(formatWordCount(c.wordCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    if (idx < filtered.size - 1) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

private fun formatWordCount(n: Int) = when { n >= 10000 -> "${n / 10000}万字"; n >= 1000 -> "${n / 1000}千字"; else -> "${n}字" }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SpeedBottomSheet(currentSpeed: Float, onSpeedSelected: (Float) -> Unit, onDismiss: () -> Unit, sheetState: SheetState) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text("播放速度", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(24.dp, 8.dp))
            FlowRow(Modifier.fillMaxWidth().padding(16.dp), Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), Arrangement.spacedBy(8.dp)) {
                speeds.forEach { FilterChip(it == currentSpeed, { onSpeedSelected(it) }, { Text("${it}x") }, Modifier.padding(horizontal = 4.dp)) }
            }
        }
    }
}

@Composable
private fun VoiceDialog(currentVoice: String, onVoiceSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var favs by remember { mutableStateOf(SettingsPrefs.getFavoriteVoices(context)) }
    val sorted = remember(favs) { EdgeTtsConstants.CHINESE_VOICES.filter { it.name in favs } + EdgeTtsConstants.CHINESE_VOICES.filter { it.name !in favs } }
    AlertDialog(onDismiss, title = { Text("选择配音") }, text = {
        LazyColumn {
            items(sorted.size) { idx ->
                val v = sorted[idx]
                Row(Modifier.fillMaxWidth().clickable { onVoiceSelected(v.name) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = v.name == currentVoice, onClick = { onVoiceSelected(v.name) })
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text("${v.displayName} (${v.gender})", style = MaterialTheme.typography.bodyMedium); Text(v.style, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton({ SettingsPrefs.toggleFavoriteVoice(context, v.name); favs = SettingsPrefs.getFavoriteVoices(context) }, Modifier.size(32.dp)) { Icon(if (v.name in favs) Icons.Default.Star else Icons.Default.StarBorder, if (v.name in favs) "取消收藏" else "收藏", tint = if (v.name in favs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("关闭") } })
}

@Composable
private fun SleepTimerDialog(currentMinutes: Int, sleepAtChapterEnd: Boolean, onSetTimer: (Int) -> Unit, onSetChapterEnd: () -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val opts = listOf(15 to "15分钟后", 30 to "30分钟后", 45 to "45分钟后", 60 to "60分钟后")
    AlertDialog(onDismiss, title = { Text("睡眠定时器") }, text = {
        Column {
            opts.forEach { (m, l) -> Row(Modifier.fillMaxWidth().clickable { onSetTimer(m) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentMinutes == m && !sleepAtChapterEnd, onClick = { onSetTimer(m) }); Spacer(Modifier.width(8.dp)); Text(l) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth().clickable { onSetChapterEnd() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = sleepAtChapterEnd, onClick = { onSetChapterEnd() }); Spacer(Modifier.width(8.dp)); Text("读完当前章节后暂停") }
            if (currentMinutes > 0 || sleepAtChapterEnd) { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Row(Modifier.fillMaxWidth().clickable { onCancel() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text("关闭定时器", color = MaterialTheme.colorScheme.error) } }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("关闭") } })
}

data class BookmarkItem(val chapterIndex: Int, val sentenceIndex: Int, val chapterTitle: String, val textPreview: String)

@Composable
private fun BookmarkListDialog(bookId: Long, chapters: List<com.tz.audiobook.domain.model.Chapter>, currentChapterIndex: Int, onJumpToBookmark: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var bookmarks by remember { mutableStateOf(emptyList<BookmarkItem>()) }
    LaunchedEffect(bookId) {
        bookmarks = SettingsPrefs.getBookmarks(context, bookId).mapNotNull { raw ->
            val parts = raw.split(":")
            if (parts.size >= 3) { val ci = parts[0].toIntOrNull() ?: return@mapNotNull null; val si = parts[1].toIntOrNull() ?: return@mapNotNull null; BookmarkItem(ci, si, chapters.getOrNull(ci)?.title ?: "第${ci + 1}章", parts.drop(2).joinToString(":")) } else null
        }.sortedByDescending { it.chapterIndex * 10000 + it.sentenceIndex }
    }
    AlertDialog(onDismiss, title = { Text("书签") }, text = {
        if (bookmarks.isEmpty()) Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.BookmarkBorder, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)); Spacer(Modifier.height(12.dp)); Text("暂无书签", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(4.dp)); Text("长按句子添加书签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
        else LazyColumn { items(bookmarks.size) { idx ->
            val b = bookmarks[idx]; val cur = b.chapterIndex == currentChapterIndex
            Row(Modifier.fillMaxWidth().clickable { onJumpToBookmark(b.chapterIndex, b.sentenceIndex) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmark, null, Modifier.size(20.dp), if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(b.chapterTitle, style = MaterialTheme.typography.labelMedium, color = if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(b.textPreview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                IconButton({ SettingsPrefs.removeBookmark(context, bookId, b.chapterIndex, b.sentenceIndex); bookmarks = SettingsPrefs.getBookmarks(context, bookId).mapNotNull { raw -> val p = raw.split(":"); if (p.size >= 3) { val c = p[0].toIntOrNull() ?: return@mapNotNull null; val s = p[1].toIntOrNull() ?: return@mapNotNull null; BookmarkItem(c, s, chapters.getOrNull(c)?.title ?: "第${c + 1}章", p.drop(2).joinToString(":")) } else null }.sortedByDescending { it.chapterIndex * 10000 + it.sentenceIndex } }, Modifier.size(36.dp)) { Icon(Icons.Default.Close, "删除书签", Modifier.size(18.dp), MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) }
            }
            if (idx < bookmarks.size - 1) HorizontalDivider(Modifier.padding(start = 32.dp))
        } }
    }, confirmButton = { TextButton(onDismiss) { Text("关闭") } })
}
