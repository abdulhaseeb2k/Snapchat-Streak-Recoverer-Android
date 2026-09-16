package com.snapstreakrecoverer.ssr.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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

        val activity = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(activity)
        val clientId = if (webClientId.isNotEmpty()) webClientId else "dummy-client-id"

        // Attempt 1: GetSignInWithGoogleOption (Official explicit button flow, works reliably on Android 14+ / OneUI)
        try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val response = credentialManager.getCredential(context = activity, request = request)
            val user = authenticateWithCredential(auth, response.credential)
            _authState.value = AuthState.Authenticated(user)
            return Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
            return Result.failure(e)
        } catch (firstAttemptException: Exception) {
            // Attempt 2: Fallback to GetGoogleIdOption (Standard compatibility flow)
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(false)
                    .build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val fallbackResponse = credentialManager.getCredential(context = activity, request = fallbackRequest)
                val user = authenticateWithCredential(auth, fallbackResponse.credential)
                _authState.value = AuthState.Authenticated(user)
                return Result.success(user)
            } catch (e: GetCredentialCancellationException) {
                _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
                return Result.failure(e)
            } catch (secondAttemptException: Exception) {
                val exToMap = if (secondAttemptException is NoCredentialException) firstAttemptException else secondAttemptException
                val friendlyMessage = mapUserFriendlyError(exToMap)
                _authState.value = AuthState.Error(friendlyMessage)
                return Result.failure(secondAttemptException)
            }
        }
    }

    private suspend fun authenticateWithCredential(auth: FirebaseAuth, credential: androidx.credentials.Credential): FirebaseUser {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val authResult = auth.signInWithCredential(authCredential).await()
            return authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")
        } else {
            throw IllegalStateException("Unsupported credential type received: ${credential.type}")
        }
    }

    private fun mapUserFriendlyError(e: Throwable): String {
        val raw = e.localizedMessage ?: e.message ?: ""
        return when {
            e is NoCredentialException || raw.contains("NoCredentialException", ignoreCase = true) ->
                "No Google account found on this device, or sign-in prompt was unavailable. Please ensure a Google account is added in device settings and Google Play Services is updated."
            raw.contains("DEVELOPER_ERROR", ignoreCase = true) || raw.contains(": 10", ignoreCase = true) || raw.contains("code: 10", ignoreCase = true) ->
                "Google Play Services configuration error (Code 10). Make sure the SHA-1 fingerprint for this device is added in Firebase Console."
            raw.contains("network", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) || raw.contains("connection", ignoreCase = true) ->
                "Network connection error. Please check your internet connection and try again."
            raw.contains("SamsungPass", ignoreCase = true) ->
                "Samsung Pass credential conflict. Please allow Google Play Services to handle sign-in."
            raw.isNotBlank() -> raw
            else -> "Unable to sign in with Google. Please try again."
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

    private fun Context.findActivity(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
