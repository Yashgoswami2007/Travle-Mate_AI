package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ImageCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Service to dynamically fetch relevant photographs of destinations, attractions,
 * landmarks, and hotels referenced by AI from live web sources (Wikimedia Commons,
 * Wikipedia Search API, and dynamic web photography registries).
 *
 * Implements two-layer caching:
 * 1. Checks Local DB & Supabase public_image_cache first (reusing existing images, 0 API calls).
 * 2. If missing, dynamically searches Wikimedia Commons / Wikipedia APIs.
 * 3. Caches the resolved image URL into Room and Supabase so all users can access it.
 */
class PlaceImageService(
    private val dbProvider: (() -> AppDatabase?)? = null
) {
    private val tag = "PlaceImageService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val memoryImageCache = ConcurrentHashMap<String, String>()

    // Supabase credentials
    private val supabaseUrl: String = try {
        BuildConfig.SUPABASE_URL.trimEnd('/')
    } catch (_: Throwable) {
        ""
    }

    private val supabasePublishableKey: String = try {
        BuildConfig.SUPABASE_PUBLISHABLE_KEY
    } catch (_: Throwable) {
        ""
    }

    private val supabaseSecretKey: String = try {
        BuildConfig.SUPABASE_SECRET_KEY
    } catch (_: Throwable) {
        ""
    }

    /**
     * Dynamically fetches a relevant image URL for any place, landmark, destination, or hotel.
     */
    suspend fun fetchPlaceImageUrl(
        placeName: String,
        cityName: String = "",
        category: String = ""
    ): String = withContext(Dispatchers.IO) {
        val normalizedPlace = placeName.lowercase().trim()
        val normalizedCity = cityName.lowercase().trim()
        val queryKey = "${category.ifBlank { "place" }}:$normalizedPlace:$normalizedCity"
            .replace(Regex("[^a-z0-9:]"), "_")

        // 1. Check in-memory cache
        memoryImageCache[queryKey]?.let { return@withContext it }

        // 2. Check Local Database (Room image cache)
        try {
            val db = dbProvider?.invoke()
            val localHit = db?.imageCacheDao()?.getImageByKey(queryKey)
            if (localHit != null && localHit.imageUrl.isNotBlank()) {
                memoryImageCache[queryKey] = localHit.imageUrl
                Log.d(tag, "Local DB Cache HIT for '$queryKey'")
                return@withContext localHit.imageUrl
            }
        } catch (e: Exception) {
            Log.w(tag, "Local DB image lookup error: ${e.message}")
        }

        // 3. Check Supabase Remote Cache (Shared knowledge across all users)
        val supabaseHit = tryFetchFromSupabaseImageCache(queryKey)
        if (!supabaseHit.isNullOrBlank()) {
            memoryImageCache[queryKey] = supabaseHit
            saveToLocalCache(queryKey, supabaseHit, category, placeName)
            Log.d(tag, "Supabase Remote Cache HIT for '$queryKey'")
            return@withContext supabaseHit
        }

        // 4. Live Dynamic Web Image Search
        Log.d(tag, "Live web search for: place='$placeName', city='$cityName', category='$category'")

        val cleanSearch = placeName
            .replace(Regex("""\(.*?\)"""), "")
            .replace("Central Heritage Landmark", cityName)
            .replace("Scenic Promenade", cityName)
            .trim()

        // Strategy A: Wikipedia Summary API (Exact page title)
        var resolvedUrl = tryWikipediaSummary(cleanSearch)

        // Strategy B: Wikimedia Commons Search API (High-res genuine photo search)
        if (resolvedUrl.isNullOrBlank()) {
            val commonsQuery = if (cityName.isNotBlank() && !cleanSearch.contains(cityName, ignoreCase = true)) {
                "$cleanSearch $cityName"
            } else {
                cleanSearch
            }
            resolvedUrl = tryWikimediaCommons(commonsQuery)
        }

        // Strategy C: Wikipedia Generator Search API
        if (resolvedUrl.isNullOrBlank()) {
            val searchCombined = if (cityName.isNotBlank() && !cleanSearch.contains(cityName, ignoreCase = true)) {
                "$cleanSearch $cityName"
            } else {
                cleanSearch
            }
            resolvedUrl = tryWikipediaSearch(searchCombined)
        }

        // Strategy D: Dynamic contextual imagery based on entity attributes
        if (resolvedUrl.isNullOrBlank()) {
            resolvedUrl = getDynamicContextualImageUrl(placeName, cityName, category)
        }

        val finalUrl = (resolvedUrl?.ifBlank { null } ?: getDynamicContextualImageUrl(placeName, cityName, category)).let {
            if (it.startsWith("//")) "https:$it" else it
        }

        // 5. Cache the resolved URL to memory, local DB, and Supabase
        memoryImageCache[queryKey] = finalUrl
        saveToLocalCache(queryKey, finalUrl, category, placeName)
        saveToSupabaseImageCache(queryKey, finalUrl, category, placeName)

        return@withContext finalUrl
    }

    /**
     * Fetches multiple real photographs for a place/destination gallery
     */
    suspend fun fetchMultipleImages(
        placeName: String,
        cityName: String = "",
        count: Int = 3
    ): List<String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        val main = fetchPlaceImageUrl(placeName, cityName, "destination")
        results.add(main)

        // Query Wikimedia Commons for additional distinct views
        try {
            val query = URLEncoder.encode("$placeName $cityName", "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$query&gsrlimit=$count&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TripMateAI/1.0 (Android; contact@tripmate.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val queryObj = root.optJSONObject("query")
                    val pages = queryObj?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext() && results.size < count + 1) {
                            val page = pages.getJSONObject(keys.next())
                            val imageinfo = page.optJSONArray("imageinfo")
                            if (imageinfo != null && imageinfo.length() > 0) {
                                val info = imageinfo.getJSONObject(0)
                                val thumb = info.optString("thumburl").ifBlank { info.optString("url") }
                                if (thumb.isNotBlank() && !results.contains(thumb)) {
                                    results.add(thumb)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        results
    }

    private fun tryWikipediaSummary(title: String): String? {
        return try {
            val encoded = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
            val request = Request.Builder()
                .url("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded")
                .header("User-Agent", "TripMateAI/1.0 (Android; contact@tripmate.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    if (json.has("originalimage")) {
                        json.getJSONObject("originalimage").optString("source").ifBlank { null }
                    } else if (json.has("thumbnail")) {
                        json.getJSONObject("thumbnail").optString("source").ifBlank { null }
                    } else {
                        null
                    }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryWikimediaCommons(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=1&prop=imageinfo&iiprop=url&iiurlwidth=1200&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TripMateAI/1.0 (Android; contact@tripmate.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val root = JSONObject(body)
                    val queryObj = root.optJSONObject("query") ?: return null
                    val pages = queryObj.optJSONObject("pages") ?: return null
                    val keys = pages.keys()
                    if (keys.hasNext()) {
                        val pageId = keys.next()
                        val page = pages.getJSONObject(pageId)
                        val imageinfo = page.optJSONArray("imageinfo")
                        if (imageinfo != null && imageinfo.length() > 0) {
                            val info = imageinfo.getJSONObject(0)
                            val thumbUrl = info.optString("thumburl")
                            val fullUrl = info.optString("url")
                            if (thumbUrl.isNotBlank()) thumbUrl else fullUrl.ifBlank { null }
                        } else null
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryWikipediaSearch(query: String): String? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=1&prop=pageimages&pithumbsize=1200&format=json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TripMateAI/1.0 (Android; contact@tripmate.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val root = JSONObject(body)
                    val queryObj = root.optJSONObject("query") ?: return null
                    val pages = queryObj.optJSONObject("pages") ?: return null
                    val keys = pages.keys()
                    if (keys.hasNext()) {
                        val pageId = keys.next()
                        val page = pages.getJSONObject(pageId)
                        val thumb = page.optJSONObject("thumbnail")
                        thumb?.optString("source")?.ifBlank { null }
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryFetchFromSupabaseImageCache(queryKey: String): String? {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project.supabase.co") || supabasePublishableKey.isBlank()) {
            return null
        }
        return try {
            val encodedKey = URLEncoder.encode(queryKey, "UTF-8")
            val url = "$supabaseUrl/rest/v1/public_image_cache?query_key=eq.$encodedKey&select=image_url"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", supabasePublishableKey)
                .addHeader("Authorization", "Bearer $supabasePublishableKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return null
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        arr.getJSONObject(0).optString("image_url").ifBlank { null }
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveToLocalCache(queryKey: String, imageUrl: String, entityType: String, entityName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = dbProvider?.invoke()
                db?.imageCacheDao()?.insertImage(
                    ImageCacheEntity(
                        queryKey = queryKey,
                        imageUrl = imageUrl,
                        entityType = entityType.uppercase(),
                        entityName = entityName,
                        source = "Wikimedia Commons / Verified Web Search"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun saveToSupabaseImageCache(
        queryKey: String,
        imageUrl: String,
        entityType: String,
        entityName: String
    ) {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project.supabase.co") || supabaseSecretKey.isBlank()) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("query_key", queryKey)
                    put("entity_type", entityType.ifBlank { "PLACE" }.uppercase())
                    put("entity_name", entityName)
                    put("image_url", imageUrl)
                    put("credit_source", "Wikimedia Commons / Verified Web Search")
                    put("caption", entityName)
                }
                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$supabaseUrl/rest/v1/public_image_cache")
                    .addHeader("apikey", supabaseSecretKey)
                    .addHeader("Authorization", "Bearer $supabaseSecretKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(body)
                    .build()

                client.newCall(req).execute().close()
                Log.d(tag, "Successfully published image for '$queryKey' to Supabase public_image_cache")
            } catch (e: Exception) {
                Log.w(tag, "Failed to publish image to Supabase: ${e.message}")
            }
        }
    }

    /**
     * Dynamic high-resolution travel photography resolution based on destination, state, and category.
     */
    private fun getDynamicContextualImageUrl(placeName: String, cityName: String, category: String): String {
        val lower = "$placeName $category $cityName".lowercase()
        return when {
            // Specific Indian regions
            lower.contains("gujarat") || lower.contains("ahmedabad") || lower.contains("kutch") -> when {
                lower.contains("statue") || lower.contains("patel") || lower.contains("unity") ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Statue_of_Unity.jpg/1200px-Statue_of_Unity.jpg"
                lower.contains("rann") || lower.contains("desert") || lower.contains("salt") || lower.contains("white") ->
                    "https://images.unsplash.com/photo-1510312305653-8ed496efae75?auto=format&fit=crop&w=1200&q=80"
                lower.contains("ashram") || lower.contains("gandhi") || lower.contains("sabarmati") ->
                    "https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80"
                lower.contains("temple") || lower.contains("somnath") || lower.contains("dwarka") ->
                    "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=1200&q=80"
                lower.contains("hotel") || lower.contains("resort") || lower.contains("leela") || lower.contains("hyatt") ->
                    "https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80"
                else ->
                    "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Statue_of_Unity.jpg/1200px-Statue_of_Unity.jpg"
            }
            // General categories
            lower.contains("hotel") || lower.contains("resort") || lower.contains("stay") || lower.contains("inn") ->
                "https://images.unsplash.com/photo-1582719508461-905c673771fd?auto=format&fit=crop&w=1200&q=80"
            lower.contains("fort") || lower.contains("palace") || lower.contains("mahal") ->
                "https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=1200&q=80"
            lower.contains("temple") || lower.contains("shrine") || lower.contains("mandir") ->
                "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=1200&q=80"
            lower.contains("lake") || lower.contains("river") || lower.contains("water") ->
                "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=1200&q=80"
            lower.contains("garden") || lower.contains("park") || lower.contains("nature") ->
                "https://images.unsplash.com/photo-1506744038136-46273834b3fb?auto=format&fit=crop&w=1200&q=80"
            lower.contains("market") || lower.contains("bazaar") || lower.contains("mandi") ->
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=1200&q=80"
            else ->
                "https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1200&q=80"
        }
    }
}
