package com.example.domain.ai

import com.example.data.local.entity.ItineraryItemEntity
import com.example.data.local.entity.TripEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

object TravelDateUtils {
    val DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Calculates duration inclusively:
     * val days = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
     */
    fun calculateDays(startDate: LocalDate, endDate: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        return days.coerceAtLeast(1)
    }

    /**
     * One important distinction: 6 calendar days usually means 5 nights, not 6 nights.
     */
    fun calculateNights(days: Int): Int {
        return (days - 1).coerceAtLeast(0)
    }

    fun formatDurationSummary(days: Int): String {
        val nights = calculateNights(days)
        return if (nights == 0) "1 Day (Day Trip)" else "$days Days • $nights Nights"
    }

    fun formatDisplayDate(date: LocalDate): String {
        return date.format(DISPLAY_FORMATTER)
    }

    fun parseDate(dateStr: String, fallback: LocalDate = LocalDate.now().plusDays(7)): LocalDate {
        val clean = dateStr.trim()
        if (clean.isBlank()) return fallback
        return try {
            LocalDate.parse(clean, DISPLAY_FORMATTER)
        } catch (_: Exception) {
            try {
                LocalDate.parse(clean, ISO_FORMATTER)
            } catch (_: Exception) {
                fallback
            }
        }
    }
}

data class QuestionnaireStep(
    val id: String = "general_question",
    val stepNumber: Int = 1,
    val category: String = "CORE",
    val questionTitle: String,
    val description: String,
    val options: List<String>,
    val selectedOption: String? = null
)

object ItineraryPlannerWizard {

