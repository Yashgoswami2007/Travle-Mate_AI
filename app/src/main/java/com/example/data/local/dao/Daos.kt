package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users LIMIT 1")
    fun getActiveUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getActiveUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations")
    fun getAllDestinations(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE id = :id")
    suspend fun getDestinationById(id: String): DestinationEntity?

    @Query("SELECT * FROM destinations WHERE normalizedName = :normalized LIMIT 1")
    suspend fun getDestinationByNormalizedName(normalized: String): DestinationEntity?

    @Query("UPDATE destinations SET searchCount = searchCount + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun incrementSearchCount(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE destinations SET lastVerifiedAt = :timestamp WHERE id = :id")
    suspend fun updateLastVerifiedAt(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM destinations WHERE name LIKE '%' || :query || '%' OR stateRegion LIKE '%' || :query || '%' OR country LIKE '%' || :query || '%' OR normalizedName LIKE '%' || :query || '%'")
    suspend fun searchDestinations(query: String): List<DestinationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDestinations(destinations: List<DestinationEntity>)

    @Query("UPDATE destinations SET isSaved = :isSaved WHERE id = :id")
    suspend fun updateSavedStatus(id: String, isSaved: Boolean)

    @Query("SELECT * FROM places WHERE destinationId = :destinationId")
    fun getPlacesForDestination(destinationId: String): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE destinationId = :destinationId AND normalizedName = :normalizedName LIMIT 1")
    suspend fun getPlaceByNormalizedName(destinationId: String, normalizedName: String): PlaceEntity?

    @Query("SELECT * FROM places WHERE destinationId = :destinationId")
    suspend fun getPlacesForDestinationSync(destinationId: String): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE isSaved = 1")
    fun getSavedPlaces(): Flow<List<PlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<PlaceEntity>)

    @Query("UPDATE places SET isSaved = :isSaved WHERE id = :id")
    suspend fun updatePlaceSaved(id: String, isSaved: Boolean)

    @Query("SELECT * FROM restaurants WHERE destinationId = :destinationId")
    fun getRestaurantsForDestination(destinationId: String): Flow<List<RestaurantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestaurants(restaurants: List<RestaurantEntity>)

    @Query("UPDATE restaurants SET isSaved = :isSaved WHERE id = :id")
    suspend fun updateRestaurantSaved(id: String, isSaved: Boolean)
}

@Dao
interface DestinationImageDao {
    @Query("SELECT * FROM destination_images WHERE destinationId = :destinationId ORDER BY isCover DESC, createdAt ASC")
    fun getImagesForDestination(destinationId: String): Flow<List<DestinationImageEntity>>

    @Query("SELECT * FROM destination_images WHERE destinationId = :destinationId ORDER BY isCover DESC")
    suspend fun getImagesForDestinationSync(destinationId: String): List<DestinationImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<DestinationImageEntity>)
}

@Dao
interface ImageCacheDao {
    @Query("SELECT * FROM public_image_cache WHERE queryKey = :key LIMIT 1")
    suspend fun getImageByKey(key: String): ImageCacheEntity?

    @Query("SELECT * FROM public_image_cache WHERE destinationId = :destinationId")
    suspend fun getImagesForDestination(destinationId: String): List<ImageCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(item: ImageCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(items: List<ImageCacheEntity>)
}

@Dao
interface UserSavedDestinationDao {
    @Query("SELECT * FROM user_saved_destinations WHERE userId = :userId ORDER BY savedAt DESC")
    fun getSavedDestinations(userId: String): Flow<List<UserSavedDestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDestination(item: UserSavedDestinationEntity)

    @Query("DELETE FROM user_saved_destinations WHERE userId = :userId AND destinationId = :destinationId")
    suspend fun removeSavedDestination(userId: String, destinationId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM user_saved_destinations WHERE userId = :userId AND destinationId = :destinationId)")
    suspend fun isDestinationSaved(userId: String, destinationId: String): Boolean
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :tripId")
    fun getTripFlow(tripId: String): Flow<TripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    // Itinerary
    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId ORDER BY dayNumber ASC, orderIndex ASC")
    fun getItineraryItems(tripId: String): Flow<List<ItineraryItemEntity>>

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId AND dayNumber = :dayNumber ORDER BY orderIndex ASC")
    suspend fun getItemsForDay(tripId: String, dayNumber: Int): List<ItineraryItemEntity>

