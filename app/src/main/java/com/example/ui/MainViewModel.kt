package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BudgetEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.SyncLogItem
import com.example.data.repository.ExpenseRepository
import com.example.data.sync.PeerDevice
import com.example.data.sync.RealtimeSyncEngine
import com.example.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class TimeRangeFilter {
    ALL,
    THIS_MONTH,
    THIS_WEEK,
    TODAY
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val syncEngine = RealtimeSyncEngine(application, database)
    private val repository = ExpenseRepository(database, syncEngine)

    val activeExpenses: StateFlow<List<ExpenseEntity>> = repository.allActiveExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = repository.allBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus
    val vaultCode: StateFlow<String> = repository.vaultCode
    val lastSyncedTime: StateFlow<Long> = repository.lastSyncedTime
    val syncLogs: StateFlow<List<SyncLogItem>> = repository.syncLogs
    val peerDevices: StateFlow<Map<String, PeerDevice>> = repository.peerDevices

    val currentDeviceId: String = repository.currentDeviceId
    val currentDeviceName: String = repository.currentDeviceName

    // Filters
    private val _timeFilter = MutableStateFlow(TimeRangeFilter.THIS_MONTH)
    val timeFilter: StateFlow<TimeRangeFilter> = _timeFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _typeFilter = MutableStateFlow("ALL") // "ALL", "EXPENSE", "INCOME"
    val typeFilter: StateFlow<String> = _typeFilter.asStateFlow()

    // Dialog & Navigation UI State
    private val _showAddEditSheet = MutableStateFlow(false)
    val showAddEditSheet: StateFlow<Boolean> = _showAddEditSheet.asStateFlow()

    private val _editingExpense = MutableStateFlow<ExpenseEntity?>(null)
    val editingExpense: StateFlow<ExpenseEntity?> = _editingExpense.asStateFlow()

    private val _showBudgetDialog = MutableStateFlow(false)
    val showBudgetDialog: StateFlow<Boolean> = _showBudgetDialog.asStateFlow()

    private val _showSyncSheet = MutableStateFlow(false)
    val showSyncSheet: StateFlow<Boolean> = _showSyncSheet.asStateFlow()

    // Filtered Expenses
    val filteredExpenses: StateFlow<List<ExpenseEntity>> = combine(
        activeExpenses,
        _timeFilter,
        _searchQuery,
        _categoryFilter,
        _typeFilter
    ) { list, time, query, category, type ->
        val now = Calendar.getInstance()

        list.filter { expense ->
            // Time range check
            val matchesTime = when (time) {
                TimeRangeFilter.ALL -> true
                TimeRangeFilter.TODAY -> {
                    val expCal = Calendar.getInstance().apply { timeInMillis = expense.date }
                    expCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            expCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                }
                TimeRangeFilter.THIS_WEEK -> {
                    val expCal = Calendar.getInstance().apply { timeInMillis = expense.date }
                    expCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            expCal.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)
                }
                TimeRangeFilter.THIS_MONTH -> {
                    val expCal = Calendar.getInstance().apply { timeInMillis = expense.date }
                    expCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            expCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                }
            }

            // Query check
            val matchesQuery = query.isBlank() ||
                    expense.title.contains(query, ignoreCase = true) ||
                    expense.category.contains(query, ignoreCase = true) ||
                    expense.note.contains(query, ignoreCase = true) ||
                    expense.accountName.contains(query, ignoreCase = true)

            // Category check
            val matchesCategory = category == null || expense.category.equals(category, ignoreCase = true)

            // Type check
            val matchesType = type == "ALL" || expense.type.equals(type, ignoreCase = true)

            matchesTime && matchesQuery && matchesCategory && matchesType
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial Totals for the current time filter
    val currentTotals: StateFlow<Triple<Double, Double, Double>> = filteredExpenses.combine(activeExpenses) { _, _ ->
        // Calculate based on the filtered set
        val currentList = filteredExpenses.value
        val income = currentList.filter { it.type == "INCOME" }.sumOf { it.amount }
        val expense = currentList.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        val balance = income - expense
        Triple(income, expense, balance)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0.0, 0.0, 0.0))

    // Monthly Expense Total for Budget Tracking
    val monthlyExpenseTotal: StateFlow<Double> = activeExpenses.combine(budgets) { list, _ ->
        val now = Calendar.getInstance()
        list.filter {
            if (it.type != "EXPENSE") return@filter false
            val expCal = Calendar.getInstance().apply { timeInMillis = it.date }
            expCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    expCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setTimeFilter(filter: TimeRangeFilter) {
        _timeFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun setTypeFilter(type: String) {
        _typeFilter.value = type
    }

    fun openAddExpense() {
        _editingExpense.value = null
        _showAddEditSheet.value = true
    }

    fun openEditExpense(expense: ExpenseEntity) {
        _editingExpense.value = expense
        _showAddEditSheet.value = true
    }

    fun closeAddEditSheet() {
        _showAddEditSheet.value = false
        _editingExpense.value = null
    }

    fun openBudgetDialog() {
        _showBudgetDialog.value = true
    }

    fun closeBudgetDialog() {
        _showBudgetDialog.value = false
    }

    fun openSyncSheet() {
        _showSyncSheet.value = true
    }

    fun closeSyncSheet() {
        _showSyncSheet.value = false
    }

    fun saveExpense(
        id: String?,
        title: String,
        amount: Double,
        type: String,
        category: String,
        accountName: String,
        date: Long,
        note: String
    ) {
        viewModelScope.launch {
            if (id == null) {
                repository.addExpense(title, amount, type, category, accountName, date, note)
            } else {
                val existing = _editingExpense.value
                val updated = (existing ?: ExpenseEntity(
                    id = id,
                    title = title,
                    amount = amount,
                    type = type,
                    category = category,
                    accountName = accountName,
                    date = date,
                    note = note
                )).copy(
                    title = title.trim(),
                    amount = amount,
                    type = type,
                    category = category,
                    accountName = accountName,
                    date = date,
                    note = note.trim()
                )
                repository.updateExpense(updated)
            }
            closeAddEditSheet()
        }
    }

    fun deleteExpense(id: String) {
        viewModelScope.launch {
            repository.deleteExpense(id)
        }
    }

    fun setBudget(category: String, limit: Double) {
        viewModelScope.launch {
            repository.setBudget(category, limit)
            closeBudgetDialog()
        }
    }

    fun updateVaultCode(newCode: String) {
        repository.setVaultCode(newCode)
    }

    fun generateNewVault() {
        repository.generateNewVaultCode()
    }

    fun updateDeviceName(name: String) {
        repository.updateDeviceName(name)
    }

    fun pushFullVault() {
        viewModelScope.launch {
            repository.pushFullVault()
        }
    }

    fun simulatePartnerTransaction() {
        val options = listOf(
            Triple("Blue Bottle Pour-over", 6.50, "Food & Dining"),
            Triple("Trader Joe's Groceries", 48.75, "Food & Dining"),
            Triple("Lyft Ride Home", 22.40, "Transport"),
            Triple("Target Household Items", 34.20, "Shopping"),
            Triple("Cinema Tickets for Two", 29.00, "Entertainment"),
            Triple("Pharmacy Health Essentials", 18.90, "Health")
        )
        val selected = options.random()
        repository.simulateRemotePeerTransaction(selected.first, selected.second, selected.third)
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
        }
    }
}
