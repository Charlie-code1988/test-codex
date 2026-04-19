package com.example.financeapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "simple_records")
data class SimpleRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)
