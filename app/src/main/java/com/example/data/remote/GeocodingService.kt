package com.example.data.remote

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeocodedLocation(
    val name: String,
    val stateRegion: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    val displayName: String
)

class GeocodingService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun searchLocations(query: String): List<GeocodedLocation> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<GeocodedLocation>()

        // 1. Try Photon Geocoder API (Fast, OpenStreetMap based, no key needed)
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://photon.komoot.io/api/?q=$encodedQuery&limit=8"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TripMateAI-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonString = response.body?.string()
                if (!jsonString.isNullOrBlank()) {
                    val root = JSONObject(jsonString)
                    val features = root.optJSONArray("features") ?: JSONArray()
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val geometry = feature.optJSONObject("geometry")
                        val coords = geometry?.optJSONArray("coordinates")
                        val props = feature.optJSONObject("properties") ?: JSONObject()

                        val lon = coords?.optDouble(0) ?: 0.0
                        val lat = coords?.optDouble(1) ?: 0.0

                        val name = props.optString("name", "").ifBlank { props.optString("city", query) }
                        val state = props.optString("state", "").ifBlank { props.optString("county", "") }
                        val country = props.optString("country", "India")
                        val type = props.optString("osm_value", props.optString("type", "City"))

                        if (lat != 0.0 && lon != 0.0 && name.isNotBlank()) {
                            val displayName = buildString {
                                append(name)
                                if (state.isNotBlank()) append(", $state")
                                if (country.isNotBlank()) append(", $country")
                            }
                            results.add(
                                GeocodedLocation(
                                    name = name,
                                    stateRegion = state.ifBlank { name },
                                    country = country,
                                    latitude = lat,
                                    longitude = lon,
                                    type = type.replaceFirstChar { it.uppercase() },
                                    displayName = displayName
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Photon API failed, fallback to native Android Geocoder or Nominatim
        }

        // 2. Fallback to OpenStreetMap Nominatim API if results are empty
        if (results.isEmpty()) {
            try {
                val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=6"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "TripMateAI-Android-App/1.0 (travel@tripmate.ai)")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val lat = obj.optDouble("lat", 0.0)
                            val lon = obj.optDouble("lon", 0.0)
                            val address = obj.optJSONObject("address") ?: JSONObject()

                            val name = address.optString("city", "").ifBlank {
                                address.optString("town", "").ifBlank {
                                    address.optString("village", "").ifBlank {
                                        obj.optString("name", query)
                                    }
                                }
                            }
                            val state = address.optString("state", "").ifBlank { address.optString("state_district", "") }
                            val country = address.optString("country", "")
                            val type = obj.optString("type", "City")

                            if (lat != 0.0 && lon != 0.0) {
                                results.add(
                                    GeocodedLocation(
                                        name = name.ifBlank { query },
                                        stateRegion = state.ifBlank { name },
                                        country = country.ifBlank { "International" },
                                        latitude = lat,
                                        longitude = lon,
                                        type = type.replaceFirstChar { it.uppercase() },
                                        displayName = obj.optString("display_name", "$name, $country")
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback to Native Android Geocoder if HTTP is unavailable or returned empty
        if (results.isEmpty() && Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses: List<Address>? = geocoder.getFromLocationName(query, 5)
                addresses?.forEach { addr ->
                    val name = addr.locality ?: addr.subAdminArea ?: addr.featureName ?: query
                    val state = addr.adminArea ?: ""
                    val country = addr.countryName ?: ""
                    results.add(
                        GeocodedLocation(
                            name = name,
                            stateRegion = state.ifBlank { name },
                            country = country,
                            latitude = addr.latitude,
                            longitude = addr.longitude,
                            type = "City",
                            displayName = listOfNotNull(name, state, country).filter { it.isNotBlank() }.joinToString(", ")
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        // Deduplicate results by name
        results.distinctBy { it.name.lowercase().trim() }
    }
}
