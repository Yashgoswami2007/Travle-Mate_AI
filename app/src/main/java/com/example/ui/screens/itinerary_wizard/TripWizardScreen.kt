package com.example.ui.screens.itinerary_wizard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import com.example.domain.ai.TravelDateUtils
import com.example.ui.components.TravelBadge
import com.example.ui.components.travelTextFieldColors
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
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
                        Text("AI Trip Planner Wizard", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "Personalizing for $destinationName • $durationLabel",
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Budget Scope", fontSize = 11.sp, color = TextSecondary)
                            Text(
                                text = "₹$budget ($durationLabel)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TravelNavyPrimary
                            )
                        }

                        Button(
                            onClick = { viewModel.completeWizardAndGenerateTrip() },
                            enabled = !isGenerating,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                            modifier = Modifier
                                .testTag("generate_itinerary_button")
                                .height(48.dp)
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
                            TravelBadge(text = "STEP 1: TAILORED TRAVEL ENGINE", containerColor = TravelAmberAccent, contentColor = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tailoring your $durationLabel to $destinationName",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Contextually tuned for $travellerType ($travellers travelers). Dates, opening hours, local transit, and budget will be verified.",
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

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = destinationName,
                                onValueChange = { viewModel.setWizardDestination(it) },
                                label = { Text("Destination") },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                                colors = travelTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = startingCity,
                                onValueChange = { viewModel.setWizardStartingCity(it) },
                                label = { Text("Origin City") },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                                colors = travelTextFieldColors(),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Budget Input
                        OutlinedTextField(
                            value = budget,
                            onValueChange = { viewModel.setWizardBudget(it) },
                            label = { Text("Estimated Total Budget (₹)") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 15.sp),
                            colors = travelTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
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

                        // Quick Preset Chips
                        Text("Quick Date Presets:", fontSize = 12.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val today = LocalDate.now()
                            // Next Weekend (Friday to Sunday = 3 Days, 2 Nights)
                            val nextFri = today.plusDays(((5 - today.dayOfWeek.value + 7) % 7 + 7).toLong())
                            val nextSun = nextFri.plusDays(2)

                            // Long Weekend (Thursday to Sunday = 4 Days, 3 Nights)
                            val longThu = today.plusDays(((4 - today.dayOfWeek.value + 7) % 7 + 7).toLong())
                            val longSun = longThu.plusDays(3)

                            // Week Trip (Saturday to Friday = 7 Days, 6 Nights)
                            val weekSat = today.plusDays(((6 - today.dayOfWeek.value + 7) % 7 + 7).toLong())
                            val weekFri = weekSat.plusDays(6)

                            AssistChip(
                                onClick = {
                                    viewModel.updateWizardDates(
                                        TravelDateUtils.formatDisplayDate(nextFri),
                                        TravelDateUtils.formatDisplayDate(nextSun)
                                    )
                                },
                                label = { Text("Weekend (3D)", fontSize = 11.sp) }
                            )

                            AssistChip(
                                onClick = {
                                    viewModel.updateWizardDates(
                                        TravelDateUtils.formatDisplayDate(longThu),
                                        TravelDateUtils.formatDisplayDate(longSun)
                                    )
                                },
                                label = { Text("Long Wknd (4D)", fontSize = 11.sp) }
                            )

                            AssistChip(
                                onClick = {
                                    viewModel.updateWizardDates(
                                        TravelDateUtils.formatDisplayDate(weekSat),
                                        TravelDateUtils.formatDisplayDate(weekFri)
                                    )
                                },
                                label = { Text("Week (7D)", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Traveler Group & Count Card
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
                                Icon(Icons.Default.Group, contentDescription = null, tint = TravelNavyPrimary, modifier = Modifier.size(18.dp))
                                Text("Travel Party & Group Vibe", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            // Stepper: - Count +
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = TravelSandBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, TravelBorderSubtle)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.setWizardTravellers(travellers - 1) },
                                        enabled = travellers > 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                                    }

                                    Text(
                                        text = "$travellers",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TravelNavyPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    IconButton(
                                        onClick = { viewModel.setWizardTravellers(travellers + 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }

                        // Companion Vibe Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Solo", "Couple", "Friends", "Family").forEach { type ->
                                val isSelected = travellerType.equals(type, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setWizardTravellerType(type)
                                        if (type == "Solo") viewModel.setWizardTravellers(1)
                                        else if (type == "Couple") viewModel.setWizardTravellers(2)
                                        else if (type == "Friends" && travellers < 3) viewModel.setWizardTravellers(3)
                                        else if (type == "Family" && travellers < 3) viewModel.setWizardTravellers(4)
                                    },
                                    label = { Text(type, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TravelNavyPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
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
                        text = "Adaptive Context Questions (${questions.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Auto-tuned",
                        fontSize = 11.sp,
                        color = TravelForestGreen,
                        fontWeight = FontWeight.Bold
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
                                color = TravelNavyContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Q${q.stepNumber}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TravelNavyDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
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

                        // Selectable options
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            q.options.forEach { opt ->
                                val isSelected = q.selectedOption == opt
                                Surface(
                                    onClick = {
                                        val updated = questions.toMutableList()
                                        updated[index] = q.copy(selectedOption = opt)
                                        viewModel.wizardQuestions.value = updated
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) TravelNavyPrimary.copy(alpha = 0.08f) else TravelSandBackground,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) TravelNavyPrimary else TravelBorderSubtle
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = opt,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) TravelNavyPrimary else TextPrimary
                                        )
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = TravelNavyPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
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
        val parsedStart = TravelDateUtils.parseDate(customStartInput)
        val parsedEnd = TravelDateUtils.parseDate(customEndInput)
        val previewDays = TravelDateUtils.calculateDays(parsedStart, parsedEnd)
        val previewNights = TravelDateUtils.calculateNights(previewDays)

        AlertDialog(
            onDismissRequest = { showCustomDateDialog = false },
            title = {
                Text("Select Exact Travel Dates", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Calculates inclusive days ($previewDays Days • $previewNights Nights):",
                        fontSize = 12.sp,
                        color = TravelNavyPrimary,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = customStartInput,
                        onValueChange = { customStartInput = it },
                        label = { Text("Start Date (e.g. Nov 14, 2026)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                        colors = travelTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customEndInput,
                        onValueChange = { customEndInput = it },
                        label = { Text("End Date (e.g. Nov 19, 2026)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                        colors = travelTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateWizardDates(customStartInput.trim(), customEndInput.trim())
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
        )
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
                        text = "Generating ",
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
