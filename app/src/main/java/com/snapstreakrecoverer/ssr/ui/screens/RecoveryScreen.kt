package com.snapstreakrecoverer.ssr.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.snapstreakrecoverer.ssr.recovery.RecoveryManager
import com.snapstreakrecoverer.ssr.ui.viewmodel.RecoveryViewModel

@Composable
fun RecoveryScreen(
    viewModel: RecoveryViewModel,
    onComplete: () -> Unit
) {
    val context = LocalContext.current

    val profile by viewModel.profile.collectAsState()
    val selectedFriends by viewModel.selectedFriends.collectAsState()

    // Create the WebView and engine once. The WebView is reused for every friend,
    // mirroring the extension reusing a single tab.
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    val recoveryManager = remember { RecoveryManager(webView) }

    // Observe engine flows unconditionally so the UI actually reflects progress.
    val state by recoveryManager.state.collectAsState()
    val isLoading by recoveryManager.isLoading.collectAsState()

    // Start once the profile and at least one selected friend have loaded from the DB.
    LaunchedEffect(profile, selectedFriends) {
        val p = profile
        if (p != null && selectedFriends.isNotEmpty()) {
            recoveryManager.startRecovery(p, selectedFriends)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recoveryManager.cleanup()
            webView.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading && state !is RecoveryManager.RecoveryState.Complete) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Loading Snapchat Support...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        when (val s = state) {
            is RecoveryManager.RecoveryState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("❌ Error", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onComplete) { Text("Go Back") }
                    }
                }
            }

            is RecoveryManager.RecoveryState.Complete -> {
                val succeeded = (selectedFriends.size - s.failures.size).coerceAtLeast(0)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(32.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("🎉 Recovery Complete", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$succeeded of ${selectedFriends.size} request(s) submitted.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        if (s.failures.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "⚠️ ${s.failures.size} failed:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            s.failures.forEach { failure ->
                                Text(
                                    "• ${failure.friend.username}: ${failure.error}",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onComplete) { Text("Done") }
                    }
                }
            }

            is RecoveryManager.RecoveryState.Processing -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val label = s.friend.displayName.ifBlank { s.friend.username }
                        Text(
                            "Recovering: $label (${s.current + 1} of ${s.total})",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { (s.current + 1).toFloat() / s.total },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = Color.Yellow
                        )
                    }
                }
            }

            else -> {}
        }
    }
}
