package com.example.ui.screens.itinerary_wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.*
import com.example.domain.ai.TravelDateUtils
import com.example.ui.components.TravelBadge
import com.example.ui.components.travelTextFieldColors
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripWizardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val questions by viewModel.wizardQuestions.collectAsState()
    val isGenerating by viewModel.isGeneratingItinerary.collectAsState()
    val progressMessage by viewModel.generationProgressMessage.collectAsState()
    val destinationName by viewModel.wizardSelectedDestination.collectAsState()
    val budget by viewModel.wizardBudget.collectAsState()
    val startingCity by viewModel.wizardStartingCity.collectAsState()
    val days by viewModel.wizardDays.collectAsState()
    val startDate by viewModel.wizardStartDate.collectAsState()
    val endDate by viewModel.wizardEndDate.collectAsState()
    val travellers by viewModel.wizardTravellers.collectAsState()
    val travellerType by viewModel.wizardTravellerType.collectAsState()
    val liveLocations by viewModel.liveLocationResults.collectAsState()
    val isSearchingLocations by viewModel.isSearchingLocations.collectAsState()

    var originExpanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }

    var showCustomDateDialog by remember { mutableStateOf(false) }
    var customStartInput by remember { mutableStateOf(startDate) }
    var customEndInput by remember { mutableStateOf(endDate) }

    val durationLabel = TravelDateUtils.formatDurationSummary(days)

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Design Your Trip", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "Planning for $destinationName • $durationLabel",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { viewModel.completeWizardAndGenerateTrip() },
                        enabled = !isGenerating,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        modifier = Modifier
                            .testTag("generate_itinerary_button")
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesizing Plan...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TravelAmberAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate Itinerary", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Introductory Card explaining AI Q&A personalization flow
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelNavyPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TravelBadge(text = "CRAFTING YOUR PERFECT TRIP", containerColor = TravelAmberAccent, contentColor = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Let's craft your $destinationName getaway",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tell us about your travel style and preferences. We'll handle the logistics, verified routes, and budget tracking for your $travellerType group.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Quick Parameters: Destination, Origin & Budget
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Core Trip Boundaries", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        // Origin Dropdown
                        ExposedDropdownMenuBox(
                            expanded = originExpanded,
                            onExpandedChange = { originExpanded = !originExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = startingCity,
                                onValueChange = { 
                                    viewModel.setWizardStartingCity(it)
                                    originExpanded = true
                                    if (it.isNotBlank()) viewModel.onSearchQueryChanged("$it, India") else viewModel.onSearchQueryChanged("")
                                },
                                label = { Text("From") },
                                leadingIcon = { Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = TravelNavyPrimary) },
                                trailingIcon = { 
                                    if (isSearchingLocations && startingCity.isNotBlank()) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                                colors = travelTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            if (originExpanded && liveLocations.isNotEmpty()) {
                                val indianLiveLocations = liveLocations.filter { it.country.equals("India", ignoreCase = true) }
                                if (indianLiveLocations.isNotEmpty()) {
                                    ExposedDropdownMenu(
                                        expanded = originExpanded,
                                        onDismissRequest = { originExpanded = false },
                                        modifier = Modifier.background(TravelSandBackground)
                                    ) {
                                        indianLiveLocations.forEach { loc ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(loc.name, color = TravelNavyDark, fontWeight = FontWeight.Bold)
                                                        Text("${loc.stateRegion}, ${loc.country}", color = TravelNavyDark.copy(alpha=0.7f), fontSize = 12.sp)
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setWizardStartingCity("${loc.name}, ${loc.stateRegion}")
                                                    originExpanded = false
                                                    viewModel.onSearchQueryChanged("")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Destination Dropdown
                        ExposedDropdownMenuBox(
                            expanded = destinationExpanded,
                            onExpandedChange = { destinationExpanded = !destinationExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = destinationName,
                                onValueChange = { 
                                    viewModel.setWizardDestination(it)
                                    destinationExpanded = true
                                    if (it.isNotBlank()) viewModel.onSearchQueryChanged("$it, India") else viewModel.onSearchQueryChanged("")
                                },
                                label = { Text("To") },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = TravelTerracotta) },
                                trailingIcon = { 
                                    if (isSearchingLocations && destinationName.isNotBlank()) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                                colors = travelTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            if (destinationExpanded && liveLocations.isNotEmpty()) {
                                val indianLiveLocations = liveLocations.filter { it.country.equals("India", ignoreCase = true) }
                                if (indianLiveLocations.isNotEmpty()) {
                                    ExposedDropdownMenu(
                                        expanded = destinationExpanded,
                                        onDismissRequest = { destinationExpanded = false },
                                        modifier = Modifier.background(TravelSandBackground)
                                    ) {
                                        indianLiveLocations.forEach { loc ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(loc.name, color = TravelNavyDark, fontWeight = FontWeight.Bold)
                                                        Text("${loc.stateRegion}, ${loc.country}", color = TravelNavyDark.copy(alpha=0.7f), fontSize = 12.sp)
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setWizardDestination("${loc.name}, ${loc.stateRegion}")
                                                    destinationExpanded = false
                                                    viewModel.onSearchQueryChanged("")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = budget,
                                onValueChange = { viewModel.setWizardBudget(it) },
                                label = { Text("Budget (₹)") },
                                leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = TravelForestGreen) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                                colors = travelTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            
                            OutlinedTextField(
                                value = if (travellers > 0) travellers.toString() else "",
                                onValueChange = { 
                                    val count = it.toIntOrNull() ?: 0
                                    viewModel.setWizardTravellers(count) 
                                },
                                label = { Text("Tripmates") },
                                leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, tint = TravelNavyPrimary) },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                                colors = travelTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Calendar Dates & Inclusive Duration Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = TravelTerracotta, modifier = Modifier.size(18.dp))
                                Text("Travel Dates & Duration", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Surface(
                                color = TravelNavyContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = durationLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TravelNavyDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Current Selected Window Display
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = TravelSandBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TravelBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Scheduled Window", fontSize = 11.sp, color = TextSecondary)
                                    Text(
                                        text = "$startDate → $endDate",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TravelNavyPrimary
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        customStartInput = startDate
                                        customEndInput = endDate
                                        showCustomDateDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Change Dates", fontSize = 12.sp)
                                }
                            }
                        }

                    }
                }
            }

            // Interactive Questions List with Category Tags
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Personalize Your Experience",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            items(questions.size) { index ->
                val q = questions[index]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = TravelAmberAccent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = q.category,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TravelAmberAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = q.questionTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = q.description,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Question Dropdown
                        var qExpanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = qExpanded,
                            onExpandedChange = { qExpanded = !qExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = q.selectedOption ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Option") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qExpanded) },
                                colors = travelTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = qExpanded,
                                onDismissRequest = { qExpanded = false },
                                modifier = Modifier.background(TravelSandBackground)
                            ) {
                                q.options.forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt, color = TravelNavyDark) },
                                        onClick = {
                                            val updated = questions.toMutableList()
                                            updated[index] = q.copy(selectedOption = opt)
                                            viewModel.wizardQuestions.value = updated
                                            qExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Custom Date Range Input Dialog
    if (showCustomDateDialog) {
        val dateRangePickerState = rememberDateRangePickerState()
        
        DatePickerDialog(
            onDismissRequest = { showCustomDateDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            val startLd = java.time.Instant.ofEpochMilli(startMillis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            val endLd = java.time.Instant.ofEpochMilli(endMillis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            viewModel.updateWizardDates(
                                TravelDateUtils.formatDisplayDate(startLd),
                                TravelDateUtils.formatDisplayDate(endLd)
                            )
                        }
                        showCustomDateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
                ) {
                    Text("Apply Window")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDateDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Modal Loading Progress Dialog when Gemini AI is generating itinerary & options
    if (isGenerating) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss while AI is generating */ },
            confirmButton = {},
            dismissButton = null,
            shape = RoundedCornerShape(20.dp),
            containerColor = TravelSurfaceCard,
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(TravelNavyContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = TravelNavyPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Generating",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TravelNavyPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = TravelNavyPrimary,
                        trackColor = TravelNavyContainer
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = progressMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }
}
