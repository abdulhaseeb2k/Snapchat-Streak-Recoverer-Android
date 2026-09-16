package com.snapstreakrecoverer.ssr.update

import com.snapstreakrecoverer.ssr.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String
)

sealed interface UpdateCheckState {
    object Idle : UpdateCheckState
    object Checking : UpdateCheckState
    data class Available(val info: AppUpdateInfo) : UpdateCheckState
    data class UpToDate(val currentVersion: String) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

fun isNewerVersion(latest: String, current: String): Boolean {
    val cleanLatest = latest.trim().removePrefix("v").removePrefix("V")
    val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
    val latestParts = cleanLatest.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    val currentParts = cleanCurrent.split(".").mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
    val maxLen = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until maxLen) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android/releases/latest"
    private const val DEFAULT_RELEASE_URL = "https://github.com/abdulhaseeb2k/Snapchat-Streak-Recoverer-Android/releases/latest"

    suspend fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Snapchat-Streak-Recoverer-Android")
            }

            val responseCode = conn.responseCode
            if (responseCode == 404) {
                return@withContext Result.failure(Exception("No GitHub releases published yet."))
            } else if (responseCode != 200) {
                return@withContext Result.failure(Exception("GitHub API returned error: $responseCode"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trim()
            val cleanTag = tagName.removePrefix("v").removePrefix("V")
            val releaseName = json.optString("name", "Version $cleanTag")
            val releaseNotes = json.optString("body", "")
            val htmlUrl = json.optString("html_url", DEFAULT_RELEASE_URL)

            var apkDownloadUrl = ""
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
            }

            if (apkDownloadUrl.isEmpty()) {
                apkDownloadUrl = htmlUrl
            }

            val isAvailable = isNewerVersion(cleanTag, currentVersion)

            Result.success(
                AppUpdateInfo(
                    isUpdateAvailable = isAvailable,
                    latestVersion = cleanTag,
                    currentVersion = currentVersion,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkDownloadUrl,
                    releasePageUrl = htmlUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
