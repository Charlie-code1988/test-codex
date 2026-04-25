package com.example.financeapp.data

data class Document(
    val id: Long,
    val originalUri: String?,
    val appUri: String,
    val createdAt: Long,
    val status: String,
    val ocrStatus: String,
    val latinRawText: String?,
    val chineseRawText: String?,
    val finalOcrText: String?,
    val ocrRawText: String?,
    val ocrUpdatedAt: Long?,
    val docType: String,
    val classifyStatus: String,
    val classifyUpdatedAt: Long?,
    val classifyReason: String?
)

object OcrStatuses {
    const val IDLE = "IDLE"
    const val RUNNING = "RUNNING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}

object DocTypes {
    const val SALES_CONTRACT = "SALES_CONTRACT"
    const val PURCHASE_CONTRACT = "PURCHASE_CONTRACT"
    const val RECEIPT = "RECEIPT"
    const val PAYMENT = "PAYMENT"
    const val UNKNOWN = "UNKNOWN"
}

object ClassifyStatuses {
    const val IDLE = "IDLE"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}
