package com.example.domain.ai

import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.data.remote.GeminiTravelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class AiActionResult {
    data class Success(val message: String, val actionType: String, val affectedItemTitle: String? = null) : AiActionResult()
    data class QuestionPrompt(val question: String, val stepIndex: Int, val options: List<String>) : AiActionResult()
    data class Informational(val message: String, val structuredCards: List<StructuredCard>? = null) : AiActionResult()
}

data class StructuredCard(
    val title: String,
    val subtitle: String,
    val priceText: String,
    val rating: Float? = null,
    val tag: String,
    val actionLabel: String = "View / Book"
)

/**
 * Intelligent AI Travel Planner Engine backed by real Google Gemini (gemini-3.5-flash).
 * Handles conversational inquiries, local travel expertise, cultural tips,
 * and executes live modifications to the trip itinerary database via structured AI tool actions.
 */
class AiTravelPlannerEngine(
    private val db: AppDatabase,
    private val geminiService: GeminiTravelService = GeminiTravelService()
) {

    suspend fun processUserMessage(
        tripId: String,
        userPrompt: String,
        tripContext: TripEntity?,
        existingItems: List<ItineraryItemEntity>,
        chatHistory: List<ChatMessageEntity> = emptyList()
    ): AiActionResult = withContext(Dispatchers.IO) {
        val destName = tripContext?.destinationName ?: "Your Destination"
        val totalBudget = tripContext?.totalBudget ?: 25000.0
        val spentBudget = tripContext?.spentBudget ?: 0.0
        val remainingBudget = totalBudget - spentBudget
        val daysCount = tripContext?.numberOfDays ?: 3

        // Build context summary for Gemini
        val itinerarySummary = if (existingItems.isEmpty()) {
            "No activities planned yet."
        } else {
            existingItems.sortedWith(compareBy({ it.dayNumber }, { it.orderIndex }))
                .joinToString("\n") {
                    "• Day ${it.dayNumber} [${it.startTime}-${it.endTime}]: ${it.title} (${it.category}, Cost: ₹${it.estimatedCost.toInt()})"
                }
        }

        val systemPrompt = """
            You are TripMate AI, a deeply knowledgeable, warm, and helpful travel companion and itinerary planner.
            You are assisting a traveler with their trip to $destName ($daysCount days).
            Trip details:
            - Destination: $destName (${tripContext?.destinationRegion ?: ""}, ${tripContext?.destinationCountry ?: ""})
            - Total Budget: ₹${totalBudget.toInt()} | Spent so far: ₹${spentBudget.toInt()} | Remaining: ₹${remainingBudget.toInt()}
            - Current Itinerary:
            $itinerarySummary

            Guidelines:
            1. Answer any travel questions directly, with specific recommendations, cultural insights, opening hours, local customs, street foods to try, scams to avoid, packing advice, and practical transport tips.
            2. If the user asks to modify their trip (e.g. add a place, move an activity, remove a place, or relax a day), fulfill their request enthusiastically AND include an action tag at the very end of your response in one of these exact formats:
               [[ACTION:ADD|day:<day_number>|title:<place_name>|time:<HH:MM>|cost:<number>|category:<category>]]
               [[ACTION:MOVE|title:<place_name>|targetDay:<day_number>|time:<HH:MM>]]
               [[ACTION:REMOVE|title:<place_name>]]
               [[ACTION:RELAX|day:<day_number>]]
            3. If the user does not request a trip modification, do NOT include any [[ACTION:...]] tag. Just give a brilliant, helpful, friendly answer.
            4. Keep responses crisp, well-formatted using bolding and bullet points, and under 250 words.
        """.trimIndent()

        // Include recent conversation turns for context
        val recentHistory = chatHistory.takeLast(4).joinToString("\n") { msg ->
            if (msg.sender == "USER") "User: ${msg.content}" else "TripMate AI: ${msg.content}"
        }

        val fullPrompt = if (recentHistory.isNotBlank()) {
            "Recent Chat:\n$recentHistory\n\nUser: $userPrompt\nTripMate AI:"
        } else {
            userPrompt
        }

        // Call Gemini 3.5 Flash
        val rawAiReply = geminiService.generateContent(
            prompt = fullPrompt,
            systemInstruction = systemPrompt,
            asJson = false
        )

        if (!rawAiReply.isNullOrBlank()) {
            // Check if Gemini included an executable action tag
            val actionResult = tryExecuteAiAction(rawAiReply, tripId, tripContext, existingItems)
            if (actionResult != null) {
                return@withContext actionResult
            }

            return@withContext AiActionResult.Informational(
                message = rawAiReply.trim()
            )
        }

        // Local Smart Fallback if Gemini network is temporarily unavailable
        return@withContext executeLocalFallback(tripId, userPrompt, tripContext, existingItems)
    }

    private suspend fun tryExecuteAiAction(
        rawReply: String,
        tripId: String,
        tripContext: TripEntity?,
        existingItems: List<ItineraryItemEntity>
    ): AiActionResult? {
        val actionRegex = Regex("""\[\[ACTION:([A-Z_]+)\|(.*?)\]\]""")
        val match = actionRegex.find(rawReply) ?: return null

        val actionType = match.groupValues[1]
        val paramsStr = match.groupValues[2]
        val params = paramsStr.split("|").associate { param ->
            val parts = param.split(":", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
        }

        val cleanMessage = rawReply.replace(match.value, "").trim()

        when (actionType) {
            "ADD" -> {
                val day = params["day"]?.toIntOrNull() ?: 1
                val title = params["title"] ?: "Added Activity"
                val time = params["time"] ?: "11:00"
                val cost = params["cost"]?.toDoubleOrNull() ?: 250.0
                val category = params["category"] ?: "Attraction"

                val newItem = ItineraryItemEntity(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    dayNumber = day,
                    title = title,
                    locationName = "$title, ${tripContext?.destinationName ?: "City"}",
                    latitude = tripContext?.destinationId?.let { 26.9855 } ?: 26.9124,
                    longitude = 75.8513,
                    startTime = time,
                    endTime = calculateEndTime(time, 90),
                    durationMinutes = 90,
                    category = category,
                    estimatedCost = cost,
                    travelTimeFromPrevious = "20 mins",
                    notes = "Added by Gemini AI Assistant",
                    source = "Gemini Travel AI",
                    orderIndex = existingItems.filter { it.dayNumber == day }.size
                )
                db.tripDao().insertItineraryItem(newItem)

                tripContext?.let {
                    db.tripDao().updateTrip(it.copy(spentBudget = it.spentBudget + cost))
                }

                return AiActionResult.Success(
                    message = cleanMessage,
                    actionType = "ADD_ACTIVITY",
                    affectedItemTitle = title
                )
            }

            "MOVE" -> {
                val title = params["title"] ?: ""
                val targetDay = params["targetDay"]?.toIntOrNull()
                val targetTime = params["time"] ?: "15:00"

                val targetItem = existingItems.firstOrNull { it.title.contains(title, ignoreCase = true) || title.contains(it.title, ignoreCase = true) }
                if (targetItem != null) {
                    val newDay = targetDay ?: targetItem.dayNumber
                    val updated = targetItem.copy(
                        dayNumber = newDay,
                        startTime = targetTime,
                        endTime = calculateEndTime(targetTime, targetItem.durationMinutes)
                    )
                    db.tripDao().updateItineraryItem(updated)
                    return AiActionResult.Success(
                        message = cleanMessage,
                        actionType = "MOVE_ACTIVITY",
                        affectedItemTitle = targetItem.title
                    )
                }
            }

            "REMOVE" -> {
                val title = params["title"] ?: ""
                val targetItem = existingItems.firstOrNull { it.title.contains(title, ignoreCase = true) || title.contains(it.title, ignoreCase = true) }
                if (targetItem != null) {
                    db.tripDao().deleteItineraryItem(targetItem.id)
                    tripContext?.let {
                        val newSpent = (it.spentBudget - targetItem.estimatedCost).coerceAtLeast(0.0)
                        db.tripDao().updateTrip(it.copy(spentBudget = newSpent))
                    }
                    return AiActionResult.Success(
                        message = cleanMessage,
                        actionType = "REMOVE_ACTIVITY",
                        affectedItemTitle = targetItem.title
                    )
                }
            }

            "RELAX" -> {
                val day = params["day"]?.toIntOrNull() ?: 2
                val dayItems = existingItems.filter { it.dayNumber == day }
                if (dayItems.size > 2) {
                    val toRemove = dayItems.last()
                    db.tripDao().deleteItineraryItem(toRemove.id)
                    val restBreak = ItineraryItemEntity(
                        id = UUID.randomUUID().toString(),
                        tripId = tripId,
                        dayNumber = day,
                        title = "Leisurely Courtyard Rest & Chai Break",
                        locationName = "Local Shaded Garden / Haveli Courtyard",
                        latitude = 26.9250,
                        longitude = 75.7910,
                        startTime = "15:00",
                        endTime = "17:00",
                        durationMinutes = 120,
                        category = "Café",
                        estimatedCost = 150.0,
                        travelTimeFromPrevious = "Walk",
                        notes = "Pacing break scheduled by Gemini AI",
                        source = "Gemini AI Pacing",
                        orderIndex = 2
                    )
                    db.tripDao().insertItineraryItem(restBreak)
                }
                return AiActionResult.Success(
                    message = cleanMessage,
                    actionType = "PACING_OPTIMIZED",
                    affectedItemTitle = "Day $day"
                )
            }
        }

        return null
    }

    private suspend fun executeLocalFallback(
        tripId: String,
        userPrompt: String,
        tripContext: TripEntity?,
        existingItems: List<ItineraryItemEntity>
    ): AiActionResult {
        return AiActionResult.Informational(
            message = "⚠️ **AI Assistant Unavailable**\n\nI am currently unable to connect to the Gemini AI network. Please check your connection or try again in a moment."
        )
    }

    private fun calculateEndTime(startTime: String, durationMins: Int): String {
        return try {
            val parts = startTime.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val totalM = h * 60 + m + durationMins
            val endH = (totalM / 60) % 24
            val endM = totalM % 60
            String.format("%02d:%02d", endH, endM)
        } catch (_: Exception) {
            "13:00"
        }
    }
}
