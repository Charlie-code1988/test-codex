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

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): DocumentEntity?
}
