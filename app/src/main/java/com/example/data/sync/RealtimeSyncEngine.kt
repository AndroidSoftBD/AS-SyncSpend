package com.example.data.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.BudgetEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.SyncLogItem
import com.example.data.model.SyncPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class SyncStatus {
    CONNECTED,
    CONNECTING,
    SYNCING,
    OFFLINE
}

data class PeerDevice(
    val id: String,
    val name: String,
    val lastSeen: Long
)

class RealtimeSyncEngine(
    private val context: Context,
    private val database: AppDatabase
) {
    private val TAG = "RealtimeSyncEngine"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val prefs = context.getSharedPreferences("syncspend_prefs", Context.MODE_PRIVATE)

    // Device Identification
    val deviceId: String = getOrCreateDeviceId()
    var deviceName: String = getOrCreateDeviceName()
        private set

    // Vault Code
    private val _vaultCode = MutableStateFlow(getSavedVaultCode())
    val vaultCode: StateFlow<String> = _vaultCode.asStateFlow()

    // Sync State
    private val _syncStatus = MutableStateFlow(SyncStatus.OFFLINE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow(System.currentTimeMillis())
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<SyncLogItem>>(emptyList())
    val syncLogs: StateFlow<List<SyncLogItem>> = _syncLogs.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, PeerDevice>>(emptyMap())
    val peers: StateFlow<Map<String, PeerDevice>> = _peers.asStateFlow()

    private var listenerJob: Job? = null
    private var pingJob: Job? = null

    // OkHttpClient with infinite read timeout for SSE stream
    private val streamClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    // Client for standard POSTs and catch-up polls
    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    init {
        startSync()
    }

    private fun getOrCreateDeviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = "dev_" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    private fun getOrCreateDeviceName(): String {
        var name = prefs.getString("device_name", null)
        if (name == null) {
            val model = Build.MODEL
            name = if (model.isNotBlank()) model else "Android Device"
            prefs.edit().putString("device_name", name).apply()
        }
        return name
    }

    private fun getSavedVaultCode(): String {
        var code = prefs.getString("vault_code", null)
        if (code == null) {
            // Generate clean human-friendly vault code
            code = "SYNC-" + (1000..9999).random().toString()
            prefs.edit().putString("vault_code", code).apply()
        }
        return code
    }

    fun updateDeviceName(newName: String) {
        val clean = newName.trim()
        if (clean.isNotBlank()) {
            deviceName = clean
            prefs.edit().putString("device_name", clean).apply()
            broadcastPing()
        }
    }

    fun setVaultCode(newVault: String) {
        val clean = newVault.trim().uppercase()
        if (clean.isNotBlank() && clean != _vaultCode.value) {
            _vaultCode.value = clean
            prefs.edit().putString("vault_code", clean).apply()
            // Clear peers for previous vault
            _peers.value = emptyMap()
            // Restart stream on new channel
            startSync()
        }
    }

    fun generateNewVaultCode(): String {
        val code = "SPEND-" + (1000..9999).random().toString()
        setVaultCode(code)
        return code
    }

    fun startSync() {
        listenerJob?.cancel()
        pingJob?.cancel()

        listenerJob = scope.launch {
            while (isActive) {
                try {
                    _syncStatus.value = SyncStatus.CONNECTING
                    val topic = getTopicForVault(_vaultCode.value)

                    // 1. Initial catch-up: poll any recent messages from last 24h
                    pollRecentUpdates(topic)

                    // 2. Push any local offline pending items
                    pushPendingChanges()

                    // 3. Connect to live SSE stream
                    connectLiveStream(topic)
                } catch (e: Exception) {
                    Log.w(TAG, "Stream disconnected: ${e.message}, reconnecting in 5s...")
                    _syncStatus.value = SyncStatus.OFFLINE
                    delay(5000)
                }
            }
        }

        // Periodic ping to discover and maintain peer device presence
        pingJob = scope.launch {
            while (isActive) {
                broadcastPing()
                delay(30000) // ping every 30 seconds
            }
        }
    }

    private fun getTopicForVault(vault: String): String {
        val clean = vault.lowercase().replace(Regex("[^a-z0-9]"), "")
        return "syncspend_$clean"
    }

    private suspend fun pollRecentUpdates(topic: String) = withContext(Dispatchers.IO) {
        try {
            val url = "https://ntfy.sh/$topic/json?poll=1&since=24h"
            val request = Request.Builder().url(url).build()
            val response: Response = apiClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.charStream()?.use { reader ->
                    BufferedReader(reader).forEachLine { line ->
                        handleIncomingRawJson(line)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Poll recent failed: ${e.message}")
        }
    }

    private suspend fun connectLiveStream(topic: String) = withContext(Dispatchers.IO) {
        val url = "https://ntfy.sh/$topic/json"
        val request = Request.Builder().url(url).build()

        _syncStatus.value = SyncStatus.CONNECTED
        _lastSyncedTime.value = System.currentTimeMillis()

        streamClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Stream response code: ${response.code}")
            }

            val inputStream = response.body?.byteStream() ?: throw Exception("Null body")
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                _syncStatus.value = SyncStatus.CONNECTED
                line?.let { handleIncomingRawJson(it) }
            }
        }
    }

    private fun handleIncomingRawJson(rawLine: String) {
        try {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return

            val wrapper = JSONObject(trimmed)
            val eventType = wrapper.optString("event")
            if (eventType != "message") return

            val messageContent = wrapper.optString("message", "")
            if (messageContent.isEmpty()) return

            val payload = SyncPayload.fromJsonString(messageContent) ?: return

            // Ignore our own broadcasted updates
            if (payload.deviceId == deviceId) {
                return
            }

            // Verify vault matches
            if (payload.vaultCode != _vaultCode.value) {
                return
            }

            // Register peer
            updatePeerDevice(payload.deviceId, payload.deviceName)

            scope.launch(Dispatchers.IO) {
                processIncomingPayload(payload)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse incoming stream message: ${e.message}")
        }
    }

    private suspend fun processIncomingPayload(payload: SyncPayload) {
        _lastSyncedTime.value = System.currentTimeMillis()

        when (payload.action) {
            "UPSERT_EXPENSE" -> {
                payload.expense?.let { exp ->
                    val applied = database.expenseDao().upsertRemoteExpense(exp)
                    if (applied) {
                        addLog(
                            deviceName = payload.deviceName,
                            action = "Synced ${exp.type.lowercase()}: \"${exp.title}\" ($${String.format("%.2f", exp.amount)})",
                            isIncoming = true
                        )
                    }
                }
            }
            "DELETE_EXPENSE" -> {
                payload.deletedExpenseId?.let { id ->
                    database.expenseDao().softDelete(
                        id = id,
                        updatedAt = payload.timestamp,
                        deviceId = payload.deviceId,
                        deviceName = payload.deviceName,
                        syncStatus = "SYNCED"
                    )
                    addLog(
                        deviceName = payload.deviceName,
                        action = "Deleted transaction",
                        isIncoming = true
                    )
                }
            }
            "SET_BUDGET" -> {
                payload.budget?.let { b ->
                    database.budgetDao().setBudget(b)
                    addLog(
                        deviceName = payload.deviceName,
                        action = "Updated budget for ${b.category} to $${b.monthlyLimit.toInt()}",
                        isIncoming = true
                    )
                }
            }
            "PING" -> {
                // Presence notification already handled
            }
            "SIMULATED_PEER" -> {
                payload.expense?.let { exp ->
                    database.expenseDao().upsertRemoteExpense(exp)
                    addLog(
                        deviceName = payload.deviceName,
                        action = "Simulated Live Sync: \"${exp.title}\" ($${String.format("%.2f", exp.amount)})",
                        isIncoming = true
                    )
                }
            }
        }
    }

    private fun updatePeerDevice(peerId: String, peerName: String) {
        val current = _peers.value.toMutableMap()
        current[peerId] = PeerDevice(peerId, peerName, System.currentTimeMillis())
        _peers.value = current
    }

    private fun addLog(deviceName: String, action: String, isIncoming: Boolean) {
        val item = SyncLogItem(
            id = UUID.randomUUID().toString(),
            time = System.currentTimeMillis(),
            deviceName = deviceName,
            actionDescription = action,
            isIncoming = isIncoming
        )
        val list = _syncLogs.value.toMutableList()
        list.add(0, item)
        if (list.size > 50) {
            _syncLogs.value = list.take(50)
        } else {
            _syncLogs.value = list
        }
    }

    fun broadcastPing() {
        val payload = SyncPayload(
            eventId = UUID.randomUUID().toString(),
            vaultCode = _vaultCode.value,
            action = "PING",
            timestamp = System.currentTimeMillis(),
            deviceId = deviceId,
            deviceName = deviceName
        )
        scope.launch(Dispatchers.IO) {
            sendPayload(payload)
        }
    }

    fun broadcastExpenseUpsert(expense: ExpenseEntity) {
        val payload = SyncPayload(
            eventId = UUID.randomUUID().toString(),
            vaultCode = _vaultCode.value,
            action = "UPSERT_EXPENSE",
            timestamp = expense.updatedAt,
            deviceId = deviceId,
            deviceName = deviceName,
            expense = expense
        )
        scope.launch(Dispatchers.IO) {
            val success = sendPayload(payload)
            if (success) {
                database.expenseDao().markSynced(expense.id)
                addLog(
                    deviceName = "This Device",
                    action = "Broadcasted ${expense.type.lowercase()} \"${expense.title}\"",
                    isIncoming = false
                )
            }
        }
    }

    fun broadcastExpenseDelete(id: String) {
        val payload = SyncPayload(
            eventId = UUID.randomUUID().toString(),
            vaultCode = _vaultCode.value,
            action = "DELETE_EXPENSE",
            timestamp = System.currentTimeMillis(),
            deviceId = deviceId,
            deviceName = deviceName,
            deletedExpenseId = id
        )
        scope.launch(Dispatchers.IO) {
            val success = sendPayload(payload)
            if (success) {
                addLog(
                    deviceName = "This Device",
                    action = "Broadcasted deletion",
                    isIncoming = false
                )
            }
        }
    }

    fun broadcastBudget(budget: BudgetEntity) {
        val payload = SyncPayload(
            eventId = UUID.randomUUID().toString(),
            vaultCode = _vaultCode.value,
            action = "SET_BUDGET",
            timestamp = budget.updatedAt,
            deviceId = deviceId,
            deviceName = deviceName,
            budget = budget
        )
        scope.launch(Dispatchers.IO) {
            sendPayload(payload)
            addLog(
                deviceName = "This Device",
                action = "Broadcasted budget update for ${budget.category}",
                isIncoming = false
            )
        }
    }

    private suspend fun sendPayload(payload: SyncPayload): Boolean = withContext(Dispatchers.IO) {
        try {
            val topic = getTopicForVault(payload.vaultCode)
            val jsonString = payload.toJsonString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonString.toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://ntfy.sh/$topic")
                .post(body)
                .addHeader("Title", "SyncSpend Event")
                .addHeader("Priority", "default")
                .build()

            val response = apiClient.newCall(request).execute()
            val successful = response.isSuccessful
            response.close()
            successful
        } catch (e: Exception) {
            Log.w(TAG, "Send payload error: ${e.message}")
            false
        }
    }

    suspend fun pushPendingChanges() = withContext(Dispatchers.IO) {
        val pending = database.expenseDao().getPendingPushExpenses()
        if (pending.isNotEmpty()) {
            _syncStatus.value = SyncStatus.SYNCING
            for (expense in pending) {
                val success = sendPayload(
                    SyncPayload(
                        eventId = UUID.randomUUID().toString(),
                        vaultCode = _vaultCode.value,
                        action = if (expense.isDeleted) "DELETE_EXPENSE" else "UPSERT_EXPENSE",
                        timestamp = expense.updatedAt,
                        deviceId = deviceId,
                        deviceName = deviceName,
                        expense = if (expense.isDeleted) null else expense,
                        deletedExpenseId = if (expense.isDeleted) expense.id else null
                    )
                )
                if (success) {
                    database.expenseDao().markSynced(expense.id)
                }
            }
            _syncStatus.value = SyncStatus.CONNECTED
        }
    }

    /**
     * Pushes all local records to the vault so any newly paired device gets the full ledger.
     */
    suspend fun pushFullVault() = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.SYNCING
        val allExpenses = database.expenseDao().getAllExpensesForSync()
        for (expense in allExpenses) {
            sendPayload(
                SyncPayload(
                    eventId = UUID.randomUUID().toString(),
                    vaultCode = _vaultCode.value,
                    action = if (expense.isDeleted) "DELETE_EXPENSE" else "UPSERT_EXPENSE",
                    timestamp = expense.updatedAt,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    expense = if (expense.isDeleted) null else expense,
                    deletedExpenseId = if (expense.isDeleted) expense.id else null
                )
            )
            delay(100) // slight delay to prevent rate limit
        }
        _syncStatus.value = SyncStatus.CONNECTED
        _lastSyncedTime.value = System.currentTimeMillis()
        addLog(
            deviceName = "This Device",
            action = "Pushed ${allExpenses.size} records to Vault",
            isIncoming = false
        )
    }

    /**
     * Simulates an incoming transaction from another device (e.g. Partner's Phone)
     * Demonstrates instant real-time synchronization in single-device or demo testing.
     */
    fun simulateRemotePeerTransaction(title: String, amount: Double, category: String) {
        scope.launch(Dispatchers.IO) {
            val peerId = "peer_device_b_99"
            val peerName = "Partner's Phone"
            val simulatedExpense = ExpenseEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                amount = amount,
                type = "EXPENSE",
                category = category,
                accountName = "Shared Debit",
                date = System.currentTimeMillis(),
                note = "Added from Partner's device",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                deviceId = peerId,
                deviceName = peerName,
                isDeleted = false,
                syncStatus = "SYNCED"
            )

            // Broadcast to the channel so any real devices receive it too!
            val payload = SyncPayload(
                eventId = UUID.randomUUID().toString(),
                vaultCode = _vaultCode.value,
                action = "SIMULATED_PEER",
                timestamp = System.currentTimeMillis(),
                deviceId = peerId,
                deviceName = peerName,
                expense = simulatedExpense
            )

            // Also insert directly locally if stream echo takes a second
            database.expenseDao().upsertRemoteExpense(simulatedExpense)
            updatePeerDevice(peerId, peerName)
            addLog(
                deviceName = peerName,
                action = "Simulated Live Sync: \"$title\" ($${String.format("%.2f", amount)})",
                isIncoming = true
            )

            // Broadcast out so other connected devices receive it
            sendPayload(payload)
        }
    }
}
