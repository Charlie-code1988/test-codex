package com.example.financeapp.ui.document

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    val clipboardManager = LocalClipboardManager.current

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
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        AsyncImage(
            model = document.appUri,
            contentDescription = "document_preview",
            modifier = Modifier
                .fillMaxWidth()
                .size(220.dp)
        )

        Button(
            onClick = { viewModel.runOcr(document.id) },
            enabled = !uiState.isRunningOcr,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(if (uiState.isRunningOcr) "OCR 识别中..." else "执行 OCR")
        }

        Text(text = "documentId: ${document.id}", modifier = Modifier.padding(top = 16.dp))
        Text(text = "createdAt: ${formatTime(document.createdAt)}", modifier = Modifier.padding(top = 8.dp))
        Text(
            text = "status: ${document.status}",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(text = "appUri: ${document.appUri}", modifier = Modifier.padding(top = 8.dp))
        Text(text = "originalUri: ${document.originalUri ?: "-"}", modifier = Modifier.padding(top = 8.dp))

        Text(
            text = "ocrStatus: ${document.ocrStatus}",
            color = statusColor(document.ocrStatus),
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(text = "ocrUpdatedAt: ${document.ocrUpdatedAt?.let { formatTime(it) } ?: "-"}")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "ocrRawText:")
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(document.ocrRawText.orEmpty()))
                }
            ) {
                Text("复制 OCR 文本")
            }
        }

        Text(
            text = document.ocrRawText ?: "(空)",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        )

        uiState.infoMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        uiState.errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "IDLE" -> MaterialTheme.colorScheme.outline
    "RUNNING" -> MaterialTheme.colorScheme.tertiary
    "SUCCESS" -> MaterialTheme.colorScheme.primary
    "FAILED" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
