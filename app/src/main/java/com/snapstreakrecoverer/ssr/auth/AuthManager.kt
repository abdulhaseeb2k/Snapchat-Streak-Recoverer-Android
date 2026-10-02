package com.snapstreakrecoverer.ssr.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

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
        val auth = firebaseAuth
        if (auth == null) {
            val err = "Firebase Auth is not available. Please check Google Play Services."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalStateException(err))
        }

        val activity = context.findActivity() ?: context
        _authState.value = AuthState.Loading

        val credentialManager = CredentialManager.create(activity)
        val clientId = if (webClientId.isNotEmpty()) webClientId else "dummy-client-id"

        // 1. Retrieve Credential from CredentialManager
        // Primary Attempt: GetSignInWithGoogleOption (Official user-initiated button flow)
        // Fallback Attempt: GetGoogleIdOption (Universal Play Services bottom sheet for compatibility)
        val credential = try {
            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val response = credentialManager.getCredential(context = activity, request = request)
            response.credential
        } catch (e: GetCredentialCancellationException) {
            Log.i("AuthManager", "User explicitly cancelled Google sign-in dialog")
            _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
            return Result.failure(e)
        } catch (primaryException: Throwable) {
            Log.w("AuthManager", "Primary GetSignInWithGoogleOption failed (${primaryException.javaClass.simpleName}: ${primaryException.message}), attempting GetGoogleIdOption fallback")
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
                fallbackResponse.credential
            } catch (e: GetCredentialCancellationException) {
                Log.i("AuthManager", "User cancelled Google sign-in on fallback")
                _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
                return Result.failure(e)
            } catch (fallbackException: Throwable) {
                Log.e("AuthManager", "Both Google sign-in options failed: ${fallbackException.javaClass.simpleName} - ${fallbackException.message}", fallbackException)
                val finalEx = if (fallbackException is NoCredentialException) primaryException else fallbackException
                val friendlyMessage = mapUserFriendlyError(finalEx)
                _authState.value = AuthState.Error(friendlyMessage)
                return Result.failure(finalEx)
            }
        }

        // 2. Authenticate with Firebase using retrieved credential
        return try {
            val user = authenticateWithCredential(auth, credential)
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (authException: Throwable) {
            Log.e("AuthManager", "Firebase token authentication failed: ${authException.javaClass.simpleName} - ${authException.message}", authException)
            val friendlyMessage = mapUserFriendlyError(authException)
            _authState.value = AuthState.Error(friendlyMessage)
            Result.failure(authException)
        }
    }

    private suspend fun authenticateWithCredential(auth: FirebaseAuth, credential: androidx.credentials.Credential): FirebaseUser {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            if (idToken.isNullOrBlank()) {
                throw IllegalStateException("Google ID token was empty. Ensure Release SHA-1 fingerprint is registered in Firebase Console.")
            }
            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(authCredential).await()
            return authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")
        } else {
            throw IllegalStateException("Unsupported credential type received: ${credential.type}")
        }
    }

    private fun mapUserFriendlyError(e: Throwable): String {
        val raw = e.localizedMessage ?: e.message ?: ""
        val errorType = e.javaClass.simpleName
        return when {
            e is TimeoutCancellationException ->
                "Sign-in timed out. Please check your internet connection and try again."
            e is NoCredentialException || raw.contains("NoCredentialException", ignoreCase = true) ->
                "No Google account found or prompt unavailable. Please ensure a Google account is added in device settings and Google Play Services is updated."
            raw.contains("DEVELOPER_ERROR", ignoreCase = true) || raw.contains(": 10", ignoreCase = true) || raw.contains("code: 10", ignoreCase = true) ->
                "Google Play Services configuration error (Code 10: DEVELOPER_ERROR). Please ensure Release SHA-1 is added and Google Play Services is updated."
            raw.contains("SamsungPass", ignoreCase = true) ->
                "Samsung Pass credential conflict. Please allow Google Play Services to handle sign-in."
            raw.isNotBlank() -> "Sign-in error ($errorType): $raw"
            else -> "Google sign-in error ($errorType). Please try again."
        }
    }

    fun cancelSignIn() {
        if (_authState.value is AuthState.Loading) {
            _authState.value = firebaseAuth?.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
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
