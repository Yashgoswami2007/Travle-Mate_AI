package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import com.example.ui.screens.ai.AiChatScreen
import com.example.ui.screens.auth.AuthScreen
import com.example.ui.screens.destination.DestinationDetailScreen
import com.example.ui.screens.expenses.ExpensesScreen
import com.example.ui.screens.explore.ExploreScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.itinerary_wizard.TripWizardScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.trip.TripDetailScreen
import com.example.ui.screens.trip.TripsListScreen
import com.example.ui.screens.chat.ChatInboxScreen
import com.example.ui.screens.chat.ChatConversationScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private var viewModelRef: MainViewModel? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "tripmate" && uri.host == "auth-callback") {
                viewModelRef?.handleOAuthCallback(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val mainViewModel: MainViewModel = viewModel()
                viewModelRef = mainViewModel

                // Handle initial intent if launched via deep link
                LaunchedEffect(Unit) {
                    intent?.data?.let { uri ->
                        if (uri.scheme == "tripmate" && uri.host == "auth-callback") {
                            mainViewModel.handleOAuthCallback(uri)
                        }
                    }
                }
                val currentScreen by mainViewModel.currentScreen.collectAsState()
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }

                // Collect toasts and snackbars
                LaunchedEffect(Unit) {
                    mainViewModel.toastMessage.collectLatest { message ->
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
                val totalUnreadChatCount by mainViewModel.totalUnreadChatCount.collectAsState()
                
                // Check if current screen is a main tab
                val isMainTab = when (currentScreen) {
                    Screen.Home, Screen.Trips, Screen.Explore, Screen.Expenses, Screen.Profile, Screen.ChatInbox -> true
                    else -> false
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = TravelSandBackground,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = TravelNavyDark,
                                contentColor = Color.White,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    bottomBar = {
                        if (isMainTab) {
                            NavigationBar(
                                containerColor = TravelSurfaceCard,
                                contentColor = TravelNavyPrimary,
                                tonalElevation = 8.dp,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("main_bottom_nav_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == Screen.Home,
                                    onClick = { mainViewModel.navigateTo(Screen.Home) },
                                    icon = {
                                        Icon(
                                            if (currentScreen == Screen.Home) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = "Home"
                                        )
                                    },
                                    label = { Text("Home", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TravelNavyPrimary,
                                        indicatorColor = TravelNavyContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_home")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == Screen.Trips,
                                    onClick = { mainViewModel.navigateTo(Screen.Trips) },
                                    icon = {
                                        Icon(
                                            if (currentScreen == Screen.Trips) Icons.Filled.Luggage else Icons.Outlined.Luggage,
                                            contentDescription = "Trips"
                                        )
                                    },
                                    label = { Text("Trips", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TravelNavyPrimary,
                                        indicatorColor = TravelNavyContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_trips")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == Screen.Explore,
                                    onClick = { mainViewModel.navigateTo(Screen.Explore) },
                                    icon = {
                                        Icon(
                                            if (currentScreen == Screen.Explore) Icons.Filled.Explore else Icons.Outlined.Explore,
                                            contentDescription = "Explore"
                                        )
                                    },
                                    label = { Text("Explore", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TravelNavyPrimary,
                                        indicatorColor = TravelNavyContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_explore")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == Screen.Expenses,
                                    onClick = { mainViewModel.navigateTo(Screen.Expenses) },
                                    icon = {
                                        Icon(
                                            if (currentScreen == Screen.Expenses) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                                            contentDescription = "Expenses"
                                        )
                                    },
                                    label = { Text("Expenses", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TravelNavyPrimary,
                                        indicatorColor = TravelNavyContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_expenses")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == Screen.ChatInbox,
                                    onClick = { mainViewModel.navigateTo(Screen.ChatInbox) },
                                    icon = {
                                        if (totalUnreadChatCount > 0) {
                                            BadgedBox(badge = { Badge { Text(totalUnreadChatCount.toString()) } }) {
                                                Icon(
                                                    if (currentScreen == Screen.ChatInbox) Icons.Filled.Chat else Icons.Outlined.Chat,
                                                    contentDescription = "Chat"
                                                )
                                            }
                                        } else {
                                            Icon(
                                                if (currentScreen == Screen.ChatInbox) Icons.Filled.Chat else Icons.Outlined.Chat,
                                                contentDescription = "Chat"
                                            )
                                        }
                                    },
                                    label = { Text("Chat", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TravelNavyPrimary,
                                        indicatorColor = TravelNavyContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_chat")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == Screen.Profile,
                                    onClick = { mainViewModel.navigateTo(Screen.Profile) },
                                    icon = {
                                        Icon(
                                            if (currentScreen == Screen.Profile) Icons.Filled.Person else Icons.Outlined.Person,
                                            contentDescription = "Profile"
                                        )
                                    },
                                    label = { Text("Profile", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = TravelNavyPrimary,
                                        indicatorColor = TravelNavyContainer
                                    ),
                                    modifier = Modifier.testTag("nav_item_profile")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (val screen = currentScreen) {
                            is Screen.Home -> HomeScreen(viewModel = mainViewModel)
                            is Screen.Trips -> TripsListScreen(viewModel = mainViewModel)
                            is Screen.Explore -> ExploreScreen(viewModel = mainViewModel)
                            is Screen.Expenses -> ExpensesScreen(viewModel = mainViewModel)
                            is Screen.Profile -> ProfileScreen(viewModel = mainViewModel)
                            is Screen.TripDetail -> TripDetailScreen(tripId = screen.tripId, viewModel = mainViewModel)
                            is Screen.DestinationDetail -> DestinationDetailScreen(destinationId = screen.destinationId, viewModel = mainViewModel)
                            is Screen.TripWizard -> TripWizardScreen(viewModel = mainViewModel)
                            is Screen.AiChat -> AiChatScreen(tripId = screen.tripId, viewModel = mainViewModel)
                            is Screen.ChatInbox -> ChatInboxScreen(viewModel = mainViewModel)
                            is Screen.ChatConversation -> ChatConversationScreen(convId = screen.convId, viewModel = mainViewModel)
                            is Screen.Auth -> AuthScreen(viewModel = mainViewModel)
                            else -> HomeScreen(viewModel = mainViewModel)
                        }
                    }
                }
            }
        }
    }
}
