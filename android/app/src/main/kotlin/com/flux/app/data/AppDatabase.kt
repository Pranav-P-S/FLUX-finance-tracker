package com.flux.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        TrainingSample::class,
        SettingEntry::class,
    ],
    version = 2,
    exportSchema = false,
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
    }
}