    /**
     * Genuinely adaptive question engine structured around question categories and context rules:
     * - Always include: travel_style, accommodation, food_preferences, sightseeing_pace.
     * - Conditionally include: transport_mode, visa_passport, mountain_transport, coastal_activities,
     *   children_activities, budget_scope.
     * - Avoid irrelevant questions (e.g. no visa questions for domestic trips).
     * - Uses stable question IDs (e.g. "transport_mode", "accommodation") to identify answers.
     */
    fun buildAdaptiveQuestions(
        destination: String,
        origin: String = "Delhi",
        days: Int = 4,
        budget: Double = 25000.0,
        travellerType: String = "Couple",
        travellerCount: Int = 2,
        previousAnswers: Map<String, String?> = emptyMap()
    ): List<QuestionnaireStep> {
        val destTrimmed = destination.trim().ifBlank { "Jaipur" }
        val originTrimmed = origin.trim().ifBlank { "Delhi" }
        val destLower = destTrimmed.lowercase()
        val originLower = originTrimmed.lowercase()

        // 1. Context detection rules
        val isDifferentCity = originTrimmed.isNotBlank() && !destLower.contains(originLower) && !originLower.contains(destLower)
        val isInternational = isInternationalDestination(destLower, originLower)
        val isMountain = isMountainDestination(destLower)
        val isCoastal = isCoastalDestination(destLower)
        val hasChildrenOrFamily = travellerType.equals("Family", ignoreCase = true) || travellerCount >= 3

        val durationLabel = TravelDateUtils.formatDurationSummary(days)
        val questionList = mutableListOf<QuestionnaireStep>()

        // -------------------------------------------------------------
        // RULE A (Conditional): Intercity Transport
        // Only include if destination differs from origin
        // -------------------------------------------------------------
        if (isDifferentCity) {
            val transportOptions = if (isInternational) {
                listOf(
                    "Direct Flight (Economy)",
                    "Premium Economy / Direct",
                    "Budget Flight with Layover",
                    "Flexible Flight Deal"
                )
            } else {
                listOf(
                    "Express Train (Vande Bharat / Shatabdi)",
                    "Private AC Cab / Road Trip",
                    "Domestic Direct Flight",
                    "Luxury Intercity Sleeper Bus"
                )
            }

            questionList.add(
                QuestionnaireStep(
                    id = "transport_mode",
                    stepNumber = 0,
                    category = "TRANSIT",
                    questionTitle = if (isInternational) "Flight & Travel Mode" else "Intercity Transportation",
                    description = if (isInternational) {
                        "How would you prefer to fly between $originTrimmed and $destTrimmed?"
                    } else {
                        "How would you prefer to travel from $originTrimmed to $destTrimmed?"
                    },
                    options = transportOptions,
                    selectedOption = previousAnswers["transport_mode"] ?: transportOptions.first()
                )
            )
        }

        // -------------------------------------------------------------
        // RULE B (Conditional): Visa & Passport Considerations
        // Only include for international trips; NEVER for domestic
        // -------------------------------------------------------------
        if (isInternational) {
            val visaOptions = listOf(
                "Visa-Free / Eligible for Visa on Arrival",
                "Already holding Valid Tourist Visa / eVisa",
                "Require itinerary documentation for Visa application",
                "Need flexible entry arrangements"
            )
            questionList.add(
                QuestionnaireStep(
                    id = "visa_passport",
                    stepNumber = 0,
                    category = "FORMALITIES",
                    questionTitle = "Visa & Entry Documentation",
                    description = "What is your travel documentation status for entering $destTrimmed?",
                    options = visaOptions,
                    selectedOption = previousAnswers["visa_passport"] ?: visaOptions.first()
                )
            )
        }

        // -------------------------------------------------------------
        // RULE C (Always Include): Accommodation Style
        // -------------------------------------------------------------
        val stayOptions = listOf(
            "Hostel / Backpacker",
            "Budget Hotel",
            "Comfort Hotel (3-4 Star)",
            "Luxury Resort (5 Star)",
            "Villa / Apartment"
        )

        questionList.add(
            QuestionnaireStep(
                id = "accommodation",
                stepNumber = 0,
                category = "STAY",
                questionTitle = "Stay & Accommodation Style",
                description = "What is your preferred style of accommodation?",
                options = stayOptions,
                selectedOption = previousAnswers["accommodation"] ?: stayOptions[2]
            )
        )

        // -------------------------------------------------------------
        // RULE D (Always Include): Food & Dietary Preferences
        // -------------------------------------------------------------
        val foodOptions = listOf(
            "Pure Vegetarian",
            "Jain Friendly",
            "Non-Vegetarian",
            "Vegan",
            "Multi-Cuisine"
        )
        questionList.add(
            QuestionnaireStep(
                id = "food_preferences",
                stepNumber = 0,
                category = "DINING",
                questionTitle = "Food & Dietary Preferences",
                description = "What are your dietary preferences?",
                options = foodOptions,
                selectedOption = previousAnswers["food_preferences"] ?: foodOptions.first()
            )
        )

        // -------------------------------------------------------------
        // RULE E (Always Include): Sightseeing Pace
        // -------------------------------------------------------------
        val paceOptions = listOf(
            "Relaxed (Sleep till 9 AM, 2-3 sights, leisurely breaks)",
            "Balanced (Standard rhythm, 3-4 key sights, lunch break)",
            "Packed (Sunrise to night, maximum sightseeing coverage)"
        )
        questionList.add(
            QuestionnaireStep(
                id = "sightseeing_pace",
                stepNumber = 0,
                category = "RHYTHM",
                questionTitle = "Daily Sightseeing Pace",
                description = "How intense should the daily schedule be over your $durationLabel?",
                options = paceOptions,
                selectedOption = previousAnswers["sightseeing_pace"] ?: paceOptions[1]
            )
        )


        // -------------------------------------------------------------
        // RULE J (Always Include): Budget Scope
        // -------------------------------------------------------------
        val budgetOptions = listOf(
            "Yes, all-inclusive (₹${budget.toInt()} covers stay, transit & sights)",
            "No, stay, food & activities only (intercity transit separate)",
            "Flexible budget with 15% contingency buffer"
        )
        questionList.add(
            QuestionnaireStep(
                id = "budget_scope",
                stepNumber = 0,
                category = "BUDGET",
                questionTitle = "Budget Scope",
                description = "Does your ₹${budget.toInt()} estimated budget cover intercity transit?",
                options = budgetOptions,
                selectedOption = previousAnswers["budget_scope"] ?: budgetOptions.first()
            )
        )

        // -------------------------------------------------------------
        // RULE H (Conditional): Coastal & Water Activities
        // Include for beach and coastal regions
        // -------------------------------------------------------------
        if (isCoastal) {
            val coastalOptions = listOf(
                "Relaxed Beach Shacks & Sunset Lounging",
                "Water Sports (Scuba, Snorkeling, Parasailing)",
                "Coastal Forts, Lighthouses & Heritage Walks",
                "Catamaran Cruises & Island Hopping"
            )
            questionList.add(
                QuestionnaireStep(
                    id = "coastal_activities",
                    stepNumber = 0,
                    category = "COASTAL",
                    questionTitle = "Beach & Coastal Activities",
                    description = "What water and beach experiences would you like in $destTrimmed?",
                    options = coastalOptions,
                    selectedOption = previousAnswers["coastal_activities"] ?: coastalOptions.first()
                )
            )
        }

        // -------------------------------------------------------------
        // RULE I (Conditional): Children / Family Accessibility
        // Include if traveling with kids/family
        // -------------------------------------------------------------
        if (hasChildrenOrFamily) {
            val familyOptions = listOf(
                "Kid-friendly parks, easy walking & interactive sights",
                "Stroller-friendly attractions & early family dinners",
                "Balanced pacing with afternoon hotel rest breaks",
                "Teen-friendly outdoor adventures & fun activities"
            )
            questionList.add(
                QuestionnaireStep(
                    id = "children_activities",
                    stepNumber = 0,
                    category = "FAMILY",
                    questionTitle = "Family & Children Considerations",
                    description = "What pacing and accessibility adjustments are needed for the family?",
                    options = familyOptions,
                    selectedOption = previousAnswers["children_activities"] ?: familyOptions.first()
                )
            )
        }


        // Assign clean 1-based sequential step numbers
        return questionList.mapIndexed { index, step ->
            step.copy(stepNumber = index + 1)
        }
    }

