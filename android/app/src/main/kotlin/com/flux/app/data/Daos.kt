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

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    /** Net account movement. Pending holds have not left the account yet. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE kind != 'pending'")
    suspend fun net(): Double

    /** Earned income only — refunds offset spend instead of inflating income. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE amount > 0 AND kind = 'purchase'")
    suspend fun totalCredit(): Double

    @Query("SELECT COALESCE(SUM(-amount), 0) FROM transactions WHERE amount < 0 AND kind != 'pending'")
    suspend fun totalDebit(): Double

    /** Net/credit/debit restricted to the base currency: foreign-currency rows
     *  are unconverted and must never inflate the base-currency totals. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE kind != 'pending' AND currency = :currency")
    suspend fun netInCurrency(currency: String): Double

    @Query("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE amount > 0 AND kind = 'purchase' AND currency = :currency")
    suspend fun totalCreditInCurrency(currency: String): Double

    @Query("SELECT COALESCE(SUM(-amount), 0) FROM transactions WHERE amount < 0 AND kind != 'pending' AND currency = :currency")
    suspend fun totalDebitInCurrency(currency: String): Double

    /**
     * Per-category spend aggregated in SQL: debits add, refunds for the same
     * category subtract, holds are excluded, income rows are ignored, and
     * foreign-currency rows never enter base-currency totals.
     */
    @Query(
        """
        SELECT category AS categoryId,
               COALESCE(SUM(CASE WHEN amount < 0 OR kind = 'refund' THEN -amount ELSE 0 END), 0) AS total,
               SUM(CASE WHEN amount < 0 OR kind = 'refund' THEN 1 ELSE 0 END) AS count
        FROM transactions
        WHERE kind != 'pending'
          AND timestamp BETWEEN :start AND :end
          AND currency = :currency
          AND (amount < 0 OR kind = 'refund')
        GROUP BY category
        """,
    )
    suspend fun spendByCategory(start: Long, end: Long, currency: String): List<CategorySpendRow>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(-amount), 0) AS total
        FROM transactions
        WHERE amount < 0
          AND kind != 'pending'
          AND timestamp BETWEEN :start AND :end
          AND currency = :currency
        GROUP BY day
        ORDER BY day
        """,
    )
    suspend fun spendByDay(start: Long, end: Long, currency: String): List<DaySpendRow>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    suspend fun pendingReview(): Int

    @Query("SELECT * FROM transactions WHERE kind = 'pending' ORDER BY createdAt ASC")
    suspend fun pendingHolds(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE kind = 'pending' AND createdAt < :cutoffMs")
    suspend fun deletePendingBefore(cutoffMs: Long): Int

    /** Raw alert text only lives as long as it can be useful: triage and audit. */
    @Query("UPDATE transactions SET rawText = '' WHERE needsReview = 0 AND createdAt < :cutoffMs")
    suspend fun scrubRawTextBefore(cutoffMs: Long): Int

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

/** Projection row for [TransactionDao.spendByCategory]. */
data class CategorySpendRow(
    val categoryId: String,
    val total: Double,
    val count: Int,
)

/** Projection row for [TransactionDao.spendByDay]. */
data class DaySpendRow(
    val day: String,
    val total: Double,
)
