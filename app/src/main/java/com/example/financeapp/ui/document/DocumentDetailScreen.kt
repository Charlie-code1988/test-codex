package com.example.financeapp.ui.document

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentDetailScreen(
    documentId: Long,
    viewModel: DocumentViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(documentId) {
        viewModel.observeDocumentDetail(documentId)
    }

    val document = uiState.selectedDocument
    if (document == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            CircularProgressIndicator()
            Text(text = "加载单据详情中...", modifier = Modifier.padding(top = 12.dp))
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        AsyncImage(
            model = document.uri,
            contentDescription = "document_preview",
            modifier = Modifier
                .fillMaxWidth()
                .size(220.dp)
        )
        Text(text = "documentId: ${document.id}", modifier = Modifier.padding(top = 16.dp))
        Text(text = "createdAt: ${formatTime(document.createdAt)}", modifier = Modifier.padding(top = 8.dp))
        Text(
            text = "status: ${document.status}",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(text = "uri: ${document.uri}", modifier = Modifier.padding(top = 8.dp))
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
