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
    val classifyReason: String?,
    val parsedDocument: ParsedDocument?
)

data class ParsedDocument(
    val documentId: Long,
    val docType: String,
    val extractedFields: ExtractedFields,
    val extractStatus: String,
    val extractUpdatedAt: Long?,
    val extractReason: String?,
    val lineItemsRawText: String?
)

data class ExtractedLineItem(
    val productName: String = "",
    val productModel: String = "",
    val quantity: String = "",
    val unitPrice: String = "",
    val lineTotal: String = ""
)

data class ExtractedFields(
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
    val direction: String? = null,
    val lineItems: List<ExtractedLineItem> = emptyList()
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

object ExtractStatuses {
    const val IDLE = "IDLE"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}
