package com.example.domain.ai

import com.example.data.local.entity.ItineraryItemEntity
import java.util.Locale
import kotlin.math.*

/**
 * Mathematical and schedule coordination utilities for TripMate AI.
 * Handles:
 * 1. Haversine spherical distance calculation between coordinates.
 * 2. Realistic ground travel time estimation (walking vs transit).
 * 3. Chronological, non-overlapping schedule timing recalculation per day.
 * 4. Venue operating hours validation.
 * 5. Aggregate budget and expense verification.
 */
object TravelScheduleUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates great-circle distance between two geographic coordinates using the Haversine formula.
     */
    fun calculateDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (lat1 == 0.0 || lon1 == 0.0 || lat2 == 0.0 || lon2 == 0.0) return 0.0
        if (lat1 == lat2 && lon1 == lon2) return 0.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Estimates ground travel time in minutes based on distance in kilometers.
     * < 1 km: Walking speed (~4 km/h = 15 mins/km)
     * >= 1 km: Urban vehicular speed (~17 km/h = ~3.5 mins/km + 5 min traffic/pickup buffer)
     */
    fun estimateTravelMinutes(distanceKm: Double): Int {
        if (distanceKm <= 0.05) return 0
        return if (distanceKm < 1.0) {
            maxOf(4, (distanceKm * 15.0).roundToInt())
        } else {
            maxOf(8, (distanceKm * 3.5 + 5.0).roundToInt())
        }
    }

    /**
     * Formats a human-readable transit summary with distance and duration.
     * e.g., "Walk ~7 mins (0.5 km)" or "Cab ~18 mins (4.2 km)".
     */
    fun formatTravelTimeFromPrevious(
        distanceKm: Double,
        isFirstOfDay: Boolean = false
    ): String {
        if (isFirstOfDay) return "Morning Departure"
        if (distanceKm <= 0.05) return "Same location"

        val minutes = estimateTravelMinutes(distanceKm)
        val distFormatted = String.format(Locale.US, "%.1f", distanceKm)
        return if (distanceKm < 1.0) {
            "Walk ~$minutes mins ($distFormatted km)"
        } else {
            "Cab ~$minutes mins ($distFormatted km)"
        }
    }

    /**
     * Converts minutes from midnight into 24-hour "HH:mm" format.
     */
    fun minutesToTimeString(totalMinutes: Int): String {
        val clamped = (totalMinutes % 1440).coerceAtLeast(0)
        val hours = clamped / 60
        val mins = clamped % 60
        return String.format(Locale.US, "%02d:%02d", hours, mins)
    }

    /**
     * Converts "HH:mm" or "H:mm" string into total minutes from midnight.
     */
    fun timeStringToMinutes(timeStr: String, fallbackMinutes: Int = 570): Int {
        val clean = timeStr.trim()
        val parts = clean.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h != null && m != null) {
                return (h * 60 + m).coerceIn(0, 1439)
            }
        }
        return fallbackMinutes
    }

    /**
     * Calculates the end time string formatted as "HH:mm" based on start time and duration in minutes.
     */
    fun calculateEndTime(startTime: String, durationMinutes: Int): String {
        val startMins = timeStringToMinutes(startTime, 570)
        return minutesToTimeString(startMins + durationMinutes.coerceAtLeast(10))
    }

    /**
     * Recalculates start and end times sequentially for all activities within each day.
     * Ensures:
     * 1. No activities overlap.
     * 2. Travel time between consecutive stops is calculated from real coordinates.
     * 3. Order indices are zero-indexed and sequential.
     */
    fun recalculateSequentialSchedule(
        items: List<ItineraryItemEntity>,
        defaultMorningStartMinutes: Int = 570, // 09:30 AM
        anchorFirstItemStart: Boolean = true
    ): List<ItineraryItemEntity> {
        if (items.isEmpty()) return emptyList()

        val result = mutableListOf<ItineraryItemEntity>()
        val groupedByDay = items.groupBy { it.dayNumber }.toSortedMap()

        for ((day, dayItems) in groupedByDay) {
            // Sort by current orderIndex or startTime
            val sortedDayItems = dayItems.sortedWith(
                compareBy<ItineraryItemEntity> { it.orderIndex }
                    .thenBy { timeStringToMinutes(it.startTime) }
            )

            // Calculate starting time for the day:
            // If anchorFirstItemStart is true and first item has a valid startTime, honor that time
            val firstItemStartMinutes = if (anchorFirstItemStart) {
                sortedDayItems.firstOrNull()?.let {
                    val parsed = timeStringToMinutes(it.startTime, -1)
                    if (parsed in 300..1380) parsed else null // Between 05:00 AM and 11:00 PM
                }
            } else null

            var currentMinutes = firstItemStartMinutes
                ?: (if (day == 1 && sortedDayItems.firstOrNull()?.category == "Transport") 480 else defaultMorningStartMinutes)

            var previousLat = 0.0
            var previousLon = 0.0

            sortedDayItems.forEachIndexed { index, item ->
                val duration = item.durationMinutes.coerceAtLeast(30)
                val isFirst = (index == 0)

                // Travel distance & time from previous stop
                val distanceKm = if (!isFirst && previousLat != 0.0 && item.latitude != 0.0) {
                    calculateDistanceKm(previousLat, previousLon, item.latitude, item.longitude)
                } else {
                    0.0
                }

                val travelMinutes = if (!isFirst) estimateTravelMinutes(distanceKm) else 0
                currentMinutes += travelMinutes

                val startTimeStr = minutesToTimeString(currentMinutes)
                val endTimeStr = minutesToTimeString(currentMinutes + duration)
                val travelLabel = formatTravelTimeFromPrevious(distanceKm, isFirstOfDay = isFirst)

                result.add(
                    item.copy(
                        orderIndex = index,
                        startTime = startTimeStr,
                        endTime = endTimeStr,
                        durationMinutes = duration,
                        travelTimeFromPrevious = travelLabel
                    )
                )

                // Advance cursor to end of this activity
                currentMinutes += duration
                previousLat = item.latitude
                previousLon = item.longitude
            }
        }

        return result
    }

    /**
     * Sums all estimated costs across itinerary items.
     */
    fun calculateTotalCost(items: List<ItineraryItemEntity>): Double {
        return items.sumOf { it.estimatedCost }
    }

    /**
     * Checks if a scheduled visit time ("HH:mm") falls within venue opening hours (e.g. "09:00 - 18:00").
     */
    fun isWithinOpeningHours(visitTime: String, openingHours: String): Boolean {
        val cleanHours = openingHours.trim().lowercase(Locale.US)
        if (cleanHours.contains("24 hours") || cleanHours.contains("open 24") || cleanHours.contains("always")) {
            return true
        }

        val match = Regex("""(\d{1,2}:\d{2})\s*[-–to]+\s*(\d{1,2}:\d{2})""").find(cleanHours)
        if (match != null) {
            val openMin = timeStringToMinutes(match.groupValues[1], 0)
            val closeMin = timeStringToMinutes(match.groupValues[2], 1439)
            val visitMin = timeStringToMinutes(visitTime, 600)
            return visitMin in openMin..closeMin
        }
        return true
    }

    /**
     * Route summary metrics for an itinerary sequence.
     */
    data class RouteStats(
        val totalDistanceKm: Double,
        val totalTransitMinutes: Int,
        val segmentDistances: List<Double>
    )

    /**
     * Calculates cumulative route distance and estimated transit time for a list of sequential waypoints.
     */
    fun calculateRouteStats(items: List<ItineraryItemEntity>): RouteStats {
        if (items.size <= 1) return RouteStats(0.0, 0, emptyList())

        var totalDist = 0.0
        var totalMinutes = 0
        val segments = mutableListOf<Double>()

        for (i in 0 until items.size - 1) {
            val a = items[i]
            val b = items[i + 1]
            val dist = if (a.latitude != 0.0 && b.latitude != 0.0) {
                calculateDistanceKm(a.latitude, a.longitude, b.latitude, b.longitude)
            } else 0.0

            val mins = estimateTravelMinutes(dist)
            totalDist += dist
            totalMinutes += mins
            segments.add(dist)
        }

        return RouteStats(totalDist, totalMinutes, segments)
    }
}
