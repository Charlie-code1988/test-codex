package com.example.financeapp.data

data class Document(
    val id: Long,
    val originalUri: String?,
    val appUri: String,
    val createdAt: Long,
    val status: String
)
