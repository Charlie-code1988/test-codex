package com.example.financeapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SimpleRecordDao {
    @Insert
    suspend fun insert(record: SimpleRecordEntity): Long

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SimpleRecordEntity>>
}
