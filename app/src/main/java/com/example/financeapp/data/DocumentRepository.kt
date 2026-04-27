package com.example.financeapp.data

import android.content.Context
import android.net.Uri
import com.example.financeapp.db.DocumentDao
import com.example.financeapp.db.DocumentEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val dao: DocumentDao
) {

    private val textProcessor = OcrTextProcessor()
    private val docTypeClassifier = DocTypeClassifier()
    private val fieldExtractor = FieldExtractor()

    fun observeDocuments(): Flow<List<Document>> {
        return dao.observeAll().map { entities -> entities.map { it.toModel() } }
    }

    fun observeDocumentById(documentId: Long): Flow<Document?> {
        return dao.observeById(documentId).map { it?.toModel() }
    }

    suspend fun importDocument(originalUri: String): ImportResult {
        val existing = dao.findByOriginalUri(originalUri)
        if (existing != null) {
            return ImportResult.Duplicate(existing.toModel())
        }

        val persistedAppUri = copyToAppPrivateStorage(originalUri)
        dao.insert(
            DocumentEntity(
                originalUri = originalUri,
                appUri = persistedAppUri,
                createdAt = System.currentTimeMillis(),
                status = "IMPORTED"
            )
        )
        return ImportResult.Success
    }

    suspend fun runOcr(documentId: Long): OcrResult {
        val document = dao.getById(documentId) ?: return OcrResult.DocumentNotFound

        dao.updateOcrResult(
            documentId = documentId,
            ocrStatus = OcrStatuses.RUNNING,
            latinRawText = document.latinRawText,
            chineseRawText = document.chineseRawText,
            finalOcrText = document.finalOcrText,
            ocrRawText = document.ocrRawText,
            ocrUpdatedAt = System.currentTimeMillis()
        )

        return runCatching {
            val image = InputImage.fromFilePath(context, Uri.parse(document.appUri))

            val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

            val latinText = latinRecognizer.process(image).await().text.orEmpty()
            val chineseText = chineseRecognizer.process(image).await().text.orEmpty()

            val finalText = textProcessor.buildFinalOcrText(latinText, chineseText)

            dao.updateOcrResult(
                documentId = documentId,
                ocrStatus = OcrStatuses.SUCCESS,
                latinRawText = latinText,
                chineseRawText = chineseText,
                finalOcrText = finalText,
                ocrRawText = finalText,
                ocrUpdatedAt = System.currentTimeMillis()
            )
            OcrResult.Success
        }.getOrElse { throwable ->
            dao.updateOcrResult(
                documentId = documentId,
                ocrStatus = OcrStatuses.FAILED,
                latinRawText = null,
                chineseRawText = null,
                finalOcrText = throwable.message,
                ocrRawText = throwable.message,
                ocrUpdatedAt = System.currentTimeMillis()
            )
            OcrResult.Failed(throwable.message ?: "OCR 失败")
        }
    }

    suspend fun classifyDocType(documentId: Long): ClassifyDocTypeResult {
        val document = dao.getById(documentId) ?: return ClassifyDocTypeResult.DocumentNotFound
        val text = document.finalOcrText.orEmpty()

        return runCatching {
            val classify = docTypeClassifier.classify(text)
            dao.updateClassification(
                documentId = documentId,
                docType = classify.docType,
                classifyStatus = ClassifyStatuses.SUCCESS,
                classifyUpdatedAt = System.currentTimeMillis(),
                classifyReason = classify.reason
            )
            ClassifyDocTypeResult.Success
        }.getOrElse { throwable ->
            dao.updateClassification(
                documentId = documentId,
                docType = DocTypes.UNKNOWN,
                classifyStatus = ClassifyStatuses.FAILED,
                classifyUpdatedAt = System.currentTimeMillis(),
                classifyReason = throwable.message ?: "分类失败"
            )
            ClassifyDocTypeResult.Failed(throwable.message ?: "分类失败")
        }
    }

    suspend fun extractFields(documentId: Long): ExtractFieldsResult {
        val document = dao.getById(documentId) ?: return ExtractFieldsResult.DocumentNotFound
        val text = document.finalOcrText.orEmpty()

        return runCatching {
            val extracted = fieldExtractor.extract(document.docType, text)
            persistExtractedFields(documentId, extracted.fields, ExtractStatuses.SUCCESS, extracted.reason)
            ExtractFieldsResult.Success
        }.getOrElse { throwable ->
            persistExtractedFields(documentId, ExtractedFields(), ExtractStatuses.FAILED, throwable.message ?: "字段抽取失败")
            ExtractFieldsResult.Failed(throwable.message ?: "字段抽取失败")
        }
    }

    suspend fun saveExtractedFields(documentId: Long, fields: ExtractedFields): SaveExtractedFieldsResult {
        val document = dao.getById(documentId) ?: return SaveExtractedFieldsResult.DocumentNotFound
        return runCatching {
            persistExtractedFields(document.id, fields, document.extractStatus, document.extractReason)
            SaveExtractedFieldsResult.Success
        }.getOrElse { throwable ->
            SaveExtractedFieldsResult.Failed(throwable.message ?: "保存抽取字段失败")
        }
    }

    private suspend fun persistExtractedFields(
        documentId: Long,
        fields: ExtractedFields,
        status: String,
        reason: String?
    ) {
        dao.updateExtraction(
            documentId = documentId,
            extractStatus = status,
            extractUpdatedAt = System.currentTimeMillis(),
            extractReason = reason,
            counterpartyName = fields.counterpartyName,
            documentDate = fields.documentDate,
            contractNo = fields.contractNo,
            productName = fields.productName,
            productModel = fields.productModel,
            quantity = fields.quantity,
            unitPrice = fields.unitPrice,
            totalAmount = fields.totalAmount,
            transactionDate = fields.transactionDate,
            amount = fields.amount,
            direction = fields.direction,
            lineItemsText = serializeLineItems(fields.lineItems)
        )
    }


    private fun serializeLineItems(lineItems: List<ExtractedLineItem>): String? {
        if (lineItems.isEmpty()) return null
        return lineItems.joinToString("\n") { item ->
            listOf(item.productName, item.productModel, item.quantity, item.unitPrice, item.lineTotal)
                .joinToString("\t") { token -> token.replace("\t", " ").replace("\n", " ").trim() }
        }
    }

    private fun deserializeLineItems(serialized: String?): List<ExtractedLineItem> {
        if (serialized.isNullOrBlank()) return emptyList()
        return serialized.lines()
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.isEmpty()) return@mapNotNull null
                ExtractedLineItem(
                    productName = parts.getOrElse(0) { "" },
                    productModel = parts.getOrElse(1) { "" },
                    quantity = parts.getOrElse(2) { "" },
                    unitPrice = parts.getOrElse(3) { "" },
                    lineTotal = parts.getOrElse(4) { "" }
                )
            }
            .filter {
                it.productName.isNotBlank() || it.productModel.isNotBlank() || it.quantity.isNotBlank() ||
                    it.unitPrice.isNotBlank() || it.lineTotal.isNotBlank()
            }
    }

    private fun copyToAppPrivateStorage(originalUri: String): String {
        val sourceUri = Uri.parse(originalUri)
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(sourceUri)
            ?: error("Unable to open input stream for uri: $originalUri")

        val documentsDir = File(context.filesDir, "documents")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val extension = resolver.getType(sourceUri)
            ?.substringAfterLast('/', "jpg")
            ?.takeIf { it.isNotBlank() }
            ?: "jpg"

        val targetFile = File(documentsDir, "doc_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return Uri.fromFile(targetFile).toString()
    }

    private fun DocumentEntity.toModel(): Document {
        return Document(
            id = id,
            originalUri = originalUri,
            appUri = appUri,
            createdAt = createdAt,
            status = status,
            ocrStatus = ocrStatus,
            latinRawText = latinRawText,
            chineseRawText = chineseRawText,
            finalOcrText = finalOcrText,
            ocrRawText = ocrRawText,
            ocrUpdatedAt = ocrUpdatedAt,
            docType = docType,
            classifyStatus = classifyStatus,
            classifyUpdatedAt = classifyUpdatedAt,
            classifyReason = classifyReason,
            parsedDocument = ParsedDocument(
                documentId = id,
                docType = docType,
                extractStatus = extractStatus,
                extractUpdatedAt = extractUpdatedAt,
                extractReason = extractReason,
                extractedFields = ExtractedFields(
                    counterpartyName = counterpartyName,
                    documentDate = documentDate,
                    contractNo = contractNo,
                    productName = productName,
                    productModel = productModel,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    totalAmount = totalAmount,
                    transactionDate = transactionDate,
                    amount = amount,
                    direction = direction,
                    lineItems = deserializeLineItems(lineItemsText)
                )
            )
        )
    }
}

sealed interface ImportResult {
    data object Success : ImportResult
    data class Duplicate(val existing: Document) : ImportResult
}

sealed interface OcrResult {
    data object Success : OcrResult
    data class Failed(val message: String) : OcrResult
    data object DocumentNotFound : OcrResult
}

sealed interface ClassifyDocTypeResult {
    data object Success : ClassifyDocTypeResult
    data class Failed(val message: String) : ClassifyDocTypeResult
    data object DocumentNotFound : ClassifyDocTypeResult
}

sealed interface ExtractFieldsResult {
    data object Success : ExtractFieldsResult
    data class Failed(val message: String) : ExtractFieldsResult
    data object DocumentNotFound : ExtractFieldsResult
}

sealed interface SaveExtractedFieldsResult {
    data object Success : SaveExtractedFieldsResult
    data class Failed(val message: String) : SaveExtractedFieldsResult
    data object DocumentNotFound : SaveExtractedFieldsResult
}
