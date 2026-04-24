package com.example.financeapp.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DocumentEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
