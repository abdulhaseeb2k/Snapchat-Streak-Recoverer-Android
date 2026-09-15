package com.snapstreakrecoverer.ssr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.snapstreakrecoverer.ssr.auth.AuthManager
import com.snapstreakrecoverer.ssr.data.RecoveryDao
import com.snapstreakrecoverer.ssr.sync.SyncManager
import com.snapstreakrecoverer.ssr.ui.theme.ThemeManager

class ViewModelFactory(
    private val dao: RecoveryDao,
    private val themeManager: ThemeManager,
    private val syncManager: SyncManager? = null,
    private val authManager: AuthManager? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(dao, syncManager) as T
            modelClass.isAssignableFrom(FriendViewModel::class.java) -> FriendViewModel(dao, syncManager) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(themeManager, syncManager) as T
            modelClass.isAssignableFrom(RecoveryViewModel::class.java) -> RecoveryViewModel(dao) as T
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(authManager ?: AuthManager()) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
