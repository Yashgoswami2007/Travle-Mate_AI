package com.example.ui.screens.trip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.PublicUserProfile
import com.example.ui.components.TravelBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMembersAndInviteDialog(
    tripId: String,
    tripTitle: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val tripMembersMap by viewModel.tripMembersMap.collectAsState()
    val members = tripMembersMap[tripId] ?: emptyList()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PublicUserProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showSearchSection by remember { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        viewModel.refreshTripMembers(tripId)
    }

    // Debounced profile search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isSearching = true
            delay(400)
            searchResults = viewModel.searchProfiles(searchQuery)
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("trip_members_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trip Collaborators",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TravelNavyDark
                        )
                        Text(
                            text = tripTitle,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TravelNavySecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar: Open Trip Chat / Invite Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            viewModel.openTripChat(tripId, tripTitle)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("btn_open_trip_chat")
                    ) {
                        Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trip Chat", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { showSearchSection = !showSearchSection },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelTerracotta),
                        modifier = Modifier.weight(1f).height(44.dp).testTag("btn_toggle_invite")
                    ) {
                        Icon(
                            if (showSearchSection) Icons.Filled.Group else Icons.Filled.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (showSearchSection) "View Members" else "Invite Friend", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (showSearchSection) {
                    // Search & Invite Section
                    Text("Search users by @username:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TravelNavyDark)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("e.g. wanderer, rahul...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TravelNavySecondary) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = TravelSurfaceVariant,
                            unfocusedContainerColor = TravelSurfaceVariant,
                            focusedBorderColor = TravelTerracotta,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_search_user_invite")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = TravelTerracotta)
                        }
                    } else if (searchQuery.length >= 2 && searchResults.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No users found matching '$searchQuery'", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(searchResults) { prof ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(TravelTerracotta.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = prof.displayName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = TravelTerracotta
                                            )
                                        }

                                        Column {
                                            Text(prof.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("@${prof.username}", fontSize = 12.sp, color = TextSecondary)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.inviteUserToTrip(tripId, prof.id)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TravelTerracotta),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp).testTag("btn_send_invite_${prof.id}")
                                    ) {
                                        Text("Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                HorizontalDivider(color = TravelBorderSubtle)
                            }
                        }
                    }
                } else {
                    // Members List Section
                    Text("Trip Members (${members.size})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TravelNavyDark)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (members.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No collaborators yet. Invite someone above!", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(members) { m ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(if (m.isCurrentUser) TravelTerracotta else TravelNavyPrimary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = m.displayName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(m.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                if (m.isCurrentUser) {
                                                    TravelBadge(text = "YOU", containerColor = TravelNavyContainer, contentColor = TravelNavyDark)
                                                }
                                            }
                                            Text("@${m.username.ifBlank { "member" }} • ${m.role}", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }

                                    if (!m.isCurrentUser) {
                                        IconButton(
                                            onClick = { viewModel.removeTripMember(tripId, m.userId) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove", tint = TravelWarningRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                HorizontalDivider(color = TravelBorderSubtle)
                            }
                        }
                    }
                }
            }
        }
    }
}
