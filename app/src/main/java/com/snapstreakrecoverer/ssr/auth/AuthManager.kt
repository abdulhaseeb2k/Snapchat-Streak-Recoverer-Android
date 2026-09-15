package com.snapstreakrecoverer.ssr.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    object Unauthenticated : AuthState
    object Loading : AuthState
    data class Authenticated(val user: FirebaseUser) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthManager(
    private val firebaseAuth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull(),
    private val webClientId: String = "1073400338462-117lvbsvub9gt2bua0akr76avdj4vi4j.apps.googleusercontent.com"
) {
    private val _authState = MutableStateFlow<AuthState>(
        firebaseAuth?.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        firebaseAuth?.addAuthStateListener { auth ->
            _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
        }
    }

    suspend fun signInWithGoogle(context: Context): Result<FirebaseUser> {
        val auth = firebaseAuth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        _authState.value = AuthState.Loading
        return try {
            val credentialManager = CredentialManager.create(context)
            val clientId = if (webClientId.isNotEmpty()) webClientId else "dummy-client-id"
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context = context, request = request)
            val credential = response.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                val err = "Unsupported credential received"
                _authState.value = AuthState.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: GetCredentialCancellationException) {
            _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
            Result.failure(e)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "Failed to sign in with Google"
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            firebaseAuth?.signOut()
            _authState.value = AuthState.Unauthenticated
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.localizedMessage ?: "Error signing out")
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = firebaseAuth?.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
        }
    }
}
