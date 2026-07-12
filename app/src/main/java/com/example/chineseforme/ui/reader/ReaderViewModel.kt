package com.example.chineseforme.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chineseforme.data.db.SentenceEntity
import com.example.chineseforme.data.db.TextWorkEntity
import com.example.chineseforme.data.repo.TextRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val workId: Long,
    private val textRepository: TextRepository
) : ViewModel() {
    val sentences: StateFlow<List<SentenceEntity>> =
        textRepository.observeSentences(workId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _work = MutableStateFlow<TextWorkEntity?>(null)
    val work: StateFlow<TextWorkEntity?> = _work.asStateFlow()

    init {
        viewModelScope.launch {
            _work.value = textRepository.getWork(workId)
        }
    }

    fun markOpened(index: Int) {
        viewModelScope.launch {
            textRepository.setLastSentence(workId, index)
        }
    }

    class Factory(
        private val workId: Long,
        private val textRepository: TextRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(workId, textRepository) as T
    }
}
