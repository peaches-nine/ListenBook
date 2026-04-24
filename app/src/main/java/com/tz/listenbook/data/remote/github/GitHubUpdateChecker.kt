package com.tz.listenbook.data.remote.github

import android.util.Log
import com.tz.listenbook.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseBody: String,
    val downloadUrl: String,
    val apkUrl: String,
    val releaseDate: String
)


@Singleton
class GitHubUpdateChecker @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GitHubUpdateChecker"
        private const val API_URL = "https://api.github.com/repos/peaches-nine/ListenBook/releases/latest"
    }

    suspend fun checkForUpdate(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(API_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch release: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val tagName = json.getString("tag_name") // e.g. "v0.1.0"
            val releaseName = json.optString("name", "")
            val releaseBody = json.optString("body", "")
            val htmlUrl = json.getString("html_url")
            val publishedAt = json.getString("published_at")

            val versionName = tagName.removePrefix("v")
            val versionCode = parseVersionCode(versionName)

            // Parse APK direct download URL from release assets
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }
            // Fallback to GitHub releases download pattern
            if (apkUrl.isEmpty()) {
                apkUrl = "https://github.com/peaches-nine/ListenBook/releases/download/$tagName/ListenBook-$versionName.apk"
            }

            Log.d(TAG, "Latest: $tagName ($versionCode), current: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

            if (versionCode > BuildConfig.VERSION_CODE) {
                return@withContext ReleaseInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    releaseBody = releaseBody.ifBlank { releaseName },
                    downloadUrl = htmlUrl,
                    apkUrl = apkUrl,
                    releaseDate = publishedAt.take(10) // "2024-01-15"
                )
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update", e)
            null
        }
    }

    /**
     * Parse version string like "0.1.0" into integer code 100
     * Matches the build.gradle.kts calculation: MAJOR*10000 + MINOR*100 + PATCH
     */
    private fun parseVersionCode(version: String): Int {
        val parts = version.split(".")
        return try {
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            major * 10000 + minor * 100 + patch
        } catch (_: Exception) {
            0
        }
    }
}