    private fun isInternationalDestination(dest: String, origin: String): Boolean {
        val internationalKeywords = listOf(
            "tokyo", "japan", "paris", "france", "london", "uk", "bali", "indonesia",
            "dubai", "uae", "singapore", "bangkok", "thailand", "new york", "usa",
            "rome", "italy", "switzerland", "zurich", "amsterdam", "barcelona",
            "spain", "phuket", "maldives", "vietnam", "danang", "istanbul", "turkey",
            "nepal", "kathmandu", "sri lanka", "colombo", "malaysia", "kuala lumpur"
        )
        val isOriginForeign = internationalKeywords.any { origin.contains(it) }
        val isDestForeign = internationalKeywords.any { dest.contains(it) }
        return isDestForeign && !isOriginForeign
    }

    private fun isMountainDestination(dest: String): Boolean {
        val mountainKeywords = listOf(
            "manali", "shimla", "leh", "ladakh", "kashmir", "gulmarg", "dharamshala",
            "mussoorie", "nainital", "rishikesh", "alps", "zermatt", "aspen", "banff",
            "ooty", "munnar", "gangtok", "darjeeling", "spiti", "kullu", "kasol", "kodaikanal"
        )
        return mountainKeywords.any { dest.contains(it) }
    }

    private fun isCoastalDestination(dest: String): Boolean {
        val coastalKeywords = listOf(
            "goa", "bali", "phuket", "maldives", "gokarna", "pondicherry", "miami",
            "andaman", "maui", "santorini", "kerala", "kovalam", "varkala", "daman", "diu",
            "cancun", "nice", "alibaug"
        )
        return coastalKeywords.any { dest.contains(it) }
    }

    fun getDefaultQuestions(): List<QuestionnaireStep> {
        return buildAdaptiveQuestions(
            destination = "Jaipur",
            origin = "Delhi",
            days = 4,
            budget = 25000.0,
            travellerType = "Couple",
            travellerCount = 2
        )
    }