    @Query("SELECT * FROM itinerary_items WHERE tripId = :tripId ORDER BY dayNumber ASC, orderIndex ASC")
    suspend fun getAllItemsForTripSync(tripId: String): List<ItineraryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItineraryItem(item: ItineraryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItineraryItems(items: List<ItineraryItemEntity>)

    @Update
    suspend fun updateItineraryItem(item: ItineraryItemEntity)

    @Query("DELETE FROM itinerary_items WHERE id = :itemId")
    suspend fun deleteItineraryItem(itemId: String)

    @Query("DELETE FROM itinerary_items WHERE tripId = :tripId")
    suspend fun clearItineraryForTrip(tripId: String)
}

@Dao
interface TravelSearchDao {
    @Query("SELECT * FROM transport_options WHERE destinationId = :destinationId OR toCity LIKE '%' || :query || '%'")
    fun getTransportOptions(destinationId: String, query: String): Flow<List<TransportOptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransportOptions(options: List<TransportOptionEntity>)

    @Query("SELECT * FROM hotel_options WHERE destinationId = :destinationId")
    fun getHotelOptions(destinationId: String): Flow<List<HotelOptionEntity>>

    @Query("SELECT * FROM hotel_options WHERE destinationId = :destinationId")
    suspend fun getHotelOptionsSync(destinationId: String): List<HotelOptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHotelOptions(hotels: List<HotelOptionEntity>)

    @Query("UPDATE hotel_options SET isSaved = :isSaved WHERE id = :id")
    suspend fun updateHotelSaved(id: String, isSaved: Boolean)

    // Bookings
    @Query("SELECT * FROM bookings WHERE tripId = :tripId ORDER BY date ASC")
    fun getBookingsForTrip(tripId: String): Flow<List<BookingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: BookingEntity)

    // Search History
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearch(id: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Dao
interface SavedParticipantDao {
    @Query("SELECT * FROM saved_participants ORDER BY addedAt DESC")
    fun getAllSavedParticipants(): Flow<List<SavedParticipantEntity>>

    @Query("SELECT * FROM saved_participants WHERE id = :id")
    suspend fun getSavedParticipantById(id: String): SavedParticipantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: SavedParticipantEntity)

    @Delete
    suspend fun deleteParticipant(participant: SavedParticipantEntity)
}

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements WHERE tripId = :tripId ORDER BY updatedAt DESC")
    fun getSettlementsForTrip(tripId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE tripId = :tripId")
    suspend fun getSettlementsForTripSync(tripId: String): List<SettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SettlementEntity)

    @Delete
    suspend fun deleteSettlement(settlement: SettlementEntity)

    @Query("DELETE FROM settlements WHERE tripId = :tripId")
    suspend fun clearSettlementsForTrip(tripId: String)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY updatedAt DESC")
    fun getExpensesForTrip(tripId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tripId = :tripId")
    suspend fun getExpensesForTripSync(tripId: String): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: String)

    @Query("SELECT * FROM expense_splits WHERE tripId = :tripId")
    fun getSplitsForTrip(tripId: String): Flow<List<ExpenseSplitEntity>>

    @Query("SELECT * FROM expense_splits WHERE tripId = :tripId")
    suspend fun getSplitsForTripSync(tripId: String): List<ExpenseSplitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplits(splits: List<ExpenseSplitEntity>)

    @Query("DELETE FROM expense_splits WHERE expenseId = :expenseId")
    suspend fun deleteSplitsForExpense(expenseId: String)

    @Query("UPDATE expense_splits SET isSettled = :isSettled WHERE id = :splitId")
    suspend fun updateSplitSettled(splitId: String, isSettled: Boolean)

    @Query("DELETE FROM expenses WHERE tripId = :tripId")
    suspend fun deleteExpensesForTrip(tripId: String)

    @Query("DELETE FROM expense_splits WHERE tripId = :tripId")
    suspend fun deleteSplitsForTrip(tripId: String)

    @Transaction
    suspend fun reconcileExpensesAndSplitsForTrip(
        tripId: String,
        expenses: List<ExpenseEntity>,
        splits: List<ExpenseSplitEntity>
    ) {
        deleteSplitsForTrip(tripId)
        deleteExpensesForTrip(tripId)
        if (expenses.isNotEmpty()) {
            insertExpenses(expenses)
        }
        if (splits.isNotEmpty()) {
            insertSplits(splits)
        }
    }

    // Group Members
    @Query("SELECT * FROM group_members WHERE tripId = :tripId")
    fun getMembersForTrip(tripId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE tripId = :tripId")
    suspend fun getMembersForTripSync(tripId: String): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Delete
    suspend fun deleteMember(member: GroupMemberEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getChatMessages(tripId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE tripId = :tripId")
    suspend fun clearChat(tripId: String)
}
