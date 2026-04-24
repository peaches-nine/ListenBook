package com.tz.listenbook.presentation.settings

import android.content.Intent
import android.net.Uri
import android.content.Context
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
import androidx.compose.ui.unit.sp
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
    private const val KEY_FONT_SIZE_SP = "font_size_sp"
    private const val KEY_LINE_HEIGHT_MULT = "line_height_mult_x10"

    fun getFontSizeSp(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FONT_SIZE_SP, 16)
    }

    fun setFontSizeSp(context: Context, size: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FONT_SIZE_SP, size.coerceIn(12, 28)).apply()
    }

    fun getLineHeightMult(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LINE_HEIGHT_MULT, 15) / 10f
    }

    fun setLineHeightMult(context: Context, mult: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LINE_HEIGHT_MULT, (mult * 10).toInt().coerceIn(10, 30)).apply()
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

    private const val KEY_AUTO_UPDATE = "auto_update_check"

    fun isAutoUpdateCheck(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE, true)
    }

    fun setAutoUpdateCheck(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
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
    viewModel: SettingsViewModel = hiltViewModel(),
    updateChecker: com.tz.listenbook.presentation.bookshelf.UpdateCheckerViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val updateUiState by updateChecker.uiState.collectAsState()
    var bgPlayEnabled by remember { mutableStateOf(SettingsPrefs.isBackgroundPlayEnabled(context)) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var deleteBookId by remember { mutableLongStateOf(-1) }
    var deleteBookTitle by remember { mutableStateOf("") }
    val versionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" } catch (_: Exception) { "1.0" }
    }

    // Lift font settings state to parent level for preview
    var fontSize by remember { mutableIntStateOf(SettingsPrefs.getFontSizeSp(context)) }
    var lineHeight by remember { mutableFloatStateOf(SettingsPrefs.getLineHeightMult(context)) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> if (uri != null) viewModel.exportData(context, uri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) viewModel.importData(context, uri) }

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
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 外观
            item { SectionHeader("外观") }

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

            item {
                SettingSliderRow(
                    icon = Icons.Default.TextFields,
                    title = "字体大小",
                    value = fontSize,
                    valueRange = 12..28,
                    valueText = "${fontSize}sp",
                    onValueChange = { fontSize = it; SettingsPrefs.setFontSizeSp(context, it) }
                )
                HorizontalDivider()
            }

            item {
                SettingSliderRow(
                    icon = Icons.Default.FormatLineSpacing,
                    title = "行间距",
                    value = (lineHeight * 10).toInt(),
                    valueRange = 10..30,
                    valueText = String.format("%.1fx", lineHeight),
                    onValueChange = { lineHeight = it / 10f; SettingsPrefs.setLineHeightMult(context, it / 10f) }
                )
            }

            // Preview section - uses the same fontSize/lineHeight state
            item {
                TextPreviewCard(fontSize = fontSize, lineHeightMult = lineHeight)
                HorizontalDivider()
            }

            // 播放
            item { SectionHeader("播放") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        bgPlayEnabled = !bgPlayEnabled
                        SettingsPrefs.setBackgroundPlayEnabled(context, bgPlayEnabled)
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("后台继续播放", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (bgPlayEnabled) "切到后台时继续播放音频" else "切到后台时暂停播放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(bgPlayEnabled, onCheckedChange = { bgPlayEnabled = it; SettingsPrefs.setBackgroundPlayEnabled(context, it) })
                }
                HorizontalDivider()
            }

            // 存储
            item { SectionHeader("存储") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("音频缓存", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(formatSize(uiState.totalCacheSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (uiState.bookCaches.isNotEmpty()) {
                items(uiState.bookCaches, key = { it.bookId }) { cache ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { deleteBookId = cache.bookId; deleteBookTitle = cache.bookTitle }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cache.bookTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(formatSize(cache.cacheSize), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleteBookId = cache.bookId; deleteBookTitle = cache.bookTitle }) {
                            Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            } else if (!uiState.isLoading) {
                item { Text("暂无缓存", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }

            item {
                HorizontalDivider()
                TextButton(onClick = { showClearAllDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("清除全部缓存", color = MaterialTheme.colorScheme.error)
                }
            }

            // 数据
            item { SectionHeader("数据") }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                        exportLauncher.launch("audiobook_backup_$ts.json")
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("导出数据", style = MaterialTheme.typography.bodyLarge)
                        Text("导出阅读进度、设置、书签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { importLauncher.launch(arrayOf("application/json")) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("导入数据", style = MaterialTheme.typography.bodyLarge)
                        Text("从备份文件恢复数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 关于
            item { SectionHeader("关于") }

            item {
                var autoUpdate by remember { mutableStateOf(SettingsPrefs.isAutoUpdateCheck(context)) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        autoUpdate = !autoUpdate
                        SettingsPrefs.setAutoUpdateCheck(context, autoUpdate)
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("自动检查更新", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (autoUpdate) "启动时自动检查新版本" else "仅在手动点击时检查",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(autoUpdate, onCheckedChange = { autoUpdate = it; SettingsPrefs.setAutoUpdateCheck(context, it) })
                }
                HorizontalDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { updateChecker.checkForUpdate() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("检查更新", style = MaterialTheme.typography.bodyLarge)
                        Text("当前版本 $versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (updateUiState.isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

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

        uiState.exportMessage?.let { msg ->
            AlertDialog(onDismissRequest = { viewModel.clearExportMessage() }, title = { Text("导出") }, text = { Text(msg) }, confirmButton = { TextButton(onClick = { viewModel.clearExportMessage() }) { Text("确定") } })
        }

        uiState.importMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearImportMessage() },
                title = { Text("导入") },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = { viewModel.clearImportMessage(); bgPlayEnabled = SettingsPrefs.isBackgroundPlayEnabled(context) }) { Text("确定") } }
            )
        }

        // Update dialog
        updateUiState.updateInfo?.let { info ->
            com.tz.listenbook.presentation.bookshelf.UpdateDialog(
                info = info,
                state = updateUiState,
                onDismiss = { updateChecker.clearUpdateInfo() },
                onDownloadClick = { updateChecker.downloadAndInstall(info.apkUrl) },
                onInstallClick = {
                    val apkFile = updateUiState.apkFile
                    if (apkFile != null && apkFile.exists()) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile
                        )
                        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                            data = uri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                            }
                        }
                        context.startActivity(intent)
                    }
                },
                onBrowserClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                    updateChecker.clearUpdateInfo()
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
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
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, l) -> FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(l) }) }
        }
    }
}

@Composable
private fun SettingSliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: Int,
    valueRange: IntRange,
    valueText: String,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = valueRange.last - valueRange.first - 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0)
    if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024))
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

@Composable
private fun TextPreviewCard(fontSize: Int, lineHeightMult: Float) {
    val previewText = "天下风云出我辈，一入江湖岁月催。\n皇图霸业谈笑中，不胜人生一场醉。\n提剑跨骑挥鬼雨，白骨如山鸟惊飞。"
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("预览效果", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                text = previewText,
                fontSize = fontSize.sp,
                lineHeight = fontSize.sp * lineHeightMult
            )
        }
    }
}

@Composable
private fun UpdateDialog(
    info: com.tz.listenbook.data.remote.github.ReleaseInfo,
    onDismiss: () -> Unit,
    onUpdateClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.versionName}") },
        text = {
            Column {
                Text(
                    "发布日期: ${info.releaseDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = info.releaseBody.ifBlank { "请查看 GitHub 获取更新详情" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUpdateClick(); onDismiss() }) {
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        }
    )
}