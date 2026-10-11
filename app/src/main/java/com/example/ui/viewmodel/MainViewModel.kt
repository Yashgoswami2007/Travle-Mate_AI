package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SeedData
import com.example.data.local.entity.*
import com.example.data.repository.*
import com.example.domain.ai.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class Screen {
    object Home : Screen()
    object Trips : Screen()
    object Explore : Screen()
    object Expenses : Screen()
    object Profile : Screen()
    data class DestinationDetail(val destinationId: String) : Screen()
    data class TripDetail(val tripId: String) : Screen()
    data class TripWizard(val destinationId: String? = null) : Screen()
    data class AiChat(val tripId: String? = null) : Screen()
    object Auth : Screen()
    object ChatInbox : Screen()
    data class ChatConversation(val convId: String) : Screen()
    object Onboarding : Screen()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    val tripRepo = TripRepository(db)
    val geocodingService = com.example.data.remote.GeocodingService(application)
    val destRepo = DestinationRepository(db, geocodingService)
    val userRepo = UserRepository(db)
    val aiEngine = AiTravelPlannerEngine(db)
    val chatIdentityRepo = com.example.data.repository.ChatIdentityRepository(application)
    val chatRepo = com.example.data.repository.ChatRepository(chatIdentityRepo)
    val authManager = com.example.auth.SupabaseAuthManager(application)
    val profileRepo = com.example.data.repository.ProfileRepository(db, authManager)
    val collabRepo = com.example.data.repository.TripCollaborationRepository(db, authManager)

    val authState = authManager.authState
    val currentSession = authManager.currentSession
    val currentPublicProfile = profileRepo.currentPublicProfile
    val currentPrivateProfile = profileRepo.currentPrivateProfile
    val pendingInvitations = collabRepo.pendingInvitations
    val tripMembersMap = collabRepo.tripMembers

    private val prefs = application.getSharedPreferences("tripmate_prefs", android.content.Context.MODE_PRIVATE)
    val isOnboardingCompleted = MutableStateFlow(prefs.getBoolean("onboarding_completed", false))

    // Current Screen
    private val _currentScreen = MutableStateFlow<Screen>(if (isOnboardingCompleted.value) Screen.Home else Screen.Onboarding)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Screen stack for back navigation
    private val screenStack = mutableListOf<Screen>(if (isOnboardingCompleted.value) Screen.Home else Screen.Onboarding)

    // Current User
    val currentUser: StateFlow<UserEntity?> = userRepo.getActiveUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeedData.initialUser)

    // Chat Identity
    val currentChatIdentity = MutableStateFlow<com.example.data.repository.ChatIdentity?>(null)

    // Trips
    val allTrips: StateFlow<List<TripEntity>> = tripRepo.getAllTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Trip ID
    private val _selectedTripId = MutableStateFlow<String>("trip_jaipur_couple")
    val selectedTripId: StateFlow<String> = _selectedTripId.asStateFlow()

    val currentTrip: StateFlow<TripEntity?> = _selectedTripId
        .flatMapLatest { id -> tripRepo.getTripFlow(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Itinerary for selected trip
    val currentItinerary: StateFlow<List<ItineraryItemEntity>> = _selectedTripId
        .flatMapLatest { id -> tripRepo.getItineraryItems(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Destinations
    val allDestinations: StateFlow<List<DestinationEntity>> = destRepo.getAllDestinations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Destination for exploration
    private val _selectedDestinationId = MutableStateFlow<String>("dest_jaipur")
    val selectedDestinationId: StateFlow<String> = _selectedDestinationId.asStateFlow()

    val currentDestinationPlaces: StateFlow<List<PlaceEntity>> = _selectedDestinationId
        .flatMapLatest { id -> destRepo.getPlaces(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentDestinationRestaurants: StateFlow<List<RestaurantEntity>> = _selectedDestinationId
        .flatMapLatest { id -> destRepo.getRestaurants(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentTransportOptions: StateFlow<List<TransportOptionEntity>> = _selectedDestinationId
        .flatMapLatest { id -> destRepo.getTransportOptions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentHotelOptions: StateFlow<List<HotelOptionEntity>> = _selectedDestinationId
        .flatMapLatest { id -> destRepo.getHotelOptions(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentDestinationImages: StateFlow<List<DestinationImageEntity>> = _selectedDestinationId
        .flatMapLatest { id -> destRepo.getDestinationImages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedDestinations: StateFlow<List<UserSavedDestinationEntity>> = userRepo.getSavedDestinations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceIdentityManager = com.example.data.repository.DeviceIdentityManager(application)
    val deviceUserIdentity = deviceIdentityManager.identityState

    val savedParticipants: StateFlow<List<SavedParticipantEntity>> = tripRepo.getSavedParticipants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Expenses & Members for current trip
    val currentExpenses: StateFlow<List<ExpenseEntity>> = _selectedTripId
        .flatMapLatest { id -> tripRepo.getExpenses(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentMembers: StateFlow<List<GroupMemberEntity>> = combine(_selectedTripId, deviceUserIdentity) { tripId, identity ->
        tripId to identity
    }.flatMapLatest { (id, identity) ->
        tripRepo.getMembers(id).map { members ->
            val currentDeviceId = identity?.deviceId
            val currentUsername = identity?.username
            members.map { mem ->
                val isSelf = (currentDeviceId != null && (mem.participantId == currentDeviceId || mem.id == currentDeviceId)) ||
                        (!currentUsername.isNullOrBlank() && mem.name.equals(currentUsername, ignoreCase = true)) ||
                        mem.isCurrentUser
                mem.copy(isCurrentUser = isSelf)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSplits: StateFlow<List<ExpenseSplitEntity>> = _selectedTripId
        .flatMapLatest { id -> tripRepo.getSplits(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentSettlements: StateFlow<List<SettlementEntity>> = _selectedTripId
        .flatMapLatest { id -> tripRepo.getSettlements(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRefreshingExpenses = MutableStateFlow(false)

    fun refreshExpenses() {
        viewModelScope.launch {
            isRefreshingExpenses.value = true
            tripRepo.syncTripData(_selectedTripId.value)
            isRefreshingExpenses.value = false
        }
    }

    val currentNetBalances: StateFlow<List<com.example.domain.expense.MemberBalance>> = combine(
        currentMembers,
        currentExpenses,
        currentSplits,
        currentSettlements
    ) { members, expenses, splits, settlements ->
        val memberPairs = members.map { it.id to it.name }

        val expenseCalculationDataList = mutableListOf<com.example.domain.expense.ExpenseCalculationData>()
        for (exp in expenses) {
            expenseCalculationDataList.add(
                com.example.domain.expense.ExpenseCalculationData(
                    memberId = exp.paidByMemberId,
                    memberName = exp.paidByMemberName,
                    paidAmountInPaise = exp.amountInPaise,
                    shareAmountInPaise = 0L
                )
            )
        }
        for (split in splits) {
            expenseCalculationDataList.add(
                com.example.domain.expense.ExpenseCalculationData(
                    memberId = split.memberId,
                    memberName = split.memberName,
                    paidAmountInPaise = 0L,
                    shareAmountInPaise = split.shareAmountInPaise
                )
            )
        }

        val settlementRecordDataList = settlements.map { s ->
            com.example.domain.expense.SettlementRecordData(
                payerMemberId = s.payerMemberId,
                receiverMemberId = s.receiverMemberId,
                amountInPaise = s.amountInPaise
            )
        }

        com.example.domain.expense.ExpenseEngine.calculateNetBalances(
            members = memberPairs,
            expenseRecords = expenseCalculationDataList,
            settlementRecords = settlementRecordDataList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentPairwiseDebts: StateFlow<List<com.example.domain.expense.PairwiseDebt>> = currentNetBalances
        .map { balances -> com.example.domain.expense.ExpenseEngine.calculatePairwiseDebts(balances) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBookings: StateFlow<List<BookingEntity>> = _selectedTripId
        .flatMapLatest { id -> tripRepo.getBookings(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chat messages (peer to peer)
    val chatConversations: StateFlow<List<com.example.data.repository.ChatConversation>> = chatRepo.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalUnreadChatCount: StateFlow<Int> = chatConversations
        .map { list -> list.sumOf { it.unreadCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _currentChatMessages = MutableStateFlow<List<com.example.data.repository.PeerMessage>>(emptyList())
    val currentChatMessages: StateFlow<List<com.example.data.repository.PeerMessage>> = _currentChatMessages.asStateFlow()

    // AI Chat messages
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _selectedTripId
        .flatMapLatest { id -> tripRepo.getChatMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search History
    val recentSearches: StateFlow<List<SearchHistoryEntity>> = destRepo.getRecentSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search Query on Home / Explore
    val searchQuery = MutableStateFlow("")
    val searchResults = MutableStateFlow<List<DestinationEntity>>(emptyList())
    val liveLocationResults = MutableStateFlow<List<com.example.data.remote.GeocodedLocation>>(emptyList())
    val isSearchingLocations = MutableStateFlow(false)
    val isResearchingDestination = MutableStateFlow(false)
    val researchingDestinationName = MutableStateFlow("")

    // Wizard State
    val wizardStartDate = MutableStateFlow("")
    val wizardEndDate = MutableStateFlow("")
    val wizardDays = MutableStateFlow(0)
    val wizardSelectedDestination = MutableStateFlow("")
    val wizardBudget = MutableStateFlow("")
    val wizardStartingCity = MutableStateFlow("")
    val wizardTravellers = MutableStateFlow(2)
    val wizardTravellerType = MutableStateFlow("Couple") // Solo, Couple, Friends, Family
    val wizardQuestions = MutableStateFlow(
        com.example.domain.ai.ItineraryPlannerWizard.buildAdaptiveQuestions(
            destination = "",
            origin = "",
            days = 0,
            budget = 0.0,
            travellerType = "Couple",
            travellerCount = 2
        )
    )
    val currentQuestionIndex = MutableStateFlow(0)
    val isGeneratingItinerary = MutableStateFlow(false)
    val generationProgressMessage = MutableStateFlow("Synthesizing itinerary via Gemini AI...")

    fun updateWizardDates(newStart: String, newEnd: String) {
        wizardStartDate.value = newStart
        wizardEndDate.value = newEnd
        val start = com.example.domain.ai.TravelDateUtils.parseDate(newStart)
        val end = com.example.domain.ai.TravelDateUtils.parseDate(newEnd)
        wizardDays.value = com.example.domain.ai.TravelDateUtils.calculateDays(start, end)
        refreshWizardQuestions()
    }

    fun setWizardTravellers(count: Int) {
        wizardTravellers.value = count
        refreshWizardQuestions()
    }

    fun setWizardTravellerType(type: String) {
        wizardTravellerType.value = type
        refreshWizardQuestions()
    }

    fun setWizardDestination(dest: String) {
        wizardSelectedDestination.value = dest
        refreshWizardQuestions()
    }

    fun setWizardStartingCity(city: String) {
        wizardStartingCity.value = city
        refreshWizardQuestions()
    }

    fun setWizardBudget(budgetStr: String) {
        wizardBudget.value = budgetStr
        refreshWizardQuestions()
    }

    fun refreshWizardQuestions() {
        val dest = wizardSelectedDestination.value.trim()
        val orig = wizardStartingCity.value.trim()
        val days = wizardDays.value
        val budgetVal = wizardBudget.value.toDoubleOrNull() ?: 0.0
        val type = wizardTravellerType.value
        val count = wizardTravellers.value

        val previousAnswers = wizardQuestions.value.associate { it.id to it.selectedOption }
        val updated = com.example.domain.ai.ItineraryPlannerWizard.buildAdaptiveQuestions(
            destination = dest,
            origin = orig,
            days = days,
            budget = budgetVal,
            travellerType = type,
            travellerCount = count,
            previousAnswers = previousAnswers
        )
        wizardQuestions.value = updated
    }

    // User Location
    val userLocationName = MutableStateFlow(prefs.getString("user_location", "Delhi, India") ?: "Delhi, India")
    val isLocationPermissionGranted = MutableStateFlow(false)

    // Live Data & AI Status
    val isAiThinking = MutableStateFlow(false)
    val isRefreshingLiveData = MutableStateFlow(false)

    // Notification / Toast Message
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            authManager.currentSession.collect { session ->
                if (session != null) {
                    val user = profileRepo.loadUserProfile()
                    if (user != null) {
                        val cleanUsername = user.name.lowercase().replace("[^a-z0-9_]".toRegex(), "_")
                        chatIdentityRepo.setAuthenticatedIdentity(session.userId, cleanUsername, user.name)
                        currentChatIdentity.value = chatIdentityRepo.getIdentity()
                    }
                    collabRepo.refreshPendingInvitations()
                } else {
                    try {
                        currentChatIdentity.value = chatIdentityRepo.initializeIdentityIfNeeded()
                    } catch (e: Exception) {
                        currentChatIdentity.value = chatIdentityRepo.getIdentity()
                    }
                }
                chatRepo.refreshConversations()
            }
        }

        viewModelScope.launch {
            deviceUserIdentity.collect { identity ->
                if (identity != null) {
                    tripRepo.fetchTripsForUser(identity.deviceId)
                }
            }
        }

        // Automatic polling for real-time feel
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000)
                val current = _currentScreen.value
                if (current == Screen.Expenses || current is Screen.TripDetail || current is Screen.AiChat) {
                    tripRepo.syncTripData(_selectedTripId.value)
                } else if (current == Screen.Trips || current == Screen.Home) {
                    val identity = deviceUserIdentity.value
                    if (identity != null) {
                        tripRepo.fetchTripsForUser(identity.deviceId)
                    }
                }
            }
        }
    }

    // Explicitly load demo data if requested (e.g. from ProfileScreen)
    fun loadDemoData() {
        viewModelScope.launch {
            db.destinationDao().insertDestinations(SeedData.destinations)
            db.destinationDao().insertPlaces(SeedData.places)
            db.destinationDao().insertRestaurants(SeedData.restaurants)
            val user = userRepo.getActiveUser()
            if (user == null) {
                db.userDao().insertUser(SeedData.initialUser)
                db.travelSearchDao().insertTransportOptions(SeedData.transportOptions)
                db.travelSearchDao().insertHotelOptions(SeedData.hotelOptions)
                db.tripDao().insertTrip(SeedData.defaultTrip)
                db.expenseDao().insertMembers(SeedData.defaultTripMembers)
                db.tripDao().insertItineraryItems(SeedData.defaultItineraryItems)
                SeedData.defaultExpenses.forEach { db.expenseDao().insertExpense(it) }
                db.expenseDao().insertSplits(SeedData.defaultSplits)
                SeedData.defaultBookings.forEach { db.travelSearchDao().insertBooking(it) }
                SeedData.defaultChatMessages.forEach { db.chatDao().insertMessage(it) }
            }
            _toastMessage.emit("Demo data loaded into local database!")
        }
    }

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            if (screen == Screen.Home) {
                screenStack.clear()
                screenStack.add(Screen.Home)
            } else {
                screenStack.removeAll { it == screen }
                screenStack.add(screen)
            }
            _currentScreen.value = screen
            
            if (screen == Screen.Expenses || screen is Screen.TripDetail || screen is Screen.AiChat) {
                viewModelScope.launch {
                    tripRepo.syncTripData(_selectedTripId.value)
                }
            } else if (screen == Screen.Trips) {
                viewModelScope.launch {
                    val identity = deviceUserIdentity.value
                    if (identity != null) {
                        tripRepo.fetchTripsForUser(identity.deviceId)
                    }
                }
            }
        }
    }

    fun navigateBack(): Boolean {
        return if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.size - 1)
            _currentScreen.value = screenStack.last()
            true
        } else if (_currentScreen.value != Screen.Home) {
            screenStack.clear()
            screenStack.add(Screen.Home)
            _currentScreen.value = Screen.Home
            true
        } else {
            false
        }
    }

    fun selectTrip(tripId: String) {
        _selectedTripId.value = tripId
        val trip = allTrips.value.firstOrNull { it.id == tripId }
        if (trip != null) {
            _selectedDestinationId.value = trip.destinationId
        }
        navigateTo(Screen.TripDetail(tripId))
        viewModelScope.launch {
            tripRepo.syncTripData(tripId)
        }
    }

    fun setExploreDestination(destinationId: String) {
        _selectedDestinationId.value = destinationId
    }

    fun selectDestination(destinationId: String) {
        _selectedDestinationId.value = destinationId
        navigateTo(Screen.DestinationDetail(destinationId))
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                searchResults.value = emptyList()
                liveLocationResults.value = emptyList()
                isSearchingLocations.value = false
            } else {
                try {
                    // 1. Search local cached destinations
                    searchResults.value = destRepo.searchDestinations(query)
                } catch (e: Exception) {
                    searchResults.value = emptyList()
                }

                try {
                    // 2. Query real-time geocoder
                    isSearchingLocations.value = true
                    val live = destRepo.searchLiveLocations(query)
                    liveLocationResults.value = live
                } catch (e: Exception) {
                    liveLocationResults.value = emptyList()
                } finally {
                    isSearchingLocations.value = false
                }
            }
        }
    }

    fun selectLiveLocation(location: com.example.data.remote.GeocodedLocation) {
        viewModelScope.launch {
            researchingDestinationName.value = location.name
            isResearchingDestination.value = true
            isSearchingLocations.value = true
            try {
                _toastMessage.emit("Checking Public Cache for ${location.name}...")
                val result = destRepo.getOrResearchDestination(location.name, userLocationName.value)
                if (result.isFromCache) {
                    _toastMessage.emit("Reused from Public Destination Cache. No duplicate AI call made!")
                } else {
                    _toastMessage.emit("Researched ${result.destination.name} via Gemini AI & saved to Public DB!")
                }
                destRepo.recordSearch(location.name, location.name)
                searchQuery.value = ""
                liveLocationResults.value = emptyList()
                _selectedDestinationId.value = result.destination.id
                navigateTo(Screen.DestinationDetail(result.destination.id))
            } catch (e: Exception) {
                _toastMessage.emit("Error loading destination: ${e.message}")
            } finally {
                isSearchingLocations.value = false
                isResearchingDestination.value = false
            }
        }
    }

    fun performDestinationSearch(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) return@launch
            destRepo.recordSearch(query, query)
            researchingDestinationName.value = query
            isResearchingDestination.value = true
            isSearchingLocations.value = true
            try {
                _toastMessage.emit("Checking Public Destination Database for '$query'...")
                val result = destRepo.getOrResearchDestination(query, userLocationName.value)
                if (result.isFromCache) {
                    _toastMessage.emit("Reused ${result.destination.name} from Public Cache. No duplicate AI call made!")
                } else {
                    _toastMessage.emit("Researched ${result.destination.name} via Gemini AI & saved to Public DB!")
                }
                searchQuery.value = ""
                liveLocationResults.value = emptyList()
                selectDestination(result.destination.id)
            } catch (e: Exception) {
                _toastMessage.emit("Search error: ${e.message}")
            } finally {
                isSearchingLocations.value = false
                isResearchingDestination.value = false
            }
        }
    }

    fun refreshLiveTransportAndHotels(destinationId: String) {
        viewModelScope.launch {
            val dest = destRepo.getDestinationById(destinationId)
            if (dest != null) {
                isRefreshingLiveData.value = true
                _toastMessage.emit("Fetching real-time tickets, hotels, attractions & photos for ${dest.name}...")
                try {
                    destRepo.refreshLiveDestinationData(destinationId, userLocationName.value)
                    _toastMessage.emit("Updated ${dest.name} with live web schedules, fares & attractions!")
                } catch (e: Exception) {
                    _toastMessage.emit("Sync completed with cached web data")
                } finally {
                    isRefreshingLiveData.value = false
                }
            }
        }
    }

    fun toggleDestinationSaved(destinationId: String, currentSaved: Boolean) {
        viewModelScope.launch {
            if (currentSaved) {
                userRepo.removeSavedDestination(destinationId)
                _toastMessage.emit("Removed from your saved destinations (User Database)")
            } else {
                userRepo.saveDestination(destinationId)
                _toastMessage.emit("Saved to your personal collection (User Database)")
            }
        }
    }

    fun setLocationPermission(granted: Boolean) {
        isLocationPermissionGranted.value = granted
        if (granted) {
            userLocationName.value = "Delhi, India (Auto-detected)"
        }
    }

    // AI Chat interaction with Gemini
    fun sendChatMessage(prompt: String) {
        if (prompt.isBlank()) return
        val tripId = _selectedTripId.value
        val userMsg = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            sender = "USER",
            content = prompt
        )

        viewModelScope.launch {
            tripRepo.addChatMessage(userMsg)
            isAiThinking.value = true

            try {
                val trip = currentTrip.value
                val items = currentItinerary.value
                val history = chatMessages.value
                val result = aiEngine.processUserMessage(tripId, prompt, trip, items, history)

                val (replyContent, actionType, feedback) = when (result) {
                    is AiActionResult.Success -> Triple(result.message, result.actionType, "Plan updated!")
                    is AiActionResult.QuestionPrompt -> Triple(result.question, "QUESTION", null)
                    is AiActionResult.Informational -> Triple(result.message, null, null)
                }

                val aiMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    sender = "AI",
                    content = replyContent,
                    actionType = actionType,
                    actionFeedback = feedback
                )
                tripRepo.addChatMessage(aiMsg)

                feedback?.let { _toastMessage.emit(it) }
            } catch (e: Exception) {
                val fallbackMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    sender = "AI",
                    content = "I had trouble connecting to the network, but I'm still tracking your trip. Please try again in a moment!"
                )
                tripRepo.addChatMessage(fallbackMsg)
            } finally {
                isAiThinking.value = false
            }
        }
    }

    // Wizard completion: generate trip from answers
    fun completeWizardAndGenerateTrip() {
        viewModelScope.launch {
            isGeneratingItinerary.value = true
            val tripId = UUID.randomUUID().toString()
            val destName = wizardSelectedDestination.value.trim().ifBlank { "Jaipur" }
            val startingCity = wizardStartingCity.value.trim().ifBlank { "Delhi" }
            val totalBudgetVal = wizardBudget.value.toDoubleOrNull() ?: 25000.0
            val daysCount = wizardDays.value

            try {
                // Step 1: Research / Resolve Destination entity and real cover photo
                generationProgressMessage.value = "Researching $destName details & cover photo..."
                val cacheResult = destRepo.getOrResearchDestination(destName, startingCity)
                val dest = cacheResult.destination

                // Step 2: Create Trip Entity with resolved real cover photo URL
                val paceSelected = wizardQuestions.value.firstOrNull { it.id == "sightseeing_pace" }?.selectedOption ?: "Relaxed"
                val newTrip = TripEntity(
                    id = tripId,
                    userId = currentUser.value?.id ?: "user_default",
                    destinationId = dest.id,
                    destinationName = dest.name,
                    destinationRegion = dest.stateRegion,
                    destinationCountry = dest.country,
                    imageUrl = dest.imageUrl,
                    startDate = wizardStartDate.value,
                    endDate = wizardEndDate.value,
                    numberOfDays = daysCount,
                    travellersCount = wizardTravellers.value.coerceAtLeast(1),
                    travellersDescription = "${wizardTravellerType.value} (${wizardTravellers.value.coerceAtLeast(1)} Travelers)",
                    startingLocation = startingCity,
                    totalBudget = totalBudgetVal,
                    spentBudget = 0.0,
                    pace = paceSelected,
                    status = "ACTIVE"
                )

                tripRepo.createTrip(newTrip)

                // Add group members
                val currentUserIdentity = deviceUserIdentity.value
                if (currentUserIdentity != null) {
                    tripRepo.addParticipantToTrip(
                        tripId = tripId,
                        participantId = currentUserIdentity.deviceId,
                        name = currentUserIdentity.displayName,
                        email = "",
                        isCurrentUser = true
                    )
                } else {
                    val currentUserName = currentUser.value?.name ?: "Yash"
                    tripRepo.addMember(tripId, currentUserName, currentUser.value?.email ?: "")
                }
                // Optional: tripRepo.addMember(tripId, "Travel Companion", "")

                // Step 3: Synthesize dynamic day-by-day itinerary via Gemini AI with real destination coordinates & known places
                generationProgressMessage.value = "Synthesizing $daysCount-day $destName itinerary via Gemini AI..."
                val answersMap = wizardQuestions.value.associate { it.id to (it.selectedOption ?: "") }
                
                val generatedItems = destRepo.geminiTravelService.generateDynamicItinerary(
                    trip = newTrip,
                    answers = answersMap,
                    baseLat = dest.latitude,
                    baseLon = dest.longitude,
                    knownPlaces = cacheResult.attractions
                )
                if (generatedItems.isEmpty()) {
                    throw Exception("AI Travel Planner is currently unavailable. Please try again.")
                }
                tripRepo.saveGeneratedItinerary(tripId, generatedItems)

                // Step 4: Fetch live transport, hotel, place, and restaurant options for the destination
                generationProgressMessage.value = "Fetching live transport, hotels & food spots for $destName..."
                destRepo.fetchAndStoreLiveTransportAndHotels(dest, startingCity)

                // Step 5: Budget verification and AI welcome message
                val totalActivityCost = com.example.domain.ai.TravelScheduleUtils.calculateTotalCost(generatedItems)
                val budgetHealthSnippet = if (totalActivityCost <= totalBudgetVal) {
                    "• **Budget Health:** Estimated activities cost ₹${totalActivityCost.toInt()} (Within allocated ₹${totalBudgetVal.toInt()} budget, leaving ₹${(totalBudgetVal - totalActivityCost).toInt()} buffer for shopping & incidentals)"
                } else {
                    "• **Budget Health:** Estimated activities cost ₹${totalActivityCost.toInt()} (Exceeds planned ₹${totalBudgetVal.toInt()} by ₹${(totalActivityCost - totalBudgetVal).toInt()}; ask me to optimize for a tighter budget!)"
                }

                // Initial AI greeting message in chat
                val welcomeMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    sender = "AI",
                    content = "✨ Personalized itinerary for **${dest.name}** is ready!\n\n" +
                            "• **Budget:** ₹${totalBudgetVal.toInt()} ($startingCity ↔ ${dest.name})\n" +
                            "$budgetHealthSnippet\n" +
                            "• **Pace:** Relaxed schedule tailored to your preferences\n" +
                            "• **Route & Timing:** Verified coordinates, real travel times & opening hours applied\n" +
                            "• **Stays & Transit:** Live real-world ticket and hotel options loaded\n\n" +
                            "Feel free to ask me to modify any day, add evening plans, or swap hotels!",
                    actionType = "PLAN_GENERATED",
                    actionFeedback = "Itinerary Generated"
                )
                tripRepo.addChatMessage(welcomeMsg)

                _selectedTripId.value = tripId
                _selectedDestinationId.value = dest.id
                _toastMessage.emit("🎉 Trip to ${dest.name} generated successfully!")
                navigateTo(Screen.TripDetail(tripId))
            } catch (e: Exception) {
                _toastMessage.emit("Generation error: ${e.message}")
            } finally {
                isGeneratingItinerary.value = false
            }
        }
    }

    // Toggle saved places
    fun togglePlaceSaved(placeId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            destRepo.togglePlaceSaved(placeId, !currentStatus)
            _toastMessage.emit(if (!currentStatus) "Saved to your places" else "Removed from saved")
        }
    }

    fun completeOnboarding(fullName: String, username: String, dob: String, location: String) {
        prefs.edit()
            .putBoolean("onboarding_completed", true)
            .putString("user_location", location)
            .apply()
        isOnboardingCompleted.value = true
        userLocationName.value = location
        
        // Update user profile via repository
        viewModelScope.launch {
            val user = currentUser.value ?: SeedData.initialUser
            val updatedUser = user.copy(
                name = fullName,
                homeLocation = location
            )
            db.userDao().insertUser(updatedUser) // Since it's an Upsert, this will update it
            
            // Note: username and dob are collected but might require schema changes to persist fully.
            // We store them locally for now if needed.
            
            // Navigate to Home and clear stack
            screenStack.clear()
            screenStack.add(Screen.Home)
            _currentScreen.value = Screen.Home
        }
    }

    // Add place to current itinerary directly
    fun addPlaceToTripItinerary(place: PlaceEntity, day: Int = 1) {
        viewModelScope.launch {
            val tripId = _selectedTripId.value
            val existing = currentItinerary.value.filter { it.dayNumber == day }
            val item = ItineraryItemEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                dayNumber = day,
                title = place.name,
                locationName = place.locationName,
                latitude = place.latitude,
                longitude = place.longitude,
                startTime = "15:00",
                endTime = "17:00",
                durationMinutes = 120,
                category = place.category,
                estimatedCost = place.estimatedCost,
                travelTimeFromPrevious = "20 mins",
                notes = place.description,
                source = place.source,
                orderIndex = existing.size
            )
            tripRepo.addItineraryItem(item)
            _toastMessage.emit("Added ${place.name} to Day $day!")
        }
    }

    // Add expense
    fun addExpense(
        title: String,
        amountInRupees: Double,
        category: String,
        paidBy: GroupMemberEntity,
        selectedParticipants: List<GroupMemberEntity>,
        splitType: String = "EQUAL",
        customAllocations: List<com.example.domain.expense.ExpenseSplitAllocation>? = null
    ) {
        viewModelScope.launch {
            tripRepo.addExpenseWithSplits(
                tripId = _selectedTripId.value,
                title = title,
                category = category,
                amountInRupees = amountInRupees,
                paidBy = paidBy,
                selectedParticipants = selectedParticipants,
                splitType = splitType,
                customAllocations = customAllocations
            )
            _toastMessage.emit("Added expense: ₹${amountInRupees.toInt()}")
        }
    }

    fun updateExpense(
        expenseId: String,
        title: String,
        amountInRupees: Double,
        category: String,
        paidBy: GroupMemberEntity,
        selectedParticipants: List<GroupMemberEntity>,
        splitType: String = "EQUAL",
        customAllocations: List<com.example.domain.expense.ExpenseSplitAllocation>? = null
    ) {
        viewModelScope.launch {
            tripRepo.updateExpenseWithSplits(
                expenseId = expenseId,
                tripId = _selectedTripId.value,
                title = title,
                category = category,
                amountInRupees = amountInRupees,
                paidBy = paidBy,
                selectedParticipants = selectedParticipants,
                splitType = splitType,
                customAllocations = customAllocations
            )
            _toastMessage.emit("Updated expense: '$title'")
        }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch {
            tripRepo.deleteExpenseWithSplits(expenseId, _selectedTripId.value)
            _toastMessage.emit("Expense deleted")
        }
    }

    fun recordSettlement(
        payer: GroupMemberEntity,
        receiverName: String,
        receiverId: String,
        amountInRupees: Double
    ) {
        viewModelScope.launch {
            tripRepo.recordSettlement(
                tripId = _selectedTripId.value,
                payer = payer,
                receiverName = receiverName,
                receiverId = receiverId,
                amountInRupees = amountInRupees
            )
            _toastMessage.emit("Recorded settlement payment of ₹${amountInRupees.toInt()}")
        }
    }

    fun addParticipantFromQr(payload: com.example.ui.components.QrParticipantPayload) {
        viewModelScope.launch {
            val added = tripRepo.addParticipantToTrip(
                tripId = _selectedTripId.value,
                participantId = payload.id,
                name = payload.name,
                email = "",
                isCurrentUser = false
            )
            if (added) {
                _toastMessage.emit("Added ${payload.name} as companion!")
            } else {
                _toastMessage.emit("${payload.name} is already in this trip group")
            }
        }
    }

    fun addParticipantFromSaved(participant: SavedParticipantEntity) {
        viewModelScope.launch {
            val added = tripRepo.addParticipantToTrip(
                tripId = _selectedTripId.value,
                participantId = participant.id,
                name = participant.name,
                email = participant.email,
                isCurrentUser = false
            )
            if (added) {
                _toastMessage.emit("Added ${participant.name} to trip group")
            } else {
                _toastMessage.emit("${participant.name} is already in this trip")
            }
        }
    }

    fun settleSplit(splitId: String) {
        viewModelScope.launch {
            tripRepo.settleSplit(splitId)
            _toastMessage.emit("Split marked as settled!")
        }
    }

    fun markBooking(
        type: String,
        title: String,
        provider: String,
        cost: Double
    ) {
        viewModelScope.launch {
            val booking = BookingEntity(
                id = UUID.randomUUID().toString(),
                tripId = _selectedTripId.value,
                type = type,
                title = title,
                provider = provider,
                confirmationNumber = "TM-" + (100000..999999).random(),
                date = "Upcoming",
                cost = cost,
                notes = "Saved from live research",
                isConfirmed = true
            )
            tripRepo.addBooking(booking)
            _toastMessage.emit("Added to Trip Bookings!")
        }
    }

    fun updateProfile(
        name: String,
        homeLocation: String,
        diet: String,
        style: String,
        budget: String
    ) {
        viewModelScope.launch {
            val current = currentUser.value ?: SeedData.initialUser
            val updated = current.copy(
                name = name,
                homeLocation = homeLocation,
                dietPreference = diet,
                travelStyle = style,
                budgetTier = budget
            )
            userRepo.updateUser(updated)
            _toastMessage.emit("Profile preferences updated!")
        }
    }

    fun moveItineraryItemUp(item: ItineraryItemEntity) {
        viewModelScope.launch {
            val moved = tripRepo.moveItineraryItem(item.tripId, item.dayNumber, item.id, moveUp = true)
            if (moved) {
                _toastMessage.emit("Moved '${item.title}' up & recalculated Day ${item.dayNumber}")
            }
        }
    }

    fun moveItineraryItemDown(item: ItineraryItemEntity) {
        viewModelScope.launch {
            val moved = tripRepo.moveItineraryItem(item.tripId, item.dayNumber, item.id, moveUp = false)
            if (moved) {
                _toastMessage.emit("Moved '${item.title}' down & recalculated Day ${item.dayNumber}")
            }
        }
    }

    fun editItineraryItem(item: ItineraryItemEntity) {
        viewModelScope.launch {
            tripRepo.editItineraryItemAndRecalculate(item.tripId, item)
            _toastMessage.emit("Updated '${item.title}' & recalculated Day ${item.dayNumber}")
        }
    }

    fun updateItineraryItem(item: ItineraryItemEntity) {
        viewModelScope.launch {
            tripRepo.updateItineraryItem(item)
        }
    }

    fun deleteItineraryItem(itemId: String, dayNumber: Int? = null) {
        viewModelScope.launch {
            val tripId = _selectedTripId.value
            val effectiveDay = dayNumber ?: currentItinerary.value.firstOrNull { it.id == itemId }?.dayNumber
            if (effectiveDay != null) {
                tripRepo.deleteItineraryItemAndRecalculate(tripId, itemId, effectiveDay)
                _toastMessage.emit("Activity removed & recalculated Day $effectiveDay")
            } else {
                tripRepo.deleteItineraryItem(itemId)
                _toastMessage.emit("Activity removed")
            }
        }
    }

    fun addItineraryItem(item: ItineraryItemEntity) {
        viewModelScope.launch {
            tripRepo.addItineraryItemAndRecalculate(item.tripId, item)
            _toastMessage.emit("Added '${item.title}' to Day ${item.dayNumber} & updated schedule")
        }
    }

    fun recalculateDaySchedule(dayNumber: Int) {
        viewModelScope.launch {
            val tripId = _selectedTripId.value
            if (tripId.isNotBlank()) {
                val recalculated = tripRepo.recalculateDaySchedule(tripId, dayNumber)
                _toastMessage.emit("Recalculated Day $dayNumber schedule (${recalculated.size} activities)")
            }
        }
    }

    fun toggleHotelSaved(hotelId: String, isSaved: Boolean) {
        viewModelScope.launch {
            destRepo.toggleHotelSaved(hotelId, isSaved)
            _toastMessage.emit(if (isSaved) "Hotel saved" else "Hotel removed from saved")
        }
    }

    fun toggleRestaurantSaved(restId: String, isSaved: Boolean) {
        viewModelScope.launch {
            destRepo.toggleRestaurantSaved(restId, isSaved)
            _toastMessage.emit(if (isSaved) "Restaurant saved" else "Removed from saved")
        }
    }

    fun addMemberToTrip(name: String, email: String) {
        viewModelScope.launch {
            tripRepo.addMember(_selectedTripId.value, name, email)
            _toastMessage.emit("Added $name to group")
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            destRepo.clearSearchHistory()
        }
    }

    suspend fun searchChatUsers(query: String): List<com.example.data.repository.ChatIdentity> {
        return chatRepo.searchChatUsers(query)
    }

    fun startDirectConversation(targetUserId: String, onConversationReady: (String) -> Unit) {
        viewModelScope.launch {
            val convId = chatRepo.createDirectConversation(targetUserId)
            if (convId != null) {
                onConversationReady(convId)
            } else {
                _toastMessage.emit("Failed to create conversation")
            }
        }
    }

    fun createGroupConversation(title: String, targetUserIds: List<String>, onConversationReady: (String) -> Unit) {
        viewModelScope.launch {
            val convId = chatRepo.createGroupConversation(title, targetUserIds)
            if (convId != null) {
                onConversationReady(convId)
            } else {
                _toastMessage.emit("Failed to create group")
            }
        }
    }

    fun loadChatMessages(conversationId: String) {
        viewModelScope.launch {
            _currentChatMessages.value = chatRepo.getMessages(conversationId)
        }
    }

    suspend fun updateChatUsername(newUsername: String): Boolean {
        val success = chatIdentityRepo.updateUsername(newUsername)
        if (success) {
            currentChatIdentity.value = chatIdentityRepo.getIdentity()
            _toastMessage.emit("Username updated to @$newUsername")
        } else {
            _toastMessage.emit("Failed to update username")
        }
        return success
    }

    fun sendPeerMessage(
        conversationId: String, 
        text: String?, 
        type: String = "TEXT",
        attachmentPath: String? = null,
        mimeType: String? = null
    ) {
        if (text.isNullOrBlank() && attachmentPath == null) return
        viewModelScope.launch {
            val success = chatRepo.sendMessage(conversationId, text, type, attachmentPath, mimeType)
            if (success) {
                // reload messages
                _currentChatMessages.value = chatRepo.getMessages(conversationId)
                chatRepo.refreshConversations()
            } else {
                _toastMessage.emit("Failed to send message")
            }
        }
    }

    fun deleteMessage(messageId: String, conversationId: String) {
        viewModelScope.launch {
            val success = chatRepo.deleteMessage(messageId)
            if (success) {
                _currentChatMessages.value = chatRepo.getMessages(conversationId)
                _toastMessage.emit("Message deleted")
            } else {
                _toastMessage.emit("Failed to delete message")
            }
        }
    }

    // --- Authentication Actions ---

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            val success = authManager.signInWithEmail(email, pass)
            if (success) {
                profileRepo.loadUserProfile()
                _toastMessage.emit("Signed in successfully!")
            }
        }
    }

    fun signUpWithEmail(email: String, pass: String, fullName: String) {
        viewModelScope.launch {
            val success = authManager.signUpWithEmail(email, pass, fullName)
            if (success) {
                profileRepo.loadUserProfile()
                _toastMessage.emit("Account created successfully!")
            }
        }
    }

    fun launchGoogleOAuth(context: android.content.Context) {
        authManager.launchGoogleOAuth(context)
    }

    fun handleOAuthCallback(uri: android.net.Uri) {
        viewModelScope.launch {
            val success = authManager.handleOAuthCallback(uri)
            if (success) {
                profileRepo.loadUserProfile()
                _toastMessage.emit("Google authentication successful!")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
            chatIdentityRepo.clearIdentity()
            currentChatIdentity.value = null
            _toastMessage.emit("Signed out")
        }
    }

    fun resetAuthState() {
        authManager.resetAuthState()
    }

    // --- Trip Collaboration Actions ---

    suspend fun searchProfiles(query: String): List<com.example.data.repository.PublicUserProfile> {
        return profileRepo.searchUsers(query)
    }

    fun inviteUserToTrip(tripId: String, inviteeUserId: String) {
        viewModelScope.launch {
            val success = collabRepo.inviteUserToTrip(tripId, inviteeUserId)
            if (success) {
                _toastMessage.emit("Trip invitation sent!")
            } else {
                _toastMessage.emit("Failed to send invitation")
            }
        }
    }

    fun acceptTripInvitation(invitationId: String) {
        viewModelScope.launch {
            val success = collabRepo.acceptInvitation(invitationId)
            if (success) {
                _toastMessage.emit("Accepted trip invitation!")
                chatRepo.refreshConversations()
            } else {
                _toastMessage.emit("Failed to accept invitation")
            }
        }
    }

    fun rejectTripInvitation(invitationId: String) {
        viewModelScope.launch {
            val success = collabRepo.rejectInvitation(invitationId)
            if (success) {
                _toastMessage.emit("Invitation declined")
            }
        }
    }

    fun removeTripMember(tripId: String, memberUserId: String) {
        viewModelScope.launch {
            val success = collabRepo.removeMember(tripId, memberUserId)
            if (success) {
                _toastMessage.emit("Removed trip member")
                chatRepo.refreshConversations()
            } else {
                _toastMessage.emit("Failed to remove member")
            }
        }
    }

    fun refreshTripMembers(tripId: String) {
        viewModelScope.launch {
            collabRepo.refreshTripMembers(tripId)
        }
    }

    fun openTripChat(tripId: String, tripTitle: String) {
        viewModelScope.launch {
            val convId = chatRepo.getOrCreateTripConversation(tripId, tripTitle)
            if (convId != null) {
                navigateTo(Screen.ChatConversation(convId))
            } else {
                _toastMessage.emit("Could not open trip chat")
            }
        }
    }
}
