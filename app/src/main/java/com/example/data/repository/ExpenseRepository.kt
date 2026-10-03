package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.model.BudgetEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.SyncLogItem
import com.example.data.sync.PeerDevice
import com.example.data.sync.RealtimeSyncEngine
import com.example.data.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ExpenseRepository(
    private val database: AppDatabase,
    private val syncEngine: RealtimeSyncEngine
) {
    private val expenseDao = database.expenseDao()
    private val budgetDao = database.budgetDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    val allActiveExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllActiveExpenses()
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()

    val syncStatus: StateFlow<SyncStatus> = syncEngine.syncStatus
    val vaultCode: StateFlow<String> = syncEngine.vaultCode
    val lastSyncedTime: StateFlow<Long> = syncEngine.lastSyncedTime
    val syncLogs: StateFlow<List<SyncLogItem>> = syncEngine.syncLogs
    val peerDevices: StateFlow<Map<String, PeerDevice>> = syncEngine.peers

    val currentDeviceId: String get() = syncEngine.deviceId
    val currentDeviceName: String get() = syncEngine.deviceName

    init {
        // Pre-populate with realistic starter data if completely fresh
        coroutineScope.launch {
            checkAndSeedInitialData()
        }
    }

    private suspend fun checkAndSeedInitialData() {
        val existing = expenseDao.getAllActiveExpenses().first()
        if (existing.isEmpty()) {
            val now = System.currentTimeMillis()
            val dayMs = 86_400_000L

            val starterExpenses = listOf(
                ExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Monthly Salary Deposit",
                    amount = 4250.00,
                    type = "INCOME",
                    category = "Salary",
                    accountName = "Bank Account",
                    date = now - (dayMs * 2),
                    note = "Direct payroll deposit",
                    createdAt = now - (dayMs * 2),
                    updatedAt = now - (dayMs * 2),
                    deviceId = syncEngine.deviceId,
                    deviceName = syncEngine.deviceName,
                    syncStatus = "SYNCED"
                ),
                ExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Whole Foods Organic Market",
                    amount = 86.40,
                    type = "EXPENSE",
                    category = "Food & Dining",
                    accountName = "Main Card",
                    date = now - (dayMs * 1),
                    note = "Weekly groceries, fresh produce",
                    createdAt = now - (dayMs * 1),
                    updatedAt = now - (dayMs * 1),
                    deviceId = syncEngine.deviceId,
                    deviceName = syncEngine.deviceName,
                    syncStatus = "SYNCED"
                ),
                ExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    title = "High-speed Fiber Internet",
                    amount = 65.00,
                    type = "EXPENSE",
                    category = "Bills & Utilities",
                    accountName = "Bank Account",
                    date = now - (dayMs * 1) - 3600000L,
                    note = "Monthly recurring bill",
                    createdAt = now - (dayMs * 1) - 3600000L,
                    updatedAt = now - (dayMs * 1) - 3600000L,
                    deviceId = syncEngine.deviceId,
                    deviceName = syncEngine.deviceName,
                    syncStatus = "SYNCED"
                ),
                ExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Artisan Espresso & Pastry",
                    amount = 9.75,
                    type = "EXPENSE",
                    category = "Food & Dining",
                    accountName = "Main Card",
                    date = now - 7200000L,
                    note = "Morning coffee run",
                    createdAt = now - 7200000L,
                    updatedAt = now - 7200000L,
                    deviceId = syncEngine.deviceId,
                    deviceName = syncEngine.deviceName,
                    syncStatus = "SYNCED"
                ),
                ExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    title = "Metro Transit Pass",
                    amount = 32.50,
                    type = "EXPENSE",
                    category = "Transport",
                    accountName = "Main Card",
                    date = now - 3600000L,
                    note = "City weekly travel reload",
                    createdAt = now - 3600000L,
                    updatedAt = now - 3600000L,
                    deviceId = syncEngine.deviceId,
                    deviceName = syncEngine.deviceName,
                    syncStatus = "SYNCED"
                )
            )

            expenseDao.insertExpenses(starterExpenses)

            val starterBudgets = listOf(
                BudgetEntity("ALL", 2500.0, now, syncEngine.deviceId),
                BudgetEntity("Food & Dining", 600.0, now, syncEngine.deviceId),
                BudgetEntity("Transport", 200.0, now, syncEngine.deviceId),
                BudgetEntity("Shopping", 400.0, now, syncEngine.deviceId),
                BudgetEntity("Bills & Utilities", 350.0, now, syncEngine.deviceId)
            )
            budgetDao.insertBudgets(starterBudgets)
        }
    }

    suspend fun addExpense(
        title: String,
        amount: Double,
        type: String,
        category: String,
        accountName: String,
        date: Long,
        note: String
    ): ExpenseEntity {
        val now = System.currentTimeMillis()
        val expense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            amount = amount,
            type = type,
            category = category,
            accountName = accountName,
            date = date,
            note = note.trim(),
            createdAt = now,
            updatedAt = now,
            deviceId = syncEngine.deviceId,
            deviceName = syncEngine.deviceName,
            isDeleted = false,
            syncStatus = "PENDING_PUSH"
        )

        expenseDao.insertExpense(expense)
        syncEngine.broadcastExpenseUpsert(expense)
        return expense
    }

    suspend fun updateExpense(updated: ExpenseEntity) {
        val now = System.currentTimeMillis()
        val toSave = updated.copy(
            updatedAt = now,
            deviceId = syncEngine.deviceId,
            deviceName = syncEngine.deviceName,
            syncStatus = "PENDING_PUSH"
        )
        expenseDao.insertExpense(toSave)
        syncEngine.broadcastExpenseUpsert(toSave)
    }

    suspend fun deleteExpense(id: String) {
        val now = System.currentTimeMillis()
        expenseDao.softDelete(
            id = id,
            updatedAt = now,
            deviceId = syncEngine.deviceId,
            deviceName = syncEngine.deviceName,
            syncStatus = "PENDING_PUSH"
        )
        syncEngine.broadcastExpenseDelete(id)
    }

    suspend fun setBudget(category: String, limit: Double) {
        val budget = BudgetEntity(
            category = category,
            monthlyLimit = limit,
            updatedAt = System.currentTimeMillis(),
            deviceId = syncEngine.deviceId
        )
        budgetDao.setBudget(budget)
        syncEngine.broadcastBudget(budget)
    }

    fun setVaultCode(code: String) {
        syncEngine.setVaultCode(code)
    }

    fun generateNewVaultCode(): String {
        return syncEngine.generateNewVaultCode()
    }

    fun updateDeviceName(name: String) {
        syncEngine.updateDeviceName(name)
    }

    suspend fun pushFullVault() {
        syncEngine.pushFullVault()
    }

    fun simulateRemotePeerTransaction(title: String, amount: Double, category: String) {
        syncEngine.simulateRemotePeerTransaction(title, amount, category)
    }

    suspend fun clearAllData() {
        expenseDao.clearAll()
        budgetDao.clearAll()
    }
}
