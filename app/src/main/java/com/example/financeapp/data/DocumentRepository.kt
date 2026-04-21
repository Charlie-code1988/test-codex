package com.example.financeapp.data

import android.content.Context
import android.net.Uri
import com.example.financeapp.db.DocumentDao
import com.example.financeapp.db.DocumentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class DocumentRepository(
    private val context: Context,
    private val dao: DocumentDao
) {

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
            status = status
        )
    }
}

sealed interface ImportResult {
    data object Success : ImportResult
    data class Duplicate(val existing: Document) : ImportResult
}
