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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.financeapp.data.ExtractedFields
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

    val parsed = document.parsedDocument
    var counterpartyName by remember(document.id, parsed?.extractedFields?.counterpartyName) {
        mutableStateOf(parsed?.extractedFields?.counterpartyName.orEmpty())
    }
    var documentDate by remember(document.id, parsed?.extractedFields?.documentDate) {
        mutableStateOf(parsed?.extractedFields?.documentDate.orEmpty())
    }
    var contractNo by remember(document.id, parsed?.extractedFields?.contractNo) {
        mutableStateOf(parsed?.extractedFields?.contractNo.orEmpty())
    }
    var productName by remember(document.id, parsed?.extractedFields?.productName) {
        mutableStateOf(parsed?.extractedFields?.productName.orEmpty())
    }
    var productModel by remember(document.id, parsed?.extractedFields?.productModel) {
        mutableStateOf(parsed?.extractedFields?.productModel.orEmpty())
    }
    var quantity by remember(document.id, parsed?.extractedFields?.quantity) {
        mutableStateOf(parsed?.extractedFields?.quantity.orEmpty())
    }
    var unitPrice by remember(document.id, parsed?.extractedFields?.unitPrice) {
        mutableStateOf(parsed?.extractedFields?.unitPrice.orEmpty())
    }
    var totalAmount by remember(document.id, parsed?.extractedFields?.totalAmount) {
        mutableStateOf(parsed?.extractedFields?.totalAmount.orEmpty())
    }
    var transactionDate by remember(document.id, parsed?.extractedFields?.transactionDate) {
        mutableStateOf(parsed?.extractedFields?.transactionDate.orEmpty())
    }
    var amount by remember(document.id, parsed?.extractedFields?.amount) {
        mutableStateOf(parsed?.extractedFields?.amount.orEmpty())
    }
    var direction by remember(document.id, parsed?.extractedFields?.direction) {
        mutableStateOf(parsed?.extractedFields?.direction.orEmpty())
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

        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(
                onClick = { viewModel.runOcr(document.id) },
                enabled = !uiState.isRunningOcr
            ) {
                Text(if (uiState.isRunningOcr) "OCR 识别中..." else "执行 OCR")
            }

            Button(
                onClick = { viewModel.runClassify(document.id) },
                enabled = !uiState.isClassifying,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text(if (uiState.isClassifying) "分类中..." else "执行分类 / 重新分类")
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = { viewModel.runExtractFields(document.id) },
                enabled = !uiState.isExtracting
            ) {
                Text(if (uiState.isExtracting) "抽取中..." else "执行字段抽取 / 重新抽取")
            }

            Button(
                onClick = {
                    viewModel.saveExtractedFields(
                        document.id,
                        ExtractedFields(
                            counterpartyName = counterpartyName.ifBlank { null },
                            documentDate = documentDate.ifBlank { null },
                            contractNo = contractNo.ifBlank { null },
                            productName = productName.ifBlank { null },
                            productModel = productModel.ifBlank { null },
                            quantity = quantity.ifBlank { null },
                            unitPrice = unitPrice.ifBlank { null },
                            totalAmount = totalAmount.ifBlank { null },
                            transactionDate = transactionDate.ifBlank { null },
                            amount = amount.ifBlank { null },
                            direction = direction.ifBlank { null },
                            lineItems = parsed?.extractedFields?.lineItems ?: emptyList()
                        )
                    )
                },
                enabled = !uiState.isSavingExtractedFields,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text(if (uiState.isSavingExtractedFields) "保存中..." else "保存复核结果")
            }
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

        Text(text = "docType: ${document.docType}", modifier = Modifier.padding(top = 12.dp))
        Text(text = "classifyStatus: ${document.classifyStatus}")
        Text(text = "classifyUpdatedAt: ${document.classifyUpdatedAt?.let { formatTime(it) } ?: "-"}")
        Text(text = "classifyReason: ${document.classifyReason ?: "-"}")

        Text(text = "extractStatus: ${parsed?.extractStatus ?: "IDLE"}", modifier = Modifier.padding(top = 12.dp))
        Text(text = "extractUpdatedAt: ${parsed?.extractUpdatedAt?.let { formatTime(it) } ?: "-"}")
        Text(text = "extractReason: ${parsed?.extractReason ?: "-"}")

        Text(text = "抽取结果（可编辑复核）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

        EditableField("对方公司(counterpartyName)", counterpartyName) { counterpartyName = it }
        EditableField("合同日期(documentDate)", documentDate) { documentDate = it }
        EditableField("合同号(contractNo)", contractNo) { contractNo = it }
        EditableField("产品名称(productName)", productName) { productName = it }
        EditableField("产品型号(productModel)", productModel) { productModel = it }
        EditableField("数量(quantity)", quantity) { quantity = it }
        EditableField("单价(unitPrice)", unitPrice) { unitPrice = it }
        EditableField("总价(totalAmount)", totalAmount) { totalAmount = it }
        EditableField("交易日期(transactionDate)", transactionDate) { transactionDate = it }
        EditableField("金额(amount)", amount) { amount = it }
        EditableField("方向(direction)", direction) { direction = it }

        Text(text = "商品明细 lineItems", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        val lineItems = parsed?.extractedFields?.lineItems.orEmpty()
        if (lineItems.isEmpty()) {
            Text(text = "(暂无明细)", modifier = Modifier.padding(top = 6.dp))
        } else {
            lineItems.forEachIndexed { index, item ->
                Text(
                    text = "#${index + 1} 产品名称: ${item.productName} | 型号: ${item.productModel} | 数量: ${item.quantity} | 单价: ${item.unitPrice} | 小计: ${item.lineTotal}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "finalOcrText:")
            Button(onClick = {
                clipboardManager.setText(AnnotatedString(document.finalOcrText.orEmpty()))
            }) {
                Text("复制 OCR 文本")
            }
        }

        Text(
            text = document.finalOcrText ?: "(空)",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 360.dp)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        )

        Text(text = "—— 原始 OCR 对比 ——", modifier = Modifier.padding(top = 12.dp))
        Text(text = "Latin 原文:", modifier = Modifier.padding(top = 8.dp))
        Text(
            text = document.latinRawText ?: "(空)",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        )
        Text(text = "Chinese 原文:", modifier = Modifier.padding(top = 8.dp))
        Text(
            text = document.chineseRawText ?: "(空)",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
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
private fun EditableField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
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
