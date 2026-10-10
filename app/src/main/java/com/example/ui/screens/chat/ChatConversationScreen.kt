package com.example.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(convId: String, viewModel: MainViewModel) {
    val messages by viewModel.currentChatMessages.collectAsState()
    val identity by viewModel.currentChatIdentity.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var isGroupInfoOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var expandedMessageId by remember { mutableStateOf<String?>(null) }
    
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showFacialRecognition by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val contentResolver = viewModel.getApplication<android.app.Application>().contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/*"
            val type = when {
                mimeType.startsWith("video/") -> "VIDEO"
                mimeType.startsWith("audio/") -> "AUDIO"
                else -> "IMAGE"
            }
            // For MVP: Send local URI string. In prod, upload to Supabase Storage first.
            viewModel.sendPeerMessage(
                conversationId = convId,
                text = null,
                type = type,
                attachmentPath = uri.toString(),
                mimeType = mimeType
            )
        }
    }

    LaunchedEffect(convId) {
        viewModel.loadChatMessages(convId)
        
        // Simple polling for MVP real-time effect
        while (true) {
            delay(3000)
            viewModel.loadChatMessages(convId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversation", fontWeight = FontWeight.Bold, color = TravelNavyPrimary) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TravelNavyPrimary)
                    }
                },
                actions = {
                    // MVP placeholder for Group Info trigger
                    TextButton(onClick = { isGroupInfoOpen = true }) {
                        Text("Info", color = TravelNavyPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TravelSandBackground)
            )
        },
        containerColor = TravelSandBackground
    ) { paddingValues ->
        if (isGroupInfoOpen) {
            GroupInfoOverlay(
                convId = convId,
                viewModel = viewModel,
                onClose = { isGroupInfoOpen = false }
            )
        } else if (showFacialRecognition) {
            FacialRecognitionOverlay(
                onClose = { showFacialRecognition = false }
            )
        } else {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = true
            ) {
                items(messages.reversed()) { msg ->
                    val isFromMe = msg.isFromMe
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { expandedMessageId = msg.id }
                                    )
                                }
                                .background(
                                    color = if (isFromMe) TravelNavyPrimary else Color.White,
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isFromMe) 16.dp else 4.dp,
                                        bottomEnd = if (isFromMe) 4.dp else 16.dp
                                    )
                                )
                                .padding(12.dp)
                                .widthIn(max = 280.dp)
                        ) {
                            Column {
                                if (msg.attachmentPath != null) {
                                    if (msg.type == "IMAGE") {
                                        AsyncImage(
                                            model = msg.attachmentPath,
                                            contentDescription = "Attachment",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                                .padding(bottom = if (msg.textContent.isNullOrBlank()) 0.dp else 8.dp)
                                        )
                                    } else {
                                        // Rudimentary UI for video/audio MVP
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.AttachFile,
                                                contentDescription = "File",
                                                tint = if (isFromMe) Color.White else TravelNavyDark,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (msg.type == "VIDEO") "Video Attached" else "Audio Attached",
                                                color = if (isFromMe) Color.White else TravelNavyDark,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(if (msg.textContent.isNullOrBlank()) 0.dp else 8.dp))
                                    }
                                }
                                
                                if (!msg.textContent.isNullOrBlank()) {
                                    Text(
                                        text = msg.textContent,
                                        color = if (isFromMe) Color.White else TravelNavyDark,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = expandedMessageId == msg.id,
                                onDismissRequest = { expandedMessageId = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy Text") },
                                    onClick = {
                                        expandedMessageId = null
                                        msg.textContent?.let { text ->
                                            clipboardManager.setText(AnnotatedString(text))
                                        }
                                    }
                                )
                                if (isFromMe) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Message") },
                                        onClick = {
                                            expandedMessageId = null
                                            viewModel.deleteMessage(msg.id, convId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Message Composer
            Surface(
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        IconButton(
                            onClick = { showAttachmentMenu = true },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Attach", tint = TravelNavyPrimary)
                        }
                        
                        DropdownMenu(
                            expanded = showAttachmentMenu,
                            onDismissRequest = { showAttachmentMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Photos") },
                                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, tint = TravelNavyPrimary) },
                                onClick = { 
                                    showAttachmentMenu = false
                                    filePickerLauncher.launch("image/*") 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Videos") },
                                leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null, tint = TravelNavyPrimary) },
                                onClick = { 
                                    showAttachmentMenu = false
                                    filePickerLauncher.launch("video/*") 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Document") },
                                leadingIcon = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = TravelNavyPrimary) },
                                onClick = { 
                                    showAttachmentMenu = false
                                    filePickerLauncher.launch("application/*") 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Facial Recognition") },
                                leadingIcon = { Icon(Icons.Filled.Face, contentDescription = null, tint = TravelNavyPrimary) },
                                onClick = { 
                                    showAttachmentMenu = false
                                    showFacialRecognition = true
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = TravelSandBackground,
                            unfocusedContainerColor = TravelSandBackground,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        maxLines = 4
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendPeerMessage(convId, messageText)
                                messageText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(TravelNavyPrimary, RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
}
