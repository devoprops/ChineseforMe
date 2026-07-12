package com.example.chineseforme.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chineseforme.data.db.TextWorkEntity
import com.example.chineseforme.data.importing.BundledText
import com.example.chineseforme.data.importing.BundledTextCatalog
import com.example.chineseforme.data.importing.DocumentTextExtractor
import com.example.chineseforme.data.repo.TextRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiExtras(
    val bundled: List<BundledText> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null
)

class LibraryViewModel(
    private val textRepository: TextRepository,
    private val documentExtractor: DocumentTextExtractor,
    private val bundledCatalog: BundledTextCatalog
) : ViewModel() {
    val works: StateFlow<List<TextWorkEntity>> = textRepository.observeWorks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extras = MutableStateFlow(LibraryUiExtras())
    val extras: StateFlow<LibraryUiExtras> = _extras.asStateFlow()

    init {
        _extras.update { it.copy(bundled = bundledCatalog.list()) }
    }

    fun clearError() {
        _extras.update { it.copy(error = null) }
    }

    fun importPaste(title: String, content: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            runImport(onDone) {
                textRepository.importText(title, content)
            }
        }
    }

    fun importUri(uri: Uri, displayName: String?, mime: String?, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            runImport(onDone) {
                val doc = withContext(Dispatchers.IO) {
                    documentExtractor.extractFromUri(uri, displayName, mime)
                }
                textRepository.importText(doc.title, doc.content)
            }
        }
    }

    fun importUrl(url: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            runImport(onDone) {
                val doc = withContext(Dispatchers.IO) {
                    documentExtractor.extractFromUrl(url)
                }
                textRepository.importText(doc.title, doc.content)
            }
        }
    }

    fun loadBundled(item: BundledText, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            runImport(onDone) {
                val content = withContext(Dispatchers.IO) {
                    bundledCatalog.read(item.assetPath)
                }
                val workId = textRepository.importText(
                    item.title,
                    content,
                    sourceKey = item.sourceKey
                )
                workId
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { textRepository.deleteWork(id) }
    }

    private suspend fun runImport(onDone: (Long) -> Unit, block: suspend () -> Long) {
        _extras.update { it.copy(importing = true, error = null) }
        try {
            val id = block()
            _extras.update { it.copy(importing = false) }
            onDone(id)
        } catch (t: Throwable) {
            _extras.update {
                it.copy(
                    importing = false,
                    error = t.message ?: "Import failed"
                )
            }
        }
    }

    class Factory(
        private val textRepository: TextRepository,
        private val documentExtractor: DocumentTextExtractor,
        private val bundledCatalog: BundledTextCatalog
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(textRepository, documentExtractor, bundledCatalog) as T
    }
}
