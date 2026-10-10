package com.example.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.travelTextFieldColors
import com.example.ui.theme.TravelNavyDark
import com.example.ui.theme.TravelNavyPrimary
import com.example.ui.theme.TravelSandBackground
import com.example.ui.theme.TravelTerracotta
import com.example.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val indianStates = listOf(
        "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", 
        "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", 
        "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", 
        "Karnataka", "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", 
        "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", 
        "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", 
        "Uttarakhand", "West Bengal"
    )

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var locationExpanded by remember { mutableStateOf(false) }
    var dobStr by remember { mutableStateOf("") }
    
    val liveLocations by viewModel.liveLocationResults.collectAsState()
    val isSearchingLocations by viewModel.isSearchingLocations.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Picker)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        val date = Instant.ofEpochMilli(selectedMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        dobStr = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    }
                }) {
                    Text("OK", color = TravelNavyPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TravelNavyPrimary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = TravelSandBackground)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = TravelNavyDark,
                    headlineContentColor = TravelNavyDark,
                    selectedDayContainerColor = TravelTerracotta,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = TravelNavyPrimary,
                    todayContentColor = TravelNavyPrimary
                )
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Logo & Welcome Text
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.ic_official_logo),
                    contentDescription = "TripMate AI Official Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Welcome to TripMate AI",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TravelNavyDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Let's set up your traveler profile.",
                fontSize = 14.sp,
                color = TravelNavyDark.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Input Fields
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TravelNavyPrimary) },
                        colors = travelTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TravelNavyPrimary) },
                        colors = travelTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Date of Birth Field (Clicks open Date Picker)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = dobStr,
                            onValueChange = { },
                            label = { Text("Date of Birth") },
                            leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TravelNavyPrimary) },
                            colors = travelTextFieldColors(),
                            singleLine = true,
                            enabled = false, // disable text input to force calendar
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true }
                        )
                        // Invisible box over text field to intercept clicks
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showDatePicker = true }
                        )
                    }

                    // Location Dropdown (Editable + Live Search, Restricted to India)
                    ExposedDropdownMenuBox(
                        expanded = locationExpanded,
                        onExpandedChange = { locationExpanded = !locationExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { 
                                location = it
                                locationExpanded = true
                                if (it.isNotBlank()) {
                                    viewModel.onSearchQueryChanged("$it, India")
                                } else {
                                    viewModel.onSearchQueryChanged("")
                                }
                            },
                            readOnly = false,
                            label = { Text("Home State / District / City") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = TravelNavyPrimary) },
                            trailingIcon = { 
                                if (isSearchingLocations && location.isNotBlank()) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = TravelNavyPrimary)
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationExpanded) 
                                }
                            },
                            colors = travelTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        if (locationExpanded) {
                            ExposedDropdownMenu(
                                expanded = locationExpanded,
                                onDismissRequest = { locationExpanded = false },
                                modifier = Modifier.background(TravelSandBackground)
                            ) {
                                if (location.isBlank()) {
                                    indianStates.forEach { stateName ->
                                        DropdownMenuItem(
                                            text = { Text(stateName, color = TravelNavyDark) },
                                            onClick = {
                                                location = stateName
                                                locationExpanded = false
                                                viewModel.onSearchQueryChanged("")
                                            }
                                        )
                                    }
                                } else {
                                    val indianLiveLocations = liveLocations.filter { it.country.equals("India", ignoreCase = true) }
                                    
                                    if (indianLiveLocations.isEmpty() && !isSearchingLocations) {
                                        DropdownMenuItem(
                                            text = { Text("No Indian matches found", color = TravelNavyDark.copy(alpha = 0.5f)) },
                                            onClick = { }
                                        )
                                    }
                                    indianLiveLocations.forEach { loc ->
                                        DropdownMenuItem(
                                            text = { 
                                                Column {
                                                    Text(loc.name, color = TravelNavyDark, fontWeight = FontWeight.Bold)
                                                    Text("${loc.stateRegion}, ${loc.country}", color = TravelNavyDark.copy(alpha=0.7f), fontSize = 12.sp)
                                                }
                                            },
                                            onClick = {
                                                location = "${loc.name}, ${loc.stateRegion}"
                                                locationExpanded = false
                                                viewModel.onSearchQueryChanged("")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (fullName.isNotBlank() && username.isNotBlank() && dobStr.isNotBlank() && location.isNotBlank()) {
                        viewModel.completeOnboarding(
                            fullName = fullName,
                            username = username,
                            dob = dobStr,
                            location = location
                        )
                    }
                },
                enabled = fullName.isNotBlank() && username.isNotBlank() && dobStr.isNotBlank() && location.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TravelTerracotta,
                    disabledContainerColor = TravelTerracotta.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Get Started",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
