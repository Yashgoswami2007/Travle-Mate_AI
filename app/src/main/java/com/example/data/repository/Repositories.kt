package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder
import java.util.UUID

class TripRepository(private val db: AppDatabase) {

    fun getAllTrips(): Flow<List<TripEntity>> = db.tripDao().getAllTrips()

    fun getTripFlow(tripId: String): Flow<TripEntity?> = db.tripDao().getTripFlow(tripId)

    suspend fun getTripById(tripId: String): TripEntity? = db.tripDao().getTripById(tripId)

    suspend fun createTrip(trip: TripEntity) = db.tripDao().insertTrip(trip)

    suspend fun updateTrip(trip: TripEntity) = db.tripDao().updateTrip(trip)

    suspend fun deleteTrip(trip: TripEntity) = db.tripDao().deleteTrip(trip)

    // Itinerary items
    fun getItineraryItems(tripId: String): Flow<List<ItineraryItemEntity>> =
        db.tripDao().getItineraryItems(tripId)

    suspend fun addItineraryItem(item: ItineraryItemEntity) =
        db.tripDao().insertItineraryItem(item)

    suspend fun updateItineraryItem(item: ItineraryItemEntity) =
        db.tripDao().updateItineraryItem(item)

    suspend fun deleteItineraryItem(itemId: String) =
        db.tripDao().deleteItineraryItem(itemId)

    suspend fun updateTripSpentBudget(tripId: String) {
        val allItems = db.tripDao().getAllItemsForTripSync(tripId)
        val totalActivityCosts = allItems.sumOf { it.estimatedCost }
        val expenses = db.expenseDao().getExpensesForTripSync(tripId)
        val totalExpenses = expenses.sumOf { it.amount }
        val trip = db.tripDao().getTripById(tripId)
        if (trip != null) {
            db.tripDao().updateTrip(trip.copy(spentBudget = totalActivityCosts + totalExpenses))
        }
    }

    suspend fun recalculateDaySchedule(tripId: String, dayNumber: Int): List<ItineraryItemEntity> {
        val items = db.tripDao().getItemsForDay(tripId, dayNumber)
        if (items.isEmpty()) return emptyList()

        val sorted = items.sortedBy { it.orderIndex }
        val recalculated = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(
            items = sorted,
            anchorFirstItemStart = true
        )
        db.tripDao().insertItineraryItems(recalculated)
        updateTripSpentBudget(tripId)
        return recalculated
    }

    suspend fun moveItineraryItem(tripId: String, dayNumber: Int, itemId: String, moveUp: Boolean): Boolean {
        val items = db.tripDao().getItemsForDay(tripId, dayNumber).sortedBy { it.orderIndex }.toMutableList()
        val index = items.indexOfFirst { it.id == itemId }
        if (index == -1) return false

        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= items.size) return false

        val item = items.removeAt(index)
        items.add(targetIndex, item)

