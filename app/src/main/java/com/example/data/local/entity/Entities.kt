package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// ==============================================================================
// LAYER 1: USER DATABASE ENTITIES (PRIVATE DATA LINKED VIA userId)
// ==============================================================================

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String, // userId
    val email: String,
    val name: String,
    val homeLocation: String = "Delhi, India",
    val currency: String = "₹",
    val dietPreference: String = "Vegetarian", // Vegetarian, Vegan, Non-vegetarian, Jain
    val travelStyle: String = "Relaxed", // Relaxed, Balanced, Packed
    val budgetTier: String = "Medium", // Budget, Moderate, Luxury
    val preferredAccommodation: String = "Boutique Hotel",
    val preferredTransport: String = "Train",
    val avatarInitials: String = "TM",
    val username: String = "",
    val dateOfBirth: String = ""
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val userId: String, // Linked to User
    val destinationId: String,
    val destinationName: String,
    val destinationRegion: String,
    val destinationCountry: String,
    val imageUrl: String,
    val startDate: String, // e.g. "Nov 12, 2026"
    val endDate: String,   // e.g. "Nov 15, 2026"
    val numberOfDays: Int = 4,
    val travellersCount: Int = 2,
    val travellersDescription: String = "Couple", // Solo, Couple, Family, Friends
    val startingLocation: String = "Delhi",
    val totalBudget: Double = 25000.0,
    val spentBudget: Double = 0.0,
    val pace: String = "Relaxed", // Relaxed, Balanced, Packed
    val status: String = "ACTIVE", // PLANNING, ACTIVE, COMPLETED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "itinerary_items")
data class ItineraryItemEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val dayNumber: Int, // 1, 2, 3, 4...
    val title: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val startTime: String, // "09:30"
    val endTime: String,   // "11:00"
    val durationMinutes: Int = 90,
    val category: String, // Attraction, Café, Restaurant, Fort, Museum, Transport, Rest
    val estimatedCost: Double = 0.0,
    val travelTimeFromPrevious: String = "15 mins",
    val notes: String = "",
    val source: String = "TripMate AI",
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0
)

@Entity(tableName = "saved_participants")
data class SavedParticipantEntity(
    @PrimaryKey val id: String, // Participant / Device ID
    val name: String,
    val username: String = "",
    val email: String = "",
    val avatarColorHex: String = "#1E3A5F",
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val payerMemberId: String,
    val payerName: String,
    val receiverMemberId: String,
    val receiverName: String,
    val amountInPaise: Long,
    val date: String = "Today",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val amount: Double get() = amountInPaise / 100.0
}

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val title: String,
    val category: String, // Transport, Accommodation, Food, Activities, Shopping, Other
    val amountInPaise: Long,
    val paidByMemberId: String,
    val paidByMemberName: String,
    val splitType: String = "EQUAL", // EQUAL, CUSTOM
    val participantIdsJson: String = "", // Comma-separated or JSON list of selected participant IDs
    val date: String = "Today",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val amount: Double get() = amountInPaise / 100.0
}

@Entity(tableName = "expense_splits")
data class ExpenseSplitEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val tripId: String,
    val memberId: String,
    val memberName: String,
    val shareAmountInPaise: Long,
    val isSettled: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val shareAmount: Double get() = shareAmountInPaise / 100.0
}

@Entity(tableName = "group_members")
data class GroupMemberEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val participantId: String = "", // Links to SavedParticipant / DeviceUser ID
    val name: String,
    val email: String = "",
    val avatarColorHex: String = "#1E3A5F",
    val isCurrentUser: Boolean = false
)

@Entity(tableName = "user_saved_destinations")
data class UserSavedDestinationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val destinationId: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val id: String,
    val query: String,
    val destinationName: String,
    val userId: String = "user_default",
    val destinationId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val type: String, // Transport, Hotel, Attraction Ticket, Restaurant
    val title: String,
    val provider: String,
    val confirmationNumber: String,
    val date: String,
    val cost: Double,
    val notes: String = "",
    val isConfirmed: Boolean = true
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val sender: String, // "USER" or "AI"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null, // e.g. "ADD_ACTIVITY", "MOVE_ACTIVITY", "UPDATE_BUDGET"
    val actionPayload: String? = null,
    val actionFeedback: String? = null
)

