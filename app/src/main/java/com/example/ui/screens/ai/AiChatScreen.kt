package com.example.ui.screens.ai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ChatMessageEntity
import com.example.ui.components.AiSuggestionPill
import com.example.ui.components.TravelBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    tripId: String?,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val trip by viewModel.currentTrip.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isAiThinking by viewModel.isAiThinking.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var inputPrompt by remember { mutableStateOf("") }

    BackHandler {
        viewModel.navigateBack()
    }

    LaunchedEffect(chatMessages.size, isAiThinking) {
        if (chatMessages.isNotEmpty() || isAiThinking) {
            val target = if (isAiThinking) chatMessages.size else (chatMessages.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TravelNavyPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TravelAmberAccent, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("TripMate AI Assistant", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Context: ${trip?.destinationName ?: "All Trips"} • ₹${trip?.totalBudget?.toInt() ?: 25000} Budget",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TravelSandBackground)
            )
        },
        bottomBar = {
            Surface(
                color = TravelSurfaceCard,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Quick Action Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        item {
                            AiSuggestionPill(
                                text = "What are must-see places & tickets?",
                                onClick = { viewModel.sendChatMessage("What are the key attractions to visit here, their opening hours and ticket costs?") }
                            )
                        }
                        item {
                            AiSuggestionPill(
                                text = "Best local food & restaurants",
                                onClick = { viewModel.sendChatMessage("What are the iconic street foods and top restaurants to try in this city?") }
                            )
                        }
                        item {
                            AiSuggestionPill(
                                text = "Add Amber Fort to Day 2",
                                onClick = { viewModel.sendChatMessage("Add Amber Fort to Day 2 at 10 AM") }
                            )
                        }
                        item {
                            AiSuggestionPill(
                                text = "Move City Palace to afternoon",
                                onClick = { viewModel.sendChatMessage("Move City Palace to the afternoon") }
                            )
                        }
                        item {
                            AiSuggestionPill(
                                text = "Make Day 3 more relaxed",
                                onClick = { viewModel.sendChatMessage("We're tired. Make Day 3 more relaxed.") }
                            )
                        }
                        item {
                            AiSuggestionPill(
                                text = "Check remaining budget",
                                onClick = { viewModel.sendChatMessage("We only have ₹5,000 left. Recalculate remaining budget.") }
                            )
                        }
                    }

                    // Input Field and Send Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputPrompt,
                            onValueChange = { inputPrompt = it },
                            placeholder = { Text("Ask or instruct TripMate AI...", fontSize = 14.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF0F172A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                cursorColor = TravelNavyPrimary,
                                focusedContainerColor = TravelSurfaceVariant,
                                unfocusedContainerColor = TravelSurfaceVariant,
                                focusedBorderColor = TravelNavyPrimary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedPlaceholderColor = TextMuted,
                                unfocusedPlaceholderColor = TextMuted
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field")
                        )

                        FilledIconButton(
                            onClick = {
                                if (inputPrompt.isNotBlank()) {
                                    val text = inputPrompt
                                    inputPrompt = ""
                                    viewModel.sendChatMessage(text)
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = TravelNavyPrimary),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Context banner
                Surface(
                    color = TravelNavyContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TravelNavyPrimary, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Connected to ${trip?.destinationName ?: "Jaipur"} trip database. Any additions or schedule shifts will update your live itinerary directly.",
                            fontSize = 12.sp,
                            color = TravelNavyDark,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            items(chatMessages) { message ->
                ChatMessageBubble(message = message)
            }

            if (isAiThinking) {
                item {
                    Surface(
                        color = TravelSurfaceCard,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TravelNavyPrimary
                            )
                            Text(
                                text = "Researching & Planning...",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageEntity) {
    val isUser = message.sender == "USER"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) TravelNavyPrimary else TravelSurfaceCard,
            contentColor = if (isUser) Color.White else TextPrimary,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!isUser && message.actionFeedback != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TravelForestGreen, modifier = Modifier.size(14.dp))
                        Text(
                            text = message.actionFeedback,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TravelForestGreen
                        )
                    }
                }

                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
