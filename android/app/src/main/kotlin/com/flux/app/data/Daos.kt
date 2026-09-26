package com.flux.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TransactionDao {
    /** Returns -1 for rows discarded by the unique-hash dedup index. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(txs: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun inbox(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :pageSize OFFSET :page * :pageSize")
    suspend fun page(page: Int, pageSize: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp")
    suspend fun range(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions")
    suspend fun net(): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE amount > 0")
    suspend fun totalCredit(): Double

    @Query("SELECT COALESCE(SUM(-amount), 0) FROM transactions WHERE amount < 0")
    suspend fun totalDebit(): Double

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    suspend fun pendingReview(): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, label ASC")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("UPDATE transactions SET category = 'uncategorized', categoryConfidence = 0, needsReview = 1 WHERE category = :categoryId")
    suspend fun reassignTransactionsOnDelete(categoryId: String)
}

@Dao
interface TrainingDao {
    @Insert
    suspend fun add(sample: TrainingSample)

    @Insert
    suspend fun addAll(samples: List<TrainingSample>)

    @Query("SELECT * FROM training_samples")
    suspend fun all(): List<TrainingSample>

    @Query("SELECT COUNT(*) FROM training_samples")
    suspend fun count(): Int

    @Query("DELETE FROM training_samples")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: SettingEntry)

    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntry>
}