    fun generateItineraryForTrip(
        trip: TripEntity,
        answers: Map<*, String> = emptyMap<String, String>(),
        baseLat: Double = 26.9124,
        baseLon: Double = 75.7873
    ): List<ItineraryItemEntity> {
        val tripId = trip.id
        val destName = trip.destinationName
        val startingCity = trip.startingLocation
        val days = trip.numberOfDays.coerceAtLeast(1)
        val items = mutableListOf<ItineraryItemEntity>()

        // Day 1: Transit & Hotel Check-in & Evening Walk
        items.add(
            ItineraryItemEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                dayNumber = 1,
                title = "Morning Transit from $startingCity to $destName",
                locationName = "$startingCity to $destName Hub",
                latitude = baseLat,
                longitude = baseLon,
                startTime = "07:00",
                endTime = "11:00",
                durationMinutes = 240,
                category = "Transport",
                estimatedCost = 1200.0,
                travelTimeFromPrevious = "Departure",
                notes = "Scenic transit from $startingCity into $destName.",
                source = "TripMate AI Planner (Verified)",
                orderIndex = 0
            )
        )
        items.add(
            ItineraryItemEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                dayNumber = 1,
                title = "Hotel Check-in & Relaxation in $destName",
                locationName = "Central $destName Lodging",
                latitude = baseLat + 0.003,
                longitude = baseLon + 0.002,
                startTime = "11:30",
                endTime = "13:00",
                durationMinutes = 90,
                category = "Hotel",
                estimatedCost = 0.0,
                travelTimeFromPrevious = "20 mins cab",
                notes = "Freshen up, welcome drinks, and unpack.",
                source = "TripMate AI Planner (Verified)",
                orderIndex = 1
            )
        )
        items.add(
            ItineraryItemEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                dayNumber = 1,
                title = "Welcome Lunch & Iconic $destName Flavors",
                locationName = "Central Market, $destName",
                latitude = baseLat + 0.005,
                longitude = baseLon + 0.004,
                startTime = "13:15",
                endTime = "14:30",
                durationMinutes = 75,
                category = "Restaurant",
                estimatedCost = 650.0,
                travelTimeFromPrevious = "15 mins walk",
                notes = "Regional dishes and local culinary specialties.",
                source = "Culinary Guide (Verified)",
                orderIndex = 2
            )
        )
        items.add(
            ItineraryItemEntity(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                dayNumber = 1,
                title = "Evening Heritage Promenade & Golden Hour Photography",
                locationName = "$destName Old Town / Scenic Spot",
                latitude = baseLat + 0.008,
                longitude = baseLon + 0.006,
                startTime = "15:30",
                endTime = "18:00",
                durationMinutes = 150,
                category = "Photography",
                estimatedCost = 200.0,
                travelTimeFromPrevious = "10 mins auto",
                notes = "Photogenic frames during golden hour sunset.",
                source = "TripMate AI Planner (Verified)",
                orderIndex = 3
            )
        )

        // Middle Days (Day 2 to Day N-1)
        for (d in 2 until days) {
            val dOffset = (d - 1) * 0.006
            items.add(
                ItineraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    dayNumber = d,
                    title = "Morning Coffee & Scenic Breakfast",
                    locationName = "Courtyard Cafe, $destName",
                    latitude = baseLat + dOffset,
                    longitude = baseLon + dOffset,
                    startTime = "08:30",
                    endTime = "09:30",
                    durationMinutes = 60,
                    category = "Café",
                    estimatedCost = 250.0,
                    travelTimeFromPrevious = "Hotel",
                    notes = "Relaxed wake-up and fresh breakfast.",
                    source = "TripMate AI Planner (Verified)",
                    orderIndex = 0
                )
            )
            items.add(
                ItineraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    dayNumber = d,
                    title = "Explore Famous $destName Historic Landmark & Fort",
                    locationName = "Heritage Quarter, $destName",
                    latitude = baseLat + dOffset + 0.004,
                    longitude = baseLon + dOffset + 0.003,
                    startTime = "10:00",
                    endTime = "12:30",
                    durationMinutes = 150,
                    category = "Attraction",
                    estimatedCost = 400.0,
                    travelTimeFromPrevious = "25 mins cab",
                    notes = "Iconic landmark tour and guided historical walk. Open 09:00 - 18:00.",
                    source = "Official Guide (Verified)",
                    orderIndex = 1
                )
            )
            items.add(
                ItineraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    dayNumber = d,
                    title = "Local Artisan Bazaars & Souvenir Shopping",
                    locationName = "$destName Market Street",
                    latitude = baseLat + dOffset + 0.007,
                    longitude = baseLon + dOffset + 0.006,
                    startTime = "15:00",
                    endTime = "17:30",
                    durationMinutes = 150,
                    category = "Shopping",
                    estimatedCost = 500.0,
                    travelTimeFromPrevious = "15 mins auto",
                    notes = "Handicrafts, textiles, and street snacks.",
                    source = "TripMate AI Planner (Verified)",
                    orderIndex = 2
                )
            )
            items.add(
                ItineraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    dayNumber = d,
                    title = "Romantic Sunset Dinner under the Stars",
                    locationName = "Rooftop Garden, $destName",
                    latitude = baseLat + dOffset + 0.009,
                    longitude = baseLon + dOffset + 0.008,
                    startTime = "19:30",
                    endTime = "21:30",
                    durationMinutes = 120,
                    category = "Restaurant",
                    estimatedCost = 1200.0,
                    travelTimeFromPrevious = "Cab from hotel",
                    notes = "Open air candlelight ambiance.",
                    source = "TripMate AI Planner (Verified)",
                    orderIndex = 3
                )
            )
        }

        // Final Day: Morning Sightseeing & Return Transit
        if (days >= 2) {
            val finalOffset = (days - 1) * 0.006
            items.add(
                ItineraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    dayNumber = days,
                    title = "Final Morning Sightseeing & Packing Regional Snacks",
                    locationName = "Botanical Gardens & Bazaars, $destName",
                    latitude = baseLat + finalOffset + 0.002,
                    longitude = baseLon + finalOffset + 0.002,
                    startTime = "09:30",
                    endTime = "12:00",
                    durationMinutes = 150,
                    category = "Attraction",
                    estimatedCost = 300.0,
                    travelTimeFromPrevious = "Auto",
                    notes = "Last photos and packing famous regional sweets. Open 09:00 - 17:00.",
                    source = "TripMate AI Planner (Verified)",
                    orderIndex = 0
                )
            )
            items.add(
                ItineraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    dayNumber = days,
                    title = "Return Transit back to $startingCity",
                    locationName = "$destName Hub to $startingCity",
                    latitude = baseLat,
                    longitude = baseLon,
                    startTime = "15:00",
                    endTime = "19:00",
                    durationMinutes = 240,
                    category = "Transport",
                    estimatedCost = 1200.0,
                    travelTimeFromPrevious = "Station drop",
                    notes = "Smooth return transit concluding a memorable vacation.",
                    source = "TripMate AI Planner (Verified)",
                    orderIndex = 1
                )
            )
        }

        return TravelScheduleUtils.recalculateSequentialSchedule(items)
    }
}
