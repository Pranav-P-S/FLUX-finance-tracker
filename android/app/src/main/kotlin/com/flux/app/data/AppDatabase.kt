package com.flux.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        TrainingSample::class,
        SettingEntry::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(KeywordConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun training(): TrainingDao
    abstract fun settings(): SettingsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN kind TEXT NOT NULL DEFAULT '${TransactionKind.PURCHASE}'"
                )
            }
        }

        /** v3: query-serving indices; the entity's index list describes them. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_needsReview_timestamp` " +
                        "ON `transactions` (`needsReview`, `timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` " +
                        "ON `transactions` (`timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_kind_createdAt` " +
                        "ON `transactions` (`kind`, `createdAt`)"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
