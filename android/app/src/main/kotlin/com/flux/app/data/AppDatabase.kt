package com.flux.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        TrainingSample::class,
        SettingEntry::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(KeywordConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactions(): TransactionDao
    abstract fun categories(): CategoryDao
    abstract fun training(): TrainingDao
    abstract fun settings(): SettingsDao
}
