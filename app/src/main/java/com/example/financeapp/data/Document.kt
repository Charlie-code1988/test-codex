package com.example.financeapp.data

data class Document(
    val id: Long,
    val originalUri: String?,
    val appUri: String,
    val createdAt: Long,
    val status: String,
    val ocrStatus: String,
    val ocrRawText: String?,
    val ocrUpdatedAt: Long?
)

object OcrStatuses {
    const val IDLE = "IDLE"
    const val RUNNING = "RUNNING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}
