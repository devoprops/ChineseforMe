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
import com.example.chineseforme.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val currentWork: TextWorkEntity? = null,
    /** Stored works that are not the current text. */
    val libraryWorks: List<TextWorkEntity> = emptyList(),
    val bundled: List<BundledText> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null
)

class LibraryViewModel(
    private val textRepository: TextRepository,
    private val documentExtractor: DocumentTextExtractor,
    private val bundledCatalog: BundledTextCatalog,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _extras = MutableStateFlow(
        HomeUiState(bundled = bundledCatalog.list())
    )

    val uiState: StateFlow<HomeUiState> = combine(
        textRepository.observeWorks(),
        settingsRepository.settings,
        _extras
    ) { works, settings, extras ->
        val currentId = settings.currentWorkId
        val current = currentId?.let { id -> works.find { it.id == id } }
        // If stored id points at a deleted work, treat as no current.
        extras.copy(
            currentWork = current,
            libraryWorks = works.filter { it.id != current?.id },
            bundled = extras.bundled.ifEmpty { bundledCatalog.list() }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun clearError() {
        _extras.update { it.copy(error = null) }
    }

    fun setCurrentWork(workId: Long) {
        viewModelScope.launch {
            settingsRepository.setCurrentWorkId(workId)
        }
    }

    fun clearCurrentWork() {
        viewModelScope.launch {
            settingsRepository.setCurrentWorkId(null)
        }
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
                textRepository.importText(
                    item.title,
                    content,
                    sourceKey = item.sourceKey
                )
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val currentId = uiState.value.currentWork?.id
            textRepository.deleteWork(id)
            if (currentId == id) {
                settingsRepository.setCurrentWorkId(null)
            }
        }
    }

    private suspend fun runImport(onDone: (Long) -> Unit, block: suspend () -> Long) {
        _extras.update { it.copy(importing = true, error = null) }
        try {
            val id = block()
            settingsRepository.setCurrentWorkId(id)
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
        private val bundledCatalog: BundledTextCatalog,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(
                textRepository,
                documentExtractor,
                bundledCatalog,
                settingsRepository
            ) as T
    }
}
