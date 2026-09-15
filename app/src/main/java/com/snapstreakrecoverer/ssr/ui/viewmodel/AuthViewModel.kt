package com.snapstreakrecoverer.ssr.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapstreakrecoverer.ssr.auth.AuthManager
import com.snapstreakrecoverer.ssr.auth.AuthState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val authManager: AuthManager) : ViewModel() {
    val authState: StateFlow<AuthState> = authManager.authState

    fun signIn(context: Context) {
        viewModelScope.launch {
            authManager.signInWithGoogle(context)
        }
    }

    fun signOut() {
        authManager.signOut()
    }

    fun clearError() {
        authManager.clearError()
    }
}
