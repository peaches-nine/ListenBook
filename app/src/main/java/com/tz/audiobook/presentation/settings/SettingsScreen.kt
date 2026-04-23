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
    private const val KEY_DARK_MODE = "dark_mode" // "system", "light", "dark"

    fun isBackgroundPlayEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BG_PLAY, false)
    }

    fun setBackgroundPlayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BG_PLAY, enabled)
            .apply()
    }

    fun getDarkMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DARK_MODE, "system") ?: "system"
    }

    fun setDarkMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK_MODE, mode)
            .apply()
    }

    private const val KEY_FAVORITE_VOICES = "favorite_voices"
    private const val KEY_FONT_SIZE = "font_size" // 0 = small, 1 = medium, 2 = large
    private const val KEY_LINE_HEIGHT = "line_height" // 0 = compact, 1 = normal, 2 = relaxed

    fun getFontSize(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FONT_SIZE, 1) // default: medium
    }

    fun setFontSize(context: Context, size: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FONT_SIZE, size)
            .apply()
    }

    fun getLineHeight(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LINE_HEIGHT, 1) // default: normal
    }

    fun setLineHeight(context: Context, height: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LINE_HEIGHT, height)
            .apply()
    }

    fun getFavoriteVoices(context: Context): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITE_VOICES, emptySet()) ?: emptySet()
    }

    fun setFavoriteVoices(context: Context, voices: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVORITE_VOICES, voices)
            .apply()
    }

    fun toggleFavoriteVoice(context: Context, voiceName: String) {
        val favorites = getFavoriteVoices(context).toMutableSet()
        if (voiceName in favorites) {
            favorites.remove(voiceName)
        } else {
            favorites.add(voiceName)
        }
        setFavoriteVoices(context, favorites)
    }

    private const val KEY_BOOKMARKS = "bookmarks_%d" // bookId

    fun getBookmarks(context: Context, bookId: Long): Set<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_BOOKMARKS.format(bookId), emptySet()) ?: emptySet()
    }

    fun addBookmark(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int, textPreview: String) {
        val bookmarks = getBookmarks(context, bookId).toMutableSet()
        bookmarks.add("$chapterIndex:$sentenceIndex:$textPreview")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_BOOKMARKS.format(bookId), bookmarks)
            .apply()
    }

    fun removeBookmark(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int) {
        val bookmarks = getBookmarks(context, bookId).toMutableSet()
        bookmarks.removeAll { it.startsWith("$chapterIndex:$sentenceIndex:") }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_BOOKMARKS.format(bookId), bookmarks)
            .apply()
    }

    fun isBookmarked(context: Context, bookId: Long, chapterIndex: Int, sentenceIndex: Int): Boolean {
        return getBookmarks(context, bookId).any { it.startsWith("$chapterIndex:$sentenceIndex:") }
    }

    fun setBookmarks(context: Context, bookId: Long, bookmarks: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_BOOKMARKS.format(bookId), bookmarks)
            .apply()
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

    // File pickers for backup
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportData(context, uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importData(context, uri)
        }
    }

    LaunchedEffect(uiState) {
        // Refresh triggered by viewModel
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Background play toggle
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
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
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
                        onCheckedChange = {
                            bgPlayEnabled = it
                            SettingsPrefs.setBackgroundPlayEnabled(context, it)
                        }
                    )
                }
                HorizontalDivider()
            }

            // Dark mode setting
            item {
                var darkMode by remember { mutableStateOf(SettingsPrefs.getDarkMode(context)) }
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "深色模式", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
                        options.forEach { (value, label) ->
                            FilterChip(
                                selected = darkMode == value,
                                onClick = {
                                    darkMode = value
                                    SettingsPrefs.setDarkMode(context, value)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            // Reading settings
            item {
                var fontSize by remember { mutableStateOf(SettingsPrefs.getFontSize(context)) }
                var lineHeight by remember { mutableStateOf(SettingsPrefs.getLineHeight(context)) }
                Column(modifier = Modifier.padding(16.dp)) {
                    // Font size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "字体大小", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "小", 1 to "中", 2 to "大").forEach { (value, label) ->
                            FilterChip(
                                selected = fontSize == value,
                                onClick = {
                                    fontSize = value
                                    SettingsPrefs.setFontSize(context, value)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Line height
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FormatLineSpacing,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "行间距", style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "紧凑", 1 to "标准", 2 to "宽松").forEach { (value, label) ->
                            FilterChip(
                                selected = lineHeight == value,
                                onClick = {
                                    lineHeight = value
                                    SettingsPrefs.setLineHeight(context, value)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            // Cache section header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "音频缓存",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = "共 ${formatSize(uiState.totalCacheSize)}",
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
                            .clickable {
                                deleteBookId = bookCache.bookId
                                deleteBookTitle = bookCache.bookTitle
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookCache.bookTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                text = formatSize(bookCache.cacheSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            deleteBookId = bookCache.bookId
                            deleteBookTitle = bookCache.bookTitle
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除缓存",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else if (!uiState.isLoading) {
                item {
                    Text(
                        text = "暂无缓存",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Clear all cache
            item {
                HorizontalDivider()
                TextButton(
                    onClick = { showClearAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清除全部缓存", color = MaterialTheme.colorScheme.error)
                }
            }

            // Backup section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "数据备份",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "导出阅读进度、设置、书签等数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                                    .format(java.util.Date())
                                exportLauncher.launch("audiobook_backup_$timestamp.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导出")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导入")
                        }
                    }
                }
            }
        }

        // Clear single book cache dialog
        if (deleteBookId >= 0) {
            AlertDialog(
                onDismissRequest = { deleteBookId = -1 },
                title = { Text("删除缓存") },
                text = { Text("确定要清除《$deleteBookTitle》的音频缓存吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearBookCache(deleteBookId)
                            deleteBookId = -1
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteBookId = -1 }) {
                        Text("取消")
                    }
                }
            )
        }

        // Clear all cache dialog
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = { Text("清除全部缓存") },
                text = { Text("确定要清除所有音频缓存吗？下次播放时需要重新生成。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllCache()
                            showClearAllDialog = false
                        }
                    ) {
                        Text("清除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // Export result dialog
        uiState.exportMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.clearExportMessage() },
                title = { Text("导出") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearExportMessage() }) {
                        Text("确定")
                    }
                }
            )
        }

        // Import result dialog
        uiState.importMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { viewModel.clearImportMessage() },
                title = { Text("导入") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearImportMessage()
                        // Refresh settings after import
                        bgPlayEnabled = SettingsPrefs.isBackgroundPlayEnabled(context)
                    }) {
                        Text("确定")
                    }
                }
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024))
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
