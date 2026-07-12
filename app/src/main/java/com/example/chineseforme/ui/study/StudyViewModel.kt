package com.example.chineseforme.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chineseforme.data.db.SentenceEntity
import com.example.chineseforme.data.repo.TextRepository
import com.example.chineseforme.data.settings.AppSettings
import com.example.chineseforme.data.settings.SettingsRepository
import com.example.chineseforme.domain.analysis.SentenceAnalyzer
import com.example.chineseforme.domain.model.CharTile
import com.example.chineseforme.domain.model.GroupSpan
import com.example.chineseforme.domain.model.SentenceAnalysis
import com.example.chineseforme.domain.translation.NotionalTranslationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val loading: Boolean = true,
    val translating: Boolean = false,
    val sentence: SentenceEntity? = null,
    val analysis: SentenceAnalysis? = null,
    val selectedIndices: Set<Int> = emptySet(),
    val groupMode: Boolean = false,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val error: String? = null
)

class StudyViewModel(
    private val sentenceId: Long,
    private val workId: Long,
    private val textRepository: TextRepository,
    private val analyzer: SentenceAnalyzer,
    private val notionalTranslationService: NotionalTranslationService,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private var sentences: List<SentenceEntity> = emptyList()

    init {
        viewModelScope.launch { loadAround(sentenceId, generateIfMissing = true) }
    }

    private suspend fun loadAround(targetId: Long, generateIfMissing: Boolean) {
        _state.update { it.copy(loading = true, error = null) }
        sentences = textRepository.listSentences(workId)
        var sentence = sentences.find { it.id == targetId }
            ?: textRepository.getSentence(targetId)
        if (sentence == null) {
            _state.update { it.copy(loading = false, error = "Sentence not found") }
            return
        }

        if (generateIfMissing && sentence.parallelEnglish.isNullOrBlank()) {
            _state.update { it.copy(translating = true) }
            val draft = notionalTranslationService.translateSentence(sentence.text)
            if (draft.isNotBlank()) {
                textRepository.setSentenceParallelEnglish(sentence.id, draft)
                sentences = textRepository.listSentences(workId)
                sentence = sentences.find { it.id == targetId } ?: sentence.copy(parallelEnglish = draft)
            }
            _state.update { it.copy(translating = false) }
        }

        val analysis = analyzer.analyze(sentence)
        _state.update {
            it.copy(
                loading = false,
                translating = false,
                sentence = sentence,
                analysis = analysis,
                selectedIndices = emptySet(),
                sentenceIndex = sentence.indexInWork,
                sentenceCount = sentences.size
            )
        }
        textRepository.setLastSentence(workId, sentence.indexInWork)
    }

    fun toggleGroupMode() {
        _state.update { it.copy(groupMode = !it.groupMode, selectedIndices = emptySet()) }
    }

    fun onTileClick(tile: CharTile) {
        val s = _state.value
        if (!s.groupMode) return
        if (tile.isPunctuation) return
        _state.update {
            val next = if (tile.index in it.selectedIndices) {
                it.selectedIndices - tile.index
            } else {
                it.selectedIndices + tile.index
            }
            it.copy(selectedIndices = next)
        }
    }

    fun mergeSelected() {
        val s = _state.value
        val sentence = s.sentence ?: return
        val analysis = s.analysis ?: return
        val indices = s.selectedIndices.sorted()
        if (indices.size < 2) return
        if (indices.zipWithNext().any { (a, b) -> b != a + 1 }) return
        val spans = analysis.groups.map { GroupSpan(it.startIndex, it.endIndexExclusive) }
        viewModelScope.launch {
            val next = analyzer.mergeAndSave(sentence, spans, indices.first(), indices.last() + 1)
            _state.update { it.copy(analysis = next, selectedIndices = emptySet()) }
        }
    }

    fun splitSelectedGroup() {
        val s = _state.value
        val sentence = s.sentence ?: return
        val analysis = s.analysis ?: return
        val index = s.selectedIndices.singleOrNull() ?: return
        val group = analysis.groups.find { index in it.startIndex until it.endIndexExclusive } ?: return
        if (group.endIndexExclusive - group.startIndex <= 1) return
        val spans = analysis.groups.map { GroupSpan(it.startIndex, it.endIndexExclusive) }
        viewModelScope.launch {
            val next = analyzer.splitAndSave(
                sentence,
                spans,
                group.startIndex,
                group.endIndexExclusive
            )
            _state.update { it.copy(analysis = next, selectedIndices = emptySet()) }
        }
    }

    fun goPrev() {
        val s = _state.value
        val idx = s.sentenceIndex
        if (idx <= 0) return
        val prev = sentences.getOrNull(idx - 1) ?: return
        viewModelScope.launch { loadAround(prev.id, generateIfMissing = true) }
    }

    fun goNext() {
        val s = _state.value
        val idx = s.sentenceIndex
        if (idx >= sentences.lastIndex) return
        val next = sentences.getOrNull(idx + 1) ?: return
        viewModelScope.launch { loadAround(next.id, generateIfMissing = true) }
    }

    fun saveParallelEnglish(english: String) {
        val sentence = _state.value.sentence ?: return
        viewModelScope.launch {
            textRepository.setSentenceParallelEnglish(sentence.id, english)
            loadAround(sentence.id, generateIfMissing = false)
        }
    }

    /** Overwrite stored notional with a freshly generated draft. */
    fun regenerateNotional() {
        val sentence = _state.value.sentence ?: return
        viewModelScope.launch {
            _state.update { it.copy(translating = true) }
            textRepository.setSentenceParallelEnglish(sentence.id, null)
            loadAround(sentence.id, generateIfMissing = true)
        }
    }

    /** Wipe misaligned book-level pairs so each sentence regenerates on open. */
    fun clearAllNotionalsForWork() {
        viewModelScope.launch {
            textRepository.clearParallelEnglishForWork(workId)
            val current = _state.value.sentence?.id ?: return@launch
            loadAround(current, generateIfMissing = true)
        }
    }

    class Factory(
        private val sentenceId: Long,
        private val workId: Long,
        private val textRepository: TextRepository,
        private val analyzer: SentenceAnalyzer,
        private val notionalTranslationService: NotionalTranslationService,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StudyViewModel(
                sentenceId,
                workId,
                textRepository,
                analyzer,
                notionalTranslationService,
                settingsRepository
            ) as T
    }
}
