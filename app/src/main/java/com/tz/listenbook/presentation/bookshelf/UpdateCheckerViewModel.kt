package com.tz.listenbook.presentation.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tz.listenbook.data.remote.github.GitHubUpdateChecker
import com.tz.listenbook.data.remote.github.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class UpdateUiState(
    val isChecking: Boolean = false,
    val updateInfo: ReleaseInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val apkFile: File? = null
)

@HiltViewModel
class UpdateCheckerViewModel @Inject constructor(
    private val updateChecker: GitHubUpdateChecker,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "UpdateCheckerVM"
    }

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    // Separate client for downloads with no WebSocket interference
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, downloadError = null)
            val info = updateChecker.checkForUpdate()
            _uiState.value = _uiState.value.copy(isChecking = false, updateInfo = info)
        }
    }

    fun clearUpdateInfo() {
        _uiState.value = _uiState.value.copy(updateInfo = null, apkFile = null, downloadError = null)
    }

    fun downloadAndInstall(apkUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.value = _uiState.value.copy(isDownloading = true, downloadProgress = 0f, downloadError = null, apkFile = null)

                try {
                    Log.d(TAG, "Downloading APK from: $apkUrl")
                    val request = Request.Builder().url(apkUrl).build()
                    val response = downloadClient.newCall(request).execute()

                    Log.d(TAG, "Response: code=${response.code}, content-length=${response.body?.contentLength()}")

                    if (!response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(
                            isDownloading = false,
                            downloadError = "下载失败: HTTP ${response.code} ${response.message}"
                        )
                        return@withContext
                    }

                    val body = response.body ?: throw RuntimeException("Empty response body")
                    val contentLength = body.contentLength()

                    val downloadDir = context.externalCacheDir ?: context.cacheDir
                    Log.d(TAG, "Download dir: ${downloadDir.absolutePath}, isExternal: ${context.externalCacheDir != null}")
                    val apkFile = File(downloadDir, "ListenBook-update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (contentLength > 0) {
                                    val progress = downloaded.toFloat() / contentLength.toFloat()
                                    _uiState.value = _uiState.value.copy(downloadProgress = progress)
                                }
                            }
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        apkFile = apkFile
                    )
                    Log.d(TAG, "Download complete: ${apkFile.absolutePath}, size=${apkFile.length()}")

                    // Launch system installer (must be on Main thread)
                    withContext(Dispatchers.Main) {
                        installApk(apkFile)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: ${e.javaClass.simpleName} - ${e.message}", e)
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadError = "下载失败: ${e.javaClass.simpleName} - ${e.message}"
                    )
                }
            }
        }
    }

    private fun installApk(apkFile: File) {
        Log.d(TAG, "installApk: ${apkFile.absolutePath}, exists=${apkFile.exists()}, size=${apkFile.length()}")
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
        }
        Log.d(TAG, "Starting installer intent with URI: $uri")
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start installer", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up downloaded APK
        _uiState.value.apkFile?.delete()
    }
}
