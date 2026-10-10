package com.example.ui.screens.destination

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.SeedData
import com.example.ui.components.TravelBadge
import com.example.ui.components.rememberTravelImageRequest
import com.example.ui.screens.map.TripRouteMap
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen

@Composable
fun DestinationDetailScreen(
    destinationId: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val destinations by viewModel.allDestinations.collectAsState()
    val places by viewModel.currentDestinationPlaces.collectAsState()
    val images by viewModel.currentDestinationImages.collectAsState()
    val savedDestinations by viewModel.savedDestinations.collectAsState()
    val isRefreshingLiveData by viewModel.isRefreshingLiveData.collectAsState()
    val dest = destinations.firstOrNull { it.id == destinationId } ?: SeedData.destinations.first()
    val isSaved = savedDestinations.any { it.destinationId == dest.id } || dest.isSaved

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        bottomBar = {
            Surface(
                color = TravelSurfaceCard,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.navigateTo(Screen.Explore) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Explore Places")
                    }

                    Button(
                        onClick = {
                            viewModel.setWizardDestination(dest.name)
                            viewModel.navigateTo(Screen.TripWizard(dest.id))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("plan_trip_dest_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TravelAmberAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Plan Trip with AI")
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Hero Photo
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    AsyncImage(
                        model = rememberTravelImageRequest(dest.imageUrl),
                        contentDescription = dest.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x66000000), Color.Transparent, Color(0xCC0F172A))
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // User Database: Bookmark / Save Destination
                            IconButton(
                                onClick = { viewModel.toggleDestinationSaved(dest.id, isSaved) },
                                modifier = Modifier
                                    .background(Color(0x66000000), CircleShape)
                                    .size(40.dp)
                            ) {
                                Icon(
                                    if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Save Destination",
                                    tint = if (isSaved) TravelAmberAccent else Color.White
                                )
                            }

                            // Web & Gemini Sync Button
                            Button(
                                onClick = { viewModel.refreshLiveTransportAndHotels(dest.id) },
                                enabled = !isRefreshingLiveData,
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xCC0F172A),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("sync_web_gemini_button")
                            ) {
                                if (isRefreshingLiveData) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = TravelAmberAccent
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fetching...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = TravelAmberAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Web & AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TravelBadge(text = dest.type, containerColor = TravelAmberAccent, contentColor = Color.Black)
                            TravelBadge(text = "${dest.weatherSummary} (${dest.temperature})", containerColor = Color(0x99FFFFFF), contentColor = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = dest.name,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "${dest.stateRegion}, ${dest.country}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Description
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("About ${dest.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = dest.description,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(color = TravelBorderSubtle)

                        Text("Popular Neighborhoods & Areas", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = dest.popularAreas,
                            fontSize = 12.sp,
                            color = TravelTerracotta,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Weather Card (Requirement 11: Weather)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Forecast", fontSize = 12.sp, color = TextSecondary)
                            Text(dest.temperature, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TravelNavyPrimary)
                            Text(dest.weatherSummary, fontSize = 12.sp, color = TextSecondary)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Rain Probability", fontSize = 12.sp, color = TextSecondary)
                            Text(dest.rainProbability, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TravelTerracotta)
                            Text("Best time: ${dest.bestTimeToVisit}", fontSize = 11.sp, color = TravelForestGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Public Destination Knowledge Base & Cache Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Saved to Database",
                        tint = TravelForestGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Synced to Public Database",
                        fontSize = 12.sp,
                        color = TravelForestGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Verified Destination Photos Gallery (from public_destination_images)
            if (images.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Verified Photos & Landmarks (${images.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(images) { img ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.width(200.dp)
                                ) {
                                    Column {
                                        AsyncImage(
                                            model = rememberTravelImageRequest(img.imageUrl),
                                            contentDescription = img.caption,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                        )
                                        if (img.caption.isNotBlank()) {
                                            Text(
                                                text = img.caption,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(8.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Must-see Highlights
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Key Attractions in ${dest.name} (${places.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            items(places) { place ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp))) {
                            AsyncImage(
                                model = rememberTravelImageRequest(place.imageUrl, "https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80"),
                                contentDescription = place.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(place.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${place.category} • ${place.visitDuration}", fontSize = 11.sp, color = TextSecondary)
                            Text("★ ${place.rating} • ₹${place.estimatedCost.toInt()}", fontSize = 11.sp, color = TravelTerracotta)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
