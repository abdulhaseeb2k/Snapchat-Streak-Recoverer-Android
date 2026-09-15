package com.snapstreakrecoverer.ssr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.snapstreakrecoverer.ssr.auth.AuthState
import com.snapstreakrecoverer.ssr.ui.theme.ThemeSelection
import com.snapstreakrecoverer.ssr.ui.viewmodel.AuthViewModel
import com.snapstreakrecoverer.ssr.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel? = null,
    onBack: () -> Unit
) {
    val themeSelection by viewModel.themeSelection.collectAsState()
    val authState by authViewModel?.authState?.collectAsState() ?: remember { mutableStateOf(AuthState.Unauthenticated) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Account & Cloud Sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            when (val state = authState) {
                is AuthState.Unauthenticated -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Signed in as Guest", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Sign in with Google to synchronize your profiles, friends, and settings across all your devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { authViewModel?.signIn(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sign In with Google")
                            }
                        }
                    }
                }
                is AuthState.Loading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Signing in with Google...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is AuthState.Authenticated -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        state.user.displayName ?: "Google User",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        state.user.email ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "✓ Cloud sync active",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedButton(onClick = { authViewModel?.signOut() }) {
                                    Text("Sign Out")
                                }
                            }
                        }
                    }
                }
                is AuthState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Sign In Error", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { authViewModel?.signIn(context) }) {
                                    Text("Retry")
                                }
                                TextButton(onClick = { authViewModel?.clearError() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            
            Column {
                ThemeOption("Light", themeSelection == ThemeSelection.LIGHT) {
                    viewModel.setThemeSelection(ThemeSelection.LIGHT)
                }
                ThemeOption("Dark", themeSelection == ThemeSelection.DARK) {
                    viewModel.setThemeSelection(ThemeSelection.DARK)
                }
                ThemeOption("System Default", themeSelection == ThemeSelection.SYSTEM) {
                    viewModel.setThemeSelection(ThemeSelection.SYSTEM)
                }
            }

            HorizontalDivider()
            
            Text("General Information", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("App Version") },
                supportingContent = { Text("1.0.0") }
            )
        }
    }
}

@Composable
fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
