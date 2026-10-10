package com.example.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SeedData
import com.example.data.local.entity.BookingEntity
import com.example.ui.components.TravelBadge
import com.example.ui.components.travelTextFieldColors
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val bookings by viewModel.currentBookings.collectAsState()
    val user = currentUser ?: SeedData.initialUser

    var name by remember(user) { mutableStateOf(user.name) }
    var homeLocation by remember(user) { mutableStateOf(user.homeLocation) }
    var diet by remember(user) { mutableStateOf(user.dietPreference) }
    var style by remember(user) { mutableStateOf(user.travelStyle) }
    var budgetTier by remember(user) { mutableStateOf(user.budgetTier) }

    val currentSession by viewModel.currentSession.collectAsState()
    val currentPublicProfile by viewModel.currentPublicProfile.collectAsState()
    val pendingInvitations by viewModel.pendingInvitations.collectAsState()
    val deviceIdentity by viewModel.deviceUserIdentity.collectAsState()

    var showQrScannerDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Profile Card & Companion QR Code
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelNavyDark),
                    modifier = Modifier.fillMaxWidth().testTag("device_profile_card")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(TravelTerracotta),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = deviceIdentity.avatarInitials,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }

                                Column {
                                    Text(
                                        text = deviceIdentity.displayName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "@${deviceIdentity.username}",
                                        fontSize = 13.sp,
                                        color = TravelNavyContainer
                                    )
                                    Text(
                                        text = "Device ID: ${deviceIdentity.deviceId.take(12)}...",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            TravelBadge(
                                text = "ACTIVE DEVICE",
                                containerColor = TravelForestGreen.copy(alpha = 0.3f),
                                contentColor = Color(0xFF86EFAC)
                            )
                        }

                        // QR Code Generation
                        val qrPayload = com.example.ui.components.QrCodeUtils.encodeParticipantPayload(
                            id = deviceIdentity.deviceId,
                            name = deviceIdentity.displayName,
                            username = deviceIdentity.username
                        )
                        val qrBitmap = remember(qrPayload) {
                            com.example.ui.components.QrCodeUtils.generateQrCodeBitmap(qrPayload, 360)
                        }

                        if (qrBitmap != null) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "My Participant QR Code",
                                        modifier = Modifier.size(160.dp).testTag("my_qr_code_image")
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Scan to add me as travel companion",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TravelNavyDark
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showQrScannerDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TravelTerracotta),
                                modifier = Modifier.weight(1f).height(44.dp).testTag("btn_scan_companion_qr")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Companion QR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            // Authentication Status Banner (if guest / not logged in)
            if (currentSession == null) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelTerracotta),
                        modifier = Modifier.fillMaxWidth().testTag("auth_banner_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_official_logo),
                                        contentDescription = "TripMate AI Official Logo",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Column {
                                    Text("Sign In to TripMate", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Color.White)
                                    Text("Sync trips, invite friends & split expenses", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                                }
                            }

                            Button(
                                onClick = { viewModel.navigateTo(com.example.ui.viewmodel.Screen.Auth) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TravelTerracotta),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_open_login")
                            ) {
                                Text("Sign In / Sign Up with Google", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                // Profile Card Header for Authenticated User
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelNavyPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("auth_profile_header")
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(TravelTerracotta),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = user.avatarInitials,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = currentPublicProfile?.displayName ?: user.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        if (currentPublicProfile?.username != null) {
                                            Text(
                                                text = "@${currentPublicProfile?.username}",
                                                fontSize = 13.sp,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                        }
                                        val rawEmail = currentSession?.email ?: user.email
                                        val censoredEmail = rawEmail.takeIf { it.isNotBlank() }?.let { if (it.contains("@")) "${it.take(2)}***@***.com" else "***" } ?: ""
                                        Text(
                                            text = censoredEmail,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.logout() },
                                    modifier = Modifier.testTag("btn_logout")
                                ) {
                                    Icon(Icons.Filled.Logout, contentDescription = "Sign Out", tint = Color.White)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TravelBadge(
                                    text = "Home: ${user.homeLocation}",
                                    containerColor = Color(0x33FFFFFF),
                                    contentColor = Color.White
                                )
                                TravelBadge(
                                    text = "Google Authenticated",
                                    containerColor = TravelForestGreen.copy(alpha = 0.25f),
                                    contentColor = Color(0xFF86EFAC)
                                )
                            }
                        }
                    }
                }
            }

            // Pending Trip Invitations Section
            if (pendingInvitations.isNotEmpty()) {
                item {
                    Text(
                        text = "Trip Invitations (${pendingInvitations.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TravelNavyDark
                    )
                    Text("Friends invited you to collaborate on their trips", fontSize = 12.sp, color = TextSecondary)
                }

                items(pendingInvitations) { inv ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth().testTag("invitation_card_${inv.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inv.tripName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TravelNavyDark)
                                    Text("From ${inv.inviterName} (@${inv.inviterUsername})", fontSize = 12.sp, color = TextSecondary)
                                }
                                TravelBadge(text = "PENDING", containerColor = TravelAmberLight, contentColor = TravelAmberAccent)
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.acceptTripInvitation(inv.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TravelForestGreen),
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("btn_accept_invite_${inv.id}")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.rejectTripInvitation(inv.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TravelWarningRed),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, TravelWarningRed.copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(1f).height(40.dp).testTag("btn_decline_invite_${inv.id}")
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Decline", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Travel Preferences Editor
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("AI Personalization Preferences", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Display Name") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                            colors = travelTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = homeLocation,
                            onValueChange = { homeLocation = it },
                            label = { Text("Default Departure / Home City") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                            colors = travelTextFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Dietary preferences
                        Column {
                            Text("Dietary Preference", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Vegetarian", "Vegan", "Jain Friendly", "Non-veg").forEach { d ->
                                    FilterChip(
                                        selected = diet == d,
                                        onClick = { diet = d },
                                        label = { Text(d, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        // Travel Style
                        Column {
                            Text("Travel Pace", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Relaxed", "Balanced", "Packed").forEach { s ->
                                    FilterChip(
                                        selected = style == s,
                                        onClick = { style = s },
                                        label = { Text(s, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.updateProfile(name, homeLocation, diet, style, budgetTier)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Travel Preferences")
                        }
                    }
                }
            }

            // Confirmed Bookings Section (Requirement 20: Booking Model)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trip Bookings & Reservations (${bookings.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text("Saved confirmations from connected travel providers", fontSize = 12.sp, color = TextSecondary)
            }

            items(bookings) { bk ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (bk.type == "Hotel") Icons.Default.Hotel else Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = TravelNavyPrimary
                                )
                                Text(bk.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            TravelBadge(text = "CONFIRMED", containerColor = TravelForestGreenLight, contentColor = TravelForestGreen)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Provider: ${bk.provider}", fontSize = 12.sp, color = TextSecondary)
                            Text("Ref: ${bk.confirmationNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Date: ${bk.date} • Cost: ₹${bk.cost.toInt()}", fontSize = 11.sp, color = TextMuted)
                        if (bk.notes.isNotBlank()) {
                            Text(bk.notes, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }



            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }

    if (showQrScannerDialog) {
        com.example.ui.components.QrScannerDialog(
            currentUserId = deviceIdentity.deviceId,
            onParticipantConfirmed = { payload ->
                viewModel.addParticipantFromQr(payload)
                showQrScannerDialog = false
            },
            onDismiss = { showQrScannerDialog = false }
        )
    }
}
