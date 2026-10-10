package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class RouteStep(
    val instruction: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val streetName: String
)

data class RouteResult(
    val success: Boolean,
    val profile: String,
    val totalDistanceKm: Double,
    val totalDurationMinutes: Int,
    val coordinates: List<Pair<Double, Double>>, // List of (latitude, longitude)
    val steps: List<RouteStep>,
    val rawGeoJson: String?,
    val errorMessage: String? = null,
    val isFallback: Boolean = false
)

object OpenRouteServiceConfig {
    private const val PREFS_NAME = "openrouteservice_prefs"
    private const val KEY_API_TOKEN = "ors_api_token"

    fun getApiKey(context: Context?): String {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedToken = prefs.getString(KEY_API_TOKEN, "")?.trim() ?: ""
            if (savedToken.isNotBlank()) return savedToken
        }
        // Fallback to BuildConfig if injected via .env
        return try {
            val field = BuildConfig::class.java.getField("OPENROUTESERVICE_API_KEY")
            (field.get(null) as? String)?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun saveApiKey(context: Context, token: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_TOKEN, token.trim()).apply()
    }
}

/**
 * Direct client for the Openrouteservice Directions API (openrouteservice.org).
 * Fetches real road routing geometry (GeoJSON LineString), turn-by-turn steps,
 * driving/walking travel times and distances between stops.
 */
class OpenRouteService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val PROFILE_DRIVING = "driving-car"
        const val PROFILE_WALKING = "foot-walking"
        const val PROFILE_CYCLING = "cycling-regular"
        private const val BASE_URL = "https://api.openrouteservice.org/v2/directions"
        private const val TAG = "OpenRouteService"
    }

    // In-memory cache to prevent redundant quota usage
    private val routeCache = ConcurrentHashMap<String, RouteResult>()

    /**
     * Fetches real road directions between a sequence of waypoints (latitude, longitude).
     * Automatically handles GeoJSON coordinate conversion [lon, lat], instructions extraction,
     * and fallback geodesic paths when the API key is not configured or network fails.
     */
    suspend fun fetchRoute(
        waypoints: List<Pair<Double, Double>>, // (lat, lon)
        profile: String = PROFILE_DRIVING,
        apiKey: String = ""
    ): RouteResult = withContext(Dispatchers.IO) {
        val validPoints = waypoints.filter { it.first != 0.0 && it.second != 0.0 }
        if (validPoints.size < 2) {
            return@withContext RouteResult(
                success = true,
                profile = profile,
                totalDistanceKm = 0.0,
                totalDurationMinutes = 0,
                coordinates = validPoints,
                steps = emptyList(),
                rawGeoJson = null,
                isFallback = true
            )
        }

        val cacheKey = "$profile:${validPoints.joinToString(";") { "${it.first},${it.second}" }}"
        routeCache[cacheKey]?.let { return@withContext it }

        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            val fallback = createGeodesicFallback(
                validPoints, profile,
                "OpenRouteService token not configured. Showing direct coordinate paths."
            )
            return@withContext fallback
        }

        try {
            val url = "$BASE_URL/$profile/geojson"
            val jsonBody = JSONObject().apply {
                val coordsArray = JSONArray()
                validPoints.forEach { pt ->
                    // Openrouteservice expects [longitude, latitude] in GeoJSON
                    val pair = JSONArray().apply {
                        put(pt.second)
                        put(pt.first)
                    }
                    coordsArray.put(pair)
                }
                put("coordinates", coordsArray)
                put("instructions", true)
                put("elevation", false)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", cleanKey)
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("Accept", "application/json, application/geo+json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "ORS API request failed: HTTP ${response.code} -> $responseString")
                val errMsg = try {
                    val errJson = JSONObject(responseString)
                    errJson.optJSONObject("error")?.optString("message")
                        ?: "HTTP ${response.code} from Openrouteservice"
                } catch (_: Exception) {
                    "HTTP ${response.code}: Openrouteservice request failed"
                }
                val fallback = createGeodesicFallback(validPoints, profile, errMsg)
                return@withContext fallback
            }

            val root = JSONObject(responseString)
            val features = root.optJSONArray("features")
            if (features == null || features.length() == 0) {
                return@withContext createGeodesicFallback(validPoints, profile, "No route geometry returned")
            }

            val feature = features.getJSONObject(0)
            val geometry = feature.getJSONObject("geometry")
            val coordArray = geometry.getJSONArray("coordinates")

            val polylineCoordinates = mutableListOf<Pair<Double, Double>>()
            for (i in 0 until coordArray.length()) {
                val pt = coordArray.getJSONArray(i)
                val lon = pt.getDouble(0)
                val lat = pt.getDouble(1)
                polylineCoordinates.add(Pair(lat, lon))
            }

            val properties = feature.getJSONObject("properties")
            val summary = properties.optJSONObject("summary")
            val totalDistanceMeters = summary?.optDouble("distance", 0.0) ?: 0.0
            val totalDurationSeconds = summary?.optDouble("duration", 0.0) ?: 0.0

            val stepsList = mutableListOf<RouteStep>()
            val segments = properties.optJSONArray("segments")
            if (segments != null) {
                for (s in 0 until segments.length()) {
                    val segObj = segments.getJSONObject(s)
                    val stepsArray = segObj.optJSONArray("steps")
                    if (stepsArray != null) {
                        for (st in 0 until stepsArray.length()) {
                            val stepObj = stepsArray.getJSONObject(st)
                            val dist = (stepObj.optDouble("distance", 0.0) / 1000.0)
                            val dur = (stepObj.optDouble("duration", 0.0) / 60.0).roundToInt()
                            val inst = stepObj.optString("instruction", "")
                            val name = stepObj.optString("name", "Road")
                            if (inst.isNotBlank()) {
                                stepsList.add(RouteStep(inst, dist, dur, name))
                            }
                        }
                    }
                }
            }

            val result = RouteResult(
                success = true,
                profile = profile,
                totalDistanceKm = totalDistanceMeters / 1000.0,
                totalDurationMinutes = (totalDurationSeconds / 60.0).roundToInt(),
                coordinates = polylineCoordinates,
                steps = stepsList,
                rawGeoJson = responseString,
                isFallback = false
            )

            routeCache[cacheKey] = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Openrouteservice: ${e.message}", e)
            createGeodesicFallback(validPoints, profile, e.message ?: "Network error calling Openrouteservice")
        }
    }

    private fun createGeodesicFallback(
        waypoints: List<Pair<Double, Double>>,
        profile: String,
        errorMessage: String
    ): RouteResult {
        var totalDist = 0.0
        val steps = mutableListOf<RouteStep>()

        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val dist = com.example.domain.ai.TravelScheduleUtils.calculateDistanceKm(
                a.first, a.second, b.first, b.second
            )
            totalDist += dist
            steps.add(
                RouteStep(
                    instruction = "Proceed to Stop ${i + 2}",
                    distanceKm = dist,
                    durationMinutes = com.example.domain.ai.TravelScheduleUtils.estimateTravelMinutes(dist),
                    streetName = "Way to Stop ${i + 2}"
                )
            )
        }

        return RouteResult(
            success = true,
            profile = profile,
            totalDistanceKm = totalDist,
            totalDurationMinutes = com.example.domain.ai.TravelScheduleUtils.estimateTravelMinutes(totalDist),
            coordinates = waypoints,
            steps = steps,
            rawGeoJson = null,
            errorMessage = errorMessage,
            isFallback = true
        )
    }
}
