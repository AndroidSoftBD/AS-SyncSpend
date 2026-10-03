package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val category: String, // "ALL" for total monthly budget, or specific category like "Food & Dining"
    val monthlyLimit: Double,
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String = "default"
)
