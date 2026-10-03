package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.sync.SyncStatus
import com.example.ui.MainViewModel
import com.example.ui.screens.AddEditExpenseDialog
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.BudgetDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.SyncSettingsScreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme

enum class AppNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    DASHBOARD("Overview", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "tab_dashboard"),
    LEDGER("Ledger", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "tab_ledger"),
    ANALYTICS("Insights", Icons.Filled.BarChart, Icons.Outlined.BarChart, "tab_analytics"),
    SYNC("Sync Vault", Icons.Filled.CloudSync, Icons.Outlined.CloudSync, "tab_sync")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SyncSpendApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSpendApp(viewModel: MainViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(AppNavTab.DASHBOARD) }

    // State Collection
    val activeExpenses by viewModel.activeExpenses.collectAsStateWithLifecycle()
    val filteredExpenses by viewModel.filteredExpenses.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val vaultCode by viewModel.vaultCode.collectAsStateWithLifecycle()
    val lastSyncedTime by viewModel.lastSyncedTime.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()
    val peerDevices by viewModel.peerDevices.collectAsStateWithLifecycle()

    val currentTotals by viewModel.currentTotals.collectAsStateWithLifecycle()
    val monthlyExpenseTotal by viewModel.monthlyExpenseTotal.collectAsStateWithLifecycle()

    val timeFilter by viewModel.timeFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()

    val showAddEditSheet by viewModel.showAddEditSheet.collectAsStateWithLifecycle()
    val editingExpense by viewModel.editingExpense.collectAsStateWithLifecycle()
    val showBudgetDialog by viewModel.showBudgetDialog.collectAsStateWithLifecycle()
    val showSyncSheet by viewModel.showSyncSheet.collectAsStateWithLifecycle()

    // Handle back button
    BackHandler(enabled = currentTab != AppNavTab.DASHBOARD || showAddEditSheet || showBudgetDialog) {
        when {
            showAddEditSheet -> viewModel.closeAddEditSheet()
            showBudgetDialog -> viewModel.closeBudgetDialog()
            else -> currentTab = AppNavTab.DASHBOARD
        }
    }

    val overallBudget = budgets.firstOrNull { it.category == "ALL" }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Sync",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = EmeraldPrimary
                        )
                        Text(
                            text = "Spend",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Quick Vault indicator in top bar
                    IconButton(
                        onClick = { currentTab = AppNavTab.SYNC },
                        modifier = Modifier.testTag("top_sync_action")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val dotColor = when (syncStatus) {
                                SyncStatus.CONNECTED -> EmeraldPrimary
                                SyncStatus.CONNECTING, SyncStatus.SYNCING -> AmberWarning
                                SyncStatus.OFFLINE -> CoralExpense
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sync",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                AppNavTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        },
                        modifier = Modifier.testTag(tab.tag),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            indicatorColor = EmeraldPrimary.copy(alpha = 0.15f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppNavTab.DASHBOARD -> {
                    DashboardScreen(
                        syncStatus = syncStatus,
                        vaultCode = vaultCode,
                        peerCount = peerDevices.size,
                        totals = currentTotals,
                        monthlyExpenseTotal = monthlyExpenseTotal,
                        monthlyBudget = overallBudget,
                        recentExpenses = activeExpenses,
                        syncLogs = syncLogs,
                        currentDeviceId = viewModel.currentDeviceId,
                        onOpenSyncSettings = { currentTab = AppNavTab.SYNC },
                        onOpenAddExpense = { viewModel.openAddExpense() },
                        onQuickAdd = { title, amount, type, category ->
                            viewModel.saveExpense(
                                id = null,
                                title = title,
                                amount = amount,
                                type = type,
                                category = category,
                                accountName = "Main Card",
                                date = System.currentTimeMillis(),
                                note = "Quick add"
                            )
                        },
                        onExpenseClick = { expense -> viewModel.openEditExpense(expense) },
                        onDeleteExpense = { id -> viewModel.deleteExpense(id) },
                        onNavigateToLedger = { currentTab = AppNavTab.LEDGER },
                        onOpenBudgetDialog = { viewModel.openBudgetDialog() }
                    )
                }

                AppNavTab.LEDGER -> {
                    LedgerScreen(
                        expenses = filteredExpenses,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        timeFilter = timeFilter,
                        onTimeFilterChange = { viewModel.setTimeFilter(it) },
                        typeFilter = typeFilter,
                        onTypeFilterChange = { viewModel.setTypeFilter(it) },
                        categoryFilter = categoryFilter,
                        onCategoryFilterChange = { viewModel.setCategoryFilter(it) },
                        currentDeviceId = viewModel.currentDeviceId,
                        onExpenseClick = { expense -> viewModel.openEditExpense(expense) },
                        onDeleteExpense = { id -> viewModel.deleteExpense(id) },
                        onOpenAddExpense = { viewModel.openAddExpense() }
                    )
                }

                AppNavTab.ANALYTICS -> {
                    AnalyticsScreen(
                        expenses = activeExpenses,
                        budgets = budgets,
                        onCategoryClick = { category ->
                            viewModel.setCategoryFilter(category)
                            currentTab = AppNavTab.LEDGER
                        },
                        onOpenBudgetDialog = { viewModel.openBudgetDialog() }
                    )
                }

                AppNavTab.SYNC -> {
                    SyncSettingsScreen(
                        syncStatus = syncStatus,
                        vaultCode = vaultCode,
                        lastSyncedTime = lastSyncedTime,
                        peerDevices = peerDevices,
                        syncLogs = syncLogs,
                        currentDeviceId = viewModel.currentDeviceId,
                        currentDeviceName = viewModel.currentDeviceName,
                        onUpdateVaultCode = { viewModel.updateVaultCode(it) },
                        onGenerateNewVault = { viewModel.generateNewVault() },
                        onUpdateDeviceName = { viewModel.updateDeviceName(it) },
                        onPushFullVault = { viewModel.pushFullVault() },
                        onSimulatePartnerTransaction = { viewModel.simulatePartnerTransaction() }
                    )
                }
            }
        }
    }

    // Add / Edit Transaction Sheet
    if (showAddEditSheet) {
        AddEditExpenseDialog(
            expenseToEdit = editingExpense,
            onDismiss = { viewModel.closeAddEditSheet() },
            onSave = { id, title, amount, type, category, accountName, date, note ->
                viewModel.saveExpense(id, title, amount, type, category, accountName, date, note)
            }
        )
    }

    // Budget Configuration Dialog
    if (showBudgetDialog) {
        BudgetDialog(
            currentLimit = overallBudget?.monthlyLimit ?: 2500.0,
            onDismiss = { viewModel.closeBudgetDialog() },
            onSave = { category, limit ->
                viewModel.setBudget(category, limit)
            }
        )
    }
}
