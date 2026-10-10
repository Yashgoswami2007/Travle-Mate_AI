package com.example.ui.screens.trip

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.data.local.entity.*
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.DemoModeBadge
import com.example.ui.components.LiveConnectedBadge
import com.example.ui.components.TravelBadge
import com.example.ui.components.rememberTravelImageRequest
import com.example.ui.components.sanitizeImageUrl
import com.example.ui.components.travelTextFieldColors
import com.example.ui.screens.map.TripRouteMap
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen
import java.util.UUID

enum class WorkspaceTab(val title: String) {
    ITINERARY("Itinerary"),
    TRANSPORT("Transport"),
    STAYS("Hotels"),
    PLACES("Places & Food"),
    MAP("Route Map"),
    EXPENSES("Expenses")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val trip by viewModel.currentTrip.collectAsState()
    val itineraryItems by viewModel.currentItinerary.collectAsState()
    val transportOptions by viewModel.currentTransportOptions.collectAsState()
    val hotelOptions by viewModel.currentHotelOptions.collectAsState()
    val places by viewModel.currentDestinationPlaces.collectAsState()
    val restaurants by viewModel.currentDestinationRestaurants.collectAsState()
    val members by viewModel.currentMembers.collectAsState()
    val expenses by viewModel.currentExpenses.collectAsState()
    val splits by viewModel.currentSplits.collectAsState()
    val isRefreshingLiveData by viewModel.isRefreshingLiveData.collectAsState()

