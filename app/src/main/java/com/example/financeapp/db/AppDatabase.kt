package com.example.financeapp.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SimpleRecordEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun simpleRecordDao(): SimpleRecordDao
}
