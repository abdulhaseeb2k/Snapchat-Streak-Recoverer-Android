package com.snapstreakrecoverer.ssr.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapstreakrecoverer.ssr.data.Profile
import com.snapstreakrecoverer.ssr.ui.theme.Avatar
import com.snapstreakrecoverer.ssr.ui.theme.ProfileInactive
import com.snapstreakrecoverer.ssr.ui.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onProfileSelected: (Profile) -> Unit,
    onSettingsClick: () -> Unit
) {
    val profiles by viewModel.allProfiles.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<Profile?>(null) }
    val context = LocalContext.current

    val jsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                viewModel.importProfilesFromJson(reader.readText())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Snapchat Streak Recoverer",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(listOf(Color(0xFFFFFC00), Color(0xFFFFD700)))
                            )
                        )
                        Text("Automated Support Form Submitter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { jsonLauncher.launch("application/json") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Import", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("+ Add New", fontSize = 12.sp)
                    }
                }
            }

            if (profiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No accounts yet. Add one!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(profiles) { profile ->
                        ProfileItem(
                            profile = profile,
                            onClick = { onProfileSelected(profile) },
                            onEdit = { editingProfile = profile }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, username, email, phone, device, delay ->
                viewModel.insertProfile(Profile(profileName = name, snapchatUsername = username, email = email, mobileNumber = phone, device = device, refreshDelay = delay))
                showAddDialog = false
            }
        )
    }

    if (editingProfile != null) {
        EditProfileDialog(
            profile = editingProfile!!,
            onDismiss = { editingProfile = null },
            onSave = { updatedProfile ->
                viewModel.updateProfile(updatedProfile)
                editingProfile = null
            },
            onDelete = {
                viewModel.deleteProfile(editingProfile!!)
                editingProfile = null
            }
        )
    }
}

@Composable
fun ProfileItem(
    profile: Profile,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Avatar),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    profile.profileName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.profileName, fontWeight = FontWeight.Bold)
                Text("@${profile.snapchatUsername}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AddProfileDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var device by remember { mutableStateOf("") }
    var delay by remember { mutableStateOf("1.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") })
                TextField(value = username, onValueChange = { username = it }, label = { Text("Snapchat Username") })
                TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Mobile Number") })
                TextField(value = device, onValueChange = { device = it }, label = { Text("Device") })
                TextField(value = delay, onValueChange = { delay = it }, label = { Text("Refresh Delay") })
            }
        },
        confirmButton = { Button(onClick = { onSave(name, username, email, phone, device, delay.toDoubleOrNull() ?: 1.0) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditProfileDialog(profile: Profile, onDismiss: () -> Unit, onSave: (Profile) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(profile.profileName) }
    var username by remember { mutableStateOf(profile.snapchatUsername) }
    var email by remember { mutableStateOf(profile.email) }
    var phone by remember { mutableStateOf(profile.mobileNumber) }
    var device by remember { mutableStateOf(profile.device) }
    var delay by remember { mutableStateOf(profile.refreshDelay.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Account Name") })
                TextField(value = username, onValueChange = { username = it }, label = { Text("Snapchat Username") })
                TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                TextField(value = phone, onValueChange = { phone = it }, label = { Text("Mobile Number") })
                TextField(value = device, onValueChange = { device = it }, label = { Text("Device") })
                TextField(value = delay, onValueChange = { delay = it }, label = { Text("Refresh Delay") })
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                Button(onClick = { onSave(profile.copy(profileName = name, snapchatUsername = username, email = email, mobileNumber = phone, device = device, refreshDelay = delay.toDoubleOrNull() ?: 1.0)) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