// ==============================================================================
// LAYER 2: PUBLIC DESTINATION DATABASE ENTITIES (SHARED CACHE & KNOWLEDGE BASE)
// ==============================================================================

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String = "", // e.g. "moradabad", "jaipur"
    val stateRegion: String,
    val country: String,
    val type: String, // Heritage City, Hill Station, Beach, Cultural
    val description: String,
    val weatherSummary: String = "Sunny, 28°C",
    val temperature: String = "28°C",
    val rainProbability: String = "10%",
    val imageUrl: String,
    val latitude: Double,
    val longitude: Double,
    val popularAreas: String = "Old City, C-Scheme, Amer, Malviya Nagar",
    val bestTimeToVisit: String = "October to March",
    val dataSources: String = "Gemini AI & Public Knowledge Base",
    val searchCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastVerifiedAt: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false
) {
    val coverImageUrl: String get() = imageUrl
}

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey val id: String,
    val destinationId: String,
    val name: String,
    val normalizedName: String = "",
    val category: String, // Historical, Architecture, Nature, Adventure, Photography, Cafés, Religious
    val description: String,
    val locationName: String,
    val locationAddress: String = "",
    val latitude: Double,
    val longitude: Double,
    val openingHours: String = "09:00 - 18:00",
    val visitDuration: String = "2 hours",
    val estimatedCost: Double = 200.0,
    val bestTime: String = "Morning (09:00 - 11:30)",
    val crowdLevel: String = "Moderate", // Low, Moderate, High
    val imageUrl: String,
    val rating: Float = 4.6f,
    val reviewCount: Int = 1240,
    val source: String = "Official Tourism Guide",
    val createdAt: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false
) {
    val entryTicketCost: Double get() = estimatedCost
    val dataSource: String get() = source
}

@Entity(tableName = "destination_images")
data class DestinationImageEntity(
    @PrimaryKey val id: String,
    val destinationId: String,
    val attractionId: String? = null,
    val imageUrl: String,
    val caption: String = "",
    val creditSource: String = "Wikimedia Commons / Verified",
    val isCover: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "public_image_cache")
data class ImageCacheEntity(
    @PrimaryKey val queryKey: String, // Normalized lookup key: "destination:gujarat", "hotel:hyatt regency ahmedabad"
    val imageUrl: String,
    val entityType: String = "DESTINATION", // DESTINATION, ATTRACTION, HOTEL, LANDMARK
    val entityName: String,
    val destinationId: String = "",
    val source: String = "Wikimedia Commons / Verified Web Search",
    val width: Int = 1200,
    val height: Int = 800,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "restaurants")
data class RestaurantEntity(
    @PrimaryKey val id: String,
    val destinationId: String,
    val name: String,
    val cuisine: String, // Rajasthani, North Indian, Café, Multi-cuisine
    val dietaryTypes: String, // Vegetarian, Vegan, Jain friendly, Non-vegetarian
    val priceRange: String, // ₹₹, ₹₹₹, ₹
    val averageCostForTwo: Double = 800.0,
    val rating: Float = 4.7f,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val openingHours: String = "11:00 - 23:00",
    val imageUrl: String,
    val externalLink: String = "https://maps.google.com",
    val isSaved: Boolean = false
)

@Entity(tableName = "transport_options")
data class TransportOptionEntity(
    @PrimaryKey val id: String,
    val destinationId: String,
    val fromCity: String,
    val toCity: String,
    val mode: String, // Train, Flight, Bus, Taxi, Rental
    val provider: String, // IRCTC, IndiGo, Zingbus, MakeMyTrip
    val price: Double,
    val departureTime: String,
    val arrivalTime: String,
    val duration: String,
    val stops: String = "Direct",
    val bookingUrl: String,
    val lastChecked: String = "Just now",
    val isDemo: Boolean = false
)

@Entity(tableName = "hotel_options")
data class HotelOptionEntity(
    @PrimaryKey val id: String,
    val destinationId: String,
    val name: String,
    val photoUrl: String,
    val locationName: String,
    val rating: Float = 4.5f,
    val reviewCount: Int = 540,
    val nightlyPrice: Double = 2500.0,
    val totalPrice: Double = 7500.0,
    val amenities: String = "Free WiFi, Breakfast, Pool, AC, Rooftop",
    val distanceToCenter: String = "2.1 km from center",
    val type: String = "Boutique Hotel", // Hotel, Hostel, Homestay, Boutique, Resort
    val provider: String = "Booking.com / Agoda",
    val bookingUrl: String,
    val cancellationPolicy: String = "Free cancellation until 24h before",
    val isDemo: Boolean = false,
    val isSaved: Boolean = false
)
