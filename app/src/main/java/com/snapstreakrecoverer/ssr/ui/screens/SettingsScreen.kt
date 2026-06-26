package com.snapstreakrecoverer.ssr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.snapstreakrecoverer.ssr.ui.theme.ThemeSelection
import com.snapstreakrecoverer.ssr.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val themeSelection by viewModel.themeSelection.collectAsState()

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
