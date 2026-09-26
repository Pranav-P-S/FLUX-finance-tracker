package com.flux.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

/** Lifecycle of a money event, decided by the capture pipeline. */
object TransactionKind {
    const val PURCHASE = "purchase"
    const val REFUND = "refund"
    const val PENDING = "pending"
}

/**
 * A single money event extracted from a notification. [hash] is the dedup key:
 * SHA-256(raw alert text | posting app) — see com.flux.app.engine.Dedup.
 * [amount] is signed: debits are negative, credits positive.
 *
 * [kind] controls accounting: refunds offset their category instead of
 * counting as income, and pending holds are excluded from every aggregate
 * until they settle or expire.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["hash"], unique = true),
        // Inbox: WHERE needsReview = 1 ORDER BY timestamp DESC.
        Index(value = ["needsReview", "timestamp"]),
        // Ledger pages and date-range aggregates.
        Index(value = ["timestamp"]),
        // Holds sweep and TTL expiry.
        Index(value = ["kind", "createdAt"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val accountHint: String?,
    val timestamp: Long,
    val sourcePackage: String,
    val rawText: String,
    val category: String,
    val categoryConfidence: Double,
    val needsReview: Boolean,
    val parseMethod: String,
    // The defaultValue mirrors MIGRATION_1_2's SQL DEFAULT so fresh installs
    // and migrated databases share one schema.
    @ColumnInfo(defaultValue = "'${TransactionKind.PURCHASE}'")
    val kind: String = TransactionKind.PURCHASE,
    val createdAt: Long,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val label: String,
    val color: Long,
    val icon: String,
    val keywords: List<String>,
    val isDefault: Boolean,
)

class KeywordConverter {
    @TypeConverter
    fun fromKeywords(value: List<String>): String = value.joinToString("\u0001")

    @TypeConverter
    fun toKeywords(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\u0001")
}

/** One labeled example for the on-device Naive Bayes model. */
@Entity(tableName = "training_samples")
data class TrainingSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val categoryId: String,
)

@Entity(tableName = "settings")
data class SettingEntry(
    @PrimaryKey val key: String,
    val value: String,
)
