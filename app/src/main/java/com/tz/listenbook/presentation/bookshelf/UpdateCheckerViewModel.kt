package com.tz.listenbook.presentation.bookshelf

import android.content.Intent
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
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "UpdateCheckerVM"
    }

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

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
                            downloadError = "下载失败: HTTP ${response.code}"
                        )
                        return@withContext
                    }

                    val body = response.body ?: throw RuntimeException("Empty response body")
                    val contentLength = body.contentLength()

                    val apkFile = File(context.cacheDir, "ListenBook-update.apk")
                    Log.d(TAG, "Saving to: ${apkFile.absolutePath}")

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (contentLength > 0) {
                                    _uiState.value = _uiState.value.copy(
                                        downloadProgress = downloaded.toFloat() / contentLength.toFloat()
                                    )
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Download complete: size=${apkFile.length()}")
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadProgress = 1f,
                        apkFile = apkFile
                    )

                    withContext(Dispatchers.Main) {
                        installApk(apkFile)
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: ${e.message}", e)
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadError = "下载失败: ${e.message}"
                    )
                }
            }
        }
    }

    fun installApk(apkFile: File) {
        Log.d(TAG, "installApk: exists=${apkFile.exists()}, size=${apkFile.length()}")
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            Log.d(TAG, "APK URI: $uri")

            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            _uiState.value = _uiState.value.copy(
                downloadError = "安装失败: ${e.message}"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        _uiState.value.apkFile?.delete()
    }
}
