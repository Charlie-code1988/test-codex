package com.example.financeapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.financeapp.data.OcrStatuses

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUri: String?,
    val appUri: String,
    val createdAt: Long,
    val status: String = "IMPORTED",
    val ocrStatus: String = OcrStatuses.IDLE,
    val latinRawText: String? = null,
    val chineseRawText: String? = null,
    val finalOcrText: String? = null,
    val ocrRawText: String? = null,
    val ocrUpdatedAt: Long? = null
)
