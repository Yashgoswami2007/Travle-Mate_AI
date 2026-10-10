package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUsernameOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val currentIdentity by viewModel.currentChatIdentity.collectAsState()
    var newUsername by remember { mutableStateOf(currentIdentity?.username ?: "") }
    var isChecking by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Debounce username check
    LaunchedEffect(newUsername) {
        if (newUsername.length >= 3 && newUsername != currentIdentity?.username) {
            isChecking = true
            isAvailable = null
            delay(500)
            val users = viewModel.searchChatUsers(newUsername)
            isAvailable = users.none { it.username.equals(newUsername, ignoreCase = true) }
            isChecking = false
        } else {
            isAvailable = if (newUsername == currentIdentity?.username) true else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TravelSandBackground)
    ) {
        TopAppBar(
            title = { Text("Edit Username", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TravelSandBackground)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your Public Username", fontWeight = FontWeight.Bold, color = TravelNavyDark)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newUsername,
                onValueChange = { newUsername = it.lowercase().filter { char -> char.isLetterOrDigit() || char == '_' } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("@") }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isChecking) {
                Text("Checking availability...", color = TravelNavySecondary, fontSize = 14.sp)
            } else if (isAvailable == true && newUsername != currentIdentity?.username) {
                Text("Username is available!", color = TravelForestGreen, fontSize = 14.sp)
            } else if (isAvailable == false) {
                Text("Username is already taken.", color = Color.Red, fontSize = 14.sp)
            } else if (newUsername.length < 3) {
                Text("Username must be at least 3 characters.", color = TravelNavySecondary, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (viewModel.updateChatUsername(newUsername)) {
                            onClose()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isAvailable == true && newUsername.length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
            ) {
                Text("Save Username")
            }
        }
    }
}