        val reindexed = items.mapIndexed { idx, itm -> itm.copy(orderIndex = idx) }
        val recalculated = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(
            items = reindexed,
            anchorFirstItemStart = true
        )
        db.tripDao().insertItineraryItems(recalculated)
        updateTripSpentBudget(tripId)
        return true
    }

    suspend fun editItineraryItemAndRecalculate(tripId: String, updatedItem: ItineraryItemEntity) {
        val items = db.tripDao().getItemsForDay(tripId, updatedItem.dayNumber).sortedBy { it.orderIndex }.toMutableList()
        val idx = items.indexOfFirst { it.id == updatedItem.id }
        if (idx != -1) {
            items[idx] = updatedItem
        } else {
            items.add(updatedItem)
        }

        val reindexed = items.mapIndexed { index, itm -> itm.copy(orderIndex = index) }
        val recalculated = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(
            items = reindexed,
            anchorFirstItemStart = true
        )
        db.tripDao().insertItineraryItems(recalculated)
        updateTripSpentBudget(tripId)
    }

    suspend fun addItineraryItemAndRecalculate(tripId: String, newItem: ItineraryItemEntity) {
        val items = db.tripDao().getItemsForDay(tripId, newItem.dayNumber).sortedBy { it.orderIndex }.toMutableList()
        val itemWithOrder = newItem.copy(orderIndex = items.size)
        items.add(itemWithOrder)

        val recalculated = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(
            items = items,
            anchorFirstItemStart = true
        )
        db.tripDao().insertItineraryItems(recalculated)
        updateTripSpentBudget(tripId)
    }

    suspend fun deleteItineraryItemAndRecalculate(tripId: String, itemId: String, dayNumber: Int) {
        db.tripDao().deleteItineraryItem(itemId)
        val remaining = db.tripDao().getItemsForDay(tripId, dayNumber).sortedBy { it.orderIndex }
        val reindexed = remaining.mapIndexed { index, itm -> itm.copy(orderIndex = index) }
        val recalculated = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(
            items = reindexed,
            anchorFirstItemStart = true
        )
        db.tripDao().insertItineraryItems(recalculated)
        updateTripSpentBudget(tripId)
    }

    suspend fun saveGeneratedItinerary(tripId: String, items: List<ItineraryItemEntity>) {
        db.tripDao().clearItineraryForTrip(tripId)
        db.tripDao().insertItineraryItems(items)
        updateTripSpentBudget(tripId)
    }

    // Group & Expenses
    fun getExpenses(tripId: String): Flow<List<ExpenseEntity>> =
        db.expenseDao().getExpensesForTrip(tripId)

    fun getMembers(tripId: String): Flow<List<GroupMemberEntity>> =
        db.expenseDao().getMembersForTrip(tripId)

    fun getSplits(tripId: String): Flow<List<ExpenseSplitEntity>> =
        db.expenseDao().getSplitsForTrip(tripId)

    suspend fun addExpenseWithSplits(
        tripId: String,
        title: String,
        category: String,
        amount: Double,
        paidBy: GroupMemberEntity,
        members: List<GroupMemberEntity>,
        splitType: String
    ) {
        val expenseId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expenseId,
            tripId = tripId,
            title = title,
            category = category,
            amount = amount,
            paidByMemberId = paidBy.id,
            paidByMemberName = paidBy.name,
            splitType = splitType,
            date = "Today",
            notes = "Recorded in TripMate AI"
        )
        db.expenseDao().insertExpense(expense)

        val splitAmount = if (members.isNotEmpty()) amount / members.size else amount
        val splits = members.map { mem ->
            ExpenseSplitEntity(
                id = UUID.randomUUID().toString(),
                expenseId = expenseId,
                tripId = tripId,
                memberId = mem.id,
                memberName = mem.name,
                shareAmount = splitAmount,
                isSettled = mem.id == paidBy.id // Paid person is settled for their own share
            )
        }
        db.expenseDao().insertSplits(splits)

        // Update trip spent budget
        val trip = db.tripDao().getTripById(tripId)
        if (trip != null) {
            db.tripDao().updateTrip(trip.copy(spentBudget = trip.spentBudget + amount))
        }
    }

    suspend fun settleSplit(splitId: String) {
        db.expenseDao().updateSplitSettled(splitId, true)
    }

    suspend fun addMember(tripId: String, name: String, email: String) {
        val colors = listOf("#1E3A5F", "#E06D53", "#0284C7", "#16A34A", "#9333EA")
        val color = colors[name.length % colors.size]
        val member = GroupMemberEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            name = name,
            email = email,
            avatarColorHex = color,
            isCurrentUser = false
        )
        db.expenseDao().insertMember(member)
    }

    // Chat
    fun getChatMessages(tripId: String): Flow<List<ChatMessageEntity>> =
        db.chatDao().getChatMessages(tripId)

    suspend fun addChatMessage(message: ChatMessageEntity) =
        db.chatDao().insertMessage(message)

    // Bookings
    fun getBookings(tripId: String): Flow<List<BookingEntity>> =
        db.travelSearchDao().getBookingsForTrip(tripId)

    suspend fun addBooking(booking: BookingEntity) =
        db.travelSearchDao().insertBooking(booking)
}

