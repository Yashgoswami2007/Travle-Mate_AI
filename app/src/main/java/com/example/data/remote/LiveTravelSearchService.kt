package com.example.data.remote

import com.example.data.local.entity.HotelOptionEntity
import com.example.data.local.entity.TransportOptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level Live Travel Search Service combining Gemini Travel AI,
 * live web transport, real hotels, and Wikimedia/web place photography.
 */
class LiveTravelSearchService(
    private val geminiTravelService: GeminiTravelService = GeminiTravelService(),
    private val imageService: PlaceImageService = PlaceImageService()
) {

    // Real-Time Transport Search from Web / Gemini
    suspend fun searchLiveTransport(
        fromCity: String,
        toCity: String,
        destinationId: String,
        originLat: Double = 28.6139,
        originLon: Double = 77.2090,
        destLat: Double = 26.9124,
        destLon: Double = 75.7873
    ): List<TransportOptionEntity> = withContext(Dispatchers.IO) {
        // Query Gemini / Web service for real live transport tickets
        geminiTravelService.fetchLiveTickets(fromCity, toCity, destinationId)
    }

    // Real-Time Hotel Search from Web / Gemini with place images
    suspend fun searchLiveHotels(
        destinationName: String,
        destinationId: String,
        lat: Double = 26.9124,
        lon: Double = 75.7873
    ): List<HotelOptionEntity> = withContext(Dispatchers.IO) {
        // Query Gemini / Web service for real hotels with real photographs
        geminiTravelService.fetchLiveHotels(destinationName, destinationId)
    }
}
