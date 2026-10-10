import re

file_path = "app/src/main/java/com/example/data/remote/SupabaseDestinationCacheService.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update querySupabasePublicDestination to return empty attractions if they are missing
# Actually, the existing querySupabasePublicDestination already does this:
# it returns the DestinationCacheResult with empty attractions if none are found.
# Let's intercept STEP 2 in getOrResearchDestination.

# Replace STEP 2 block
step2_old = """        // -------------------------------------------------------------
        // STEP 2: Check Remote Supabase Public Destination Database
        // -------------------------------------------------------------
        val supabaseCached = querySupabasePublicDestination(normalized)
        if (supabaseCached != null) {
            Log.d(tag, "Cache HIT in Supabase Public DB for '$normalized'. Saving to local cache. NO AI CALL MADE.")
            // Cache locally for instantaneous access
            db.destinationDao().insertDestinations(listOf(supabaseCached.destination))
            db.destinationDao().insertPlaces(supabaseCached.attractions)
            db.destinationImageDao().insertImages(supabaseCached.images)
            if (supabaseCached.hotels.isNotEmpty()) {
                db.travelSearchDao().insertHotelOptions(supabaseCached.hotels)
            }

            return@withContext supabaseCached
        }"""

step2_new = """        // -------------------------------------------------------------
        // STEP 2: Check Remote Supabase Public Destination Database
        // -------------------------------------------------------------
        val supabaseCached = querySupabasePublicDestination(normalized)
        if (supabaseCached != null) {
            if (supabaseCached.attractions.isEmpty()) {
                Log.d(tag, "Destination '${normalized}' exists in Supabase, but attractions are missing. Generating missing data...")
                // We will let the flow continue to generate attractions using the EXISTING destination ID.
                // We just break out of this if-block and jump to the AI generation block.
            } else {
                Log.d(tag, "Cache HIT in Supabase Public DB for '$normalized'. Saving to local cache. NO AI CALL MADE.")
                // Cache locally for instantaneous access
                db.destinationDao().insertDestinations(listOf(supabaseCached.destination))
                db.destinationDao().insertPlaces(supabaseCached.attractions)
                db.destinationImageDao().insertImages(supabaseCached.images)
                if (supabaseCached.hotels.isNotEmpty()) {
                    db.travelSearchDao().insertHotelOptions(supabaseCached.hotels)
                }
                return@withContext supabaseCached
            }
        }"""

content = content.replace(step2_old, step2_new)


# Replace the end of the AI generation block from `try {` to the end of `try` block
step4_old = """        try {
            Log.d(tag, "Lock ACQUIRED. Researching '$searchQuery' via AI & Web APIs...")

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

            val destId = UUID.randomUUID().toString()

            // 2. Fetch live weather
            val weather = weatherService.getLiveWeather(loc.latitude, loc.longitude)

            // 3. Fetch representative cover image
            val coverImageUrl = placeImageService.fetchPlaceImageUrl(loc.name, loc.country, "city")

            // 4. Construct Public Destination Entity
            val newDestination = DestinationEntity(
                id = destId,
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

            // 5. Fetch Key Attractions with categories, opening hours, ticket costs, ratings
            val attractions = geminiTravelService.fetchKeyAttractions(
                destinationCity = loc.name,
                destinationId = destId,
                baseLat = loc.latitude,
                baseLon = loc.longitude,
                placeCacheLookup = { normalizedName ->
                    db.destinationDao().getPlaceByNormalizedName(destId, normalizedName)
                }
            )

            // 6. Fetch / Assemble Gallery Images
            val images = mutableListOf(
                DestinationImageEntity(
                    id = "img_${destId}_cover",
                    destinationId = destId,
                    imageUrl = coverImageUrl,
                    caption = "${loc.name} Scenic View",
                    creditSource = "Verified Travel Knowledge Base",
                    isCover = true,
                    createdAt = System.currentTimeMillis()
                )
            )

            attractions.take(3).forEachIndexed { index, attr ->
                images.add(
                    DestinationImageEntity(
                        id = "img_${destId}_${index + 1}",
                        destinationId = destId,
                        attractionId = attr.id,
                        imageUrl = attr.imageUrl,
                        caption = attr.name,
                        creditSource = "Wikimedia Commons",
                        isCover = false,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            // 7. Store into Public Database (Room)
            db.destinationDao().insertDestinations(listOf(newDestination))
            db.destinationDao().insertPlaces(attractions)
            db.destinationImageDao().insertImages(images)

            // 8. Publish to Supabase Public Database in background
            publishToSupabase(newDestination, attractions, images)

            val finalResult = DestinationCacheResult(
                destination = newDestination,
                attractions = attractions,
                images = images,
                isFromCache = false
            )

            // Fulfill the deferred job so all waiting concurrent requests immediately receive the result
            deferredJob!!.complete(finalResult)
            Log.d(tag, "Destination '$normalized' successfully researched, stored to Public DB, and returned.")
            return@withContext finalResult
        } catch (e: Exception) {"""

step4_new = """        try {
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
        } catch (e: Exception) {"""

content = content.replace(step4_old, step4_new)


# Replace publishToSupabase block entirely with the new helper functions
publish_old = """    /**
     * Publishes newly researched public destination data to Supabase
     */
    private fun publishToSupabase("""

content = content[:content.find(publish_old)]

# We will append the new functions at the end (before the final closing brace)
# The final closing brace is at the end of the content.
content = content.rstrip()
if content.endswith("}"):
    content = content[:-1]


new_helpers = """
    private suspend fun upsertDestinationToSupabase(dest: DestinationEntity): DestinationEntity {
        if (supabaseUrl.contains("your-project.supabase.co") || supabaseSecretKey.isBlank()) {
            return dest.copy(id = if (dest.id.isEmpty()) UUID.randomUUID().toString() else dest.id)
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
        if (supabaseUrl.contains("your-project.supabase.co") || supabaseSecretKey.isBlank()) {
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
                val attractionId = returnedObj.optString("attraction_id", null)
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
"""

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content + new_helpers)

print("SUCCESS")
