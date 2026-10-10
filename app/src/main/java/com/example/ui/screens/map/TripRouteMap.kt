package com.example.ui.screens.map

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.local.entity.ItineraryItemEntity
import com.example.data.remote.OpenRouteService
import com.example.data.remote.OpenRouteServiceConfig
import com.example.data.remote.RouteResult
import com.example.data.remote.RouteStep
import com.example.domain.ai.TravelScheduleUtils
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class MapViewMode(val title: String) {
    LIVE_MAP("Interactive OpenStreetMap"),
    DIRECTIONS("Turn-by-Turn"),
    CANVAS("Geographic Canvas")
}

/**
 * Production-grade Travel Route Map powered by Openrouteservice and OpenStreetMap.
 * Features:
 * 1. Live interactive OpenStreetMap tile rendering with zoom, pan, and custom numbered markers.
 * 2. Real road geometry snapping via Openrouteservice Directions API (driving-car / foot-walking).
 * 3. Actual road distance (km) and driving/walking time calculations.
 * 4. Step-by-step turn-by-turn navigation maneuver directions.
 * 5. Dynamic fallback to equirectangular geometric canvas if offline or API key unconfigured.
 * 6. In-app Openrouteservice API token configuration modal.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TripRouteMap(
    items: List<ItineraryItemEntity>,
    destinationName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val orsService = remember { OpenRouteService() }

    val validItems = remember(items) {
        items.filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .sortedBy { it.orderIndex }
    }

    var selectedIndex by remember(items) {
        mutableStateOf(0.coerceAtMost((validItems.size - 1).coerceAtLeast(0)))
    }
    val selectedItem = validItems.getOrNull(selectedIndex)

    var viewMode by remember { mutableStateOf(MapViewMode.LIVE_MAP) }
    var selectedProfile by remember { mutableStateOf(OpenRouteService.PROFILE_DRIVING) }
    var routeResult by remember { mutableStateOf<RouteResult?>(null) }
    var isLoadingRoute by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Fallback math metrics
    val localRouteStats = remember(validItems) {
        TravelScheduleUtils.calculateRouteStats(validItems)
    }

    // Effective active route metrics (prefer ORS real road data over straight-line Haversine)
    val totalDistanceKm = routeResult?.takeIf { it.totalDistanceKm > 0 }?.totalDistanceKm
        ?: localRouteStats.totalDistanceKm
    val totalDurationMinutes = routeResult?.takeIf { it.totalDurationMinutes > 0 }?.totalDurationMinutes
        ?: localRouteStats.totalTransitMinutes
    val isRealRoadSnapping = routeResult != null && !routeResult!!.isFallback

    // Fetch ORS directions whenever waypoints, profile, or API key changes
    fun refreshRoute() {
        if (validItems.size >= 2) {
            coroutineScope.launch {
                isLoadingRoute = true
                val waypoints = validItems.map { Pair(it.latitude, it.longitude) }
                val apiKey = OpenRouteServiceConfig.getApiKey(context)
                val res = orsService.fetchRoute(waypoints, selectedProfile, apiKey)
                routeResult = res
                isLoadingRoute = false
            }
        } else {
            routeResult = null
        }
    }

    LaunchedEffect(validItems, selectedProfile) {
        refreshRoute()
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TravelSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: Title & Map Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(TravelNavyContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = null,
                            tint = TravelNavyPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "$destinationName Road Route",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isRealRoadSnapping) TravelForestGreen else TravelAmberAccent,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (isRealRoadSnapping) "OpenRouteService Active" else "Coordinate Polyline",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Transport Mode & Key Config Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Profile toggle: Car vs Walk
                    FilledTonalIconButton(
                        onClick = {
                            selectedProfile = if (selectedProfile == OpenRouteService.PROFILE_DRIVING) {
                                OpenRouteService.PROFILE_WALKING
                            } else {
                                OpenRouteService.PROFILE_DRIVING
                            }
                        },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = TravelNavyContainer,
                            contentColor = TravelNavyPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (selectedProfile == OpenRouteService.PROFILE_DRIVING) {
                                Icons.Default.DirectionsCar
                            } else {
                                Icons.Default.DirectionsWalk
                            },
                            contentDescription = "Toggle Transit Profile",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // API Key Settings Icon
                    FilledTonalIconButton(
                        onClick = { showApiKeyDialog = true },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = TravelNavyContainer,
                            contentColor = TravelNavyPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "OpenRouteService API Key",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // View Mode Selector Pills (Live Map | Turn-by-Turn | Canvas)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapViewMode.values().forEach { mode ->
                    val isSelected = (viewMode == mode)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewMode = mode },
                        label = {
                            Text(
                                text = mode.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TravelNavyPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Route Metric Summary Bar: Distance • Road Transit • Profile
            if (validItems.size > 1) {
                Surface(
                    color = TravelSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isLoadingRoute) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = TravelNavyPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (selectedProfile == OpenRouteService.PROFILE_DRIVING) {
                                        Icons.Default.DirectionsCar
                                    } else {
                                        Icons.Default.DirectionsWalk
                                    },
                                    contentDescription = null,
                                    tint = TravelNavyPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "${String.format(Locale.US, "%.1f", totalDistanceKm)} km",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }

                        Text(text = "•", fontSize = 12.sp, color = TextSecondary)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = TravelTerracotta,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "~$totalDurationMinutes mins road transit",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }

                        Text(text = "•", fontSize = 12.sp, color = TextSecondary)

                        Text(
                            text = if (isRealRoadSnapping) "Map" else "Maps",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRealRoadSnapping) TravelForestGreen else TravelAmberAccent
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Main Map Presentation Area
            if (validItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = TravelNavySecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No waypoints with GPS coordinates for this day.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                when (viewMode) {
                    MapViewMode.LIVE_MAP -> {
                        OpenStreetMapWebView(
                            items = validItems,
                            selectedIndex = selectedIndex,
                            routeResult = routeResult,
                            onMarkerClicked = { idx ->
                                if (idx in validItems.indices) {
                                    selectedIndex = idx
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, TravelBorderSubtle, RoundedCornerShape(14.dp))
                        )
                    }

                    MapViewMode.DIRECTIONS -> {
                        val steps = routeResult?.steps.orEmpty()
                        if (steps.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(TravelSandBackground, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Connect at least 2 stops to view road directions.",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        } else {
                            Surface(
                                color = TravelSandBackground,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TravelBorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Text(
                                            text = "Turn-by-Turn Road Directions (${steps.size} maneuvers)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TravelNavyDark
                                        )
                                    }
                                    itemsIndexed(steps) { idx, step ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = TravelNavyContainer,
                                                shape = CircleShape,
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${idx + 1}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TravelNavyPrimary
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = step.instruction,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "${String.format(Locale.US, "%.2f", step.distanceKm)} km • ~${step.durationMinutes} mins • ${step.streetName}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MapViewMode.CANVAS -> {
                        CanvasMapProjection(
                            validItems = validItems,
                            selectedIndex = selectedIndex,
                            onSelectIndex = { selectedIndex = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE6EDF5))
                                .border(1.dp, TravelBorderSubtle, RoundedCornerShape(14.dp))
                        )
                    }
                }
            }

            // Interactive Selected Waypoint Card with Navigation Stepper
            selectedItem?.let { sel ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = TravelSurfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TravelBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = if (selectedIndex == 0) TravelForestGreen else TravelNavyPrimary,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Stop #${selectedIndex + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = TravelNavyContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = sel.category,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TravelNavyDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Prev / Next stop navigation buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        if (selectedIndex > 0) selectedIndex--
                                    },
                                    enabled = selectedIndex > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Previous Stop",
                                        tint = if (selectedIndex > 0) TravelNavyPrimary else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (selectedIndex < validItems.size - 1) selectedIndex++
                                    },
                                    enabled = selectedIndex < validItems.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Next Stop",
                                        tint = if (selectedIndex < validItems.size - 1) TravelNavyPrimary else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = sel.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${sel.startTime} - ${sel.endTime} (${sel.durationMinutes} mins) • ${sel.locationName}",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Transit: ${sel.travelTimeFromPrevious}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TravelNavyPrimary
                                )
                            }

                            if (sel.estimatedCost > 0) {
                                Surface(
                                    color = TravelAmberAccent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "₹${sel.estimatedCost.toInt()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TravelAmberAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // GPS Coordinates chip
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = String.format(Locale.US, "GPS: %.4f°N, %.4f°E", sel.latitude, sel.longitude),
                                fontSize = 10.sp,
                                color = TextSecondary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = "• ${sel.source}",
                                fontSize = 10.sp,
                                color = TravelForestGreen
                            )
                        }
                    }
                }
            }
        }
    }

    // OpenRouteService API Key Configuration Dialog
    if (showApiKeyDialog) {
        var keyInput by remember {
            mutableStateOf(OpenRouteServiceConfig.getApiKey(context))
        }

        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = TravelNavyPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OpenRouteService API Key", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "OpenRouteService provides live real-world road geometry snapping, turn-by-turn routing, and exact travel durations.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("API Token (openrouteservice.org)") },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFF0F172A),
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Surface(
                        color = TravelSandBackground,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Token can also be set in your root .env file as OPENROUTESERVICE_API_KEY.",
                            fontSize = 11.sp,
                            color = TravelNavyDark,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        OpenRouteServiceConfig.saveApiKey(context, keyInput.trim())
                        showApiKeyDialog = false
                        refreshRoute()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TravelNavyPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save & Refresh Route")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Android WebView hosting interactive Leaflet.js map with OpenStreetMap tiles.
 * Renders waypoint markers, road polyline from OpenRouteService GeoJSON, and handles two-way touch callbacks.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OpenStreetMapWebView(
    items: List<ItineraryItemEntity>,
    selectedIndex: Int,
    routeResult: RouteResult?,
    onMarkerClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val htmlContent = remember(items, routeResult) {
        buildLeafletHtml(items, routeResult)
    }

    var lastHtml by remember { mutableStateOf("") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onMarkerClick(index: Int) {
                            onMarkerClicked(index)
                        }
                    },
                    "AndroidBridge"
                )

                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            if (lastHtml != htmlContent) {
                lastHtml = htmlContent
                webView.loadDataWithBaseURL("https://openrouteservice.org", htmlContent, "text/html", "UTF-8", null)
            }
            webView.evaluateJavascript("if (typeof updateSelection === 'function') { updateSelection($selectedIndex); }", null)
        }
    )
}

/**
 * Builds high-definition, interactive HTML with Leaflet.js, OpenStreetMap tiles,
 * custom pin styling, and real road GeoJSON polyline.
 */
