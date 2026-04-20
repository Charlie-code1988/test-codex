package com.example.financeapp.data

import com.example.financeapp.db.SimpleRecordDao
import com.example.financeapp.db.SimpleRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DocumentRepository(
    private val dao: SimpleRecordDao
) {
    fun observeDocuments(): Flow<List<Document>> {
        return dao.observeAll().map { entities ->
            entities.map { entity ->
                Document(
                    id = entity.id,
                    uri = entity.uri,
                    createdAt = entity.createdAt,
                    status = entity.status
                )
            }
        }
    }

    suspend fun insertDocument(uri: String) {
        dao.insert(
            SimpleRecordEntity(
                uri = uri,
                createdAt = System.currentTimeMillis(),
                status = "IMPORTED"
            )
        )
    }
}
