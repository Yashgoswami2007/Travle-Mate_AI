package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class ChatIdentity(
    val id: String,
    val username: String,
    val displayName: String
)

class ChatIdentityRepository(private val context: Context) {
    private val tag = "ChatIdentityRepo"
    private val prefs = context.getSharedPreferences("chat_identity_prefs", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = try { BuildConfig.SUPABASE_URL.trimEnd('/') } catch (e: Throwable) { "" }
    private val apiKey = try { BuildConfig.SUPABASE_PUBLISHABLE_KEY } catch (e: Throwable) { "" }

    suspend fun initializeIdentityIfNeeded(): ChatIdentity = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existingId = prefs.getString("chat_user_id", null)
            val existingUsername = prefs.getString("chat_username", null)
            val existingDisplayName = prefs.getString("chat_display_name", null)

            if (existingId != null && existingUsername != null && existingDisplayName != null) {
                Log.d(tag, "Existing chat identity found: $existingUsername")
                return@withContext ChatIdentity(existingId, existingUsername, existingDisplayName)
            }

            Log.d(tag, "No chat identity found. Creating a new one...")
            val newId = UUID.randomUUID().toString()
            val randomSuffix = Random.nextInt(1000, 9999)
            val newUsername = "user_$randomSuffix"
            val newDisplayName = "User $randomSuffix"

            val success = registerIdentityWithBackend(newId, newUsername, newDisplayName)
            if (success) {
                prefs.edit()
                    .putString("chat_user_id", newId)
                    .putString("chat_username", newUsername)
                    .putString("chat_display_name", newDisplayName)
                    .apply()
                Log.d(tag, "New chat identity stored securely: $newUsername")
                return@withContext ChatIdentity(newId, newUsername, newDisplayName)
            } else {
                throw Exception("Failed to register chat identity with backend")
            }
        }
    }

    private fun registerIdentityWithBackend(id: String, username: String, displayName: String): Boolean {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            Log.w(tag, "Supabase not properly configured, bypassing backend registration for MVP")
            return true
        }

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val userJson = JSONObject().apply {
                put("id", id)
                put("username", username)
                put("display_name", displayName)
            }

            // Using upsert on conflict
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_users?on_conflict=id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation, resolution=merge-duplicates")
                .post(userJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return true
            } else {
                Log.e(tag, "Backend registration failed: ${response.code} $responseBody")
                return false
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during backend registration", e)
            return false
        }
    }

    fun getIdentity(): ChatIdentity? {
        val id = prefs.getString("chat_user_id", null) ?: return null
        val username = prefs.getString("chat_username", null) ?: return null
        val displayName = prefs.getString("chat_display_name", null) ?: return null
        return ChatIdentity(id, username, displayName)
    }

    suspend fun updateUsername(newUsername: String): Boolean = withContext(Dispatchers.IO) {
        val identity = getIdentity() ?: return@withContext false
        
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            prefs.edit().putString("chat_username", newUsername).apply()
            return@withContext true
        }

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val updateJson = JSONObject().apply {
                put("username", newUsername)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_users?id=eq.${identity.id}")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .patch(updateJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                prefs.edit().putString("chat_username", newUsername).apply()
                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }
    }

    fun setAuthenticatedIdentity(id: String, username: String, displayName: String) {
        prefs.edit()
            .putString("chat_user_id", id)
            .putString("chat_username", username)
            .putString("chat_display_name", displayName)
            .apply()
    }

    fun clearIdentity() {
        prefs.edit().clear().apply()
    }
}
