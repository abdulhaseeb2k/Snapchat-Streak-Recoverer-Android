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

sealed interface DownloadState {
    object Idle : DownloadState
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState
    data class ReadyToInstall(val apkFile: java.io.File) : DownloadState
    data class Error(val message: String) : DownloadState
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

    /**
     * Downloads the APK file to the app's cache directory with real-time byte progress.
     * Follows HTTP/HTTPS redirects (GitHub Releases redirect to AWS S3).
     */
    suspend fun downloadApk(
        context: android.content.Context,
        downloadUrl: String,
        version: String,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<java.io.File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val updatesDir = java.io.File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            // Clean up any stale partial downloads
            updatesDir.listFiles()?.forEach { it.delete() }

            val targetFile = java.io.File(updatesDir, "SSR_v${version}.apk")
            val tempFile = java.io.File(updatesDir, "SSR_v${version}.tmp")
            if (tempFile.exists()) tempFile.delete()

            var currentUrl = downloadUrl
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Snapchat-Streak-Recoverer-Android")
                }

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == 307 || status == 308
                ) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    currentUrl = newUrl
                    redirects++
                } else if (status == HttpURLConnection.HTTP_OK) {
                    break
                } else {
                    return@withContext Result.failure(Exception("HTTP error $status while downloading update."))
                }
            }

            val totalBytes = connection?.contentLengthLong ?: -1L
            val inputStream = connection?.inputStream ?: return@withContext Result.failure(Exception("Input stream null"))

            var downloadedBytes = 0L
            val buffer = ByteArray(16384)

            java.io.FileOutputStream(tempFile).use { output ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else -1f
                    onProgress(progress, downloadedBytes, totalBytes)
                }
                output.flush()
            }

            if (tempFile.renameTo(targetFile)) {
                Result.success(targetFile)
            } else {
                Result.success(tempFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Launches system package installer for the downloaded APK via FileProvider.
     */
    fun installApk(context: android.content.Context, apkFile: java.io.File) {
        if (!apkFile.exists()) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val permissionIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permissionIntent)
                return
            }
        }

        val apkUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    /**
     * Cleans up all downloaded APK files from cache to save storage space.
     */
    fun cleanupOldApks(context: android.content.Context) {
        try {
            val updatesDir = java.io.File(context.cacheDir, "updates")
            if (updatesDir.exists()) {
                updatesDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }
}
