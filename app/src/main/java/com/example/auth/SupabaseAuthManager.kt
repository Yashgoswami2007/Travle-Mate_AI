package com.example.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class AuthSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

class SupabaseAuthManager(private val context: Context) {
    private val tag = "SupabaseAuthManager"
    private val prefs = context.getSharedPreferences("tripmate_supabase_auth_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl: String = try {
        BuildConfig.SUPABASE_URL.trimEnd('/')
    } catch (e: Throwable) {
        ""
    }

    private val apiKey: String = try {
        BuildConfig.SUPABASE_PUBLISHABLE_KEY
    } catch (e: Throwable) {
        ""
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentSession = MutableStateFlow<AuthSession?>(null)
    val currentSession: StateFlow<AuthSession?> = _currentSession.asStateFlow()

    init {
        restoreSession()
    }

    /**
     * Restores existing session from local storage on app startup.
     * Automatically attempts token refresh if expired.
     */
    fun restoreSession() {
        val userId = prefs.getString("user_id", null)
        val email = prefs.getString("user_email", null)
        val accessToken = prefs.getString("access_token", null)
        val refreshToken = prefs.getString("refresh_token", null)
        val expiresAt = prefs.getLong("expires_at", 0L)

        if (userId != null && accessToken != null && refreshToken != null) {
            val session = AuthSession(userId, email ?: "", accessToken, refreshToken, expiresAt)
            _currentSession.value = session
            _authState.value = AuthState.Success

            // Check if token needs refresh (within 5 minutes of expiry)
            if (System.currentTimeMillis() > (expiresAt - 300_000L)) {
                refreshSessionToken(refreshToken)
            }
        } else {
            _authState.value = AuthState.Idle
        }
    }

    /**
     * Builds Google OAuth authorization URL to launch in browser / Custom Tabs.
     * Supabase will redirect back to tripmate://auth-callback
     */
    fun getGoogleOAuthUrl(codeChallenge: String? = null): String {
        val redirectUri = Uri.encode("tripmate://auth-callback")
        var url = "$supabaseUrl/auth/v1/authorize?provider=google&redirect_to=$redirectUri"
        if (codeChallenge != null) {
            url += "&code_challenge=$codeChallenge&code_challenge_method=s256"
        }
        return url
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val codeVerifier = ByteArray(32)
        secureRandom.nextBytes(codeVerifier)
        return Base64.encodeToString(codeVerifier, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Launches Google OAuth authorization using Chrome Custom Tabs for better UX.
     */
    fun launchGoogleOAuth(context: Context) {
        try {
            val codeVerifier = generateCodeVerifier()
            prefs.edit().putString("pkce_code_verifier", codeVerifier).apply()
            
            val codeChallenge = generateCodeChallenge(codeVerifier)
            val url = getGoogleOAuthUrl(codeChallenge)
            
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(url))
            
            _authState.value = AuthState.Loading
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch Custom Tabs for OAuth", e)
            _authState.value = AuthState.GoogleSignInFailed("Could not open browser: ${e.localizedMessage}")
        }
    }

    /**
     * Handles redirect deep link: tripmate://auth-callback#access_token=...&refresh_token=...
     */
    suspend fun handleOAuthCallback(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            var accessToken: String? = null
            var refreshToken: String? = null
            var expiresInSeconds = 3600L

            // 1. Check fragment parameters (#access_token=...&refresh_token=...)
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) {
                val params = fragment.split("&").associate { param ->
                    val parts = param.split("=")
                    if (parts.size >= 2) parts[0] to parts[1] else parts[0] to ""
                }
                accessToken = params["access_token"]
                refreshToken = params["refresh_token"]
                expiresInSeconds = params["expires_in"]?.toLongOrNull() ?: 3600L
            }

            // 2. Check query parameters (?code=... for PKCE flow)
            if (accessToken == null) {
                val code = uri.getQueryParameter("code")
                if (!code.isNullOrBlank()) {
                    return@withContext exchangeAuthCodeForSession(code)
                }
            }

            if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
                val errorDesc = uri.getQueryParameter("error_description") ?: "Missing OAuth tokens"
                _authState.value = AuthState.GoogleSignInFailed(errorDesc)
                return@withContext false
            }

            // Fetch user info using the access token
            val user = fetchUserWithAccessToken(accessToken)
            if (user != null) {
                val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
                val session = AuthSession(
                    userId = user.optString("id"),
                    email = user.optString("email"),
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = expiresAt
                )
                saveSession(session)
                _currentSession.value = session
                _authState.value = AuthState.Success
                return@withContext true
            } else {
                _authState.value = AuthState.GoogleSignInFailed("Failed to fetch user profile from Supabase")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing OAuth callback", e)
            _authState.value = AuthState.GoogleSignInFailed(e.localizedMessage ?: "Unknown error")
            return@withContext false
        }
    }

