package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit,
    onGroupCreated: (String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.example.data.repository.ChatIdentity>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    val selectedUsers = remember { mutableStateListOf<com.example.data.repository.ChatIdentity>() }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            isSearching = true
            delay(500)
            val results = viewModel.searchChatUsers(searchQuery)
            // Filter out already selected
            searchResults = results.filter { r -> selectedUsers.none { it.id == r.id } }
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TravelSandBackground)
    ) {
        TopAppBar(
            title = { Text("Create Group", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        if (groupName.isNotBlank() && selectedUsers.isNotEmpty()) {
                            viewModel.createGroupConversation(groupName, selectedUsers.map { it.id }) { convId ->
                                onGroupCreated(convId)
                            }
                        }
                    },
                    enabled = groupName.isNotBlank() && selectedUsers.isNotEmpty()
                ) {
                    Text("Create", color = if (groupName.isNotBlank() && selectedUsers.isNotEmpty()) TravelNavyPrimary else Color.Gray)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TravelSandBackground)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Group Name") },
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (selectedUsers.isNotEmpty()) {
                Text("Selected Members (${selectedUsers.size})", fontWeight = FontWeight.Bold, color = TravelNavyPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedUsers.forEach { user ->
                        AssistChip(
                            onClick = { selectedUsers.remove(user) },
                            label = { Text(user.displayName) },
                            trailingIcon = { Icon(Icons.Filled.Clear, "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search users to add...") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, "Clear")
                        }
                    }
                }
            )
        }

        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TravelNavyPrimary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedUsers.add(user)
                                searchQuery = ""
                                searchResults = emptyList()
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TravelNavyContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                user.displayName.take(1).uppercase(),
                                color = TravelNavyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(user.displayName, fontWeight = FontWeight.Bold, color = TravelNavyDark)
                            Text("@${user.username}", fontSize = 14.sp, color = TravelNavySecondary)
                        }
                    }
                }
            }
        }
    }
}
