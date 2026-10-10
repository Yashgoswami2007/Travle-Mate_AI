package com.example.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.data.local.entity.DestinationEntity
import com.example.data.local.entity.TripEntity
import com.example.ui.components.AiSuggestionPill
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.TravelBadge
import com.example.ui.components.rememberTravelImageRequest
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val allTrips by viewModel.allTrips.collectAsState()
    val allDestinations by viewModel.allDestinations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val liveLocations by viewModel.liveLocationResults.collectAsState()
    val isSearchingLocations by viewModel.isSearchingLocations.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val userLocation by viewModel.userLocationName.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setLocationPermission(isGranted)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setLocationPermission(hasPermission)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.navigateTo(Screen.AiChat(viewModel.selectedTripId.value)) },
                icon = {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI Travel Assistant",
                        tint = Color.White
                    )
                },
                text = {
                    Text(
                        "Ask Travel AI",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                containerColor = TravelNavyPrimary,
                modifier = Modifier
                    .testTag("home_ai_fab")
                    .padding(bottom = 72.dp) // accommodate bottom nav bar
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header: Greeting & Profile
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Namaste, ${currentUser?.name?.split(" ")?.firstOrNull() ?: "Explorer"}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = TravelTerracotta,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = userLocation,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Avatar button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TravelNavyPrimary)
                            .clickable { viewModel.navigateTo(Screen.Profile) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.avatarInitials ?: "TM",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // Supabase Auth Call-to-Action Banner (if guest)
            if (currentSession == null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelTerracotta.copy(alpha = 0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TravelTerracotta.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(Screen.Auth) }
                            .testTag("home_supabase_auth_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(TravelTerracotta),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text("Sign In...", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TravelNavyDark)
                                    Text("Sync trips, invite friends & track group expenses", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TravelTerracotta, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Search Bar: "Where are you going?"
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = {
                        Text(
                            "Where are you going? (e.g. Pune, Jaipur, Goa)",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TravelNavyPrimary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            ) {
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Current Location",
                                    tint = TravelTerracotta
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        cursorColor = TravelNavyPrimary,
                        focusedContainerColor = TravelSurfaceCard,
                        unfocusedContainerColor = TravelSurfaceCard,
                        focusedBorderColor = TravelNavyPrimary,
                        unfocusedBorderColor = TravelBorderSubtle,
                        focusedPlaceholderColor = TextMuted,
                        unfocusedPlaceholderColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("destination_search_input")
                )
            }

            // Search Results dropdown if typing
            if (searchQuery.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Search Results...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                if (isSearchingLocations) {
                                    Text(
                                        text = "Searching live...",
                                        fontSize = 11.sp,
                                        color = TravelTerracotta,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (isSearchingLocations) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                    color = TravelNavyPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Local cached matches
                            searchResults.forEach { dest ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.onSearchQueryChanged("")
                                            viewModel.selectDestination(dest.id)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = TravelTerracotta,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${dest.name}, ${dest.stateRegion}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${dest.country} • ${dest.type}",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    TravelBadge(text = "CACHED", containerColor = TravelNavyContainer, contentColor = TravelNavyDark)
                                }
                                HorizontalDivider(color = TravelBorderSubtle)
                            }

                            // Live geocoded matches
                            liveLocations.forEach { loc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectLiveLocation(loc)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Public,
                                        contentDescription = null,
                                        tint = TravelNavyPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = loc.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${loc.stateRegion}, ${loc.country} • ${loc.type}",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    TravelBadge(text = "LIVE", containerColor = TravelForestGreenLight, contentColor = TravelForestGreen)
                                }
                                HorizontalDivider(color = TravelBorderSubtle)
                            }

                            if (searchResults.isEmpty() && liveLocations.isEmpty() && !isSearchingLocations) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No immediate results for '$searchQuery'",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.performDestinationSearch(searchQuery) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Force Live Global Search", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick AI Prompts horizontal scroll
            item {
                Text(
                    text = "Ask your travel assistant",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiSuggestionPill(
                        text = "Plan 4-day Jaipur trip under ₹25,000",
                        onClick = {
                            viewModel.setWizardDestination("Jaipur")
                            viewModel.setWizardBudget("25000")
                            viewModel.navigateTo(Screen.TripWizard("dest_jaipur"))
                        }
                    )
                    AiSuggestionPill(
                        text = "Explore Pune weekend gateway",
                        onClick = { viewModel.selectDestination("dest_pune") }
                    )
                    AiSuggestionPill(
                        text = "Make itinerary more relaxed",
                        onClick = {
                            viewModel.navigateTo(Screen.AiChat(viewModel.selectedTripId.value))
                            viewModel.sendChatMessage("We're tired. Make Day 3 more relaxed.")
                        }
                    )
                    AiSuggestionPill(
                        text = "Find vegetarian restaurants nearby",
                        onClick = {
                            viewModel.navigateTo(Screen.AiChat(viewModel.selectedTripId.value))
                            viewModel.sendChatMessage("Find me a vegetarian dinner near our hotel under ₹800 for two.")
                        }
                    )
                }
            }

            // Active / Upcoming Trip Card
            val activeTrip = allTrips.firstOrNull()
            if (activeTrip != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Trip Workspace",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        TextButton(onClick = { viewModel.navigateTo(Screen.Trips) }) {
                            Text("All Trips (${allTrips.size})", color = TravelNavyPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ActiveTripCard(
                        trip = activeTrip,
                        onClick = { viewModel.selectTrip(activeTrip.id) },
                        onAskAiClick = { viewModel.navigateTo(Screen.AiChat(activeTrip.id)) }
                    )
                }
            }

            // Quick Actions: "Plan a New Trip" Banner
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelNavyPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(Screen.TripWizard()) }
                        .testTag("plan_new_trip_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            TravelBadge(
                                text = "NATURAL LANGUAGE TRIP PLANNER",
                                containerColor = TravelAmberAccent,
                                contentColor = Color.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Plan a Custom Trip with AI",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Answers 8 smart questions & get a day-by-day optimized schedule with travel times & budget.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Start Wizard",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Popular Destinations Carousel
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Explore Destinations",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = { viewModel.navigateTo(Screen.Explore) }) {
                        Text("See All", color = TravelNavyPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allDestinations) { dest ->
                        DestinationMiniCard(
                            destination = dest,
                            onClick = { viewModel.selectDestination(dest.id) }
                        )
                    }
                }
            }

            // Recent Searches section
            if (recentSearches.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        TextButton(onClick = { viewModel.clearSearchHistory() }) {
                            Text("Clear", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearches.forEach { search ->
                            AssistChip(
                                onClick = { viewModel.performDestinationSearch(search.query) },
                                label = { Text(search.destinationName) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // Bottom breathing room
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun ActiveTripCard(
    trip: TripEntity,
    onClick: () -> Unit,
    onAskAiClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("active_trip_card")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val coverRequest = rememberTravelImageRequest(trip.imageUrl)
                SubcomposeAsyncImage(
                    model = coverRequest,
                    contentDescription = trip.destinationName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(TravelNavyDark),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = TravelAmberAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    },
                    error = {
                        AsyncImage(
                            model = rememberTravelImageRequest("https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1200&q=80"),
                            contentDescription = trip.destinationName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xCC0F172A))
                            )
                        )
                )

                // Top badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TravelBadge(
                        text = "${trip.numberOfDays} Days • ${trip.travellersCount} Travellers",
                        containerColor = TravelNavyPrimary.copy(alpha = 0.85f),
                        contentColor = Color.White
                    )
                    TravelBadge(
                        text = trip.pace,
                        containerColor = TravelTerracotta,
                        contentColor = Color.White
                    )
                }

                // Bottom title
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = trip.destinationName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "${trip.destinationRegion}, ${trip.destinationCountry} • Starting from ${trip.startingLocation}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                BudgetProgressBar(
                    totalBudget = trip.totalBudget,
                    spentBudget = trip.spentBudget
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Workspace")
                    }

                    OutlinedButton(
                        onClick = onAskAiClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TravelNavyPrimary),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TravelAmberAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trip AI")
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationMiniCard(
    destination: DestinationEntity,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(modifier = Modifier.height(110.dp).fillMaxWidth()) {
                AsyncImage(
                    model = rememberTravelImageRequest(destination.imageUrl, "https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1200&q=80"),
                    contentDescription = destination.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                TravelBadge(
                    text = destination.temperature,
                    containerColor = Color(0xCC0F172A),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = destination.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${destination.stateRegion}, ${destination.country}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = destination.type,
                    fontSize = 11.sp,
                    color = TravelTerracotta,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
