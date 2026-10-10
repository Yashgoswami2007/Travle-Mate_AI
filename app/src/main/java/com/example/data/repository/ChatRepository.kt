package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
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

data class ChatConversation(
    val id: String,
    val type: String,
    val title: String?,
    val imagePath: String?,
    val createdBy: String,
    val createdAt: Long,
    val lastMessageAt: Long,
    val unreadCount: Int = 0,
    val lastMessagePreview: String = "",
    val otherUserAvatar: String? = null
)

data class PeerMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val type: String, // TEXT, IMAGE, VIDEO, AUDIO
    val textContent: String?,
    val attachmentPath: String? = null,
    val mimeType: String? = null,
    val createdAt: Long,
    val isFromMe: Boolean
)

class ChatRepository(
    private val identityRepo: ChatIdentityRepository
) {
    private val tag = "ChatRepository"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = try { BuildConfig.SUPABASE_URL.trimEnd('/') } catch (e: Throwable) { "" }
    private val apiKey = try { BuildConfig.SUPABASE_PUBLISHABLE_KEY } catch (e: Throwable) { "" }

    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    val conversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

    suspend fun refreshConversations() = withContext(Dispatchers.IO) {
        val identity = identityRepo.getIdentity() ?: return@withContext
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) {
            return@withContext
        }

        try {
            // Get conversation members where user_id = me, then join with conversations
            // Doing it via PostgREST:
            val url = "$supabaseUrl/rest/v1/chat_members?user_id=eq.${identity.id}&select=conversation_id,last_read_message_id,chat_conversations(*)"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(body)
                val list = mutableListOf<ChatConversation>()
                for (i in 0 until array.length()) {
                    val memberObj = array.getJSONObject(i)
                    val convObj = memberObj.optJSONObject("chat_conversations") ?: continue
                    
                    val convId = convObj.optString("id")
                    
                    list.add(
                        ChatConversation(
                            id = convId,
                            type = convObj.optString("type", "DIRECT"),
                            title = convObj.optString("title", "Conversation"),
                            imagePath = convObj.optString("image_path", null),
                            createdBy = convObj.optString("created_by", ""),
                            createdAt = 0L, // parse date if needed
                            lastMessageAt = 0L,
                            lastMessagePreview = "Tap to view conversation"
                        )
                    )
                }
                _conversations.value = list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to refresh conversations", e)
        }
    }
    
    suspend fun createDirectConversation(targetUserId: String): String? = withContext(Dispatchers.IO) {
        val identity = identityRepo.getIdentity() ?: return@withContext null
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) return@withContext null

        try {
            // Insert conversation
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val convJson = JSONObject().apply {
                put("type", "DIRECT")
                put("created_by", identity.id)
            }
            val reqConv = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_conversations?select=*")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(convJson.toString().toRequestBody(jsonMediaType))
                .build()

            val resConv = httpClient.newCall(reqConv).execute()
            val bodyConv = resConv.body?.string() ?: ""
            if (!resConv.isSuccessful) return@withContext null
            
            val convId = JSONArray(bodyConv).getJSONObject(0).getString("id")

            // Insert members
            val membersArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("conversation_id", convId)
                    put("user_id", identity.id)
                })
                put(JSONObject().apply {
                    put("conversation_id", convId)
                    put("user_id", targetUserId)
                })
            }
            val reqMem = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_members")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(membersArray.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(reqMem).execute()
            
            refreshConversations()
            return@withContext convId
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun createGroupConversation(title: String, targetUserIds: List<String>): String? = withContext(Dispatchers.IO) {
        val identity = identityRepo.getIdentity() ?: return@withContext null
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) return@withContext null

        try {
            // Insert conversation
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val convJson = JSONObject().apply {
                put("type", "GROUP")
                put("title", title)
                put("created_by", identity.id)
            }
            val reqConv = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_conversations?select=*")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(convJson.toString().toRequestBody(jsonMediaType))
                .build()

            val resConv = httpClient.newCall(reqConv).execute()
            val bodyConv = resConv.body?.string() ?: ""
            if (!resConv.isSuccessful) return@withContext null
            
            val convId = JSONArray(bodyConv).getJSONObject(0).getString("id")

            // Insert members
            val membersArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("conversation_id", convId)
                    put("user_id", identity.id)
                })
                targetUserIds.forEach { targetId ->
                    put(JSONObject().apply {
                        put("conversation_id", convId)
                        put("user_id", targetId)
                    })
                }
            }
            val reqMem = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_members")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(membersArray.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(reqMem).execute()
            
            refreshConversations()
            return@withContext convId
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun getOrCreateTripConversation(tripId: String, tripTitle: String): String? = withContext(Dispatchers.IO) {
        val identity = identityRepo.getIdentity() ?: return@withContext null
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) return@withContext null

        try {
            // Check if trip conversation already exists
            val checkUrl = "$supabaseUrl/rest/v1/chat_conversations?trip_id=eq.$tripId&select=id"
            val checkReq = Request.Builder()
                .url(checkUrl)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val checkResp = httpClient.newCall(checkReq).execute()
            val checkBody = checkResp.body?.string() ?: ""
            if (checkResp.isSuccessful) {
                val array = JSONArray(checkBody)
                if (array.length() > 0) {
                    val existingId = array.getJSONObject(0).getString("id")
                    // Ensure current user is in chat_members
                    val memberJson = JSONObject().apply {
                        put("conversation_id", existingId)
                        put("user_id", identity.id)
                        put("role", "MEMBER")
                    }
                    val joinReq = Request.Builder()
                        .url("$supabaseUrl/rest/v1/chat_members")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=ignore-duplicates")
                        .post(memberJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    httpClient.newCall(joinReq).execute()
                    refreshConversations()
                    return@withContext existingId
                }
            }

            // Create new trip group conversation
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val convJson = JSONObject().apply {
                put("type", "GROUP")
                put("title", "$tripTitle Group Chat")
                put("created_by", identity.id)
                put("trip_id", tripId)
            }
            val reqConv = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_conversations?select=*")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(convJson.toString().toRequestBody(jsonMediaType))
                .build()

            val resConv = httpClient.newCall(reqConv).execute()
            val bodyConv = resConv.body?.string() ?: ""
            if (!resConv.isSuccessful) return@withContext null

            val convId = JSONArray(bodyConv).getJSONObject(0).getString("id")

            // Add creator
            val memberJson = JSONObject().apply {
                put("conversation_id", convId)
                put("user_id", identity.id)
                put("role", "ADMIN")
            }
            val reqMem = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_members")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(memberJson.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(reqMem).execute()

            refreshConversations()
            return@withContext convId
        } catch (e: Exception) {
            Log.e(tag, "Failed to get or create trip conversation", e)
            return@withContext null
        }
    }

    suspend fun searchChatUsers(query: String): List<ChatIdentity> = withContext(Dispatchers.IO) {
        val currentIdentity = identityRepo.getIdentity()
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) return@withContext emptyList()

        try {
            val url = "$supabaseUrl/rest/v1/chat_users?username=ilike.%25${query}%25&select=id,username,display_name"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(body)
                val list = mutableListOf<ChatIdentity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    // don't include self
                    if (id != currentIdentity?.id) {
                        list.add(
                            ChatIdentity(
                                id = id,
                                username = obj.getString("username"),
                                displayName = obj.getString("display_name")
                            )
                        )
                    }
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to search users", e)
        }
        return@withContext emptyList()
    }

    suspend fun getMessages(conversationId: String): List<PeerMessage> = withContext(Dispatchers.IO) {
        val currentIdentity = identityRepo.getIdentity() ?: return@withContext emptyList()
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) return@withContext emptyList()
        
        try {
            // Using PostgREST to join chat_messages with chat_attachments
            val url = "$supabaseUrl/rest/v1/chat_messages?conversation_id=eq.$conversationId&select=*,chat_attachments(storage_path,mime_type)&order=created_at.asc"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(req).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val array = JSONArray(body)
                val list = mutableListOf<PeerMessage>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val senderId = obj.getString("sender_id")
                    
                    var attachPath: String? = null
                    var mType: String? = null
                    
                    // Handle attachments
                    val attachments = obj.optJSONArray("chat_attachments")
                    if (attachments != null && attachments.length() > 0) {
                        val attachObj = attachments.getJSONObject(0)
                        attachPath = attachObj.optString("storage_path")
                        mType = attachObj.optString("mime_type")
                    }

                    list.add(
                        PeerMessage(
                            id = obj.getString("id"),
                            conversationId = obj.getString("conversation_id"),
                            senderId = senderId,
                            type = obj.getString("type"),
                            textContent = obj.optString("text_content", null),
                            attachmentPath = attachPath,
                            mimeType = mType,
                            createdAt = System.currentTimeMillis(), // can parse actual date if needed
                            isFromMe = senderId == currentIdentity.id
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to get messages", e)
        }
        return@withContext emptyList()
    }

    suspend fun sendMessage(
        conversationId: String, 
        text: String?, 
        type: String = "TEXT",
        attachmentPath: String? = null,
        mimeType: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val currentIdentity = identityRepo.getIdentity() ?: return@withContext false
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) return@withContext false

        try {
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val msgJson = JSONObject().apply {
                put("conversation_id", conversationId)
                put("sender_id", currentIdentity.id)
                put("type", type)
                if (text != null) put("text_content", text)
            }
            
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_messages?select=id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(msgJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(req).execute()
            val resBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                // Parse message ID
                val msgId = JSONArray(resBody).getJSONObject(0).getString("id")
                
                // If attachment, insert it
                if (attachmentPath != null) {
                    val attachJson = JSONObject().apply {
                        put("message_id", msgId)
                        put("storage_path", attachmentPath)
                        if (mimeType != null) put("mime_type", mimeType)
                    }
                    val reqAttach = Request.Builder()
                        .url("$supabaseUrl/rest/v1/chat_attachments")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(attachJson.toString().toRequestBody(jsonMediaType))
                        .build()
                    httpClient.newCall(reqAttach).execute()
                }

                // Update last_message_at on the conversation
                val patchConvJson = JSONObject().apply {
                    put("last_message_at", "now()")
                }
                val reqPatch = Request.Builder()
                    .url("$supabaseUrl/rest/v1/chat_conversations?id=eq.$conversationId")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .patch(patchConvJson.toString().toRequestBody(jsonMediaType))
                    .build()
                httpClient.newCall(reqPatch).execute()

                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send message", e)
        }
        return@withContext false
    }

    suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || supabaseUrl.contains("your-project") || apiKey.isEmpty()) return@withContext false

        try {
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/chat_messages?id=eq.$messageId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()

            val response = httpClient.newCall(req).execute()
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete message", e)
        }
        return@withContext false
    }
}
