package com.example.financeapp.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.financeapp.data.Document
import com.example.financeapp.data.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DocumentUiState(
    val documents: List<Document> = emptyList(),
    val isImporting: Boolean = false,
    val lastError: String? = null
)

class DocumentViewModel(
    private val repository: DocumentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentUiState())
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeDocuments().collect { docs ->
                _uiState.update { it.copy(documents = docs) }
            }
        }
    }

    fun importDocument(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, lastError = null) }
            runCatching {
                repository.insertDocument(uri)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(lastError = throwable.message ?: "Import failed")
                }
            }
            _uiState.update { it.copy(isImporting = false) }
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
