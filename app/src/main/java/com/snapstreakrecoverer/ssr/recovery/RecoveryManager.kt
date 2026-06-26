package com.snapstreakrecoverer.ssr.recovery

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the Snapchat support form inside a [WebView], one selected friend at a
 * time — the Android counterpart of the extension's background service worker
 * (`background.js`) plus content script (`content.js`).
 *
 * Flow per friend: load form -> inject fill script -> auto-submit -> detect the
 * navigation away from `/requests/new` as a successful submission -> wait
 * `refreshDelay` seconds -> next friend. Load failures are recorded and skipped
 * (the run continues) rather than aborting the whole batch, matching the
 * extension's resilience.
 */
class RecoveryManager(private val webView: WebView) {

    private val _state = MutableStateFlow<RecoveryState>(RecoveryState.Idle)
    val state = _state.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private var profile: Profile? = null
    private var friends: List<Friend> = emptyList()
    private var currentIndex = 0
    private val failures = mutableListOf<RecoveryFailure>()

    private var started = false
    /** Guards against the fill script being injected twice for the same page load. */
    private var filledForCurrentFriend = false
    /** Guards against advancing past a friend twice (onPageFinished can fire repeatedly). */
    private var advancingFromCurrentFriend = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val FORM_URL =
        "https://help.snapchat.com/hc/en-us/requests/new?co=true&ticket_form_id=149423"

    init {
        Log.d("RecoveryManager", "Initializing RecoveryManager")
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webView.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // reCAPTCHA on the Snapchat support form needs cookies (incl. third-party).
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.addJavascriptInterface(RecoveryInterface(), "Android")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                Log.d(
                    "RecoveryJS",
                    "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}"
                )
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("RecoveryManager", "Page started: $url")
                _isLoading.value = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("RecoveryManager", "Page finished: $url")
                _isLoading.value = false

                if (_state.value !is RecoveryState.Processing) return

                if (url?.contains("requests/new") == true) {
                    if (!filledForCurrentFriend) {
                        filledForCurrentFriend = true
                        injectFillScript()
                    }
                } else if (url != null) {
                    // Navigated away from the form -> submission succeeded.
                    Log.d("RecoveryManager", "Submission detected via URL change")
                    advanceAfterSubmission()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("RecoveryManager", "Error loading page: ${error?.description} (${error?.errorCode})")
                if (request?.isForMainFrame == true && _state.value is RecoveryState.Processing) {
                    recordFailureAndContinue("Failed to load page: ${error?.description}")
                }
            }
        }
    }

    fun startRecovery(profile: Profile, friends: List<Friend>) {
        if (started) return
        started = true
        this.profile = profile
        this.friends = friends.filter { it.isSelected }
        this.currentIndex = 0
        this.failures.clear()
        if (this.friends.isEmpty()) {
            _state.value = RecoveryState.Error("No friends selected")
            return
        }
        _state.value = RecoveryState.Processing(currentIndex, this.friends.size, this.friends[currentIndex])
        loadForm()
    }

    private fun loadForm() {
        filledForCurrentFriend = false
        advancingFromCurrentFriend = false
        webView.loadUrl(FORM_URL)
    }

    private fun injectFillScript() {
        val currentProfile = profile ?: return
        val currentFriend = friends.getOrNull(currentIndex) ?: return

        val script = RecoveryScript.getFillFormScript(
            username = currentProfile.snapchatUsername,
            email = currentProfile.email,
            mobileNumber = currentProfile.mobileNumber,
            device = currentProfile.device,
            friendUsername = currentFriend.username
        )
        webView.evaluateJavascript(script, null)
    }

    /** Called when the current friend's form was submitted (URL changed or JS bridge). */
    private fun advanceAfterSubmission() {
        if (_state.value !is RecoveryState.Processing || advancingFromCurrentFriend) return
        advancingFromCurrentFriend = true
        val delayMs = ((profile?.refreshDelay ?: 1.0) * 1000).toLong().coerceAtLeast(0L)
        mainHandler.postDelayed({ moveToNext() }, delayMs)
    }

    private fun recordFailureAndContinue(error: String) {
        if (advancingFromCurrentFriend) return
        advancingFromCurrentFriend = true
        val friend = friends.getOrNull(currentIndex)
        if (friend != null) {
            failures.add(RecoveryFailure(friend, error))
        }
        // Small pause so a failing page doesn't hot-loop, then continue.
        mainHandler.postDelayed({ moveToNext() }, 1000L)
    }

    private fun moveToNext() {
        currentIndex++
        if (currentIndex < friends.size) {
            _state.value = RecoveryState.Processing(currentIndex, friends.size, friends[currentIndex])
            loadForm()
        } else {
            _state.value = RecoveryState.Complete(failures.toList())
        }
    }

    /** Stops any pending work. Call when the recovery screen is disposed. */
    fun cleanup() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    inner class RecoveryInterface {
        /** Optional reliability hook: the fill script calls this right after clicking submit. */
        @JavascriptInterface
        fun onSubmitClicked() {
            // URL-change detection is the primary signal; this is a no-op safety net.
            Log.d("RecoveryManager", "JS reported submit clicked")
        }

        @JavascriptInterface
        fun onFillFailed(message: String?) {
            mainHandler.post { recordFailureAndContinue(message ?: "Form fill failed") }
        }
    }

    sealed class RecoveryState {
        object Idle : RecoveryState()
        data class Processing(val current: Int, val total: Int, val friend: Friend) : RecoveryState()
        data class Complete(val failures: List<RecoveryFailure>) : RecoveryState()
        data class Error(val message: String) : RecoveryState()
    }

    data class RecoveryFailure(val friend: Friend, val error: String)
}
