package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInboxScreen(viewModel: MainViewModel) {
    val conversations by viewModel.chatConversations.collectAsState()
    val currentIdentity by viewModel.currentChatIdentity.collectAsState()
    var isSearchOpen by remember { mutableStateOf(false) }
    var isGroupCreateOpen by remember { mutableStateOf(false) }
    var isEditUsernameOpen by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat", fontWeight = FontWeight.Bold, color = TravelNavyPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TravelSandBackground),
                actions = {
                    IconButton(onClick = { isSearchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search Users", tint = TravelNavyPrimary)
                    }
                    IconButton(onClick = { isEditUsernameOpen = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = TravelNavyPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showFabMenu = true },
                    containerColor = TravelNavyPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New Chat")
                }
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Direct Message") },
                        onClick = {
                            showFabMenu = false
                            isSearchOpen = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Group Chat") },
                        onClick = {
                            showFabMenu = false
                            isGroupCreateOpen = true
                        }
                    )
                }
            }
        },
        containerColor = TravelSandBackground
    ) { paddingValues ->
        if (isSearchOpen) {
            UserSearchOverlay(
                viewModel = viewModel,
                onClose = { isSearchOpen = false },
                onUserSelected = { userId ->
                    isSearchOpen = false
                    viewModel.startDirectConversation(userId) { convId ->
                        viewModel.navigateTo(com.example.ui.viewmodel.Screen.ChatConversation(convId))
                    }
                }
            )
        } else if (isGroupCreateOpen) {
            CreateGroupOverlay(
                viewModel = viewModel,
                onClose = { isGroupCreateOpen = false },
                onGroupCreated = { convId ->
                    isGroupCreateOpen = false
                    viewModel.navigateTo(com.example.ui.viewmodel.Screen.ChatConversation(convId))
                }
            )
        } else if (isEditUsernameOpen) {
            EditUsernameOverlay(
                viewModel = viewModel,
                onClose = { isEditUsernameOpen = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (currentIdentity == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TravelNavyPrimary)
                    }
                } else if (conversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No conversations yet.",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TravelNavyPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Search for a username to start chatting.",
                                fontSize = 14.sp,
                                color = TravelNavySecondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { isSearchOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
                            ) {
                                Text("Search Users")
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Your username: @${currentIdentity?.username}",
                                fontSize = 14.sp,
                                color = TravelNavySecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Your username: @${currentIdentity?.username}",
                                fontSize = 12.sp,
                                color = TravelNavySecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(conversations) { conv ->
                            ConversationRow(
                                title = conv.title ?: "Chat",
                                preview = conv.lastMessagePreview,
                                unreadCount = conv.unreadCount,
                                onClick = {
                                    viewModel.navigateTo(com.example.ui.viewmodel.Screen.ChatConversation(conv.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationRow(
    title: String,
    preview: String,
    unreadCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar Placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(TravelOrange),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TravelNavyDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview,
                fontSize = 14.sp,
                color = TravelNavySecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(TravelNavyPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unreadCount.toString(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