class DestinationRepository(
    private val db: AppDatabase,
    private val geocodingService: com.example.data.remote.GeocodingService,
    private val weatherService: com.example.data.remote.WeatherService = com.example.data.remote.WeatherService(),
    val placeImageService: com.example.data.remote.PlaceImageService = com.example.data.remote.PlaceImageService(),
    val geminiTravelService: com.example.data.remote.GeminiTravelService = com.example.data.remote.GeminiTravelService(placeImageService),
    private val liveTravelSearchService: com.example.data.remote.LiveTravelSearchService = com.example.data.remote.LiveTravelSearchService(geminiTravelService),
    private val cacheService: com.example.data.remote.SupabaseDestinationCacheService = com.example.data.remote.SupabaseDestinationCacheService(
        db, geminiTravelService, placeImageService, geocodingService, weatherService
    )
) {

    fun getAllDestinations(): Flow<List<DestinationEntity>> =
        db.destinationDao().getAllDestinations()

    suspend fun getDestinationById(id: String): DestinationEntity? =
        db.destinationDao().getDestinationById(id)

    suspend fun searchDestinations(query: String): List<DestinationEntity> =
        db.destinationDao().searchDestinations(query)

    suspend fun searchLiveLocations(query: String): List<com.example.data.remote.GeocodedLocation> {
        return geocodingService.searchLocations(query)
    }

    /**
     * Resolves a destination using the two-layer Destination Cache:
     * Check Public DB -> If exists, return immediately (no AI call)
     * If not exists -> Acquire concurrency lock, research once via AI/APIs, save to Public DB, return.
     */
    suspend fun resolveAndSaveDestination(
        loc: com.example.data.remote.GeocodedLocation,
        fromCity: String = "Delhi"
    ): DestinationEntity {
        val result = cacheService.getOrResearchDestination(loc.name, fromCity)
        fetchAndStoreLiveTransportAndHotels(result.destination, fromCity)
        return result.destination
    }

    suspend fun getOrResearchDestination(
        query: String,
        fromCity: String = "Delhi"
    ): com.example.data.remote.DestinationCacheResult {
        val result = cacheService.getOrResearchDestination(query, fromCity)
        fetchAndStoreLiveTransportAndHotels(result.destination, fromCity)
        return result
    }

    fun getDestinationImages(destinationId: String): Flow<List<DestinationImageEntity>> =
        db.destinationImageDao().getImagesForDestination(destinationId)

    suspend fun refreshLiveDestinationData(
        destinationId: String,
        fromCity: String = "Delhi"
    ) {
        val dest = db.destinationDao().getDestinationById(destinationId) ?: return

        // 1. Fetch live transport tickets
        val liveTransport = geminiTravelService.fetchLiveTickets(fromCity, dest.name, dest.id)
        db.travelSearchDao().insertTransportOptions(liveTransport)

        // 2. Fetch live hotels with images
        val liveHotels = geminiTravelService.fetchLiveHotels(dest.name, dest.id)
        db.travelSearchDao().insertHotelOptions(liveHotels)

        // 3. Fetch live key attractions with real images
        val liveAttractions = geminiTravelService.fetchKeyAttractions(
            destinationCity = dest.name,
            destinationId = dest.id,
            baseLat = dest.latitude,
            baseLon = dest.longitude,
            placeCacheLookup = { normalizedName ->
                db.destinationDao().getPlaceByNormalizedName(dest.id, normalizedName)
            }
        )
        db.destinationDao().insertPlaces(liveAttractions)

        // 4. Fetch live restaurants with real images
        val liveRestaurants = geminiTravelService.fetchTopRestaurants(
            destinationCity = dest.name,
            destinationId = dest.id,
            baseLat = dest.latitude,
            baseLon = dest.longitude
        )
        db.destinationDao().insertRestaurants(liveRestaurants)

        // 5. Refresh destination image and weather
        val weather = weatherService.getLiveWeather(dest.latitude, dest.longitude)
        val freshImage = placeImageService.fetchPlaceImageUrl(dest.name, dest.country, "city")
        db.destinationDao().insertDestinations(listOf(
            dest.copy(
                weatherSummary = weather.summary,
                temperature = weather.temperature,
                rainProbability = weather.rainProbability,
                imageUrl = freshImage
            )
        ))
    }

    suspend fun fetchAndStoreLiveTransportAndHotels(
        dest: DestinationEntity,
        fromCity: String
    ) {
        // Fetch Live Transport (Flight, Train, Bus, Cab)
        val liveTransport = liveTravelSearchService.searchLiveTransport(
            fromCity = fromCity,
            toCity = dest.name,
            destinationId = dest.id,
            originLat = 28.6139,
            originLon = 77.2090,
            destLat = dest.latitude,
            destLon = dest.longitude
        )
        db.travelSearchDao().insertTransportOptions(liveTransport)

        // Fetch Live Hotels
        val liveHotels = liveTravelSearchService.searchLiveHotels(
            destinationName = dest.name,
            destinationId = dest.id,
            lat = dest.latitude,
            lon = dest.longitude
        )
        db.travelSearchDao().insertHotelOptions(liveHotels)
    }

    suspend fun toggleSaved(id: String, isSaved: Boolean) =
        db.destinationDao().updateSavedStatus(id, isSaved)

    fun getPlaces(destinationId: String): Flow<List<PlaceEntity>> =
        db.destinationDao().getPlacesForDestination(destinationId)

    fun getSavedPlaces(): Flow<List<PlaceEntity>> =
        db.destinationDao().getSavedPlaces()

    suspend fun togglePlaceSaved(id: String, isSaved: Boolean) =
        db.destinationDao().updatePlaceSaved(id, isSaved)

    fun getRestaurants(destinationId: String): Flow<List<RestaurantEntity>> =
        db.destinationDao().getRestaurantsForDestination(destinationId)

    suspend fun toggleRestaurantSaved(id: String, isSaved: Boolean) =
        db.destinationDao().updateRestaurantSaved(id, isSaved)

    fun getTransportOptions(destinationId: String, query: String = ""): Flow<List<TransportOptionEntity>> =
        db.travelSearchDao().getTransportOptions(destinationId, query)

    fun getHotelOptions(destinationId: String): Flow<List<HotelOptionEntity>> =
        db.travelSearchDao().getHotelOptions(destinationId)

    suspend fun toggleHotelSaved(id: String, isSaved: Boolean) =
        db.travelSearchDao().updateHotelSaved(id, isSaved)

    fun getRecentSearches(): Flow<List<SearchHistoryEntity>> =
        db.travelSearchDao().getRecentSearches()

    suspend fun recordSearch(query: String, destinationName: String) {
        val search = SearchHistoryEntity(
            id = UUID.randomUUID().toString(),
            query = query,
            destinationName = destinationName
        )
        db.travelSearchDao().insertSearch(search)
    }

    suspend fun deleteSearch(id: String) =
        db.travelSearchDao().deleteSearch(id)

    suspend fun clearSearchHistory() =
        db.travelSearchDao().clearSearchHistory()
}

