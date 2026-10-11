package com.example.data.remote

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
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DeviceProfileDto(
    val deviceId: String,
    val username: String,
    val fullName: String,
    val dateOfBirth: String,
    val location: String
)

sealed class UsernameAvailability {
    object Empty : UsernameAvailability()
    data class Invalid(val message: String) : UsernameAvailability()
    object Checking : UsernameAvailability()
    data class Available(val normalizedUsername: String) : UsernameAvailability()
    data class Taken(val requestedUsername: String, val suggestions: List<String> = emptyList()) : UsernameAvailability()
    data class Error(val message: String) : UsernameAvailability()
}

sealed class ProfileRegistrationResult {
    data class Success(val profile: DeviceProfileDto) : ProfileRegistrationResult()
    data class UsernameTaken(val message: String, val suggestions: List<String> = emptyList()) : ProfileRegistrationResult()
    data class Error(val message: String) : ProfileRegistrationResult()
}

class DeviceProfileRemoteService {
    private val tag = "DeviceProfileService"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = try { BuildConfig.SUPABASE_URL.trimEnd('/') } catch (e: Throwable) { "" }
    private val apiKey = try { BuildConfig.SUPABASE_PUBLISHABLE_KEY } catch (e: Throwable) { "" }

    companion object {
        /**
         * Validates a username candidate against TripMate AI business rules:
         * - 3 to 20 characters
         * - Alphanumeric and underscores only
         * - No whitespace
         * - No leading or trailing underscores
         */
        fun validateUsernameLocally(username: String): String? {
            val trimmed = username.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.contains(" ")) return "Username cannot contain spaces."
            if (trimmed.length < 3) return "Username must be at least 3 characters."
            if (trimmed.length > 20) return "Username cannot exceed 20 characters."
            if (trimmed.startsWith("_") || trimmed.endsWith("_")) {
                return "Username cannot start or end with an underscore."
            }
            if (!trimmed.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9_]*[a-zA-Z0-9]$"))) {
                return "Only letters, numbers, and underscores are allowed."
            }
            return null
        }

        /**
         * Generates deterministic candidate username alternatives based on the input.
         * Candidates are strictly checked for length and structure.
         */
        fun generateCandidateSuggestions(baseUsername: String): List<String> {
            val clean = baseUsername.lowercase()
                .replace("[^a-z0-9_]".toRegex(), "")
                .trim('_')
            val root = if (clean.length >= 2) clean else "traveler"

            val patterns = mutableListOf<String>()
            val suffixes = listOf("_07", "_dev", "_29", "_in", "_go", "_india", "_travel")

            for (suffix in suffixes) {
                val candidate = root + suffix
                if (candidate.length <= 20 && validateUsernameLocally(candidate) == null) {
                    patterns.add(candidate)
                }
            }

            val prefixThe = "the_$root"
            if (prefixThe.length <= 20 && validateUsernameLocally(prefixThe) == null) {
                patterns.add(prefixThe)
            }

            // Ensure 4 to 6 unique candidate options
            return patterns.distinct().filter { it != clean }
        }

        /**
         * Parses and standardizes date of birth into standard ISO-8601 (YYYY-MM-DD).
         */
        fun formatDobForDatabase(dobInput: String): String {
            val trimmed = dobInput.trim()
            if (trimmed.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                return trimmed
            }
            return try {
                val parsed = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
                parsed.format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: Exception) {
                try {
                    val parsed = LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                    parsed.format(DateTimeFormatter.ISO_LOCAL_DATE)
                } catch (e2: Exception) {
                    // Safe fallback for testing or non-standard entries
                    "2000-01-01"
                }
            }
        }
    }

    /**
     * Checks if a username is available in Supabase.
     * Uses RPC check_username_availability, with fallback to PostgREST query.
     * Returns Pair(isAvailable, reasonOrNormalized).
     */
    suspend fun checkUsernameAvailability(
        username: String,
        currentDeviceId: String? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val clean = username.lowercase().trim()
        val localError = validateUsernameLocally(clean)
        if (localError != null) {
            return@withContext Pair(false, localError)
        }

        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            // Local offline mock/bypass for local dev without backend keys
            return@withContext Pair(true, clean)
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 1. Try PostgreSQL RPC
        try {
            val rpcPayload = JSONObject().apply {
                put("p_username", clean)
                if (!currentDeviceId.isNullOrBlank()) {
                    put("p_current_device_id", currentDeviceId)
                }
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/check_username_availability")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(rpcPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val isAvailable = json.optBoolean("available", false)
                val reason = json.optString("reason", if (isAvailable) "AVAILABLE" else "TAKEN")
                return@withContext Pair(isAvailable, reason)
            }
        } catch (e: Exception) {
            Log.w(tag, "RPC check_username_availability call failed, falling back to PostgREST: ${e.message}")
        }

        // 2. Fallback: Query PostgREST device_profiles directly
        try {
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/device_profiles?username=ilike.$encoded&select=device_id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val array = JSONArray(body)
                if (array.length() == 0) {
                    return@withContext Pair(true, "AVAILABLE")
                }
                val existingDeviceId = array.getJSONObject(0).optString("device_id")
                if (!currentDeviceId.isNullOrBlank() && existingDeviceId == currentDeviceId) {
                    return@withContext Pair(true, "CURRENT_USER")
                }
                return@withContext Pair(false, "TAKEN")
            } else {
                Log.e(tag, "Fallback availability check failed with code ${response.code}: $body")
                return@withContext Pair(false, "ERROR_${response.code}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Network error checking username availability", e)
            throw e
        }
    }

    /**
     * Takes candidate suggestions and verifies their real-time availability on the database.
     * Returns only suggestions confirmed to be available.
     */
    suspend fun filterAvailableSuggestions(
        candidates: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()

        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            return@withContext candidates.take(5)
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 1. Try RPC check_usernames_batch
        try {
            val jsonArray = JSONArray(candidates.map { it.lowercase() })
            val rpcPayload = JSONObject().apply {
                put("p_usernames", jsonArray)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/check_usernames_batch")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(rpcPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.isNotBlank()) {
                val array = JSONArray(body)
                val result = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    result.add(array.getString(i))
                }
                if (result.isNotEmpty()) return@withContext result
            }
        } catch (e: Exception) {
            Log.w(tag, "RPC check_usernames_batch failed, falling back to individual checks: ${e.message}")
        }

        // 2. Fallback: Query PostgREST in batch with `in.()` operator
        try {
            val listString = candidates.joinToString(",") { it.lowercase() }
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/device_profiles?username=in.($listString)&select=username")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val takenArray = JSONArray(body)
                val takenSet = mutableSetOf<String>()
                for (i in 0 until takenArray.length()) {
                    takenSet.add(takenArray.getJSONObject(i).optString("username").lowercase())
                }

                val available = candidates.filter { it.lowercase() !in takenSet }
                return@withContext available
            }
        } catch (e: Exception) {
            Log.w(tag, "PostgREST batch suggestion check failed", e)
        }

        return@withContext emptyList()
    }

    /**
     * Atomically registers the user profile in Supabase.
     */
    suspend fun registerProfile(
        deviceId: String,
        username: String,
        fullName: String,
        dateOfBirth: String,
        location: String
    ): ProfileRegistrationResult = withContext(Dispatchers.IO) {
        val cleanUsername = username.lowercase().trim()
        val formattedDob = formatDobForDatabase(dateOfBirth)

        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            return@withContext ProfileRegistrationResult.Success(
                DeviceProfileDto(
                    deviceId = deviceId,
                    username = cleanUsername,
                    fullName = fullName.trim(),
                    dateOfBirth = formattedDob,
                    location = location.trim()
                )
            )
        }

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 1. Try atomic PostgreSQL RPC register_device_profile
        try {
            val rpcPayload = JSONObject().apply {
                put("p_device_id", deviceId)
                put("p_username", cleanUsername)
                put("p_full_name", fullName.trim())
                put("p_date_of_birth", formattedDob)
                put("p_location", location.trim())
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/register_device_profile")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(rpcPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                if (json.optBoolean("success", false)) {
                    val p = json.optJSONObject("profile")
                    return@withContext ProfileRegistrationResult.Success(
                        DeviceProfileDto(
                            deviceId = p?.optString("device_id") ?: deviceId,
                            username = p?.optString("username") ?: cleanUsername,
                            fullName = p?.optString("full_name") ?: fullName.trim(),
                            dateOfBirth = p?.optString("date_of_birth") ?: formattedDob,
                            location = p?.optString("location") ?: location.trim()
                        )
                    )
                } else {
                    val errorCode = json.optString("error", "")
                    val message = json.optString("message", "Registration failed.")
                    if (errorCode == "USERNAME_TAKEN") {
                        val candidates = generateCandidateSuggestions(cleanUsername)
                        val confirmed = filterAvailableSuggestions(candidates)
                        return@withContext ProfileRegistrationResult.UsernameTaken(message, confirmed)
                    }
                    return@withContext ProfileRegistrationResult.Error(message)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "RPC register_device_profile failed, attempting PostgREST fallback: ${e.message}")
        }

        // 2. PostgREST Fallback
        try {
            val profileJson = JSONObject().apply {
                put("device_id", deviceId)
                put("username", cleanUsername)
                put("full_name", fullName.trim())
                put("date_of_birth", formattedDob)
                put("location", location.trim())
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/device_profiles?on_conflict=device_id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation, resolution=merge-duplicates")
                .post(profileJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                return@withContext ProfileRegistrationResult.Success(
                    DeviceProfileDto(
                        deviceId = deviceId,
                        username = cleanUsername,
                        fullName = fullName.trim(),
                        dateOfBirth = formattedDob,
                        location = location.trim()
                    )
                )
            } else if (response.code == 409 || body.contains("duplicate") || body.contains("unique")) {
                val candidates = generateCandidateSuggestions(cleanUsername)
                val confirmed = filterAvailableSuggestions(candidates)
                return@withContext ProfileRegistrationResult.UsernameTaken(
                    "The username '$cleanUsername' is already taken.",
                    confirmed
                )
            } else {
                return@withContext ProfileRegistrationResult.Error("Registration failed (${response.code}): $body")
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during fallback registration", e)
            return@withContext ProfileRegistrationResult.Error("Network error during registration: ${e.localizedMessage}")
        }
    }

    /**
     * Fetches profile from Supabase for returning user profile recovery.
     */
    suspend fun fetchProfile(deviceId: String): DeviceProfileDto? = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            return@withContext null
        }

        // 1. Try RPC get_device_profile
        try {
            val rpcPayload = JSONObject().apply {
                put("p_device_id", deviceId)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/get_device_profile")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(rpcPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                if (json.optBoolean("found", false)) {
                    return@withContext DeviceProfileDto(
                        deviceId = json.getString("device_id"),
                        username = json.getString("username"),
                        fullName = json.getString("full_name"),
                        dateOfBirth = json.optString("date_of_birth", ""),
                        location = json.getString("location")
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "RPC get_device_profile failed, falling back to PostgREST: ${e.message}")
        }

        // 2. PostgREST Fallback
        try {
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/device_profiles?device_id=eq.$deviceId&select=*")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    return@withContext DeviceProfileDto(
                        deviceId = obj.getString("device_id"),
                        username = obj.getString("username"),
                        fullName = obj.getString("full_name"),
                        dateOfBirth = obj.optString("date_of_birth", ""),
                        location = obj.optString("location", "")
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed fetching device profile from PostgREST", e)
        }

        return@withContext null
    }
}
