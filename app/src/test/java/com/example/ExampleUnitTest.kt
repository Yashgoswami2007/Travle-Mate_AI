package com.example

import com.example.data.local.entity.TripEntity
import com.example.domain.ai.ItineraryPlannerWizard
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testInclusiveDayAndNightCalculation() {
    val startDate = java.time.LocalDate.of(2026, 11, 12)
    val endDate = java.time.LocalDate.of(2026, 11, 17) // 6 calendar days = 5 nights
    val days = com.example.domain.ai.TravelDateUtils.calculateDays(startDate, endDate)
    val nights = com.example.domain.ai.TravelDateUtils.calculateNights(days)

    assertEquals(6, days)
    assertEquals(5, nights)
    assertEquals("6 Days • 5 Nights", com.example.domain.ai.TravelDateUtils.formatDurationSummary(days))
  }

  @Test
  fun testAdaptiveQuestionEngineContextRules() {
    // 1. Domestic trip (Delhi to Jaipur) -> No visa question
    val domesticQuestions = com.example.domain.ai.ItineraryPlannerWizard.buildAdaptiveQuestions(
      destination = "Jaipur",
      origin = "Delhi",
      days = 4,
      budget = 25000.0,
      travellerType = "Couple",
      travellerCount = 2
    )
    val domesticIds = domesticQuestions.map { it.id }
    assertTrue(domesticIds.contains("travel_style"))
    assertTrue(domesticIds.contains("accommodation"))
    assertTrue(domesticIds.contains("food_preferences"))
    assertTrue(domesticIds.contains("sightseeing_pace"))
    assertTrue(domesticIds.contains("transport_mode"))
    assertFalse("Domestic trip must NOT include visa questions", domesticIds.contains("visa_passport"))

    // 2. International trip (Delhi to Tokyo) -> Must include visa question
    val intlQuestions = com.example.domain.ai.ItineraryPlannerWizard.buildAdaptiveQuestions(
      destination = "Tokyo",
      origin = "Delhi",
      days = 7,
      budget = 150000.0,
      travellerType = "Friends",
      travellerCount = 3
    )
    val intlIds = intlQuestions.map { it.id }
    assertTrue("International trip must include visa questions", intlIds.contains("visa_passport"))

    // 3. Mountain destination (Manali) -> Must include mountain transit question
    val mountainQuestions = com.example.domain.ai.ItineraryPlannerWizard.buildAdaptiveQuestions(
      destination = "Manali",
      origin = "Delhi",
      days = 5,
      budget = 35000.0,
      travellerType = "Couple",
      travellerCount = 2
    )
    val mountainIds = mountainQuestions.map { it.id }
    assertTrue("Mountain trip must include mountain transit question", mountainIds.contains("mountain_transport"))

    // 4. Coastal destination (Goa) -> Must include coastal activities question
    val coastalQuestions = com.example.domain.ai.ItineraryPlannerWizard.buildAdaptiveQuestions(
      destination = "Goa",
      origin = "Mumbai",
      days = 4,
      budget = 30000.0,
      travellerType = "Friends",
      travellerCount = 4
    )
    val coastalIds = coastalQuestions.map { it.id }
    assertTrue("Coastal trip must include coastal activities question", coastalIds.contains("coastal_activities"))

    // 5. Family trip -> Must include family/children accessibility question
    val familyQuestions = com.example.domain.ai.ItineraryPlannerWizard.buildAdaptiveQuestions(
      destination = "Jaipur",
      origin = "Delhi",
      days = 4,
      budget = 40000.0,
      travellerType = "Family",
      travellerCount = 4
    )
    val familyIds = familyQuestions.map { it.id }
    assertTrue("Family trip must include children & family accessibility question", familyIds.contains("children_activities"))
  }

  @Test
  fun testHaversineDistanceAndTransitEstimation() {
    // Distance between Delhi (28.6139, 77.2090) and Jaipur (26.9124, 75.7873) is ~238 km
    val dist = com.example.domain.ai.TravelScheduleUtils.calculateDistanceKm(
      28.6139, 77.2090,
      26.9124, 75.7873
    )
    assertTrue("Distance between Delhi and Jaipur should be ~230-245 km", dist in 230.0..245.0)

    // Short walking distance (~0.4 km)
    val shortDist = 0.4
    val walkTime = com.example.domain.ai.TravelScheduleUtils.estimateTravelMinutes(shortDist)
    val walkLabel = com.example.domain.ai.TravelScheduleUtils.formatTravelTimeFromPrevious(shortDist)
    assertTrue("Short distance should take ~4-8 mins", walkTime in 4..8)
    assertTrue("Short distance should format as Walk", walkLabel.startsWith("Walk ~"))

    // Longer vehicular distance (~5.2 km)
    val cabDist = 5.2
    val cabTime = com.example.domain.ai.TravelScheduleUtils.estimateTravelMinutes(cabDist)
    val cabLabel = com.example.domain.ai.TravelScheduleUtils.formatTravelTimeFromPrevious(cabDist)
    assertTrue("Vehicular distance should take ~20-26 mins", cabTime in 20..26)
    assertTrue("Vehicular distance should format as Cab", cabLabel.startsWith("Cab ~"))
  }

  @Test
  fun testSequentialScheduleNonOverlappingAndCoordinateAnchoring() {
    val trip = TripEntity(
      id = "test_trip_1",
      userId = "user_default",
      destinationId = "dest_udaipur",
      destinationName = "Udaipur",
      destinationRegion = "Rajasthan",
      destinationCountry = "India",
      imageUrl = "",
      startDate = "Nov 12, 2026",
      endDate = "Nov 15, 2026",
      numberOfDays = 3,
      travellersCount = 2,
      travellersDescription = "Couple",
      startingLocation = "Mumbai",
      totalBudget = 30000.0,
      spentBudget = 0.0,
      pace = "Relaxed",
      status = "ACTIVE"
    )

    // Generate with Udaipur base coordinates
    val udaipurLat = 24.5854
    val udaipurLon = 73.7125
    val items = com.example.domain.ai.ItineraryPlannerWizard.generateItineraryForTrip(
      trip = trip,
      baseLat = udaipurLat,
      baseLon = udaipurLon
    )

    assertFalse("Generated items must not be empty", items.isEmpty())

    // 1. Verify coordinate anchoring: all items must be anchored near Udaipur, NOT Jaipur or Guwahati
    for (item in items) {
      val distFromCenter = com.example.domain.ai.TravelScheduleUtils.calculateDistanceKm(
        udaipurLat, udaipurLon,
        item.latitude, item.longitude
      )
      assertTrue("Item '${item.title}' at (${item.latitude}, ${item.longitude}) must be within 15 km of Udaipur", distFromCenter < 15.0)
    }

    // 2. Verify non-overlapping sequential schedule per day
    val grouped = items.groupBy { it.dayNumber }
    for ((day, dayItems) in grouped) {
      for (i in 0 until dayItems.size - 1) {
        val cur = dayItems[i]
        val next = dayItems[i + 1]

        val curEndMin = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(cur.endTime)
        val nextStartMin = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(next.startTime)

        assertTrue(
          "Day $day: Next activity '${next.title}' start ($nextStartMin) must be at or after current '${cur.title}' end ($curEndMin)",
          nextStartMin >= curEndMin
        )
      }
    }

    // 3. Verify total cost calculation
    val totalCost = com.example.domain.ai.TravelScheduleUtils.calculateTotalCost(items)
    val expectedCost = items.sumOf { it.estimatedCost }
    assertEquals(expectedCost, totalCost, 0.01)
  }

  @Test
  fun testOpeningHoursValidation() {
    // Standard museum hours: 09:00 - 18:00
    val hours = "09:00 - 18:00"
    assertTrue("10:30 should be within 09:00 - 18:00", com.example.domain.ai.TravelScheduleUtils.isWithinOpeningHours("10:30", hours))
    assertTrue("09:00 should be within 09:00 - 18:00", com.example.domain.ai.TravelScheduleUtils.isWithinOpeningHours("09:00", hours))
    assertTrue("18:00 should be within 09:00 - 18:00", com.example.domain.ai.TravelScheduleUtils.isWithinOpeningHours("18:00", hours))
    assertFalse("08:15 should be outside 09:00 - 18:00", com.example.domain.ai.TravelScheduleUtils.isWithinOpeningHours("08:15", hours))
    assertFalse("19:30 should be outside 09:00 - 18:00", com.example.domain.ai.TravelScheduleUtils.isWithinOpeningHours("19:30", hours))

    // 24 hours venue
    assertTrue(com.example.domain.ai.TravelScheduleUtils.isWithinOpeningHours("03:00", "Open 24 hours"))
  }

  @Test
  fun testRouteStatsCalculation() {
    val items = listOf(
      com.example.data.local.entity.ItineraryItemEntity(
        id = "1", tripId = "t1", dayNumber = 1, title = "Stop 1",
        locationName = "Location 1", latitude = 26.9124, longitude = 75.7873,
        startTime = "09:00", endTime = "10:30", category = "Attraction"
      ),
      com.example.data.local.entity.ItineraryItemEntity(
        id = "2", tripId = "t1", dayNumber = 1, title = "Stop 2",
        locationName = "Location 2", latitude = 26.9200, longitude = 75.7950,
        startTime = "11:00", endTime = "12:30", category = "Attraction"
      ),
      com.example.data.local.entity.ItineraryItemEntity(
        id = "3", tripId = "t1", dayNumber = 1, title = "Stop 3",
        locationName = "Location 3", latitude = 26.9300, longitude = 75.8050,
        startTime = "13:00", endTime = "14:30", category = "Attraction"
      )
    )

    val stats = com.example.domain.ai.TravelScheduleUtils.calculateRouteStats(items)
    assertEquals(2, stats.segmentDistances.size)
    assertTrue("Total route distance should be positive", stats.totalDistanceKm > 0.0)
    assertTrue("Total transit time should be positive", stats.totalTransitMinutes > 0)
    assertEquals(
      stats.segmentDistances.sum(),
      stats.totalDistanceKm,
      0.001
    )
  }

  @Test
  fun testProjectionNorthOrientation() {
    // North (higher latitude) must have smaller Y (top of screen)
    // East (higher longitude) must have larger X (right of screen)
    val southPointLat = 26.9000
    val northPointLat = 26.9500
    val westPointLon = 75.7000
    val eastPointLon = 75.8000

    val minLat = 26.8900
    val maxLat = 26.9600
    val minLon = 75.6900
    val maxLon = 75.8100

    val height = 300f
    val width = 400f
    val padding = 50f
    val effectiveH = height - padding * 2
    val effectiveW = width - padding * 2

    val yNorth = padding + ((maxLat - northPointLat) / (maxLat - minLat)).toFloat() * effectiveH
    val ySouth = padding + ((maxLat - southPointLat) / (maxLat - minLat)).toFloat() * effectiveH

    val xWest = padding + ((westPointLon - minLon) / (maxLon - minLon)).toFloat() * effectiveW
    val xEast = padding + ((eastPointLon - minLon) / (maxLon - minLon)).toFloat() * effectiveW

    assertTrue("Northern point must have smaller Y than southern point", yNorth < ySouth)
    assertTrue("Eastern point must have larger X than western point", xEast > xWest)
  }

  @Test
  fun testCalculateEndTimeHelper() {
    assertEquals("11:00", com.example.domain.ai.TravelScheduleUtils.calculateEndTime("09:30", 90))
    assertEquals("15:00", com.example.domain.ai.TravelScheduleUtils.calculateEndTime("14:15", 45))
    assertEquals("10:30", com.example.domain.ai.TravelScheduleUtils.calculateEndTime("10:00", 30))
    assertEquals("13:00", com.example.domain.ai.TravelScheduleUtils.calculateEndTime("11:00", 120))
  }

  @Test
  fun testItineraryReorderingAndDependentScheduleRecalculation() {
    val stop1 = com.example.data.local.entity.ItineraryItemEntity(
      id = "s1", tripId = "t1", dayNumber = 1, title = "Amber Fort",
      locationName = "Amber", latitude = 26.9855, longitude = 75.8513,
      startTime = "09:30", endTime = "11:00", durationMinutes = 90,
      category = "Fort", orderIndex = 0
    )
    val stop2 = com.example.data.local.entity.ItineraryItemEntity(
      id = "s2", tripId = "t1", dayNumber = 1, title = "City Palace Museum",
      locationName = "Old City", latitude = 26.9258, longitude = 75.8236,
      startTime = "11:30", endTime = "12:30", durationMinutes = 60,
      category = "Museum", orderIndex = 1
    )
    val stop3 = com.example.data.local.entity.ItineraryItemEntity(
      id = "s3", tripId = "t1", dayNumber = 1, title = "LMB Sweet Shop & Café",
      locationName = "Johari Bazar", latitude = 26.9180, longitude = 75.8260,
      startTime = "13:00", endTime = "13:45", durationMinutes = 45,
      category = "Café", orderIndex = 2
    )

    val initialItems = listOf(stop1, stop2, stop3)
    val schedule1 = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(initialItems)

    assertEquals(0, schedule1[0].orderIndex)
    assertEquals(1, schedule1[1].orderIndex)
    assertEquals(2, schedule1[2].orderIndex)
    assertEquals("s1", schedule1[0].id)
    assertEquals("s2", schedule1[1].id)
    assertEquals("s3", schedule1[2].id)

    // Now reorder: Move Stop 3 Up (swap with Stop 2)
    val reorderedList = listOf(
      schedule1[0],
      schedule1[2].copy(orderIndex = 1),
      schedule1[1].copy(orderIndex = 2)
    )

    val recalculated = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(reorderedList)

    assertEquals("s1", recalculated[0].id)
    assertEquals("s3", recalculated[1].id)
    assertEquals("s2", recalculated[2].id)
    assertEquals(0, recalculated[0].orderIndex)
    assertEquals(1, recalculated[1].orderIndex)
    assertEquals(2, recalculated[2].orderIndex)

    // Verify non-overlapping sequential order
    val end0 = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(recalculated[0].endTime)
    val start1 = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(recalculated[1].startTime)
    val end1 = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(recalculated[1].endTime)
    val start2 = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(recalculated[2].startTime)

    assertTrue("Stop 3 must start at or after Stop 1 ends", start1 >= end0)
    assertTrue("Stop 2 must start at or after Stop 3 ends", start2 >= end1)
  }

  @Test
  fun testItineraryEditingDurationAndDependentTimeShift() {
    val stop1 = com.example.data.local.entity.ItineraryItemEntity(
      id = "s1", tripId = "t1", dayNumber = 1, title = "Amber Fort",
      locationName = "Amber", latitude = 26.9855, longitude = 75.8513,
      startTime = "09:30", endTime = "11:00", durationMinutes = 90,
      category = "Fort", orderIndex = 0
    )
    val stop2 = com.example.data.local.entity.ItineraryItemEntity(
      id = "s2", tripId = "t1", dayNumber = 1, title = "City Palace Museum",
      locationName = "Old City", latitude = 26.9258, longitude = 75.8236,
      startTime = "11:30", endTime = "12:30", durationMinutes = 60,
      category = "Museum", orderIndex = 1
    )

    val scheduleBefore = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(listOf(stop1, stop2))
    val stop2StartBefore = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(scheduleBefore[1].startTime)

    // User edits Stop 1 to take 150 minutes instead of 90 minutes (+60 min extension)
    val editedStop1 = stop1.copy(durationMinutes = 150)
    val scheduleAfter = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(listOf(editedStop1, stop2))
    val stop2StartAfter = com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(scheduleAfter[1].startTime)

    assertTrue(
      "Stop 2 start time must shift later when Stop 1 duration increases",
      stop2StartAfter >= stop2StartBefore + 60
    )
  }

  @Test
  fun testAnchorFirstItemStartCustomTime() {
    // User wants day to start at 11:00 AM instead of default 09:30 AM
    val stop1 = com.example.data.local.entity.ItineraryItemEntity(
      id = "s1", tripId = "t1", dayNumber = 2, title = "Late Brunch & Art Cafe",
      locationName = "Art District", latitude = 26.9124, longitude = 75.7873,
      startTime = "11:00", endTime = "12:30", durationMinutes = 90,
      category = "Food", orderIndex = 0
    )
    val stop2 = com.example.data.local.entity.ItineraryItemEntity(
      id = "s2", tripId = "t1", dayNumber = 2, title = "Afternoon Gallery Tour",
      locationName = "Art District", latitude = 26.9130, longitude = 75.7880,
      startTime = "12:45", endTime = "14:15", durationMinutes = 90,
      category = "Attraction", orderIndex = 1
    )

    val schedule = com.example.domain.ai.TravelScheduleUtils.recalculateSequentialSchedule(
      listOf(stop1, stop2),
      anchorFirstItemStart = true
    )

    assertEquals("First item must preserve anchor start time of 11:00", "11:00", schedule[0].startTime)
    assertEquals("12:30", schedule[0].endTime)
    assertTrue("Second item must start at or after 12:30", com.example.domain.ai.TravelScheduleUtils.timeStringToMinutes(schedule[1].startTime) >= 750)
  }

  @Test
  fun testOpenRouteServiceFallbackRouting() = kotlinx.coroutines.test.runTest {
    val ors = com.example.data.remote.OpenRouteService()
    val waypoints = listOf(
      Pair(26.9855, 75.8513), // Amber Fort
      Pair(26.9258, 75.8236), // City Palace
      Pair(26.9180, 75.8260)  // LMB
    )

    // Call without API key -> Should gracefully fallback to geodesic coordinate path
    val result = ors.fetchRoute(waypoints, profile = "driving-car", apiKey = "")
    assertTrue("Route must succeed with fallback", result.success)
    assertTrue("Should be marked as fallback", result.isFallback)
    assertTrue("Total distance should be computed", result.totalDistanceKm > 0.0)
    assertEquals("Steps count must match n-1 segments", 2, result.steps.size)
    assertEquals("Coordinate count must match input waypoints", 3, result.coordinates.size)
  }
}


