package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY date DESC")
    fun getAllActiveExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 0 AND date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getExpensesBetween(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE syncStatus = 'PENDING_PUSH'")
    suspend fun getPendingPushExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY updatedAt DESC")
    suspend fun getAllExpensesForSync(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Query("UPDATE expenses SET isDeleted = 1, updatedAt = :updatedAt, deviceId = :deviceId, deviceName = :deviceName, syncStatus = :syncStatus WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long, deviceId: String, deviceName: String, syncStatus: String)

    @Query("UPDATE expenses SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()

    @Transaction
    suspend fun upsertRemoteExpense(remote: ExpenseEntity): Boolean {
        val existing = getExpenseById(remote.id)
        if (existing == null) {
            insertExpense(remote.copy(syncStatus = "SYNCED"))
            return true
        } else if (remote.updatedAt >= existing.updatedAt) {
            insertExpense(remote.copy(syncStatus = "SYNCED"))
            return true
        }
        return false
    }
}
