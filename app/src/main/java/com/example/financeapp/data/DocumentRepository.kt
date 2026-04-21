package com.example.financeapp.data

import com.example.financeapp.db.DocumentDao
import com.example.financeapp.db.DocumentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DocumentRepository(
    private val dao: DocumentDao
) {

    fun observeDocuments(): Flow<List<Document>> {
        return dao.observeAll().map { entities -> entities.map { it.toModel() } }
    }

    fun observeDocumentById(documentId: Long): Flow<Document?> {
        return dao.observeById(documentId).map { it?.toModel() }
    }

    suspend fun importDocument(uri: String): ImportResult {
        val existing = dao.findByUri(uri)
        if (existing != null) {
            return ImportResult.Duplicate(existing.toModel())
        }

        dao.insert(
            DocumentEntity(
                uri = uri,
                createdAt = System.currentTimeMillis(),
                status = "IMPORTED"
            )
        )
        return ImportResult.Success
    }

    private fun DocumentEntity.toModel(): Document {
        return Document(
            id = id,
            uri = uri,
            createdAt = createdAt,
            status = status
        )
    }
}

sealed interface ImportResult {
    data object Success : ImportResult
    data class Duplicate(val existing: Document) : ImportResult
}