    /**
     * Signs in using email and password via Supabase Auth REST API.
     */
    suspend fun signInWithEmail(email: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", pass)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val accessToken = json.getString("access_token")
                val refreshToken = json.getString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val userObj = json.getJSONObject("user")
                val userId = userObj.getString("id")
                val userEmail = userObj.optString("email", email)

                val session = AuthSession(
                    userId = userId,
                    email = userEmail,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                )
                saveSession(session)
                _currentSession.value = session
                _authState.value = AuthState.Success
                return@withContext true
            } else {
                val errJson = try { JSONObject(body) } catch (e: Exception) { null }
                val errorMsg = errJson?.optString("error_description") ?: errJson?.optString("msg") ?: "Login failed"
                if (response.code == 400 || errorMsg.contains("Invalid login credentials", ignoreCase = true)) {
                    _authState.value = AuthState.InvalidCredentials()
                } else {
                    _authState.value = AuthState.SupabaseError(errorMsg)
                }
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(tag, "Email login exception", e)
            _authState.value = AuthState.NetworkFailure(e.localizedMessage ?: "Connection error")
            return@withContext false
        }
    }

    /**
     * Signs up a new account using email and password.
     */
    suspend fun signUpWithEmail(email: String, pass: String, fullName: String): Boolean = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        try {
            if (pass.length < 6) {
                _authState.value = AuthState.WeakPassword()
                return@withContext false
            }

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val metaData = JSONObject().apply {
                put("full_name", fullName)
            }
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", pass)
                put("data", metaData)
            }

            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/signup")
                .addHeader("apikey", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id") ?: ""

                if (accessToken.isNotEmpty() && userId.isNotEmpty()) {
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val session = AuthSession(
                        userId = userId,
                        email = email,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                    )
                    saveSession(session)
                    _currentSession.value = session
                }
                _authState.value = AuthState.Success
                return@withContext true
            } else {
                val errJson = try { JSONObject(body) } catch (e: Exception) { null }
                val errorMsg = errJson?.optString("msg") ?: errJson?.optString("error_description") ?: "Sign-up failed"
                if (errorMsg.contains("already registered", ignoreCase = true) || errorMsg.contains("already exists", ignoreCase = true)) {
                    _authState.value = AuthState.EmailAlreadyRegistered()
                } else {
                    _authState.value = AuthState.SupabaseError(errorMsg)
                }
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(tag, "Email signup exception", e)
            _authState.value = AuthState.NetworkFailure(e.localizedMessage ?: "Connection error")
            return@withContext false
        }
    }

    /**
     * Refreshes the auth session token using the refresh_token.
     */
    fun refreshSessionToken(refreshToken: String) {
        scope.launch {
            try {
                val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                val payload = JSONObject().apply {
                    put("refresh_token", refreshToken)
                }

                val req = Request.Builder()
                    .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = httpClient.newCall(req).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val newAccessToken = json.getString("access_token")
                    val newRefreshToken = json.getString("refresh_token")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val userObj = json.getJSONObject("user")
                    val userId = userObj.getString("id")
                    val userEmail = userObj.optString("email", "")

                    val session = AuthSession(
                        userId = userId,
                        email = userEmail,
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken,
                        expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                    )
                    saveSession(session)
                    _currentSession.value = session
                    Log.d(tag, "Session refreshed successfully for user: $userId")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to refresh session token", e)
            }
        }
    }

    private fun exchangeAuthCodeForSession(code: String): Boolean {
        try {
            val codeVerifier = prefs.getString("pkce_code_verifier", "") ?: ""
            prefs.edit().remove("pkce_code_verifier").apply()
            
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val payload = JSONObject().apply {
                put("auth_code", code)
                if (codeVerifier.isNotEmpty()) {
                    put("code_verifier", codeVerifier)
                }
            }

            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=pkce")
                .addHeader("apikey", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                val accessToken = json.getString("access_token")
                val refreshToken = json.getString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val userObj = json.getJSONObject("user")

                val session = AuthSession(
                    userId = userObj.getString("id"),
                    email = userObj.optString("email"),
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                )
                saveSession(session)
                _currentSession.value = session
                _authState.value = AuthState.Success
                return true
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed exchanging code for token", e)
        }
        return false
    }

    private fun fetchUserWithAccessToken(token: String): JSONObject? {
        return try {
            val req = Request.Builder()
                .url("$supabaseUrl/auth/v1/user")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                JSONObject(body)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch user with access token", e)
            null
        }
    }

    /**
     * Signs out the user, clearing local session and credentials.
     */
    suspend fun logout() = withContext(Dispatchers.IO) {
        val token = _currentSession.value?.accessToken
        if (!token.isNullOrBlank()) {
            try {
                val emptyBody = "".toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$supabaseUrl/auth/v1/logout")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $token")
                    .post(emptyBody)
                    .build()
                httpClient.newCall(req).execute()
            } catch (e: Exception) {
                Log.w(tag, "Logout network request failed: ${e.message}")
            }
        }

        prefs.edit().clear().apply()
        _currentSession.value = null
        _authState.value = AuthState.Idle
    }

    fun resetAuthState() {
        if (_currentSession.value != null) {
            _authState.value = AuthState.Success
        } else {
            _authState.value = AuthState.Idle
        }
    }

    private fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString("user_id", session.userId)
            .putString("user_email", session.email)
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putLong("expires_at", session.expiresAt)
            .apply()
    }

    fun getAccessToken(): String? = _currentSession.value?.accessToken
    fun getUserId(): String? = _currentSession.value?.userId
    fun isAuthenticated(): Boolean = _currentSession.value != null
}