class UserRepository(private val db: AppDatabase) {
    fun getActiveUserFlow(): Flow<UserEntity?> = db.userDao().getActiveUserFlow()

    suspend fun getActiveUser(): UserEntity? = db.userDao().getActiveUser()

    suspend fun updateUser(user: UserEntity) = db.userDao().updateUser(user)

    suspend fun saveUser(user: UserEntity) = db.userDao().insertUser(user)

    fun getSavedDestinations(userId: String = "user_default"): Flow<List<UserSavedDestinationEntity>> =
        db.userSavedDestinationDao().getSavedDestinations(userId)

    suspend fun saveDestination(destinationId: String, userId: String = "user_default") {
        db.userSavedDestinationDao().saveDestination(
            UserSavedDestinationEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                destinationId = destinationId
            )
        )
        db.destinationDao().updateSavedStatus(destinationId, true)
    }

    suspend fun removeSavedDestination(destinationId: String, userId: String = "user_default") {
        db.userSavedDestinationDao().removeSavedDestination(userId, destinationId)
        db.destinationDao().updateSavedStatus(destinationId, false)
    }

    suspend fun isDestinationSaved(destinationId: String, userId: String = "user_default"): Boolean =
        db.userSavedDestinationDao().isDestinationSaved(userId, destinationId)
}
