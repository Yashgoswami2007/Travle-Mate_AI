package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.auth.SupabaseAuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.entity.GroupMemberEntity
import com.example.data.local.entity.TripEntity
import kotlinx.coroutines.Dispatchers
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
import java.util.UUID
import java.util.concurrent.TimeUnit

data class TripCollaborator(
    val userId: String,
    val tripId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val role: String, // OWNER, EDITOR, VIEWER
    val isCurrentUser: Boolean
)

data class TripInvitation(
    val id: String,
    val tripId: String,
    val tripName: String,
    val destinationName: String,
    val coverImageUrl: String,
    val inviterId: String,
    val inviterName: String,
    val inviterUsername: String,
    val status: String,
    val createdAt: Long
)

class TripCollaborationRepository(
    private val db: AppDatabase,
    private val authManager: SupabaseAuthManager
) {
    private val tag = "TripCollabRepo"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = try { BuildConfig.SUPABASE_URL.trimEnd('/') } catch (e: Throwable) { "" }
    private val apiKey = try { BuildConfig.SUPABASE_PUBLISHABLE_KEY } catch (e: Throwable) { "" }

    private val _pendingInvitations = MutableStateFlow<List<TripInvitation>>(emptyList())
    val pendingInvitations: StateFlow<List<TripInvitation>> = _pendingInvitations.asStateFlow()

    private val _tripMembers = MutableStateFlow<Map<String, List<TripCollaborator>>>(emptyMap())
    val tripMembers: StateFlow<Map<String, List<TripCollaborator>>> = _tripMembers.asStateFlow()

    /**
     * Sends an invitation to a user by their user ID for a specific trip.
     */
    suspend fun inviteUserToTrip(tripId: String, inviteeUserId: String): Boolean = withContext(Dispatchers.IO) {
        val currentUserId = authManager.getUserId() ?: return@withContext false
        val token = authManager.getAccessToken() ?: return@withContext false

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("trip_id", tripId)
                put("inviter_id", currentUserId)
                put("invitee_id", inviteeUserId)
                put("status", "PENDING")
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/trip_invitations")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                Log.d(tag, "Successfully invited user $inviteeUserId to trip $tripId")
                return@withContext true
            } else {
                Log.w(tag, "Failed to invite user: ${response.code} ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error sending trip invitation", e)
        }
        return@withContext false
    }

    /**
     * Fetches all pending invitations for the currently authenticated user.
     */
    suspend fun refreshPendingInvitations(): List<TripInvitation> = withContext(Dispatchers.IO) {
        val currentUserId = authManager.getUserId() ?: return@withContext emptyList()
        val token = authManager.getAccessToken() ?: return@withContext emptyList()

        try {
            // Join with user_trips and profiles (inviter)
            val url = "$supabaseUrl/rest/v1/trip_invitations?invitee_id=eq.$currentUserId&status=eq.PENDING&select=id,trip_id,status,created_at,user_trips(destination_name,cover_image_url),profiles!inviter_id(id,username,display_name)"

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
                val list = mutableListOf<TripInvitation>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tripObj = obj.optJSONObject("user_trips")
                    val inviterObj = obj.optJSONObject("profiles")

                    list.add(
                        TripInvitation(
                            id = obj.getString("id"),
                            tripId = obj.getString("trip_id"),
                            tripName = tripObj?.optString("destination_name", "Shared Trip") ?: "Shared Trip",
                            destinationName = tripObj?.optString("destination_name", "Destination") ?: "Destination",
                            coverImageUrl = tripObj?.optString("cover_image_url", "") ?: "",
                            inviterId = inviterObj?.optString("id", "") ?: "",
                            inviterName = inviterObj?.optString("display_name", "Travel Friend") ?: "Travel Friend",
                            inviterUsername = inviterObj?.optString("username", "") ?: "",
                            status = obj.optString("status", "PENDING"),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                _pendingInvitations.value = list
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to refresh pending invitations", e)
        }
        return@withContext emptyList()
    }

    /**
     * Accepts a trip invitation using the atomic PostgreSQL function `accept_trip_invitation`.
     * This atomically adds the user to trip_members and adds them to the trip's group chat.
     */
    suspend fun acceptInvitation(invitationId: String): Boolean = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext false

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("p_invitation_id", invitationId)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/accept_trip_invitation")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val result = JSONObject(body)
                if (result.optBoolean("success", false)) {
                    val tripId = result.optString("trip_id")
                    if (tripId.isNotBlank()) {
                        syncSharedTripToLocal(tripId)
                    }
                    refreshPendingInvitations()
                    return@withContext true
                }
            } else {
                Log.w(tag, "accept_trip_invitation RPC failed: $body")
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception accepting invitation", e)
        }
        return@withContext false
    }

    /**
     * Rejects a trip invitation using the atomic PostgreSQL function `reject_trip_invitation`.
     */
    suspend fun rejectInvitation(invitationId: String): Boolean = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext false

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("p_invitation_id", invitationId)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/reject_trip_invitation")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                refreshPendingInvitations()
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception rejecting invitation", e)
        }
        return@withContext false
    }

    /**
     * Removes a collaborator from a trip using the atomic PostgreSQL function `remove_trip_member`.
     * Revokes trip access and group chat access atomically.
     */
    suspend fun removeMember(tripId: String, memberUserId: String): Boolean = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext false

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("p_trip_id", tripId)
                put("p_member_user_id", memberUserId)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/rpc/remove_trip_member")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                refreshTripMembers(tripId)
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception removing trip member", e)
        }
        return@withContext false
    }

    /**
     * Refreshes the list of collaborators for a trip.
     */
    suspend fun refreshTripMembers(tripId: String): List<TripCollaborator> = withContext(Dispatchers.IO) {
        val currentUserId = authManager.getUserId() ?: ""
        val token = authManager.getAccessToken() ?: apiKey

        try {
            val url = "$supabaseUrl/rest/v1/trip_members?trip_id=eq.$tripId&select=user_id,role,profiles(id,username,display_name,avatar_url)"
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
                val list = mutableListOf<TripCollaborator>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val uid = obj.getString("user_id")
                    val role = obj.optString("role", "EDITOR")
                    val prof = obj.optJSONObject("profiles")

                    list.add(
                        TripCollaborator(
                            userId = uid,
                            tripId = tripId,
                            username = prof?.optString("username", "") ?: "",
                            displayName = prof?.optString("display_name", "Member") ?: "Member",
                            avatarUrl = prof?.optString("avatar_url", "") ?: "",
                            role = role,
                            isCurrentUser = uid == currentUserId
                        )
                    )
                }

                val currentMap = _tripMembers.value.toMutableMap()
                currentMap[tripId] = list
                _tripMembers.value = currentMap

                // Also sync to Room group_members
                val localMembers = list.map { c ->
                    GroupMemberEntity(
                        id = c.userId,
                        tripId = tripId,
                        name = c.displayName,
                        email = "",
                        avatarColorHex = "#1E3A5F",
                        isCurrentUser = c.isCurrentUser
                    )
                }
                db.expenseDao().insertMembers(localMembers)
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to refresh trip members", e)
        }
        return@withContext emptyList()
    }

    /**
     * Downloads shared trip metadata to the local Room database after accepting an invitation.
     */
    private suspend fun syncSharedTripToLocal(tripId: String) = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext
        try {
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_trips?id=eq.$tripId&select=*")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful) {
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    val entity = TripEntity(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        destinationId = obj.optString("destination_id", "dest_shared"),
                        destinationName = obj.optString("destination_name", "Shared Trip"),
                        destinationRegion = obj.optString("destination_region", ""),
                        destinationCountry = obj.optString("destination_country", "India"),
                        imageUrl = obj.optString("cover_image_url", "https://images.unsplash.com/photo-1599661046289-e31897846e41"),
                        startDate = obj.optString("start_date", "Nov 12, 2026"),
                        endDate = obj.optString("end_date", "Nov 15, 2026"),
                        numberOfDays = obj.optInt("number_of_days", 4),
                        travellersCount = obj.optInt("travellers_count", 2),
                        travellersDescription = obj.optString("travellers_description", "Friends"),
                        startingLocation = obj.optString("starting_location", "Delhi"),
                        totalBudget = obj.optDouble("total_budget", 25000.0),
                        spentBudget = obj.optDouble("spent_budget", 0.0),
                        pace = obj.optString("pace", "Relaxed"),
                        status = obj.optString("status", "ACTIVE")
                    )
                    db.tripDao().insertTrip(entity)
                    refreshTripMembers(tripId)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed syncing trip to local DB", e)
        }
    }
}
