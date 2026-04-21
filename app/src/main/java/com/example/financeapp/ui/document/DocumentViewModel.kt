package com.example.financeapp.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.financeapp.data.Document
import com.example.financeapp.data.DocumentRepository
import com.example.financeapp.data.ImportResult
import com.example.financeapp.data.OcrResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentUiState(
    val documents: List<Document> = emptyList(),
    val selectedDocument: Document? = null,
    val isImporting: Boolean = false,
    val isRunningOcr: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class DocumentViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeDocuments().collect { docs ->
                _uiState.update { it.copy(documents = docs) }
            }
        }
    }

    fun importDocument(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, infoMessage = null, errorMessage = null) }

            runCatching { repository.importDocument(uri) }
                .onSuccess { result ->
                    when (result) {
                        is ImportResult.Success -> {
                            _uiState.update { it.copy(infoMessage = "导入成功") }
                        }
                        is ImportResult.Duplicate -> {
                            _uiState.update {
                                it.copy(infoMessage = "重复导入提醒：该图片已导入（ID=${result.existing.id}）")
                            }
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(errorMessage = throwable.message ?: "导入失败")
                    }
                }

            _uiState.update { it.copy(isImporting = false) }
        }
    }

    fun runOcr(documentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningOcr = true, infoMessage = null, errorMessage = null) }
            when (val result = repository.runOcr(documentId)) {
                is OcrResult.Success -> {
                    _uiState.update { it.copy(infoMessage = "OCR 识别成功") }
                }
                is OcrResult.Failed -> {
                    _uiState.update { it.copy(errorMessage = "OCR 失败：${result.message}") }
                }
                is OcrResult.DocumentNotFound -> {
                    _uiState.update { it.copy(errorMessage = "OCR 失败：单据不存在") }
                }
            }
            _uiState.update { it.copy(isRunningOcr = false) }
        }
    }

    fun onPickerCanceled() {
        _uiState.update { it.copy(infoMessage = "已取消选图") }
    }

    fun observeDocumentDetail(documentId: Long) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            repository.observeDocumentById(documentId).collect { doc ->
                _uiState.update { it.copy(selectedDocument = doc) }
            }
        }
    }
}

class DocumentViewModelFactory(
    private val repository: DocumentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DocumentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DocumentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
