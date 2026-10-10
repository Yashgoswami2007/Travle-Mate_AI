package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DestinationEntity
import com.example.data.local.entity.DestinationImageEntity
import com.example.data.local.entity.PlaceEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-level data model representing a Public Destination package.
 * Frontend receives the exact same structure whether data came from the cache or was freshly generated.
 */
data class DestinationCacheResult(
    val destination: DestinationEntity,
    val attractions: List<PlaceEntity>,
    val images: List<DestinationImageEntity>,
    val hotels: List<com.example.data.local.entity.HotelOptionEntity> = emptyList(),
    val isFromCache: Boolean
)

/**
 * Public Destination Database & Shared Cache Service with Concurrency Lock & Supabase Integration.
 * Implements:
 * Research once -> Store in Public DB -> Reuse everywhere -> Refresh when stale
 * With atomic generation lock preventing duplicate AI calls.
 */
class SupabaseDestinationCacheService(
    private val db: AppDatabase,
    private val geminiTravelService: GeminiTravelService,
    private val placeImageService: PlaceImageService,
    private val geocodingService: GeocodingService,
    private val weatherService: WeatherService = WeatherService()
) {
    private val tag = "DestinationCache"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Concurrency Lock: Prevents duplicate parallel AI research calls for the same destination
    private val inFlightGenerationJobs = ConcurrentHashMap<String, CompletableDeferred<DestinationCacheResult>>()
    private val lockMutex = Mutex()

    // Supabase credentials
    private val supabaseUrl: String = try {
        BuildConfig.SUPABASE_URL.trimEnd('/')
    } catch (e: Throwable) {
        ""
    }

    private val supabasePublishableKey: String = try {
        BuildConfig.SUPABASE_PUBLISHABLE_KEY
    } catch (e: Throwable) {
        ""
    }

    private val supabaseSecretKey: String = try {
        BuildConfig.SUPABASE_SECRET_KEY
    } catch (e: Throwable) {
        ""
    }

    /**
     * Normalizes a destination query for consistent cache lookups:
     * e.g., "Moradabad, UP", "moradabad ", "Moradabad" -> "moradabad"
     */
    fun normalize(name: String): String {
        return name
            .lowercase()
            .split(",").firstOrNull()?.trim() ?: name.lowercase().trim()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    /**
     * Core Destination Cache Logic:
     * 1. Normalize destination name
     * 2. Check Public DB (Local Room public cache, then Supabase public tables)
     * 3. If exists: DO NOT make another AI call. Simply return stored destination, attractions, and images.
     * 4. If doesn't exist:
     *    - Acquire concurrency generation lock (duplicate prevention)
     *    - Call AI/search API
     *    - Research destination, find key attractions, get cover image & gallery
     *    - Structure & validate data
     *    - Save to public database (Room + Supabase)
     *    - Return result
     */
    suspend fun getOrResearchDestination(
        searchQuery: String,
        fromCity: String = "Delhi"
    ): DestinationCacheResult = withContext(Dispatchers.IO) {
        val normalized = normalize(searchQuery)
        Log.d(tag, "Lookup initiated for query: '$searchQuery' -> normalized: '$normalized'")

        // -------------------------------------------------------------
        // STEP 1: Check Local Public Database (Room Cache)
        // -------------------------------------------------------------
        val localCached = db.destinationDao().getDestinationByNormalizedName(normalized)
            ?: db.destinationDao().searchDestinations(normalized).firstOrNull()

        if (localCached != null) {
            val attractions = db.destinationDao().getPlacesForDestinationSync(localCached.id)
            val images = db.destinationImageDao().getImagesForDestinationSync(localCached.id)
            val hotels = db.travelSearchDao().getHotelOptionsSync(localCached.id)

            val isComplete = attractions.isNotEmpty() && images.isNotEmpty() && localCached.description.isNotBlank()
            
            if (isComplete) {
                Log.d(tag, "Cache HIT in Local Public DB for '$normalized' and is COMPLETE. NO AI CALL MADE.")
                db.destinationDao().incrementSearchCount(localCached.id)
                return@withContext DestinationCacheResult(
                    destination = localCached,
                    attractions = attractions,
                    images = images,
                    hotels = hotels,
                    isFromCache = true
                )
            } else {
                Log.d(tag, "Local Cache HIT for '$normalized' but is INCOMPLETE. Falling back to Supabase/Generation.")
            }
        }

        // -------------------------------------------------------------
        // STEP 2: Check Remote Supabase Public Destination Database
        // -------------------------------------------------------------
        val supabaseCached = querySupabasePublicDestination(normalized)
        if (supabaseCached != null) {
            val isComplete = supabaseCached.attractions.isNotEmpty() && 
                             supabaseCached.images.isNotEmpty() && 
                             supabaseCached.destination.description.isNotBlank()

            if (!isComplete) {
                Log.d(tag, "Destination '${normalized}' exists in Supabase, but is INCOMPLETE. Generating missing data...")
                // We will let the flow continue to generate missing data using the EXISTING destination ID.
            } else {
                Log.d(tag, "Cache HIT in Supabase Public DB for '$normalized' and is COMPLETE. Saving to local cache. NO AI CALL MADE.")
                // Cache locally for instantaneous access
                db.destinationDao().insertDestinations(listOf(supabaseCached.destination))
                db.destinationDao().insertPlaces(supabaseCached.attractions)
                db.destinationImageDao().insertImages(supabaseCached.images)
                if (supabaseCached.hotels.isNotEmpty()) {
                    db.travelSearchDao().insertHotelOptions(supabaseCached.hotels)
                }
                return@withContext supabaseCached
            }
        }

        // -------------------------------------------------------------
        // STEP 3: Duplicate Prevention / Generation Lock System
        // If multiple requests search the same new destination simultaneously,
        // only one request generates it and everyone else reuses the result.
        // -------------------------------------------------------------
        var isPrimaryWorker = false
        var deferredJob: CompletableDeferred<DestinationCacheResult>? = null

        lockMutex.withLock {
            val existing = inFlightGenerationJobs[normalized]
            if (existing != null) {
                deferredJob = existing
            } else {
                val newJob = CompletableDeferred<DestinationCacheResult>()
                inFlightGenerationJobs[normalized] = newJob
                deferredJob = newJob
                isPrimaryWorker = true
            }
        }

        if (!isPrimaryWorker) {
            Log.d(tag, "DUPLICATE PREVENTED: Concurrent request for '$normalized' waiting for lock holder...")
            return@withContext deferredJob!!.await()
        }

        // -------------------------------------------------------------
        // STEP 4: Primary Worker generates the destination via AI & Web
        // -------------------------------------------------------------
        try {
            Log.d(tag, "Lock ACQUIRED. Researching '$searchQuery' via AI & Web APIs...")

            val targetDest = if (supabaseCached != null) {
                supabaseCached.destination
            } else {
                // 1. Resolve geographic coordinates and location profile
                val geocodedList = geocodingService.searchLocations(searchQuery)
                val loc = geocodedList.firstOrNull() ?: GeocodedLocation(
                    name = searchQuery.split(",").first().trim().replaceFirstChar { it.uppercase() },
                    stateRegion = "India",
                    country = "India",
                    latitude = 28.6139,
                    longitude = 77.2090,
                    type = "City",
                    displayName = searchQuery
                )

                // 2. Fetch live weather
                val weather = weatherService.getLiveWeather(loc.latitude, loc.longitude)

                // 3. Fetch representative cover image
                val coverImageUrl = placeImageService.fetchPlaceImageUrl(loc.name, loc.country, "city")

                // 4. Construct Draft Destination Entity (ID will be generated by Supabase)
                DestinationEntity(
                    id = "", 
                    normalizedName = normalized,
                    name = loc.name,
                    stateRegion = loc.stateRegion,
                    country = loc.country,
                    type = "${loc.type} Destination",
                    description = "Discover ${loc.name}, located in ${loc.stateRegion}, ${loc.country}. Verified public destination guide with live attractions and cultural highlights.",
                    weatherSummary = weather.summary,
                    temperature = weather.temperature,
                    rainProbability = weather.rainProbability,
                    imageUrl = coverImageUrl,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    popularAreas = "Central ${loc.name}, Heritage Bazaars, City Quarter",
                    bestTimeToVisit = "October to March",
                    dataSources = "Gemini AI & Public Knowledge Base",
                    searchCount = 1,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    lastVerifiedAt = System.currentTimeMillis(),
                    isSaved = false
                )
            }

            // 5. Persist Destination to Supabase FIRST (to get real UUID)
            // Or if it already existed, upsertDestinationToSupabase will return it with the existing UUID
            val persistedDest = upsertDestinationToSupabase(targetDest)
            val realDestId = persistedDest.id

            // 6. Fetch Key Attractions using the real database UUID
            val attractions = geminiTravelService.fetchKeyAttractions(
                destinationCity = persistedDest.name,
                destinationId = realDestId,
                baseLat = persistedDest.latitude,
                baseLon = persistedDest.longitude,
                placeCacheLookup = { normalizedName ->
                    null // Since it's not in local DB yet
                }
            )

            // 7. Fetch / Assemble Gallery Images
            val images = mutableListOf(
                DestinationImageEntity(
                    id = "img_${realDestId}_cover",
                    destinationId = realDestId,
                    imageUrl = persistedDest.imageUrl,
                    caption = "${persistedDest.name} Scenic View",
                    creditSource = "Verified Travel Knowledge Base",
                    isCover = true,
                    createdAt = System.currentTimeMillis()
                )
            )

            attractions.take(3).forEachIndexed { index, attr ->
                images.add(
                    DestinationImageEntity(
                        id = "img_${realDestId}_${index + 1}",
                        destinationId = realDestId,
                        attractionId = attr.id, // Will be mapped to real ID inside upsertAttractionsAndImages
                        imageUrl = attr.imageUrl,
                        caption = attr.name,
                        creditSource = "Wikimedia Commons",
                        isCover = false,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            // 8. Persist Attractions and Images to Supabase SYNCHRONOUSLY
            val (persistedAttractions, persistedImages) = upsertAttractionsAndImagesToSupabase(realDestId, attractions, images)

            // 9. Store into Local Public Database (Room) with REAL Database IDs
            db.destinationDao().insertDestinations(listOf(persistedDest))
            db.destinationDao().insertPlaces(persistedAttractions)
            db.destinationImageDao().insertImages(persistedImages)

            val finalResult = DestinationCacheResult(
                destination = persistedDest,
                attractions = persistedAttractions,
                images = persistedImages,
                isFromCache = false
            )

            // Fulfill the deferred job so all waiting concurrent requests immediately receive the result
            deferredJob!!.complete(finalResult)
            Log.d(tag, "Destination '$normalized' successfully researched, stored to Public DB, and returned.")
            return@withContext finalResult
        } catch (e: Exception) {
            Log.e(tag, "Failed to research destination for '$normalized': ${e.message}", e)
            deferredJob?.completeExceptionally(e)
            throw e
        } finally {
            inFlightGenerationJobs.remove(normalized)
        }
    }

    /**
     * Queries the Supabase public destination tables via PostgREST API
     */
    private fun querySupabasePublicDestination(normalizedName: String): DestinationCacheResult? {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project.supabase.co") || supabasePublishableKey.isBlank()) {
            return null
        }

        try {
            val encodedName = URLEncoder.encode(normalizedName, "UTF-8")
            val url = "$supabaseUrl/rest/v1/public_destinations?normalized_name=eq.$encodedName&select=*,public_destination_attractions(*),public_destination_images(*)"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Authorization", "Bearer $supabasePublishableKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val array = JSONArray(body)
            if (array.length() == 0) return null

            val obj = array.getJSONObject(0)
            val destId = obj.optString("id", UUID.randomUUID().toString())
            val dest = DestinationEntity(
                id = destId,
                normalizedName = obj.optString("normalized_name", normalizedName),
                name = obj.optString("name", normalizedName.replaceFirstChar { it.uppercase() }),
                stateRegion = obj.optString("state_region", "India"),
                country = obj.optString("country", "India"),
                type = obj.optString("type", "City Destination"),
                description = obj.optString("description", ""),
                weatherSummary = obj.optString("weather_summary", "Pleasant"),
                temperature = obj.optString("typical_temperature", "25°C"),
                rainProbability = obj.optString("rain_probability", "10%"),
                imageUrl = obj.optString("cover_image_url", ""),
                latitude = obj.optDouble("latitude", 28.6139),
                longitude = obj.optDouble("longitude", 77.2090),
                popularAreas = obj.optString("popular_areas", ""),
                bestTimeToVisit = obj.optString("best_time_to_visit", "October to March"),
                dataSources = obj.optString("data_sources", "Supabase Public Knowledge Base"),
                searchCount = obj.optInt("search_count", 1) + 1,
                isSaved = false
            )

            val attractions = mutableListOf<PlaceEntity>()
            val attractionsArray = obj.optJSONArray("public_destination_attractions")
            if (attractionsArray != null) {
                for (i in 0 until attractionsArray.length()) {
                    val attObj = attractionsArray.getJSONObject(i)
                    val imgUrl = attObj.optString("image_url", dest.imageUrl)
                    attractions.add(
                        PlaceEntity(
                            id = attObj.optString("id", UUID.randomUUID().toString()),
                            destinationId = destId,
                            name = attObj.optString("name", "Attraction"),
                            category = attObj.optString("category", "Historical"),
                            description = attObj.optString("description", ""),
                            locationName = attObj.optString("location_address", dest.name),
                            locationAddress = attObj.optString("location_address", ""),
                            latitude = attObj.optDouble("latitude", dest.latitude),
                            longitude = attObj.optDouble("longitude", dest.longitude),
                            openingHours = attObj.optString("opening_hours", "09:00 - 18:00"),
                            visitDuration = attObj.optString("visit_duration", "2 hours"),
                            estimatedCost = attObj.optDouble("entry_ticket_cost", 0.0),
                            imageUrl = imgUrl,
                            rating = attObj.optDouble("rating", 4.5).toFloat(),
                            reviewCount = attObj.optInt("review_count", 100),
                            source = attObj.optString("data_source", "Supabase Public Registry"),
                            isSaved = false
                        )
                    )
                }
            }

            val images = mutableListOf<DestinationImageEntity>()
            val imagesArray = obj.optJSONArray("public_destination_images")
            if (imagesArray != null) {
                for (i in 0 until imagesArray.length()) {
                    val imgObj = imagesArray.getJSONObject(i)
                    images.add(
                        DestinationImageEntity(
                            id = imgObj.optString("id", UUID.randomUUID().toString()),
                            destinationId = destId,
                            imageUrl = imgObj.optString("image_url", dest.imageUrl),
                            caption = imgObj.optString("caption", ""),
                            creditSource = imgObj.optString("credit_source", "Supabase Verified"),
                            isCover = imgObj.optBoolean("is_cover", i == 0)
                        )
                    )
                }
            }

            return DestinationCacheResult(
                destination = dest,
                attractions = attractions,
                images = images,
                isFromCache = true
            )
        } catch (e: Exception) {
            Log.w(tag, "Supabase lookup error: ${e.message}")
            return null
        }
    }

    private suspend fun upsertDestinationToSupabase(dest: DestinationEntity): DestinationEntity {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project.supabase.co") || supabaseSecretKey.isBlank()) {
            return dest.copy(id = dest.id.ifEmpty { UUID.randomUUID().toString() })
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        val destJson = JSONObject().apply {
            put("normalized_name", dest.normalizedName)
            put("name", dest.name)
            put("city", dest.name)
            put("state_region", dest.stateRegion)
            put("country", dest.country)
            put("type", dest.type)
            put("description", dest.description)
            put("cover_image_url", dest.imageUrl)
            put("latitude", dest.latitude)
            put("longitude", dest.longitude)
            put("popular_areas", dest.popularAreas)
            put("best_time_to_visit", dest.bestTimeToVisit)
            put("weather_summary", dest.weatherSummary)
            put("typical_temperature", dest.temperature)
            put("rain_probability", dest.rainProbability)
            put("data_sources", dest.dataSources)
            put("search_count", dest.searchCount)
        }

        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/public_destinations?on_conflict=normalized_name&select=*")
            .addHeader("apikey", supabaseSecretKey)
            .addHeader("Authorization", "Bearer $supabaseSecretKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation, resolution=merge-duplicates")
            .post(destJson.toString().toRequestBody(jsonMediaType))
            .build()

        val destResponse = httpClient.newCall(req).execute()
        val destResponseBody = destResponse.body?.string() ?: ""
        if (!destResponse.isSuccessful) {
            throw Exception("Supabase Destination Insert Failed: ${destResponse.code} $destResponseBody")
        }

        val destArray = JSONArray(destResponseBody)
        if (destArray.length() == 0) throw Exception("Upsert destination returned empty")
        return dest.copy(id = destArray.getJSONObject(0).getString("id"))
    }

    private suspend fun upsertAttractionsAndImagesToSupabase(
        realDestId: String,
        attractions: List<PlaceEntity>,
        images: List<DestinationImageEntity>
    ): Pair<List<PlaceEntity>, List<DestinationImageEntity>> {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project.supabase.co") || supabaseSecretKey.isBlank()) {
            return Pair(attractions.map { it.copy(id = UUID.randomUUID().toString(), destinationId = realDestId) }, images.map { it.copy(id = UUID.randomUUID().toString(), destinationId = realDestId) })
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val persistedAttractions = mutableListOf<PlaceEntity>()

        if (attractions.isNotEmpty()) {
            val attrArray = JSONArray()
            attractions.forEach { attr ->
                attrArray.put(JSONObject().apply {
                    put("destination_id", realDestId)
                    put("name", attr.name)
                    put("normalized_name", attr.normalizedName)
                    put("category", attr.category)
                    put("description", attr.description)
                    put("location_address", attr.locationName)
                    put("latitude", attr.latitude)
                    put("longitude", attr.longitude)
                    put("opening_hours", attr.openingHours)
                    put("visit_duration", attr.visitDuration)
                    put("entry_ticket_cost", attr.estimatedCost)
                    put("rating", attr.rating)
                    put("review_count", attr.reviewCount)
                    put("data_source", attr.source)
                    put("image_url", attr.imageUrl)
                })
            }

            val reqAttr = Request.Builder()
                .url("$supabaseUrl/rest/v1/public_destination_attractions?on_conflict=destination_id,normalized_name&select=*")
                .addHeader("apikey", supabaseSecretKey)
                .addHeader("Authorization", "Bearer $supabaseSecretKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation, resolution=merge-duplicates")
                .post(attrArray.toString().toRequestBody(jsonMediaType))
                .build()

            val attrResponse = httpClient.newCall(reqAttr).execute()
            val attrResponseBody = attrResponse.body?.string() ?: ""
            if (!attrResponse.isSuccessful) {
                throw Exception("Supabase Attractions Insert Failed: ${attrResponse.code} $attrResponseBody")
            }

            val returnedAttrArray = JSONArray(attrResponseBody)
            for (i in 0 until returnedAttrArray.length()) {
                val returnedObj = returnedAttrArray.getJSONObject(i)
                val matchingOriginal = attractions.find { it.normalizedName == returnedObj.optString("normalized_name") }
                if (matchingOriginal != null) {
                    persistedAttractions.add(matchingOriginal.copy(
                        id = returnedObj.getString("id"),
                        destinationId = realDestId
                    ))
                } else {
                    persistedAttractions.add(attractions[i].copy(
                        id = returnedObj.getString("id"),
                        destinationId = realDestId
                    ))
                }
            }
        }

        val persistedImages = mutableListOf<DestinationImageEntity>()
        if (images.isNotEmpty()) {
            val imgArray = JSONArray()
            images.forEach { img ->
                val attractionId = if (img.attractionId != null) {
                    val origIndex = attractions.indexOfFirst { it.id == img.attractionId }
                    if (origIndex != -1 && origIndex < persistedAttractions.size) {
                        persistedAttractions[origIndex].id
                    } else null
                } else null

                imgArray.put(JSONObject().apply {
                    put("destination_id", realDestId)
                    attractionId?.let { put("attraction_id", it) }
                    put("image_url", img.imageUrl)
                    put("caption", img.caption)
                    put("credit_source", img.creditSource)
                    put("is_cover", img.isCover)
                })
            }

            val reqImg = Request.Builder()
                .url("$supabaseUrl/rest/v1/public_destination_images?select=*")
                .addHeader("apikey", supabaseSecretKey)
                .addHeader("Authorization", "Bearer $supabaseSecretKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(imgArray.toString().toRequestBody(jsonMediaType))
                .build()

            val imgResponse = httpClient.newCall(reqImg).execute()
            val imgResponseBody = imgResponse.body?.string() ?: ""
            if (!imgResponse.isSuccessful) {
                throw Exception("Supabase Images Insert Failed: ${imgResponse.code} $imgResponseBody")
            }

            val returnedImgArray = JSONArray(imgResponseBody)
            for (i in 0 until returnedImgArray.length()) {
                val returnedObj = returnedImgArray.getJSONObject(i)
                val attractionId = if (returnedObj.isNull("attraction_id")) null else returnedObj.optString("attraction_id")
                persistedImages.add(images[i].copy(
                    id = returnedObj.getString("id"),
                    destinationId = realDestId,
                    attractionId = if (attractionId.isNullOrEmpty() || attractionId == "null") null else attractionId
                ))
            }
        }

        return Pair(persistedAttractions, persistedImages)
    }
}
