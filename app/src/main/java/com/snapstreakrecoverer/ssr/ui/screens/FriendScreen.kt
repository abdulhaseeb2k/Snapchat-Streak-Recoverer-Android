package com.snapstreakrecoverer.ssr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapstreakrecoverer.ssr.data.Friend
import com.snapstreakrecoverer.ssr.data.Profile
import com.snapstreakrecoverer.ssr.ui.viewmodel.FriendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendScreen(
    profile: Profile,
    viewModel: FriendViewModel,
    onRecover: () -> Unit
) {
    LaunchedEffect(profile.id) {
        viewModel.setSelectedProfile(profile.id)
    }

    val friends by viewModel.friends.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCount = friends.count { it.isSelected }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var friendToEdit by remember { mutableStateOf<Friend?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Friends (@${profile.snapchatUsername})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${selectedCount} / ${friends.size} Selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.selectAll(true) }) { Text("All", color = MaterialTheme.colorScheme.secondary) }
                    TextButton(onClick = { viewModel.selectAll(false) }) { Text("None", color = MaterialTheme.colorScheme.error) }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = onRecover,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = friends.any { it.isSelected },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("🚀 RECOVER SELECTED STREAKS", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("✅ Ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 18.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Search friends...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.secondary
                )
            )

            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    PaddingValues(horizontal = 14.dp, vertical = 10.dp).let {
                        Text("My Friends", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(it))
                    }
                    if (friends.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("👻", fontSize = 36.sp)
                                Text("No friends added yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Add a friend below to get started", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.padding(horizontal = 10.dp)) {
                            items(friends) { friend ->
                                FriendItem(
                                    friend = friend,
                                    onToggle = { viewModel.toggleFriendSelection(friend) },
                                    onEdit = { friendToEdit = friend },
                                    onDelete = { viewModel.deleteFriend(friend) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var newName by remember { mutableStateOf("") }
                var newUsername by remember { mutableStateOf("") }
                
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Name", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    placeholder = { Text("Username", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Button(
                    onClick = {
                        if (newUsername.isNotBlank()) {
                            viewModel.addFriend(Friend(profileId = profile.id, username = newUsername, displayName = newName))
                            newName = ""
                            newUsername = ""
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("+", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    friendToEdit?.let { editing ->
        EditFriendDialog(
            friend = editing,
            onDismiss = { friendToEdit = null },
            onSave = { updated ->
                viewModel.updateFriend(updated)
                friendToEdit = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFriendDialog(
    friend: Friend,
    onDismiss: () -> Unit,
    onSave: (Friend) -> Unit
) {
    var name by remember(friend.id) { mutableStateOf(friend.displayName) }
    var username by remember(friend.id) { mutableStateOf(friend.username) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Friend", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(friend.copy(username = username.trim(), displayName = name.trim())) },
                enabled = username.isNotBlank()
            ) {
                Text("Save", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FriendItem(
    friend: Friend,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        color = if (friend.isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (friend.isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        if (friend.isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .then(if (!friend.isSelected) Modifier.background(Color.Transparent, RoundedCornerShape(6.dp)).then(Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(6.dp))) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (friend.isSelected) {
                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(if (friend.displayName.isNotBlank()) friend.displayName else friend.username, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (friend.displayName.isNotBlank() && friend.displayName != friend.username) {
                    Text("@${friend.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            
            IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}
