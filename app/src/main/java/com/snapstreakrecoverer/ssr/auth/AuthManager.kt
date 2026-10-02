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

        val activity = context.findActivity()
        if (activity == null) {
            val err = "Cannot initiate Google sign-in without a foreground Activity."
            _authState.value = AuthState.Error(err)
            return Result.failure(IllegalStateException(err))
        }

        _authState.value = AuthState.Loading

        return try {
            withTimeout(60_000L) {
                val credentialManager = CredentialManager.create(activity)
                val clientId = if (webClientId.isNotEmpty()) webClientId else "dummy-client-id"

                // 1. Fetch credential
                // Android 14+ (API 34+) uses native Credential Manager dialog (GetSignInWithGoogleOption).
                // Android 13 and below (e.g. Vivo Android 9/10) uses Google Play Services bottom sheet (GetGoogleIdOption)
                // directly, preventing double-popup glitches and OEM cancellation loops.
                val credential = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(signInOption)
                            .build()

                        val response = credentialManager.getCredential(context = activity, request = request)
                        response.credential
                    } catch (e: GetCredentialCancellationException) {
                        Log.i("AuthManager", "User explicitly cancelled sign-in dialog")
                        _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
                        return@withTimeout Result.failure(e)
                    } catch (firstAttemptException: Throwable) {
                        Log.w("AuthManager", "GetSignInWithGoogleOption failed or unhandled (${firstAttemptException.javaClass.simpleName}: ${firstAttemptException.message}), falling back to universal GetGoogleIdOption")
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
                    }
                } else {
                    // Android 13 and below: direct GetGoogleIdOption (1 single clean account picker)
                    try {
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(clientId)
                            .setAutoSelectEnabled(false)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        val response = credentialManager.getCredential(context = activity, request = request)
                        response.credential
                    } catch (e: GetCredentialCancellationException) {
                        Log.i("AuthManager", "User explicitly cancelled sign-in dialog")
                        _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
                        return@withTimeout Result.failure(e)
                    }
                }

                // 2. Authenticate with Firebase using retrieved credential
                val user = authenticateWithCredential(auth, credential)
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            }
        } catch (t: Throwable) {
            if (t is CancellationException && t !is TimeoutCancellationException) {
                // Normal coroutine cancellation
                _authState.value = auth.currentUser?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
                Result.failure(t)
            } else {
                Log.e("AuthManager", "Sign-in error: ${t.javaClass.simpleName} - ${t.message}", t)
                val friendlyMessage = mapUserFriendlyError(t)
                _authState.value = AuthState.Error(friendlyMessage)
                Result.failure(t)
            }
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
        return when {
            e is TimeoutCancellationException ->
                "Sign-in timed out. Please check your internet connection and try again."
            e is NoCredentialException || raw.contains("NoCredentialException", ignoreCase = true) ->
                "No Google account found or prompt unavailable. Please ensure a Google account is added in device settings and Google Play Services is updated."
            raw.contains("DEVELOPER_ERROR", ignoreCase = true) || raw.contains(": 10", ignoreCase = true) || raw.contains("code: 10", ignoreCase = true) ->
                "Google Play Services configuration error (Code 10). Please ensure Google Play Services is updated."
            raw.contains("network", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) || raw.contains("connection", ignoreCase = true) ->
                "Network connection error. Please check your internet connection and try again."
            raw.contains("SamsungPass", ignoreCase = true) ->
                "Samsung Pass credential conflict. Please allow Google Play Services to handle sign-in."
            raw.isNotBlank() -> raw
            else -> "Unable to sign in with Google. Please try again."
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
