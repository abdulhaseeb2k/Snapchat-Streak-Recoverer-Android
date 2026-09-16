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

    fun clearUpdateState() {
        _updateState.value = UpdateCheckState.Idle
    }
}