private fun buildLeafletHtml(
    items: List<ItineraryItemEntity>,
    routeResult: RouteResult?
): String {
    val markersJson = JSONArray()
    items.forEachIndexed { idx, itm ->
        val obj = JSONObject().apply {
            put("index", idx)
            put("number", idx + 1)
            put("title", itm.title)
            put("time", "${itm.startTime} - ${itm.endTime}")
            put("category", itm.category)
            put("cost", if (itm.estimatedCost > 0) "₹${itm.estimatedCost.toInt()}" else "Free")
            put("lat", itm.latitude)
            put("lon", itm.longitude)
            put("isStart", idx == 0)
            put("isEnd", idx == items.size - 1 && items.size > 1)
        }
        markersJson.put(obj)
    }

    val geoJsonString = routeResult?.rawGeoJson?.takeIf { !routeResult.isFallback } ?: "null"
    val fallbackPointsJson = JSONArray()
    items.forEach { pt ->
        val pair = JSONArray().apply {
            put(pt.latitude)
            put(pt.longitude)
        }
        fallbackPointsJson.put(pair)
    }

    return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
      <style>
        html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #e2e8f0; }
        .pin-container {
          display: flex; align-items: center; justify-content: center;
          width: 30px; height: 30px; border-radius: 50%;
          color: #ffffff; font-weight: 800; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          font-size: 13px; box-shadow: 0 3px 8px rgba(15, 23, 42, 0.4);
          border: 2.5px solid #ffffff; transition: transform 0.2s ease;
        }
        .pin-start { background: #16A34A; }
        .pin-mid { background: #1E3A5F; }
        .pin-end { background: #E06D53; }
        .pin-selected { background: #E06D53; transform: scale(1.22); border-color: #FBBF24; box-shadow: 0 0 12px #E06D53; }
        .leaflet-popup-content-wrapper { border-radius: 12px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; box-shadow: 0 4px 14px rgba(0,0,0,0.25); }
        .leaflet-popup-content { margin: 10px 14px; font-size: 12px; line-height: 1.4; color: #0f172a; }
        .popup-title { font-weight: 700; font-size: 13px; margin-bottom: 2px; color: #1E3A5F; }
        .popup-badge { display: inline-block; background: #E2E8F0; color: #0F172A; border-radius: 4px; padding: 1px 6px; font-size: 10px; font-weight: 600; margin-top: 3px; }
      </style>
    </head>
    <body>
      <div id="map"></div>
      <script>
        var map = L.map('map', { zoomControl: true, attributionControl: false });
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19
        }).addTo(map);

        var markersData = $markersJson;
        var rawGeoJson = $geoJsonString;
        var fallbackCoords = $fallbackPointsJson;
        var latlngs = [];
        var markerRefs = [];

        // 1. Draw Road Polyline (OpenRouteService GeoJSON or Geodesic fallback)
        if (rawGeoJson && rawGeoJson.features && rawGeoJson.features.length > 0) {
          var routeLayer = L.geoJSON(rawGeoJson, {
            style: {
              color: '#1E3A5F',
              weight: 5,
              opacity: 0.9,
              lineJoin: 'round',
              lineCap: 'round'
            }
          }).addTo(map);
          // Highlight outline
          L.geoJSON(rawGeoJson, {
            style: {
              color: '#E06D53',
              weight: 2,
              opacity: 0.8
            }
          }).addTo(map);
        } else if (fallbackCoords && fallbackCoords.length > 1) {
          L.polyline(fallbackCoords, {
            color: '#1E3A5F',
            weight: 4,
            opacity: 0.85,
            dashArray: '8, 6'
          }).addTo(map);
        }

        // 2. Add Waypoint Numbered Markers
        markersData.forEach(function(m) {
          latlngs.push([m.lat, m.lon]);

          var pinClass = 'pin-mid';
          if (m.isStart) {
            pinClass = 'pin-start';
          } else if (m.isEnd) {
            pinClass = 'pin-end';
          }

          var customIcon = L.divIcon({
            className: '',
            html: '<div id="pin-' + m.index + '" class="pin-container ' + pinClass + '">' + m.number + '</div>',
            iconSize: [30, 30],
            iconAnchor: [15, 15],
            popupAnchor: [0, -16]
          });

          var marker = L.marker([m.lat, m.lon], { icon: customIcon }).addTo(map);
          markerRefs[m.index] = marker;

          var popupHtml = '<div class="popup-title">#' + m.number + ' ' + m.title + '</div>' +
            '<div>' + m.time + '</div>' +
            '<span class="popup-badge">' + m.category + '</span>' +
            '<span class="popup-badge" style="background:#FEF3C7;color:#D97706;margin-left:4px;">' + m.cost + '</span>';

          marker.bindPopup(popupHtml);

          marker.on('click', function() {
            if (window.AndroidBridge && window.AndroidBridge.onMarkerClick) {
              window.AndroidBridge.onMarkerClick(m.index);
            }
          });
        });

        // 3. Fit Bounds to cover all waypoints with padding
        if (latlngs.length > 0) {
          map.fitBounds(L.latLngBounds(latlngs), { padding: [36, 36], maxZoom: 16 });
        }

        // 4. Update Selection State Dynamically
        window.updateSelection = function(selectedIndex) {
          markersData.forEach(function(m) {
            var pinElement = document.getElementById('pin-' + m.index);
            if (pinElement) {
              var baseClass = m.isStart ? 'pin-start' : (m.isEnd ? 'pin-end' : 'pin-mid');
              pinElement.className = 'pin-container ' + baseClass + (m.index === selectedIndex ? ' pin-selected' : '');
            }
          });
          var selectedMarker = markerRefs[selectedIndex];
          if (selectedMarker && !selectedMarker.isPopupOpen()) {
            selectedMarker.openPopup();
          }
        };
      </script>
    </body>
    </html>
    """.trimIndent()
}

/**
 * Fallback Equirectangular Geographic Canvas.
 */
@Composable
private fun CanvasMapProjection(
    validItems: List<ItineraryItemEntity>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val numberPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(validItems) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        validItems.forEachIndexed { idx, item ->
                            val pt = projectPoint(item, validItems, w, h)
                            val dist = (tapOffset - pt).getDistance()
                            if (dist < 46f) {
                                onSelectIndex(idx)
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Cartographic Grid
            val gridColor = Color(0xFFD4DFEE)
            for (i in 1..4) {
                val y = h * (i / 5f)
                val x = w * (i / 5f)
                drawLine(color = gridColor, start = Offset(0f, y), end = Offset(w, y), strokeWidth = 1f)
                drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 1f)
            }

            val projectedPoints = validItems.map { projectPoint(it, validItems, w, h) }

            // Polylines
            if (projectedPoints.size > 1) {
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                for (i in 0 until projectedPoints.size - 1) {
                    val p1 = projectedPoints[i]
                    val p2 = projectedPoints[i + 1]
                    drawLine(
                        color = TravelNavyPrimary,
                        start = p1,
                        end = p2,
                        strokeWidth = 5f,
                        pathEffect = pathEffect
                    )
                }
            }

            // Pins
            projectedPoints.forEachIndexed { idx, pt ->
                val isSelected = (idx == selectedIndex)
                val isStart = (idx == 0)
                val isEnd = (idx == projectedPoints.size - 1 && projectedPoints.size > 1)

                val haloRadius = if (isSelected) 26f else 18f
                val haloColor = when {
                    isSelected -> TravelAmberAccent.copy(alpha = 0.45f)
                    isStart -> TravelForestGreen.copy(alpha = 0.25f)
                    isEnd -> TravelTerracotta.copy(alpha = 0.25f)
                    else -> TravelNavyPrimary.copy(alpha = 0.15f)
                }
                drawCircle(color = haloColor, radius = haloRadius, center = pt)

                val pinRadius = if (isSelected) 17f else 14f
                val pinColor = when {
                    isSelected -> TravelTerracotta
                    isStart -> TravelForestGreen
                    isEnd -> TravelTerracotta
                    else -> TravelNavyPrimary
                }
                drawCircle(color = pinColor, radius = pinRadius, center = pt)
                drawCircle(color = Color.White, radius = pinRadius, center = pt, style = Stroke(width = 2.5f))

                val stopNumberStr = (idx + 1).toString()
                drawContext.canvas.nativeCanvas.drawText(
                    stopNumberStr,
                    pt.x,
                    pt.y + 10f,
                    numberPaint
                )
            }
        }

        // Compass Rose
        Surface(
            color = Color(0xDD0F172A),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = "North",
                    tint = TravelAmberAccent,
                    modifier = Modifier.size(12.dp)
                )
                Text(text = "N", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/**
 * Projects a geographic latitude/longitude coordinate onto the 2D canvas using equirectangular projection.
 */
private fun projectPoint(
    item: ItineraryItemEntity,
    allItems: List<ItineraryItemEntity>,
    width: Float,
    height: Float
): Offset {
    val padding = 54f
    val effectiveW = (width - padding * 2).coerceAtLeast(10f)
    val effectiveH = (height - padding * 2).coerceAtLeast(10f)

    if (allItems.isEmpty()) return Offset(width / 2f, height / 2f)

    val minLat = allItems.minOf { it.latitude }
    val maxLat = allItems.maxOf { it.latitude }
    val minLon = allItems.minOf { it.longitude }
    val maxLon = allItems.maxOf { it.longitude }

    val rawLatSpan = maxLat - minLat
    val rawLonSpan = maxLon - minLon

    val latMargin = (rawLatSpan * 0.18).coerceAtLeast(0.003)
    val lonMargin = (rawLonSpan * 0.18).coerceAtLeast(0.003)

    val paddedMinLat = minLat - latMargin
    val paddedMaxLat = maxLat + latMargin
    val paddedMinLon = minLon - lonMargin
    val paddedMaxLon = maxLon + lonMargin

    val latSpan = (paddedMaxLat - paddedMinLat).coerceAtLeast(0.0001)
    val lonSpan = (paddedMaxLon - paddedMinLon).coerceAtLeast(0.0001)

    val x = padding + ((item.longitude - paddedMinLon) / lonSpan).toFloat() * effectiveW
    val y = padding + ((paddedMaxLat - item.latitude) / latSpan).toFloat() * effectiveH

    return Offset(x, y)
}
