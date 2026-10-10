package com.example.ui.screens.trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.home.ActiveTripCard
import com.example.ui.theme.TravelNavyPrimary
import com.example.ui.theme.TravelSandBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen

@Composable
fun TripsListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val trips by viewModel.allTrips.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TravelSandBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.navigateTo(Screen.TripWizard()) },
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                text = { Text("Plan New Trip", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = TravelNavyPrimary,
                modifier = Modifier
                    .testTag("plan_new_trip_fab")
                    .padding(bottom = 72.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "My Trips (${trips.size})",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Your active workspaces, itineraries, and travel budgets",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            items(trips) { trip ->
                ActiveTripCard(
                    trip = trip,
                    onClick = { viewModel.selectTrip(trip.id) },
                    onAskAiClick = { viewModel.navigateTo(Screen.AiChat(trip.id)) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
