package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val type: String = "EXPENSE", // "EXPENSE" or "INCOME"
    val category: String = "Food & Dining",
    val accountName: String = "Main Card", // "Cash", "Main Card", "Bank Account", "Savings"
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = "default",
    val deviceName: String = "Android Device",
    val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED" // "SYNCED", "PENDING_PUSH"
)