    var selectedTab by remember { mutableStateOf(WorkspaceTab.ITINERARY) }
    var selectedDay by remember { mutableStateOf(1) }
    var showAddActivityDialog by remember { mutableStateOf(false) }
    var editingActivityItem by remember { mutableStateOf<ItineraryItemEntity?>(null) }
    var showMembersDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.navigateBack()
    }

    if (trip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TravelNavyPrimary)
        }
        return
    }

    val nonNullTrip = trip!!
    val daysCount = nonNullTrip.numberOfDays
    val filteredDayItems = itineraryItems.filter { it.dayNumber == selectedDay }.sortedBy { it.orderIndex }

    if (showMembersDialog) {
        TripMembersAndInviteDialog(
            tripId = tripId,
            tripTitle = nonNullTrip.destinationName,
            viewModel = viewModel,
            onDismiss = { showMembersDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.navigateTo(Screen.AiChat(tripId)) },
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TravelAmberAccent) },
                text = { Text("Trip Assistant", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = TravelNavyPrimary,
                modifier = Modifier
                    .testTag("workspace_ai_fab")
                    .padding(bottom = 60.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Hero Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    val coverRequest = rememberTravelImageRequest(nonNullTrip.imageUrl)
                    SubcomposeAsyncImage(
                        model = coverRequest,
                        contentDescription = nonNullTrip.destinationName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(TravelNavyDark),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = TravelAmberAccent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            }
                        },
                        error = {
                            AsyncImage(
                                model = rememberTravelImageRequest("https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1200&q=80"),
                                contentDescription = nonNullTrip.destinationName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x800F172A), Color.Transparent, Color(0xE60F172A))
                                )
                            )
                    )

                    // Navigation bar on top of hero
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier
                                .background(Color(0x66000000), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { showMembersDialog = true },
                                modifier = Modifier
                                    .background(Color(0x66000000), CircleShape)
                                    .size(40.dp)
                                    .testTag("btn_open_trip_members")
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "Collaborators", tint = Color.White)
                            }

                            IconButton(
                                onClick = { viewModel.navigateTo(Screen.AiChat(tripId)) },
                                modifier = Modifier
                                    .background(Color(0x66000000), CircleShape)
                                    .size(40.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = TravelAmberAccent)
                            }
                        }
                    }

                    // Bottom Trip Info
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TravelBadge(
                                text = "${nonNullTrip.numberOfDays} Days • ${nonNullTrip.startDate}",
                                containerColor = TravelAmberAccent,
                                contentColor = Color.Black
                            )
                            TravelBadge(
                                text = nonNullTrip.travellersDescription,
                                containerColor = Color(0x99FFFFFF),
                                contentColor = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${nonNullTrip.destinationName} Workspace",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${nonNullTrip.destinationRegion}, ${nonNullTrip.destinationCountry} • From ${nonNullTrip.startingLocation}",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Budget Engine Category Breakdown & Over-budget alert
            item {
                val categoryTotals = mutableMapOf(
                    "Transport" to 0.0,
                    "Accommodation" to 0.0,
                    "Activities" to 0.0,
                    "Food" to 0.0,
                    "Shopping" to 0.0
                )
                
                // Add itinerary costs
                itineraryItems.forEach { item ->
                    val cat = when (item.category.lowercase()) {
                        "transport", "flight", "train", "bus", "taxi" -> "Transport"
                        "hotel", "stay", "accommodation", "resort" -> "Accommodation"
                        "food", "restaurant", "café", "cafe", "dining" -> "Food"
                        "shopping", "market", "souvenir" -> "Shopping"
                        else -> "Activities"
                    }
                    categoryTotals[cat] = (categoryTotals[cat] ?: 0.0) + item.estimatedCost
                }
                
                // Add expenses
                expenses.forEach { exp ->
                    val cat = when (exp.category.lowercase()) {
                        "transport", "flight", "train", "taxi", "cab" -> "Transport"
                        "accommodation", "hotel", "stay" -> "Accommodation"
                        "food", "dining", "restaurant", "café", "cafe" -> "Food"
                        "shopping", "market" -> "Shopping"
                        else -> "Activities"
                    }
                    categoryTotals[cat] = (categoryTotals[cat] ?: 0.0) + exp.amount
                }

                val perPersonTotal = if (nonNullTrip.travellersCount > 0) nonNullTrip.spentBudget / nonNullTrip.travellersCount else nonNullTrip.spentBudget
                val isOverBudget = nonNullTrip.spentBudget > nonNullTrip.totalBudget

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Budget Breakdown", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TravelNavyDark)
                            
                            Surface(
                                color = if (isOverBudget) TravelWarningRedLight else TravelForestGreenLight,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Icon(
                                        imageVector = if (isOverBudget) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isOverBudget) TravelWarningRed else TravelForestGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isOverBudget) "Over by ₹${(nonNullTrip.spentBudget - nonNullTrip.totalBudget).toInt()}" else "Within Budget",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOverBudget) TravelWarningRed else TravelForestGreen
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        BudgetProgressBar(
                            totalBudget = nonNullTrip.totalBudget,
                            spentBudget = nonNullTrip.spentBudget
                        )
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        // Per person breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text("Total Spent", fontSize = 12.sp, color = TextSecondary)
                                Text("₹${nonNullTrip.spentBudget.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TravelNavyPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Per Person (${nonNullTrip.travellersCount} travelers)", fontSize = 12.sp, color = TextSecondary)
                                Text("₹${perPersonTotal.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = TravelBorderSubtle)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Category Breakdown
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categoryTotals.filter { it.value > 0 }.toList().sortedByDescending { it.second }.forEach { (cat, amount) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val icon = when (cat) {
                                            "Transport" -> Icons.Default.DirectionsTransit
                                            "Accommodation" -> Icons.Default.Hotel
                                            "Food" -> Icons.Default.Restaurant
                                            "Shopping" -> Icons.Default.ShoppingBag
                                            else -> Icons.Default.LocalActivity
                                        }
                                        Surface(color = TravelSurfaceVariant, shape = CircleShape, modifier = Modifier.size(28.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(icon, contentDescription = cat, modifier = Modifier.size(14.dp), tint = TravelNavyPrimary)
                                            }
                                        }
                                        Text(cat, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    }
                                    Text("₹${amount.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                        }

                        if (isOverBudget) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                color = TravelWarningRedLight,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.navigateTo(Screen.AiChat(tripId))
                                    viewModel.sendChatMessage("Our budget is ₹${nonNullTrip.totalBudget.toInt()} but we've planned ₹${nonNullTrip.spentBudget.toInt()}. Please suggest some cheaper dining alternatives or free attractions to bring us back on budget.")
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TravelWarningRed, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Tap to ask AI for cheaper dining or free attraction alternatives to get back on budget.",
                                        fontSize = 12.sp,
                                        color = TravelWarningRed,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Workspace Navigation Tabs
            item {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = TravelSandBackground,
                    contentColor = TravelNavyPrimary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = TravelNavyPrimary,
                            height = 3.dp
                        )
                    },
                    divider = { HorizontalDivider(color = TravelBorderSubtle) }
                ) {
                    WorkspaceTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == tab) TravelNavyPrimary else TextSecondary
                                )
                            }
                        )
                    }
                }
            }

            // Tab Content Switcher
            when (selectedTab) {
                WorkspaceTab.ITINERARY -> {
                    // Day Selector Pills
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (day in 1..daysCount) {
                                val isSelected = selectedDay == day
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDay = day },
                                    label = { Text("Day $day", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TravelNavyPrimary,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // Day Action Bar
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Day $selectedDay Schedule (${filteredDayItems.size} activities)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                if (filteredDayItems.isNotEmpty()) {
                                    val startT = filteredDayItems.first().startTime
                                    val endT = filteredDayItems.last().endTime
                                    Text(
                                        text = "$startT – $endT • Sequence synchronized",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (filteredDayItems.size > 1) {
                                    FilledTonalIconButton(
                                        onClick = { viewModel.recalculateDaySchedule(selectedDay) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = TravelNavyContainer,
                                            contentColor = TravelNavyPrimary
                                        ),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = "Recalculate Day", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Button(
                                    onClick = { showAddActivityDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Activity", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Itinerary Items for Day
                    if (filteredDayItems.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.EventNote, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No activities planned for Day $selectedDay", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tap 'Add Activity' or ask AI to auto-populate this day!", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        }
                    } else {
                        items(filteredDayItems) { item ->
                            val index = filteredDayItems.indexOf(item)
                            val isFirst = index == 0
                            val isLast = index == filteredDayItems.size - 1
                            ItineraryItemCard(
                                item = item,
                                isFirst = isFirst,
                                isLast = isLast,
                                onMoveUp = { viewModel.moveItineraryItemUp(item) },
                                onMoveDown = { viewModel.moveItineraryItemDown(item) },
                                onEdit = { editingActivityItem = item },
                                onToggleComplete = {
                                    viewModel.updateItineraryItem(item.copy(isCompleted = !item.isCompleted))
                                },
                                onDelete = {
                                    viewModel.deleteItineraryItem(item.id, item.dayNumber)
                                },
                                onAskAiAboutItem = {
                                    viewModel.navigateTo(Screen.AiChat(tripId))
                                    viewModel.sendChatMessage("Tell me about ${item.title} and best tips for visiting.")
                                }
                            )
                        }
                    }
                }

                WorkspaceTab.TRANSPORT -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val hasLive = transportOptions.any { !it.isDemo }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Real-Time Transit Options", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (hasLive) LiveConnectedBadge() else DemoModeBadge()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "From ${nonNullTrip.startingLocation} → ${nonNullTrip.destinationName}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                TextButton(
                                    onClick = { viewModel.refreshLiveTransportAndHotels(nonNullTrip.destinationId) },
                                    enabled = !isRefreshingLiveData,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    if (isRefreshingLiveData) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TravelNavyPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fetching Web...", fontSize = 11.sp, color = TravelNavyPrimary)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Refresh Live Data", fontSize = 11.sp, color = TravelNavyPrimary)
                                    }
                                }
                            }
                        }
                    }

                    items(transportOptions) { option ->
                        TransportOptionCard(
                            option = option,
                            onBookClick = {
                                // Open provider booking link in browser
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(option.bookingUrl))
                                context.startActivity(intent)
                            },
                            onMarkBooked = {
                                viewModel.markBooking(
                                    type = option.mode,
                                    title = "${option.mode}: ${option.provider}",
                                    provider = option.provider,
                                    cost = option.price
                                )
                            }
                        )
                    }
                }

                WorkspaceTab.STAYS -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val hasLive = hotelOptions.any { !it.isDemo }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Hotels & Accommodation", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                if (hasLive) LiveConnectedBadge() else DemoModeBadge()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Real properties in ${nonNullTrip.destinationName} • Live direct links", fontSize = 12.sp, color = TextSecondary)
                                TextButton(
                                    onClick = { viewModel.refreshLiveTransportAndHotels(nonNullTrip.destinationId) },
                                    enabled = !isRefreshingLiveData,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    if (isRefreshingLiveData) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TravelNavyPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Fetching Rates...", fontSize = 11.sp, color = TravelNavyPrimary)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Refresh Live Rates", fontSize = 11.sp, color = TravelNavyPrimary)
                                    }
                                }
                            }
                        }
                    }

                    items(hotelOptions) { hotel ->
                        HotelOptionCard(
                            hotel = hotel,
                            onBookClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(hotel.bookingUrl))
                                context.startActivity(intent)
                            },
                            onMarkBooked = {
                                viewModel.markBooking(
                                    type = "Hotel",
                                    title = hotel.name,
                                    provider = hotel.provider,
                                    cost = hotel.totalPrice
                                )
                            },
                            onSaveToggle = {
                                viewModel.toggleHotelSaved(hotel.id, !hotel.isSaved)
                            }
                        )
                    }
                }

                WorkspaceTab.PLACES -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Top Attractions & Places to Visit", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = { viewModel.refreshLiveTransportAndHotels(nonNullTrip.destinationId) },
                                    enabled = !isRefreshingLiveData,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    if (isRefreshingLiveData) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = TravelNavyPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Syncing...", fontSize = 11.sp, color = TravelNavyPrimary)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sync Web AI", fontSize = 11.sp, color = TravelNavyPrimary)
                                    }
                                }
                            }
                            Text("Real landmarks and Wikimedia photos • Add to day plan", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    items(places) { place ->
                        PlaceDiscoveryCard(
                            place = place,
                            onAddToTrip = { viewModel.addPlaceToTripItinerary(place, selectedDay) },
                            onToggleSaved = { viewModel.togglePlaceSaved(place.id, place.isSaved) },
                            onAskAi = {
                                viewModel.navigateTo(Screen.AiChat(tripId))
                                viewModel.sendChatMessage("How should we fit ${place.name} into our Day $selectedDay schedule?")
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Dining & Local Culinary Spots", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Handpicked for couple ambiance and vegetarian options", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    items(restaurants) { rest ->
                        RestaurantDiscoveryCard(
                            restaurant = rest,
                            onSaveToggle = {
                                viewModel.toggleRestaurantSaved(rest.id, !rest.isSaved)
                            },
                            onAddToItinerary = {
                                val item = ItineraryItemEntity(
                                    id = UUID.randomUUID().toString(),
                                    tripId = tripId,
                                    dayNumber = selectedDay,
                                    title = "Dinner at ${rest.name}",
                                    locationName = rest.locationName,
                                    latitude = rest.latitude,
                                    longitude = rest.longitude,
                                    startTime = "19:30",
                                    endTime = "21:00",
                                    durationMinutes = 90,
                                    category = "Restaurant",
                                    estimatedCost = rest.averageCostForTwo,
                                    travelTimeFromPrevious = "15 mins",
                                    notes = "${rest.cuisine} (${rest.dietaryTypes})",
                                    source = "Culinary Guide"
                                )
                                viewModel.addItineraryItem(item)
                            }
                        )
                    }
                }

                WorkspaceTab.MAP -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Geographic Itinerary Route", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Optimized waypoint transitions for Day $selectedDay to minimize backtrack travel", fontSize = 12.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(12.dp))
                            TripRouteMap(
                                items = filteredDayItems,
                                destinationName = nonNullTrip.destinationName
                            )
                        }
                    }
                }

                WorkspaceTab.EXPENSES -> {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Trip Group & Expenses", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { viewModel.navigateTo(Screen.Expenses) }) {
                                    Text("Open Full Split Engine", color = TravelNavyPrimary)
                                }
                            }
                            Text("Recorded Expenses: ${expenses.size} items", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    items(expenses) { exp ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(exp.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Paid by ${exp.paidByMemberName} • ${exp.category}", fontSize = 12.sp, color = TextSecondary)
                                }
                                Text("₹${exp.amount.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TravelNavyPrimary)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // Add Activity Dialog
    if (showAddActivityDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newLocation by remember { mutableStateOf("") }
        var newTime by remember {
            mutableStateOf(
                if (filteredDayItems.isNotEmpty()) filteredDayItems.last().endTime else "10:00"
            )
        }
        var newDurationText by remember { mutableStateOf("90") }
        var newCategory by remember { mutableStateOf("Attraction") }
        var newCost by remember { mutableStateOf("300") }

        val durationVal = newDurationText.toIntOrNull()?.coerceAtLeast(15) ?: 90
        val liveEndTime = com.example.domain.ai.TravelScheduleUtils.calculateEndTime(newTime, durationVal)
        val categories = listOf("Attraction", "Food", "Café", "Fort", "Museum", "Transport", "Nature", "Shopping", "Relax")
        val durationPresets = listOf(30, 45, 60, 90, 120, 180)

        AlertDialog(
            onDismissRequest = { showAddActivityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = TravelNavyPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Activity to Day $selectedDay", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Activity Title (e.g. Amber Fort)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newLocation,
                        onValueChange = { newLocation = it },
                        label = { Text("Location Name") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category horizontal chips
                    Column {
                        Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = newCategory.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { newCategory = cat },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = TravelNavyPrimary,
                                        selectedLabelColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Start time & Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTime,
                            onValueChange = { newTime = it },
                            label = { Text("Start (HH:mm)") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                            colors = travelTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newDurationText,
                            onValueChange = { newDurationText = it },
                            label = { Text("Duration (m)") },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                            colors = travelTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Duration presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        durationPresets.forEach { mins ->
                            val isSel = durationVal == mins
                            AssistChip(
                                onClick = { newDurationText = mins.toString() },
                                label = { Text("${mins}m", fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (isSel) TravelNavyPrimary else TravelSandBackground,
                                    labelColor = if (isSel) Color.White else TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Live slot preview
                    Surface(
                        color = TravelNavyContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Calculated Slot:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TravelNavyDark)
                            Text("$newTime – $liveEndTime (${durationVal}m)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TravelTerracotta)
                        }
                    }

                    OutlinedTextField(
                        value = newCost,
                        onValueChange = { newCost = it },
                        label = { Text("Estimated Cost (₹)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            val baseLat = filteredDayItems.lastOrNull()?.latitude ?: 26.9124
                            val baseLon = filteredDayItems.lastOrNull()?.longitude ?: 75.7873
                            val newItem = ItineraryItemEntity(
                                id = UUID.randomUUID().toString(),
                                tripId = tripId,
                                dayNumber = selectedDay,
                                title = newTitle.trim(),
                                locationName = newLocation.trim().ifBlank { newTitle.trim() },
                                latitude = baseLat + 0.003,
                                longitude = baseLon + 0.003,
                                startTime = newTime.trim(),
                                endTime = liveEndTime,
                                durationMinutes = durationVal,
                                category = newCategory,
                                estimatedCost = newCost.toDoubleOrNull() ?: 0.0,
                                travelTimeFromPrevious = "15 mins",
                                notes = "Added manually by user",
                                source = "User Created",
                                orderIndex = filteredDayItems.size
                            )
                            viewModel.addItineraryItem(newItem)
                            showAddActivityDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
                ) {
                    Text("Add & Sync Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddActivityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Activity Dialog
    editingActivityItem?.let { itemToEdit ->
        EditActivityDialog(
            item = itemToEdit,
            onDismiss = { editingActivityItem = null },
            onSave = { updated ->
                viewModel.editItineraryItem(updated)
                editingActivityItem = null
            }
        )
    }
}

@Composable
fun EditActivityDialog(
    item: ItineraryItemEntity,
    onDismiss: () -> Unit,
    onSave: (ItineraryItemEntity) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var locationName by remember { mutableStateOf(item.locationName) }
    var startTime by remember { mutableStateOf(item.startTime) }
    var durationMinutesText by remember { mutableStateOf(item.durationMinutes.toString()) }
    var category by remember { mutableStateOf(item.category) }
    var costText by remember { mutableStateOf(if (item.estimatedCost > 0) item.estimatedCost.toInt().toString() else "0") }
    var notes by remember { mutableStateOf(item.notes) }

    val durationVal = durationMinutesText.toIntOrNull()?.coerceAtLeast(15) ?: 60
    val liveEndTime = com.example.domain.ai.TravelScheduleUtils.calculateEndTime(startTime, durationVal)

    val categories = listOf("Attraction", "Food", "Café", "Fort", "Museum", "Transport", "Nature", "Shopping", "Relax")
    val durationPresets = listOf(30, 45, 60, 90, 120, 180)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = TravelNavyPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Day ${item.dayNumber} Activity", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info callout about schedule recalculation
                Surface(
                    color = TravelSandBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TravelBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = TravelTerracotta, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Recalculation: Updating timing or duration automatically shifts subsequent activities on Day ${item.dayNumber}.",
                            fontSize = 11.sp,
                            color = TravelNavyDark,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Activity Title") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                    colors = travelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Location
                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Location / Landmark") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                    colors = travelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Chips
                Column {
                    Text("Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = category.equals(cat, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { category = cat },
                                label = { Text(cat, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TravelNavyPrimary,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Timing & Duration row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (HH:mm)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durationMinutesText,
                        onValueChange = { durationMinutesText = it },
                        label = { Text("Duration (mins)") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                        colors = travelTextFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick duration pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durationPresets.forEach { mins ->
                        val isSel = durationVal == mins
                        AssistChip(
                            onClick = { durationMinutesText = mins.toString() },
                            label = { Text("${mins}m", fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSel) TravelNavyPrimary else TravelSandBackground,
                                labelColor = if (isSel) Color.White else TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Live calculated timing banner
                Surface(
                    color = TravelNavyContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Schedule Slot:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TravelNavyDark
                        )
                        Text(
                            text = "$startTime – $liveEndTime (${durationVal}m)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TravelTerracotta
                        )
                    }
                }

                // Cost & Notes
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Estimated Cost (₹)") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 14.sp),
                    colors = travelTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Highlights") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF0F172A), fontSize = 13.sp),
                    colors = travelTextFieldColors(),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val updated = item.copy(
                            title = title.trim(),
                            locationName = locationName.trim().ifBlank { title.trim() },
                            startTime = startTime.trim(),
                            endTime = liveEndTime,
                            durationMinutes = durationVal,
                            category = category,
                            estimatedCost = costText.toDoubleOrNull() ?: 0.0,
                            notes = notes.trim()
                        )
                        onSave(updated)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary)
            ) {
                Text("Save & Recalculate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ItineraryItemCard(
    item: ItineraryItemEntity,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onAskAiAboutItem: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("itinerary_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Timing, Duration, Category & Move Up/Down Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = TravelTerracotta, modifier = Modifier.size(14.dp))
                    Text(
                        text = "${item.startTime} – ${item.endTime}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TravelTerracotta
                    )
                    TravelBadge(
                        text = "${item.durationMinutes}m",
                        containerColor = TravelSandBackground,
                        contentColor = TravelNavyPrimary
                    )
                    TravelBadge(
                        text = item.category,
                        containerColor = TravelNavyContainer,
                        contentColor = TravelNavyDark
                    )
                }

                // Move Up / Move Down controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (!isFirst) TravelNavyPrimary else TextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (!isLast) TravelNavyPrimary else TextMuted.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onToggleComplete() },
                    colors = CheckboxDefaults.colors(checkedColor = TravelForestGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCompleted) TextMuted else TextPrimary,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = item.locationName,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.estimatedCost > 0) {
                    Text(
                        text = "₹${item.estimatedCost.toInt()}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = TravelNavyPrimary
                    )
                }
            }

            // Transit info from previous stop
            if (item.travelTimeFromPrevious.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Icon(
                        if (item.travelTimeFromPrevious.contains("Walk", ignoreCase = true)) Icons.Default.DirectionsWalk else Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = item.travelTimeFromPrevious,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            if (item.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.notes,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = TravelBorderSubtle)
            Spacer(modifier = Modifier.height(6.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source: ${item.source}",
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = TravelNavyPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 11.sp, color = TravelNavyPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = onAskAiAboutItem,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp), tint = TravelAmberAccent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ask AI", fontSize = 11.sp, color = TravelNavyPrimary)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TravelWarningRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TransportOptionCard(
    option: TransportOptionEntity,
    onBookClick: () -> Unit,
    onMarkBooked: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        when (option.mode) {
                            "Flight" -> Icons.Default.Flight
                            "Train" -> Icons.Default.Train
                            "Bus" -> Icons.Default.DirectionsBus
                            else -> Icons.Default.LocalTaxi
                        },
                        contentDescription = null,
                        tint = TravelNavyPrimary
                    )
                    Text(option.provider, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                TravelBadge(
                    text = option.mode,
                    containerColor = TravelNavyContainer,
                    contentColor = TravelNavyDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(option.departureTime, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(option.fromCity, fontSize = 11.sp, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(option.duration, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    HorizontalDivider(modifier = Modifier.width(60.dp), color = TravelBorderSubtle)
                    Text(option.stops, fontSize = 10.sp, color = TravelForestGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(option.arrivalTime, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(option.toCity, fontSize = 11.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TravelBorderSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("₹${option.price.toInt()}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TravelNavyPrimary)
                    Text("Last checked: ${option.lastChecked}", fontSize = 10.sp, color = TextMuted)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onMarkBooked,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Mark Booked", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onBookClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("View / Book", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HotelOptionCard(
    hotel: HotelOptionEntity,
    onBookClick: () -> Unit,
    onMarkBooked: () -> Unit,
    onSaveToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column {
            Box(modifier = Modifier.height(130.dp).fillMaxWidth()) {
                AsyncImage(
                    model = rememberTravelImageRequest(hotel.photoUrl, "https://images.unsplash.com/photo-1582719508461-905c673771fd?auto=format&fit=crop&w=1200&q=80"),
                    contentDescription = hotel.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onSaveToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color(0x99000000), CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        if (hotel.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (hotel.isSaved) TravelAmberAccent else Color.White
                    )
                }
                TravelBadge(
                    text = "★ ${hotel.rating} (${hotel.reviewCount})",
                    containerColor = Color(0xCC0F172A),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(hotel.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(hotel.distanceToCenter, fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(hotel.amenities, fontSize = 11.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))
                Text(hotel.cancellationPolicy, fontSize = 11.sp, color = TravelForestGreen, fontWeight = FontWeight.Medium)

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = TravelBorderSubtle)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("₹${hotel.nightlyPrice.toInt()} / night", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TravelNavyPrimary)
                        Text("₹${hotel.totalPrice.toInt()} total (3 nights)", fontSize = 11.sp, color = TextSecondary)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onMarkBooked,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Mark Booked", fontSize = 12.sp)
                        }
                        Button(
                            onClick = onBookClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("View / Book", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceDiscoveryCard(
    place: PlaceEntity,
    onAddToTrip: () -> Unit,
    onToggleSaved: () -> Unit,
    onAskAi: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = rememberTravelImageRequest(place.imageUrl, "https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80"),
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(place.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onToggleSaved, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (place.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (place.isSaved) TravelAmberAccent else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(place.description, fontSize = 11.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TravelBadge(text = place.category, containerColor = TravelNavyContainer, contentColor = TravelNavyDark)
                    Text("• ${place.visitDuration}", fontSize = 10.sp, color = TextMuted)
                    Text("• ₹${place.estimatedCost.toInt()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TravelNavyPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAddToTrip,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Trip", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onAskAi,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Ask AI", fontSize = 11.sp, color = TravelNavyPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantDiscoveryCard(
    restaurant: RestaurantEntity,
    onSaveToggle: () -> Unit,
    onAddToItinerary: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = rememberTravelImageRequest(restaurant.imageUrl, "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=1200&q=80"),
                    contentDescription = restaurant.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(restaurant.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("★ ${restaurant.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TravelAmberAccent)
                }
                Text("${restaurant.cuisine} • ${restaurant.dietaryTypes}", fontSize = 11.sp, color = TravelForestGreen, fontWeight = FontWeight.Medium)
                Text(restaurant.locationName, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("~₹${restaurant.averageCostForTwo.toInt()} for two", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Button(
                        onClick = onAddToItinerary,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Add to Day", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
