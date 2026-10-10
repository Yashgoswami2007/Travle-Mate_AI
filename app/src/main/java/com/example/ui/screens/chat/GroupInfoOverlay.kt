package com.example.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoOverlay(
    convId: String,
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    // In a full implementation, we'd fetch members and group details from the backend
    // For MVP, this is a placeholder UI showing the intent of Phase 8.3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TravelSandBackground)
    ) {
        TopAppBar(
            title = { Text("Group Info", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { /* TODO: Add Member */ }) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add Member", tint = TravelNavyPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = TravelSandBackground)
        )

        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(TravelOrange),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "G",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Group Name Placeholder", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TravelNavyDark)
            Text("Group • 3 Members", fontSize = 14.sp, color = TravelNavySecondary)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { /* TODO: Leave Group */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Leave Group")
            }
        }
    }
}
