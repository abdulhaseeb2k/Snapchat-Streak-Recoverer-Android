package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.sync.SyncManager
import com.snapstreakrecoverer.ssr.ui.theme.ThemeManager
import com.snapstreakrecoverer.ssr.ui.theme.ThemeSelection
import com.snapstreakrecoverer.ssr.update.UpdateCheckState
import com.snapstreakrecoverer.ssr.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val themeManager: ThemeManager,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    val themeSelection: StateFlow<ThemeSelection> = themeManager.themeSelection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSelection.SYSTEM)

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    private val _downloadState = MutableStateFlow<com.snapstreakrecoverer.ssr.update.DownloadState>(com.snapstreakrecoverer.ssr.update.DownloadState.Idle)
    val downloadState: StateFlow<com.snapstreakrecoverer.ssr.update.DownloadState> = _downloadState.asStateFlow()

    fun setThemeSelection(selection: ThemeSelection) {
        viewModelScope.launch {
            themeManager.setThemeSelection(selection)
            syncManager?.syncTheme(selection.name)
        }
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateCheckState.Checking) return
        _updateState.value = UpdateCheckState.Checking
        viewModelScope.launch {
            val result = UpdateManager.checkForUpdate()
            result.fold(
                onSuccess = { info ->
                    if (info.isUpdateAvailable) {
                        _updateState.value = UpdateCheckState.Available(info)
                    } else {
                        _updateState.value = UpdateCheckState.UpToDate(info.currentVersion)
                    }
                },
                onFailure = { error ->
                    _updateState.value = UpdateCheckState.Error(error.message ?: "Failed to check for updates")
                }
            )
        }
    }

    fun startDownload(context: android.content.Context, downloadUrl: String, version: String) {
        if (_downloadState.value is com.snapstreakrecoverer.ssr.update.DownloadState.Downloading) return
        _downloadState.value = com.snapstreakrecoverer.ssr.update.DownloadState.Downloading(0f, 0L, 0L)
        viewModelScope.launch {
            val result = UpdateManager.downloadApk(
                context = context,
                downloadUrl = downloadUrl,
                version = version,
                onProgress = { progress, downloaded, total ->
                    _downloadState.value = com.snapstreakrecoverer.ssr.update.DownloadState.Downloading(progress, downloaded, total)
                }
            )
            result.fold(
                onSuccess = { file ->
                    _downloadState.value = com.snapstreakrecoverer.ssr.update.DownloadState.ReadyToInstall(file)
                },
                onFailure = { error ->
                    _downloadState.value = com.snapstreakrecoverer.ssr.update.DownloadState.Error(error.localizedMessage ?: "Download failed")
                }
            )
        }
    }

    fun installApk(context: android.content.Context, apkFile: java.io.File) {
        UpdateManager.installApk(context, apkFile)
    }

    fun dismissDownload(context: android.content.Context? = null) {
        _downloadState.value = com.snapstreakrecoverer.ssr.update.DownloadState.Idle
        context?.let { UpdateManager.cleanupOldApks(it) }
    }

    fun clearUpdateState() {
        _updateState.value = UpdateCheckState.Idle
    }
}
