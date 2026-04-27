package com.tz.listenbook.presentation.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tz.listenbook.domain.model.Book
import com.tz.listenbook.domain.model.ReadingProgress
import com.tz.listenbook.presentation.settings.SettingsPrefs

private fun getFileName(context: Context, uri: Uri): String {
    var name = "Unknown"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
    } catch (_: Exception) {}
    return name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShelfScreen(
    onBookClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: BookShelfViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableLongStateOf(-1L) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val updateChecker = androidx.hilt.navigation.compose.hiltViewModel<com.tz.listenbook.presentation.bookshelf.UpdateCheckerViewModel>()
    val updateUiState by updateChecker.uiState.collectAsState()

    // Auto-check for updates on first launch (if enabled)
    var autoChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoChecked && SettingsPrefs.isAutoUpdateCheck(context)) {
            autoChecked = true
            updateChecker.checkForUpdate()
        }
    }

    // Multi-file picker for batch import - filter to TXT/EPUB
    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val fileName = getFileName(context, uri)
                val ext = fileName.substringAfterLast('.', "").lowercase()
                if (ext in listOf("txt", "epub")) {
                    viewModel.importBook(uri, fileName)
                }
            }
        }
    }

    // Filter books by search query
    val filteredBooks = remember(uiState.books, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.books
        } else {
            uiState.books.filter { bookWithProgress ->
                bookWithProgress.book.title.contains(searchQuery, ignoreCase = true) ||
                bookWithProgress.book.author?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("搜索书名或作者") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text("我的书架")
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                        }
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                        IconButton(onClick = {
                            multiFilePicker.launch("*/*")
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "导入书籍")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                filteredBooks.isEmpty() -> {
                    if (searchQuery.isNotBlank()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "未找到 \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        EmptyBookShelf(
                            onAddClick = {
                                multiFilePicker.launch("*/*")
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = filteredBooks,
                            key = { it.book.id }
                        ) { bookWithProgress ->
                            BookCard(
                                book = bookWithProgress.book,
                                progress = bookWithProgress.progress,
                                onClick = { onBookClick(bookWithProgress.book.id) },
                                onLongClick = { showDeleteDialog = bookWithProgress.book.id }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.isImporting,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.importProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }

        uiState.error?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("提示") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("确定")
                    }
                }
            )
        }

        if (showDeleteDialog >= 0) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = -1L },
                title = { Text("删除书籍") },
                text = { Text("确定要删除这本书吗？相关音频缓存也会被清除。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBook(showDeleteDialog)
                            showDeleteDialog = -1L
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = -1L }) {
                        Text("取消")
                    }
                }
            )
        }

        // Update dialog (auto-check or manual)
        updateUiState.updateInfo?.let { info ->
            UpdateDialog(
                info = info,
                state = updateUiState,
                onDismiss = { updateChecker.clearUpdateInfo() },
                onDownloadClick = { updateChecker.downloadAndInstall(info.apkUrl) },
                onInstallClick = {
                    val apkFile = updateUiState.apkFile
                    Log.d("UpdateDialog", "Install clicked. File: ${apkFile?.absolutePath}, exists: ${apkFile?.exists()}")
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile!!
                        )
                        Log.d("UpdateDialog", "APK URI: $uri")
                        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                            data = uri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        if (context.packageManager.resolveActivity(intent, 0) != null) {
                            context.startActivity(intent)
                            Log.d("UpdateDialog", "Installer activity started")
                        } else {
                            Log.e("UpdateDialog", "No activity found to handle ACTION_INSTALL_PACKAGE")
                        }
                    } catch (e: Exception) {
                        Log.e("UpdateDialog", "Failed to start installer", e)
                    }
                },
                onBrowserClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.downloadUrl)))
                    updateChecker.clearUpdateInfo()
                }
            )
        }
    }
}

@Composable
private fun EmptyBookShelf(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Audiotrack,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "书架空空如也",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右上角 + 导入本地小说",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("导入书籍")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: Book,
    progress: ReadingProgress?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = book.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (progress != null) {
                Spacer(modifier = Modifier.height(4.dp))
                // Progress percentage based on chapter position
                val progressPercent = ((progress.currentChapterIndex + 1).toFloat() / book.totalChapters.coerceAtLeast(1) * 100).toInt()
                Text(
                    text = "已听至第${progress.currentChapterIndex + 1}章 (${progressPercent}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${book.totalChapters}章",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
internal fun UpdateDialog(
    info: com.tz.listenbook.data.remote.github.ReleaseInfo,
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit,
    onBrowserClick: () -> Unit
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

                // Download progress
                if (state.isDownloading || state.apkFile != null) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.apkFile != null) "下载完成，点击安装" else "下载中... ${(state.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                state.downloadError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            when {
                state.apkFile != null -> {
                    Button(onClick = onInstallClick) { Text("立即安装") }
                }
                state.isDownloading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                else -> {
                    Button(onClick = onDownloadClick) { Text("下载并安装") }
                }
            }
        },
        dismissButton = {
            if (!state.isDownloading) {
                Column {
                    TextButton(onClick = onBrowserClick) { Text("浏览器下载") }
                    TextButton(onClick = onDismiss) { Text("稍后再说") }
                }
            }
        }
    )
}
