package com.example.ui.screens.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.data.local.entity.DestinationEntity
import com.example.ui.components.rememberTravelImageRequest
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen

/**
 * Category types for the "Explore by Type" section.
 * Each category maps to keywords found in DestinationEntity.type and PlaceEntity.category.
 */
enum class ExploreCategory(
    val label: String,
    val icon: ImageVector,
    val keywords: List<String>,
    val containerColor: Color,
    val iconTint: Color
) {
    MOUNTAINS(
        label = "Mountains",
        icon = Icons.Default.Terrain,
        keywords = listOf("Hill", "Mountain", "Himalayan", "Alpine", "Valley", "Highland"),
        containerColor = Color(0xFFE8F5E9),
        iconTint = Color(0xFF2E7D32)
    ),
    BEACHES(
        label = "Beaches",
        icon = Icons.Default.BeachAccess,
        keywords = listOf("Beach", "Coast", "Tropical", "Seaside", "Marine", "Island"),
        containerColor = Color(0xFFE3F2FD),
        iconTint = Color(0xFF1565C0)
    ),
    HERITAGE(
        label = "Heritage",
        icon = Icons.Default.AccountBalance,
        keywords = listOf("Heritage", "Historic", "Royal", "Fort", "Palace", "Colonial", "Mughal", "Ancient"),
        containerColor = Color(0xFFFFF3E0),
        iconTint = Color(0xFFE65100)
    ),
    NATURE(
        label = "Nature",
        icon = Icons.Default.Park,
        keywords = listOf("Nature", "Forest", "Garden", "Wildlife", "Sanctuary", "Eco", "Green", "Scenic"),
        containerColor = Color(0xFFE0F2F1),
        iconTint = Color(0xFF00695C)
    ),
    FOOD(
        label = "Food",
        icon = Icons.Default.Restaurant,
        keywords = listOf("Food", "Culinary", "Cuisine", "Café", "Dining", "Street Food", "Handicraft"),
        containerColor = Color(0xFFFCE4EC),
        iconTint = Color(0xFFC62828)
    ),
    ADVENTURE(
        label = "Adventure",
        icon = Icons.Default.DirectionsBike,
        keywords = listOf("Adventure", "Trek", "Sport", "Extreme", "Rafting", "Paragliding", "Expedition"),
        containerColor = Color(0xFFF3E5F5),
        iconTint = Color(0xFF6A1B9A)
    );

    /** Check if a destination's type matches this category's keywords */
    fun matches(destinationType: String): Boolean {
        return keywords.any { keyword ->
            destinationType.contains(keyword, ignoreCase = true)
        }
    }
}

