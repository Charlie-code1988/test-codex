package com.example.financeapp.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert
    suspend fun insert(document: DocumentEntity): Long

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    fun observeById(documentId: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE originalUri = :originalUri LIMIT 1")
    suspend fun findByOriginalUri(originalUri: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getById(documentId: Long): DocumentEntity?

    @Query(
        """
        UPDATE documents
        SET ocrStatus = :ocrStatus,
            latinRawText = :latinRawText,
            chineseRawText = :chineseRawText,
            finalOcrText = :finalOcrText,
            ocrRawText = :ocrRawText,
            ocrUpdatedAt = :ocrUpdatedAt
        WHERE id = :documentId
        """
    )
    suspend fun updateOcrResult(
        documentId: Long,
        ocrStatus: String,
        latinRawText: String?,
        chineseRawText: String?,
        finalOcrText: String?,
        ocrRawText: String?,
        ocrUpdatedAt: Long
    )

    @Query(
        """
        UPDATE documents
        SET docType = :docType,
            classifyStatus = :classifyStatus,
            classifyUpdatedAt = :classifyUpdatedAt,
            classifyReason = :classifyReason
        WHERE id = :documentId
        """
    )
    suspend fun updateClassification(
        documentId: Long,
        docType: String,
        classifyStatus: String,
        classifyUpdatedAt: Long,
        classifyReason: String?
    )

    @Query(
        """
        UPDATE documents
        SET extractStatus = :extractStatus,
            extractUpdatedAt = :extractUpdatedAt,
            extractReason = :extractReason,
            counterpartyName = :counterpartyName,
            documentDate = :documentDate,
            contractNo = :contractNo,
            productName = :productName,
            productModel = :productModel,
            quantity = :quantity,
            unitPrice = :unitPrice,
            totalAmount = :totalAmount,
            transactionDate = :transactionDate,
            amount = :amount,
            direction = :direction,
            lineItemsText = :lineItemsText
        WHERE id = :documentId
        """
    )
    suspend fun updateExtraction(
        documentId: Long,
        extractStatus: String,
        extractUpdatedAt: Long,
        extractReason: String?,
        counterpartyName: String?,
        documentDate: String?,
        contractNo: String?,
        productName: String?,
        productModel: String?,
        quantity: String?,
        unitPrice: String?,
        totalAmount: String?,
        transactionDate: String?,
        amount: String?,
        direction: String?,
        lineItemsText: String?
    )
}
