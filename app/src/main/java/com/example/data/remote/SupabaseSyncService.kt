package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ExpenseEntity
import com.example.data.local.entity.ExpenseSplitEntity
import com.example.data.local.entity.GroupMemberEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class SupabaseSyncService(private val db: AppDatabase) {
    private val tag = "SupabaseSyncService"
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl = try { BuildConfig.SUPABASE_URL.trimEnd('/') } catch (e: Throwable) { "" }
    private val publishableKey = try { BuildConfig.SUPABASE_PUBLISHABLE_KEY } catch (e: Throwable) { "" }
    private val secretKey = try { BuildConfig.SUPABASE_SECRET_KEY } catch (e: Throwable) { "" }

    // Prefer secret key (service role) to bypass RLS for direct device-to-device synchronization,
    // fallback to publishableKey if secret key is omitted
    private val apiKey: String
        get() = if (secretKey.isNotBlank()) secretKey else publishableKey

    /**
     * Converts any string to a canonical RFC 4122 UUID format.
     * If the string is already a valid UUID, returns it unchanged.
     * Otherwise, generates a deterministic UUID (v3) using UTF-8 byte hashing,
     * guaranteeing that identical non-UUID trip or expense IDs (e.g. "trip_jaipur_couple")
     * map to the exact same UUID on both Device A and Device B without PostgreSQL syntax errors.
     */
    fun toUuid(raw: String): String {
        val trimmed = raw.trim()
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        return if (trimmed.matches(uuidRegex)) {
            trimmed
        } else {
            UUID.nameUUIDFromBytes(trimmed.toByteArray(Charsets.UTF_8)).toString()
        }
    }

    suspend fun pushMember(member: GroupMemberEntity): Boolean = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext false
        try {
            val json = JSONObject().apply {
                put("id", toUuid(member.id))
                put("user_id", member.participantId)
                put("trip_id", toUuid(member.tripId))
                put("name", member.name)
                put("email", member.email)
                put("avatar_color_hex", member.avatarColorHex)
                put("is_current_user", member.isCurrentUser)
            }
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_group_members?on_conflict=id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val res = httpClient.newCall(req).execute()
            val successful = res.isSuccessful
            if (!successful) {
                Log.w(tag, "Failed to push member (${res.code}): ${res.body?.string()}")
            }
            res.close()
            return@withContext successful
        } catch (e: Exception) {
            Log.e(tag, "Failed to push member", e)
            return@withContext false
        }
    }

    suspend fun pushExpenseAndSplits(expense: ExpenseEntity, splits: List<ExpenseSplitEntity>): Boolean = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext false
        try {
            val remoteExpenseId = toUuid(expense.id)
            val remoteTripId = toUuid(expense.tripId)

            val expJson = JSONObject().apply {
                put("id", remoteExpenseId)
                put("user_id", expense.paidByMemberId)
                put("trip_id", remoteTripId)
                put("title", expense.title)
                put("category", expense.category)
                put("amount", expense.amount)
                put("paid_by_member_id", expense.paidByMemberId)
                put("paid_by_name", expense.paidByMemberName)
                put("split_type", expense.splitType)
                put("date", expense.date)
                put("notes", expense.notes)
            }
            val req1 = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expenses?on_conflict=id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .post(expJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val res1 = httpClient.newCall(req1).execute()
            if (!res1.isSuccessful) {
                Log.w(tag, "Failed to push expense (${res1.code}): ${res1.body?.string()}")
                res1.close()
                return@withContext false
            }
            res1.close()

            // When editing an expense, clear old remote splits to prevent dangling allocations
            val delReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expense_splits?expense_id=eq.$remoteExpenseId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            httpClient.newCall(delReq).execute().close()

            val splitsArray = JSONArray()
            splits.forEach { split ->
                splitsArray.put(JSONObject().apply {
                    put("id", toUuid(split.id))
                    put("user_id", split.memberId)
                    put("expense_id", remoteExpenseId)
                    put("trip_id", remoteTripId)
                    put("member_id", split.memberId)
                    put("member_name", split.memberName)
                    put("share_amount", split.shareAmount)
                    put("is_settled", split.isSettled)
                })
            }
            val req2 = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expense_splits?on_conflict=id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .post(splitsArray.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val res2 = httpClient.newCall(req2).execute()
            val splitsSuccessful = res2.isSuccessful
            if (!splitsSuccessful) {
                Log.w(tag, "Failed to push expense splits (${res2.code}): ${res2.body?.string()}")
            }
            res2.close()
            return@withContext splitsSuccessful
        } catch (e: Exception) {
            Log.e(tag, "Failed to push expense and splits", e)
            return@withContext false
        }
    }

    suspend fun deleteExpense(expenseId: String) = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext
        try {
            val remoteExpId = toUuid(expenseId)
            // Delete splits first to maintain clean referential state
            val delSplits = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expense_splits?expense_id=eq.$remoteExpId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            httpClient.newCall(delSplits).execute().close()

            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expenses?id=eq.$remoteExpId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) {
                Log.w(tag, "Failed to delete expense (${res.code}): ${res.body?.string()}")
            }
            res.close()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete expense", e)
        }
    }

    suspend fun updateSplitSettled(splitId: String, isSettled: Boolean) = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext
        try {
            val remoteSplitId = toUuid(splitId)
            val json = JSONObject().apply {
                put("is_settled", isSettled)
            }
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expense_splits?id=eq.$remoteSplitId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .patch(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val res = httpClient.newCall(req).execute()
            if (!res.isSuccessful) {
                Log.w(tag, "Failed to update split settled (${res.code}): ${res.body?.string()}")
            }
            res.close()
        } catch (e: Exception) {
            Log.e(tag, "Failed to update split settled status", e)
        }
    }

    /**
     * Fetches all expenses, splits, and group members for [tripId] from Supabase.
     * Reconciles remote data atomically into Room to eliminate duplicate expenses,
     * ghost splits, and corrupted balance calculations across multiple devices.
     */
    suspend fun fetchExpensesAndSplits(tripId: String) = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext
        try {
            val remoteTripId = toUuid(tripId)

            // 1. Fetch Group Members
            val memReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_group_members?trip_id=eq.$remoteTripId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val memRes = httpClient.newCall(memReq).execute()
            if (memRes.isSuccessful) {
                val memArray = JSONArray(memRes.body?.string() ?: "[]")
                val membersList = mutableListOf<GroupMemberEntity>()
                for (i in 0 until memArray.length()) {
                    val obj = memArray.getJSONObject(i)
                    membersList.add(
                        GroupMemberEntity(
                            id = obj.getString("id"),
                            tripId = tripId, // map to client tripId
                            participantId = obj.getString("user_id"),
                            name = obj.getString("name"),
                            email = obj.optString("email", ""),
                            avatarColorHex = obj.optString("avatar_color_hex", "#1E3A5F"),
                            isCurrentUser = false // evaluated reactively by MainViewModel
                        )
                    )
                }
                if (membersList.isNotEmpty()) {
                    db.expenseDao().insertMembers(membersList)
                }
            } else {
                Log.w(tag, "Fetch members failed (${memRes.code}): ${memRes.body?.string()}")
            }
            memRes.close()

            // 2. Fetch Splits
            val splitList = mutableListOf<ExpenseSplitEntity>()
            val splitReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expense_splits?trip_id=eq.$remoteTripId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val splitRes = httpClient.newCall(splitReq).execute()
            if (splitRes.isSuccessful) {
                val splitArray = JSONArray(splitRes.body?.string() ?: "[]")
                for (i in 0 until splitArray.length()) {
                    val obj = splitArray.getJSONObject(i)
                    splitList.add(
                        ExpenseSplitEntity(
                            id = obj.getString("id"),
                            expenseId = obj.getString("expense_id"),
                            tripId = tripId, // map to client tripId
                            memberId = obj.getString("member_id"),
                            memberName = obj.getString("member_name"),
                            shareAmountInPaise = Math.round(obj.optDouble("share_amount", 0.0) * 100.0),
                            isSettled = obj.optBoolean("is_settled", false)
                        )
                    )
                }
            } else {
                Log.w(tag, "Fetch splits failed (${splitRes.code}): ${splitRes.body?.string()}")
            }
            splitRes.close()

            // 3. Fetch Expenses
            val expList = mutableListOf<ExpenseEntity>()
            val expReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_expenses?trip_id=eq.$remoteTripId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val expRes = httpClient.newCall(expReq).execute()
            if (expRes.isSuccessful) {
                val expArray = JSONArray(expRes.body?.string() ?: "[]")
                for (i in 0 until expArray.length()) {
                    val obj = expArray.getJSONObject(i)
                    val expenseId = obj.getString("id")
                    val participantIds = splitList.filter { it.expenseId == expenseId }.map { it.memberId }
                    expList.add(
                        ExpenseEntity(
                            id = expenseId,
                            tripId = tripId, // map to client tripId
                            title = obj.getString("title"),
                            category = obj.getString("category"),
                            amountInPaise = Math.round(obj.optDouble("amount", 0.0) * 100.0),
                            paidByMemberId = obj.getString("paid_by_member_id"),
                            paidByMemberName = obj.getString("paid_by_name"),
                            splitType = obj.optString("split_type", "EQUAL"),
                            participantIdsJson = participantIds.joinToString(","),
                            date = obj.optString("date", "Today"),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
            } else {
                Log.w(tag, "Fetch expenses failed (${expRes.code}): ${expRes.body?.string()}")
            }
            expRes.close()

            // 4. Atomically reconcile local Room database with remote source of truth
            if (expRes.isSuccessful && splitRes.isSuccessful) {
                db.expenseDao().reconcileExpensesAndSplitsForTrip(tripId, expList, splitList)

                // Update trip spent budget locally
                val allItems = db.tripDao().getAllItemsForTripSync(tripId)
                val totalActivityCosts = allItems.sumOf { it.estimatedCost }
                val totalExpenses = expList.sumOf { it.amount }
                val trip = db.tripDao().getTripById(tripId)
                if (trip != null) {
                    db.tripDao().updateTrip(trip.copy(spentBudget = totalActivityCosts + totalExpenses))
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch expenses and splits", e)
        }
    }

    suspend fun pushTrip(trip: com.example.data.local.entity.TripEntity): Boolean = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext false
        try {
            val remoteTripId = toUuid(trip.id)
            val json = JSONObject().apply {
                put("id", remoteTripId)
                put("user_id", trip.userId)
                val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
                if (trip.destinationId.matches(uuidRegex)) {
                    put("destination_id", trip.destinationId)
                }
                put("destination_name", trip.destinationName)
                put("destination_region", trip.destinationRegion)
                put("destination_country", trip.destinationCountry)
                put("cover_image_url", trip.imageUrl)
                put("start_date", trip.startDate)
                put("end_date", trip.endDate)
                put("number_of_days", trip.numberOfDays)
                put("travellers_count", trip.travellersCount)
                put("travellers_description", trip.travellersDescription)
                put("starting_location", trip.startingLocation)
                put("total_budget", trip.totalBudget)
                put("spent_budget", trip.spentBudget)
                put("pace", trip.pace)
                put("status", trip.status)
            }
            val req = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_trips?on_conflict=id")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val res = httpClient.newCall(req).execute()
            val successful = res.isSuccessful
            if (!successful) {
                Log.w(tag, "Failed to push trip (${res.code}): ${res.body?.string()}")
            }
            res.close()
            return@withContext successful
        } catch (e: Exception) {
            Log.e(tag, "Failed to push trip", e)
            return@withContext false
        }
    }

    suspend fun fetchTripsForUser(deviceId: String) = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || apiKey.isBlank()) return@withContext
        try {
            // First fetch member rows to find trip IDs for this user
            val memReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_group_members?user_id=eq.$deviceId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val memRes = httpClient.newCall(memReq).execute()
            val tripIds = mutableSetOf<String>()
            if (memRes.isSuccessful) {
                val memArray = JSONArray(memRes.body?.string() ?: "[]")
                for (i in 0 until memArray.length()) {
                    tripIds.add(memArray.getJSONObject(i).getString("trip_id"))
                }
            }
            memRes.close()

            // Also check for trips created directly by this device/user
            val ownedReq = Request.Builder()
                .url("$supabaseUrl/rest/v1/user_trips?user_id=eq.$deviceId")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val ownedRes = httpClient.newCall(ownedReq).execute()
            if (ownedRes.isSuccessful) {
                val ownedArray = JSONArray(ownedRes.body?.string() ?: "[]")
                for (i in 0 until ownedArray.length()) {
                    tripIds.add(ownedArray.getJSONObject(i).getString("id"))
                }
            }
            ownedRes.close()

            // For each trip ID, fetch the trip details
            val tripsList = mutableListOf<com.example.data.local.entity.TripEntity>()
            for (tripId in tripIds) {
                val tripReq = Request.Builder()
                    .url("$supabaseUrl/rest/v1/user_trips?id=eq.$tripId")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                val tripRes = httpClient.newCall(tripReq).execute()
                if (tripRes.isSuccessful) {
                    val tripArray = JSONArray(tripRes.body?.string() ?: "[]")
                    if (tripArray.length() > 0) {
                        val obj = tripArray.getJSONObject(0)
                        tripsList.add(
                            com.example.data.local.entity.TripEntity(
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
                        )
                    }
                }
                tripRes.close()
            }

            if (tripsList.isNotEmpty()) {
                tripsList.forEach { db.tripDao().insertTrip(it) }
            }

            // Also trigger fetch for expenses/members for each trip
            tripIds.forEach { fetchExpensesAndSplits(it) }

        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch trips for user", e)
        }
    }
}
