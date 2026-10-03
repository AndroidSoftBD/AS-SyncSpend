package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.SyncLogItem
import com.example.data.sync.SyncStatus
import com.example.ui.components.BudgetProgressCard
import com.example.ui.components.ExpenseCard
import com.example.ui.components.SyncStatusBar
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldPrimary
import java.util.Locale

@Composable
fun DashboardScreen(
    syncStatus: SyncStatus,
    vaultCode: String,
    peerCount: Int,
    totals: Triple<Double, Double, Double>, // Income, Expense, Balance
    monthlyExpenseTotal: Double,
    monthlyBudget: BudgetEntity?,
    recentExpenses: List<ExpenseEntity>,
    syncLogs: List<SyncLogItem>,
    currentDeviceId: String,
    onOpenSyncSettings: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onQuickAdd: (title: String, amount: Double, type: String, category: String) -> Unit,
    onExpenseClick: (ExpenseEntity) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onNavigateToLedger: () -> Unit,
    onOpenBudgetDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (income, expense, balance) = totals
    val budgetLimit = monthlyBudget?.monthlyLimit ?: 2500.0

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Real-Time Sync Status Bar
            item {
                SyncStatusBar(
                    syncStatus = syncStatus,
                    vaultCode = vaultCode,
                    peerCount = peerCount,
                    onStatusClick = onOpenSyncSettings
                )
            }

            // Net Balance Hero Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Net Balance",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyanAccent.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Live Vault",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyanAccent
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", balance)}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (balance >= 0) MaterialTheme.colorScheme.onSurface else CoralExpense
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Income vs Expense sub-cards
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Income Pill
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(EmeraldPrimary.copy(alpha = 0.12f))
                                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(EmeraldPrimary.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Income",
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Income",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "+$${String.format(Locale.US, "%,.0f", income)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary
                                        )
                                    }
                                }

                                // Expense Pill
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(CoralExpense.copy(alpha = 0.12f))
                                        .border(1.dp, CoralExpense.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(CoralExpense.copy(alpha = 0.25f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Expense",
                                            tint = CoralExpense,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Expenses",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "-$${String.format(Locale.US, "%,.0f", expense)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralExpense
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Add Shortcut Bar
            item {
                Column {
                    Text(
                        text = "Quick Spend Shortcuts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            QuickPreset("Coffee", 5.50, "Food & Dining", Icons.Default.LocalCafe),
                            QuickPreset("Lunch", 15.00, "Food & Dining", Icons.Default.Restaurant),
                            QuickPreset("Groceries", 45.00, "Food & Dining", Icons.Default.ShoppingCart),
                            QuickPreset("Gas / Fuel", 35.00, "Transport", Icons.Default.LocalGasStation)
                        )
                        items(presets) { preset ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        onQuickAdd(preset.title, preset.amount, "EXPENSE", preset.category)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = preset.title,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+$${preset.amount.toInt()} ${preset.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Monthly Budget Progress
            item {
                BudgetProgressCard(
                    spent = monthlyExpenseTotal,
                    limit = budgetLimit,
                    onEditBudget = onOpenBudgetDialog
                )
            }

            // Live Cross-Device Sync Ticker
            if (syncLogs.isNotEmpty()) {
                item {
                    val latestLog = syncLogs.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { onOpenSyncSettings() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync activity",
                            tint = CyanAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${latestLog.deviceName}: ${latestLog.actionDescription}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Open sync",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Recent Transactions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "View Ledger →",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onNavigateToLedger() }
                            .padding(4.dp)
                    )
                }
            }

            // Recent Items
            if (recentExpenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions yet. Tap + to record your first spend!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(recentExpenses.take(5), key = { it.id }) { expense ->
                    ExpenseCard(
                        expense = expense,
                        currentDeviceId = currentDeviceId,
                        onClick = { onExpenseClick(expense) },
                        onDelete = { onDeleteExpense(expense.id) }
                    )
                }
            }
        }

        // Floating Action Button for adding custom expense
        FloatingActionButton(
            onClick = onOpenAddExpense,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_expense_fab"),
            containerColor = EmeraldPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add transaction",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

private data class QuickPreset(
    val title: String,
    val amount: Double,
    val category: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
