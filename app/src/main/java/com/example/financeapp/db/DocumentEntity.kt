package com.example.financeapp.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.financeapp.data.ClassifyStatuses
import com.example.financeapp.data.DocTypes
import com.example.financeapp.data.ExtractStatuses
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
    val ocrUpdatedAt: Long? = null,
    val docType: String = DocTypes.UNKNOWN,
    val classifyStatus: String = ClassifyStatuses.IDLE,
    val classifyUpdatedAt: Long? = null,
    val classifyReason: String? = null,
    val extractStatus: String = ExtractStatuses.IDLE,
    val extractUpdatedAt: Long? = null,
    val extractReason: String? = null,
    val counterpartyName: String? = null,
    val documentDate: String? = null,
    val contractNo: String? = null,
    val productName: String? = null,
    val productModel: String? = null,
    val quantity: String? = null,
    val unitPrice: String? = null,
    val totalAmount: String? = null,
    val transactionDate: String? = null,
    val amount: String? = null,
    val direction: String? = null
)
