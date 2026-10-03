package com.example.data.model

import org.json.JSONObject

/**
 * Payload sent over the real-time sync channel.
 * Designed to be easily serialized and deserialized with zero fragile dependencies.
 */
data class SyncPayload(
    val eventId: String,
    val vaultCode: String,
    val action: String, // "UPSERT_EXPENSE", "DELETE_EXPENSE", "SET_BUDGET", "PING", "FULL_SYNC_REQUEST", "FULL_SYNC_RESPONSE"
    val timestamp: Long,
    val deviceId: String,
    val deviceName: String,
    val expense: ExpenseEntity? = null,
    val deletedExpenseId: String? = null,
    val budget: BudgetEntity? = null
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("eventId", eventId)
        root.put("vaultCode", vaultCode)
        root.put("action", action)
        root.put("timestamp", timestamp)
        root.put("deviceId", deviceId)
        root.put("deviceName", deviceName)

        expense?.let { exp ->
            val expObj = JSONObject()
            expObj.put("id", exp.id)
            expObj.put("title", exp.title)
            expObj.put("amount", exp.amount)
            expObj.put("type", exp.type)
            expObj.put("category", exp.category)
            expObj.put("accountName", exp.accountName)
            expObj.put("date", exp.date)
            expObj.put("note", exp.note)
            expObj.put("createdAt", exp.createdAt)
            expObj.put("updatedAt", exp.updatedAt)
            expObj.put("deviceId", exp.deviceId)
            expObj.put("deviceName", exp.deviceName)
            expObj.put("isDeleted", exp.isDeleted)
            root.put("expense", expObj)
        }

        deletedExpenseId?.let {
            root.put("deletedExpenseId", it)
        }

        budget?.let { b ->
            val bObj = JSONObject()
            bObj.put("category", b.category)
            bObj.put("monthlyLimit", b.monthlyLimit)
            bObj.put("updatedAt", b.updatedAt)
            bObj.put("deviceId", b.deviceId)
            root.put("budget", bObj)
        }

        return root.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): SyncPayload? {
            return try {
                val root = JSONObject(jsonStr)
                val eventId = root.optString("eventId", "")
                val vaultCode = root.optString("vaultCode", "")
                val action = root.optString("action", "")
                val timestamp = root.optLong("timestamp", System.currentTimeMillis())
                val deviceId = root.optString("deviceId", "unknown")
                val deviceName = root.optString("deviceName", "Unknown Device")

                var expense: ExpenseEntity? = null
                if (root.has("expense")) {
                    val expObj = root.getJSONObject("expense")
                    expense = ExpenseEntity(
                        id = expObj.getString("id"),
                        title = expObj.getString("title"),
                        amount = expObj.getDouble("amount"),
                        type = expObj.optString("type", "EXPENSE"),
                        category = expObj.optString("category", "Food & Dining"),
                        accountName = expObj.optString("accountName", "Main Card"),
                        date = expObj.optLong("date", System.currentTimeMillis()),
                        note = expObj.optString("note", ""),
                        createdAt = expObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = expObj.optLong("updatedAt", System.currentTimeMillis()),
                        deviceId = expObj.optString("deviceId", deviceId),
                        deviceName = expObj.optString("deviceName", deviceName),
                        isDeleted = expObj.optBoolean("isDeleted", false),
                        syncStatus = "SYNCED"
                    )
                }

                val deletedExpenseId = if (root.has("deletedExpenseId")) root.getString("deletedExpenseId") else null

                var budget: BudgetEntity? = null
                if (root.has("budget")) {
                    val bObj = root.getJSONObject("budget")
                    budget = BudgetEntity(
                        category = bObj.getString("category"),
                        monthlyLimit = bObj.getDouble("monthlyLimit"),
                        updatedAt = bObj.optLong("updatedAt", System.currentTimeMillis()),
                        deviceId = bObj.optString("deviceId", deviceId)
                    )
                }

                SyncPayload(
                    eventId = eventId,
                    vaultCode = vaultCode,
                    action = action,
                    timestamp = timestamp,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    expense = expense,
                    deletedExpenseId = deletedExpenseId,
                    budget = budget
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class SyncLogItem(
    val id: String,
    val time: Long,
    val deviceName: String,
    val actionDescription: String,
    val isIncoming: Boolean
)
