package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import com.snapstreakrecoverer.ssr.ui.theme.ThemeManager

class ViewModelFactory(
    private val dao: RecoveryDao,
    private val themeManager: ThemeManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(dao) as T
            modelClass.isAssignableFrom(FriendViewModel::class.java) -> FriendViewModel(dao) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(themeManager) as T
            modelClass.isAssignableFrom(RecoveryViewModel::class.java) -> RecoveryViewModel(dao) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
