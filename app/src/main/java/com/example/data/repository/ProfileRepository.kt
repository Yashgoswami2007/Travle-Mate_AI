package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.auth.SupabaseAuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PublicUserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val bio: String = ""
)

data class PrivateUserProfile(
    val id: String,
    val email: String,
    val homeLocation: String = "Delhi, India",
    val currency: String = "₹",
    val dietPreference: String = "Vegetarian",
    val travelStyle: String = "Relaxed",
    val budgetTier: String = "Medium",
    val preferredAccommodation: String = "Boutique Hotel",
    val preferredTransport: String = "Train"
)

class ProfileRepository(
    private val db: AppDatabase,
    private val authManager: SupabaseAuthManager
) {
    private val tag = "ProfileRepository"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = try { BuildConfig.SUPABASE_URL.trimEnd('/') } catch (e: Throwable) { "" }
    private val apiKey = try { BuildConfig.SUPABASE_PUBLISHABLE_KEY } catch (e: Throwable) { "" }

    private val _currentPublicProfile = MutableStateFlow<PublicUserProfile?>(null)
    val currentPublicProfile: StateFlow<PublicUserProfile?> = _currentPublicProfile.asStateFlow()

    private val _currentPrivateProfile = MutableStateFlow<PrivateUserProfile?>(null)
    val currentPrivateProfile: StateFlow<PrivateUserProfile?> = _currentPrivateProfile.asStateFlow()

    /**
     * Searches users by username or display name for discovery and trip invitations.
     * Note: Queries public.profiles ONLY, never exposing private preferences or personal emails.
     */
    suspend fun searchUsers(query: String): List<PublicUserProfile> = withContext(Dispatchers.IO) {
        val currentUserId = authManager.getUserId()
        if (query.length < 2 || supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            val token = authManager.getAccessToken() ?: apiKey
            val encoded = java.net.URLEncoder.encode("%$query%", "UTF-8")
            val url = "$supabaseUrl/rest/v1/profiles?or=(username.ilike.$encoded,display_name.ilike.$encoded)&select=id,username,display_name,avatar_url,bio&limit=20"

            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val array = JSONArray(body)
                val list = mutableListOf<PublicUserProfile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    // Exclude self from search results
                    if (id != currentUserId) {
                        list.add(
                            PublicUserProfile(
                                id = id,
                                username = obj.getString("username"),
                                displayName = obj.getString("display_name"),
                                avatarUrl = obj.optString("avatar_url", ""),
                                bio = obj.optString("bio", "")
                            )
                        )
                    }
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to search public profiles", e)
        }
        return@withContext emptyList()
    }

    /**
     * Loads the authenticated user's public and private profiles from Supabase.
     * Caches in local Room database for offline persistence.
     */
    suspend fun loadUserProfile(): UserEntity? = withContext(Dispatchers.IO) {
        val userId = authManager.getUserId() ?: return@withContext db.userDao().getActiveUser()
        val token = authManager.getAccessToken() ?: return@withContext db.userDao().getActiveUser()

        try {
            // 1. Fetch public profile
            val pubReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/profiles?id=eq.$userId&select=id,username,display_name,avatar_url,bio")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val pubResp = httpClient.newCall(pubReq).execute()
            var displayName = "Traveler"
            var username = "user"
            var avatarUrl = ""

            if (pubResp.isSuccessful) {
                val array = JSONArray(pubResp.body?.string() ?: "[]")
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    username = obj.getString("username")
                    displayName = obj.getString("display_name")
                    avatarUrl = obj.optString("avatar_url", "")
                    _currentPublicProfile.value = PublicUserProfile(
                        id = userId,
                        username = username,
                        displayName = displayName,
                        avatarUrl = avatarUrl,
                        bio = obj.optString("bio", "")
                    )
                }
            }

            // 2. Fetch private profile
            val privReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_private_profiles?id=eq.$userId&select=*")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val privResp = httpClient.newCall(privReq).execute()
            var email = authManager.currentSession.value?.email ?: ""
            var homeLoc = "Delhi, India"
            var currency = "₹"
            var diet = "Vegetarian"
            var pace = "Relaxed"
            var budgetTier = "Medium"

            if (privResp.isSuccessful) {
                val array = JSONArray(privResp.body?.string() ?: "[]")
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    email = obj.optString("email", email)
                    homeLoc = obj.optString("home_location", homeLoc)
                    currency = obj.optString("currency", currency)
                    diet = obj.optString("diet_preference", diet)
                    pace = obj.optString("travel_style", pace)
                    budgetTier = obj.optString("budget_tier", budgetTier)

                    _currentPrivateProfile.value = PrivateUserProfile(
                        id = userId,
                        email = email,
                        homeLocation = homeLoc,
                        currency = currency,
                        dietPreference = diet,
                        travelStyle = pace,
                        budgetTier = budgetTier
                    )
                }
            }

            // Initials calculation
            val initials = displayName.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .map { it.first().uppercase() }
                .joinToString("")
                .ifBlank { "TM" }

            val localUser = UserEntity(
                id = userId,
                email = email,
                name = displayName,
                homeLocation = homeLoc,
                currency = currency,
                dietPreference = diet,
                travelStyle = pace,
                budgetTier = budgetTier,
                avatarInitials = initials
            )
            db.userDao().insertUser(localUser)
            return@withContext localUser
        } catch (e: Exception) {
            Log.e(tag, "Failed to load user profiles from backend", e)
            return@withContext db.userDao().getActiveUser()
        }
    }

    /**
     * Updates user profile (public display name and private travel preferences).
     */
    suspend fun updateProfile(
        displayName: String,
        homeLocation: String,
        dietPreference: String,
        travelStyle: String,
        budgetTier: String
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = authManager.getUserId() ?: return@withContext false
        val token = authManager.getAccessToken() ?: return@withContext false

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()

            // Update public profile
            val pubBody = JSONObject().apply {
                put("display_name", displayName)
                put("updated_at", "now()")
            }
            val pubReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/profiles?id=eq.$userId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(pubBody.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(pubReq).execute()

            // Update private profile
            val privBody = JSONObject().apply {
                put("home_location", homeLocation)
                put("diet_preference", dietPreference)
                put("travel_style", travelStyle)
                put("budget_tier", budgetTier)
                put("updated_at", "now()")
            }
            val privReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_private_profiles?id=eq.$userId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(privBody.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(privReq).execute()

            // Update local Room database
            val local = db.userDao().getActiveUser()
            if (local != null) {
                val updated = local.copy(
                    name = displayName,
                    homeLocation = homeLocation,
                    dietPreference = dietPreference,
                    travelStyle = travelStyle,
                    budgetTier = budgetTier
                )
                db.userDao().updateUser(updated)
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(tag, "Failed to update profile", e)
            return@withContext false
        }
    }
}
