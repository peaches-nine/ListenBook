package com.tz.audiobook.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

object SettingsPrefs {
    private const val PREFS_NAME = "audiobook_settings"
    private const val KEY_BG_PLAY = "background_play"
    private const val KEY_DARK_MODE = "dark_mode"

    fun isBackgroundPlayEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BG_PLAY, false)
    }

    fun setBackgroundPlayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BG_PLAY, enabled).apply()
    }

    fun getDarkMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DARK_MODE, "system") ?: "system"
    }

    fun setDarkMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DARK_MODE, mode).apply()
    }

    private const val KEY_FAVORITE_VOICES = "favorite_voices"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_LINE_HEIGHT = "line_height"

    fun getFontSize(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FONT_SIZE, 1)
    }

    fun setFontSize(context: Context, size: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FONT_SIZE, size).apply()
    }

    fun getLineHeight(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LINE_HEIGHT, 1)
    }

    fun setLineHeight(context: Context, height: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LINE_HEIGHT, height).apply()
    }

    fun getFavoriteVoices(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITE_VOICES, emptySet()) ?: emptySet()
    }

    fun setFavoriteVoices(context: Context, voices: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_FAVORITE_VOICES, voices).apply()
    }

    fun toggleFavoriteVoice(context: Context, voiceName: String) {
        val favorites = getFavoriteVoices(context).toMutableSet()
        if (voiceName in favorites) favorites.remove(voiceName) else favorites.add(voiceName)
        setFavoriteVoices(context, favorites)
    }

    private const val KEY_BOOKMARKS = "bookmarks_%d"

    fun getBookmarks(context: Context, bookId: Long): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_BOOKMARKS.format(bookId), emptySet()) ?: emptySet()
    }

    fun addBookmark(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int, textPreview: String) {
        val bookmarks = getBookmarks(context, bookId).toMutableSet()
        bookmarks.add("$chapterIndex:$sentenceIndex:$textPreview")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_BOOKMARKS.format(bookId), bookmarks).apply()
    }

    fun removeBookmark(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int) {
        val bookmarks = getBookmarks(context, bookId).toMutableSet()
        bookmarks.removeAll { it.startsWith("$chapterIndex:$sentenceIndex:") }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_BOOKMARKS.format(bookId), bookmarks).apply()
    }

    fun isBookmarked(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int): Boolean {
        return getBookmarks(context, bookId).any { it.startsWith("$chapterIndex:$sentenceIndex:") }
    }

    fun setBookmarks(context: Context, bookId: Long, bookmarks: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_BOOKMARKS.format(bookId), bookmarks).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var bgPlayEnabled by remember { mutableStateOf(SettingsPrefs.isBackgroundPlayEnabled(context)) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var deleteBookId by remember { mutableLongStateOf(-1) }
    var deleteBookTitle by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) viewModel.exportData(context, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importData(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // --- 外观设置 ---
            item {
                SettingSectionHeader(title = "外观")
            }

            // 深色模式
            item {
                var darkMode by remember { mutableStateOf(SettingsPrefs.getDarkMode(context)) }
                SettingChipRow(
                    icon = Icons.Default.DarkMode,
                    title = "深色模式",
                    options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                    selected = darkMode,
                    onSelect = { darkMode = it; SettingsPrefs.setDarkMode(context, it) }
                )
                HorizontalDivider()
            }

            // 字体大小
            item {
                var fontSize by remember { mutableStateOf(SettingsPrefs.getFontSize(context)) }
                SettingChipRow(
                    icon = Icons.Default.TextFields,
                    title = "字体大小",
                    options = listOf(0 to "小", 1 to "中", 2 to "大"),
                    selected = fontSize,
                    onSelect = { fontSize = it; SettingsPrefs.setFontSize(context, it) }
                )
                HorizontalDivider()
            }

            // 行间距
            item {
                var lineHeight by remember { mutableStateOf(SettingsPrefs.getLineHeight(context)) }
                SettingChipRow(
                    icon = Icons.Default.FormatLineSpacing,
                    title = "行间距",
                    options = listOf(0 to "紧凑", 1 to "标准", 2 to "宽松"),
                    selected = lineHeight,
                    onSelect = { lineHeight = it; SettingsPrefs.setLineHeight(context, it) }
                )
                HorizontalDivider()
            }

            // --- 播放设置 ---
            item { SettingSectionHeader(title = "播放") }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            bgPlayEnabled = !bgPlayEnabled
                            SettingsPrefs.setBackgroundPlayEnabled(context, bgPlayEnabled)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "后台继续播放", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (bgPlayEnabled) "切到后台时继续播放音频" else "切到后台时暂停播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = bgPlayEnabled,
                        onCheckedChange = { bgPlayEnabled = it; SettingsPrefs.setBackgroundPlayEnabled(context, it) }
                    )
                }
                HorizontalDivider()
            }

            // --- 存储 ---
            item { SettingSectionHeader(title = "存储") }

            // Cache section header with total
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("音频缓存", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        text = formatSize(uiState.totalCacheSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Book cache list
            if (uiState.bookCaches.isNotEmpty()) {
                items(uiState.bookCaches, key = { it.bookId }) { bookCache ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteBookId = bookCache.bookId; deleteBookTitle = bookCache.bookTitle }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = bookCache.bookTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(text = formatSize(bookCache.cacheSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleteBookId = bookCache.bookId; deleteBookTitle = bookCache.bookTitle }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除缓存", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else if (!uiState.isLoading) {
                item {
                    Text(text = "暂无缓存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }

            item {
                HorizontalDivider()
                TextButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清除全部缓存", color = MaterialTheme.colorScheme.error)
                }
            }

            // --- 数据 ---
            item { SettingSectionHeader(title = "数据") }

            // Export
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                            exportLauncher.launch("audiobook_backup_$timestamp.json")
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("导出数据", style = MaterialTheme.typography.bodyLarge)
                        Text("导出阅读进度、设置、书签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }

            // Import
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch(arrayOf("application/json")) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("导入数据", style = MaterialTheme.typography.bodyLarge)
                        Text("从备份文件恢复数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Dialogs
        if (deleteBookId >= 0) {
            AlertDialog(
                onDismissRequest = { deleteBookId = -1 },
                title = { Text("删除缓存") },
                text = { Text("确定要清除《$deleteBookTitle》的音频缓存吗？") },
                confirmButton = { TextButton(onClick = { viewModel.clearBookCache(deleteBookId); deleteBookId = -1 }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { deleteBookId = -1 }) { Text("取消") } }
            )
        }

        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = { Text("清除全部缓存") },
                text = { Text("确定要清除所有音频缓存吗？下次播放时需要重新生成。") },
                confirmButton = { TextButton(onClick = { viewModel.clearAllCache(); showClearAllDialog = false }) { Text("清除", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showClearAllDialog = false }) { Text("取消") } }
            )
        }

        uiState.exportMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.clearExportMessage() },
                title = { Text("导出") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = { viewModel.clearExportMessage() }) { Text("确定") } }
            )
        }

        uiState.importMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.clearImportMessage() },
                title = { Text("导入") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearImportMessage()
                        bgPlayEnabled = SettingsPrefs.isBackgroundPlayEnabled(context)
                    }) { Text("确定") }
                }
            )
        }
    }
}

@Composable
private fun SettingSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun <T> SettingChipRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024))
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
