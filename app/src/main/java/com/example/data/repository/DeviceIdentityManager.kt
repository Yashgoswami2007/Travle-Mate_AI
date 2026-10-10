package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DeviceUserIdentity(
    val deviceId: String,
    val displayName: String,
    val username: String,
    val avatarInitials: String
)

class DeviceIdentityManager(private val context: Context) {
    private val tag = "DeviceIdentityManager"
    private val prefs = context.getSharedPreferences("device_identity_prefs", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = try { BuildConfig.SUPABASE_URL.trimEnd('/') } catch (e: Throwable) { "" }
    private val apiKey = try { BuildConfig.SUPABASE_PUBLISHABLE_KEY } catch (e: Throwable) { "" }

    private val _identityState: MutableStateFlow<DeviceUserIdentity> by lazy {
        MutableStateFlow(loadIdentityFromPrefs())
    }
    val identityState: StateFlow<DeviceUserIdentity> get() = _identityState.asStateFlow()

    private fun loadIdentityFromPrefs(): DeviceUserIdentity {
        var id = prefs.getString("device_user_id", null)
        var name = prefs.getString("device_display_name", null)
        var username = prefs.getString("device_username", null)

        if (id == null || name == null || username == null) {
            id = UUID.randomUUID().toString()
            val suffix = id.take(4).uppercase()
            name = "User $suffix"
            username = "user_" + id.take(6)

            prefs.edit()
                .putString("device_user_id", id)
                .putString("device_display_name", name)
                .putString("device_username", username)
                .apply()

            Log.d(tag, "Initialized new persistent device identity: $id ($username)")
        }

        val initials = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifBlank { "TM" }

        return DeviceUserIdentity(id, name, username, initials)
    }

    @Synchronized
    fun getOrInitializeIdentity(): DeviceUserIdentity {
        val identity = loadIdentityFromPrefs()
        _identityState.value = identity
        return identity
    }

    suspend fun updateDisplayName(newName: String, db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        if (newName.isBlank()) return@withContext false

        prefs.edit().putString("device_display_name", newName.trim()).apply()

        val updated = getOrInitializeIdentity()
        ensureLocalUserEntity(db)
        syncIdentityToBackend(updated)
        return@withContext true
    }

    suspend fun updateUsername(newUsername: String, db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        val clean = newUsername.lowercase().replace("[^a-z0-9_]".toRegex(), "")
        if (clean.length < 3) return@withContext false

        prefs.edit().putString("device_username", clean).apply()

        val updated = getOrInitializeIdentity()
        ensureLocalUserEntity(db)
        syncIdentityToBackend(updated)
        return@withContext true
    }

    suspend fun ensureLocalUserEntity(db: AppDatabase): UserEntity = withContext(Dispatchers.IO) {
        val identity = getOrInitializeIdentity()
        val existing = db.userDao().getUserById(identity.deviceId)

        val userEntity = UserEntity(
            id = identity.deviceId,
            email = "${identity.username}@tripmate.device",
            name = identity.displayName,
            avatarInitials = identity.avatarInitials,
            homeLocation = existing?.homeLocation ?: "Delhi, India",
            currency = existing?.currency ?: "₹",
            dietPreference = existing?.dietPreference ?: "Vegetarian",
            travelStyle = existing?.travelStyle ?: "Relaxed",
            budgetTier = existing?.budgetTier ?: "Medium"
        )

        db.userDao().insertUser(userEntity)
        return@withContext userEntity
    }

    suspend fun syncIdentityToBackend(identity: DeviceUserIdentity = getOrInitializeIdentity()): Boolean = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            return@withContext true
        }

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val userJson = JSONObject().apply {
                put("id", identity.deviceId)
                put("username", identity.username)
                put("display_name", identity.displayName)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_users?on_conflict=id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation, resolution=merge-duplicates")
                .post(userJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync device identity to Supabase", e)
            return@withContext false
        }
    }
}
