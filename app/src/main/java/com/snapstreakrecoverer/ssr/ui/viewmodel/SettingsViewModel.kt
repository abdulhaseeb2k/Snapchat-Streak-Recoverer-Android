package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.ui.theme.ThemeManager
import com.snapstreakrecoverer.ssr.ui.theme.ThemeSelection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.snapstreakrecoverer.ssr.sync.SyncManager

class SettingsViewModel(
    private val themeManager: ThemeManager,
    private val syncManager: SyncManager? = null
) : ViewModel() {

    val themeSelection: StateFlow<ThemeSelection> = themeManager.themeSelection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSelection.SYSTEM)

    fun setThemeSelection(selection: ThemeSelection) {
        viewModelScope.launch {
            themeManager.setThemeSelection(selection)
            syncManager?.syncTheme(selection.name)
        }
    }
}