@Composable
fun ExploreScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val destinations by viewModel.allDestinations.collectAsState()
    val isSearching by viewModel.isSearchingLocations.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExploreCategory?>(null) }

    // Popular destinations: sorted by searchCount descending, database-first
    val popularDestinations = remember(destinations) {
        destinations.sortedByDescending { it.searchCount }
    }

    // Filtered destinations for selected category
    val categoryFilteredDestinations = remember(destinations, selectedCategory) {
        if (selectedCategory == null) {
            emptyList()
        } else {
            destinations.filter { dest ->
                selectedCategory!!.matches(dest.type) || selectedCategory!!.matches(dest.description)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ─── HEADER ───────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Explore",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        modifier = Modifier.testTag("explore_title")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // ─── SEARCH BAR ───────────────────────────────
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search destinations...",
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
                            if (searchQuery.isNotBlank()) {
                                Row {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = TextMuted
                                        )
                                    }
                                    IconButton(onClick = {
                                        val q = searchQuery.trim()
                                        searchQuery = ""
                                        viewModel.performDestinationSearch(q)
                                    }) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "Search",
                                            tint = TravelNavyPrimary
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = TravelSurfaceCard,
                            unfocusedContainerColor = TravelSurfaceCard,
                            focusedBorderColor = TravelNavyPrimary,
                            unfocusedBorderColor = TravelBorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explore_search_bar")
                    )

                    // Loading indicator while searching
                    AnimatedVisibility(visible = isSearching) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = TravelNavyPrimary,
                            trackColor = TravelBorderSubtle
                        )
                    }
                }
            }

            // ─── POPULAR DESTINATIONS ─────────────────────────────
            item {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Popular Destinations",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("explore_popular_title")
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (destinations.isEmpty()) {
                    // Skeleton loading state
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(3) {
                            DestinationCardSkeleton()
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("explore_popular_row")
                    ) {
                        items(
                            items = popularDestinations,
                            key = { it.id }
                        ) { destination ->
                            PopularDestinationCard(
                                destination = destination,
                                onClick = {
                                    viewModel.selectDestination(destination.id)
                                }
                            )
                        }
                    }
                }
            }

            // ─── EXPLORE BY TYPE ──────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Explore by Type",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("explore_by_type_title")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category grid: 2 columns x 3 rows
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val categories = ExploreCategory.values()
                    for (rowIndex in 0..2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (colIndex in 0..1) {
                                val index = rowIndex * 2 + colIndex
                                if (index < categories.size) {
                                    val category = categories[index]
                                    CategoryChip(
                                        category = category,
                                        isSelected = selectedCategory == category,
                                        onClick = {
                                            selectedCategory = if (selectedCategory == category) null else category
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── CATEGORY RESULTS ─────────────────────────────────
            if (selectedCategory != null) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${selectedCategory!!.label} Destinations",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (categoryFilteredDestinations.isEmpty()) {
                        // Empty state for category
                        Surface(
                            color = TravelSurfaceCard,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    selectedCategory!!.icon,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No ${selectedCategory!!.label.lowercase()} destinations found yet",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Search for a destination to discover more",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.testTag("explore_category_results")
                        ) {
                            items(
                                items = categoryFilteredDestinations,
                                key = { it.id }
                            ) { destination ->
                                PopularDestinationCard(
                                    destination = destination,
                                    onClick = {
                                        viewModel.selectDestination(destination.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ─── AI TRIP FINDER CTA ───────────────────────────────
            item {
                Spacer(modifier = Modifier.height(28.dp))

                AiTripFinderCard(
                    onClick = {
                        viewModel.navigateTo(Screen.TripWizard())
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag("explore_ai_trip_finder")
                )
            }

            // Bottom spacer for navigation bar
            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// POPULAR DESTINATION CARD — Image-focused, tappable
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PopularDestinationCard(
    destination: DestinationEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = modifier
            .width(170.dp)
            .height(210.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image
            val coverRequest = rememberTravelImageRequest(destination.imageUrl)
            SubcomposeAsyncImage(
                model = coverRequest,
                contentDescription = destination.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(TravelSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = TravelNavyPrimary
                        )
                    }
                },
                error = {
                    AsyncImage(
                        model = rememberTravelImageRequest("https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1200&q=80"),
                        contentDescription = destination.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )

            // Gradient overlay at the bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )

            // Text content over gradient
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = destination.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${destination.stateRegion}, ${destination.country}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SKELETON LOADING CARD
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DestinationCardSkeleton(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = TravelSurfaceCard,
        shadowElevation = 1.dp,
        modifier = modifier
            .width(170.dp)
            .height(210.dp)
    ) {
        Column {
            // Image placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(TravelSurfaceVariant)
            )
            // Text placeholders
            Column(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TravelSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(70.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TravelBorderSubtle)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CATEGORY CHIP — Compact, selectable, with icon
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CategoryChip(
    category: ExploreCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) TravelNavyPrimary else category.containerColor,
        animationSpec = tween(200),
        label = "categoryBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else category.iconTint,
        animationSpec = tween(200),
        label = "categoryContent"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = tween(150),
        label = "categoryScale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .testTag("category_${category.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                category.icon,
                contentDescription = category.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// AI TRIP FINDER CTA CARD
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AiTripFinderCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = TravelNavyPrimary,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = TravelAmberAccent,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Don't know where to go?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tell AI your budget, available days and preferences.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TravelAmberAccent,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Default.FlightTakeoff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Find a destination",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
