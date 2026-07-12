package com.example.chineseforme.ui.memorize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chineseforme.data.db.GlossDao
import com.example.chineseforme.data.db.SentenceEntity
import com.example.chineseforme.data.repo.TextRepository
import com.example.chineseforme.data.settings.AppSettings
import com.example.chineseforme.data.settings.SettingsRepository
import com.example.chineseforme.domain.memorize.MemorizeSession
import com.example.chineseforme.domain.memorize.MemorizeState
import com.example.chineseforme.domain.pinyin.PinyinResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemorizeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val sentence: SentenceEntity? = null,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val session: MemorizeState? = null,
    /** Preferred single-syllable pinyin keyed by character. */
    val pinyinByChar: Map<Char, String> = emptyMap()
)

class MemorizeViewModel(
    private val sentenceId: Long,
    private val workId: Long,
    private val textRepository: TextRepository,
    private val glossDao: GlossDao,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val engine = MemorizeSession()
    private var workHanChars: Set<Char> = emptySet()
    private var sentences: List<SentenceEntity> = emptyList()
    private var settings: AppSettings = AppSettings()
    private var flashJob: Job? = null
    /** Best filled count for this sentence visit (survives attempt resets). */
    private var visitBest: Int = 0
    private var pinyinCache: MutableMap<Char, String> = mutableMapOf()

    private val _state = MutableStateFlow(MemorizeUiState())
    val state: StateFlow<MemorizeUiState> = _state.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                val changedPool = s.distractorCount != settings.distractorCount ||
                    s.memorizeHintStyle != settings.memorizeHintStyle ||
                    s.memorizeHintsPerAttempt != settings.memorizeHintsPerAttempt ||
                    s.memorizeAllowedMistakes != settings.memorizeAllowedMistakes
                settings = s
                if (changedPool && _state.value.session != null && !_state.value.loading) {
                    restartWithSettings()
                }
            }
        }
        viewModelScope.launch { loadAround(sentenceId) }
    }

    fun pick(char: Char) {
        val next = engine.pick(char)
        visitBest = next.bestFilled
        viewModelScope.launch { publish(next) }
    }

    fun hint() {
        flashJob?.cancel()
        viewModelScope.launch {
            val next = engine.hint()
            visitBest = next.bestFilled
            publish(next)
            if (next.flashChars.isNotEmpty()) {
                flashJob = launch {
                    delay(1_200)
                    publish(engine.clearFlash())
                }
            }
        }
    }

    fun resetAttempt() {
        flashJob?.cancel()
        viewModelScope.launch {
            publish(engine.resetAttempt().also { visitBest = maxOf(visitBest, it.bestFilled) })
        }
    }

    fun goPrev() {
        val idx = _state.value.sentenceIndex
        if (idx <= 0) return
        val prev = sentences.getOrNull(idx - 1) ?: return
        viewModelScope.launch { loadAround(prev.id) }
    }

    fun goNext() {
        val idx = _state.value.sentenceIndex
        if (idx >= sentences.lastIndex) return
        val next = sentences.getOrNull(idx + 1) ?: return
        viewModelScope.launch { loadAround(next.id) }
    }

    private suspend fun loadAround(targetId: Long) {
        flashJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        sentences = textRepository.listSentences(workId)
        workHanChars = MemorizeSession.collectHanChars(sentences.map { it.text })
        val sentence = sentences.find { it.id == targetId }
            ?: textRepository.getSentence(targetId)
        if (sentence == null) {
            _state.update { it.copy(loading = false, error = "Sentence not found") }
            return
        }
        visitBest = 0
        textRepository.setLastSentence(workId, sentence.indexInWork)
        val session = engine.start(
            sentenceText = sentence.text,
            workHanChars = workHanChars,
            distractorCount = settings.distractorCount,
            allowedMistakes = settings.memorizeAllowedMistakes,
            hintsPerAttempt = settings.memorizeHintsPerAttempt,
            hintStyle = settings.memorizeHintStyle,
            preservedBest = 0
        )
        ensurePinyin(session.slots.map { it.expected } + session.pool)
        _state.update {
            it.copy(
                loading = false,
                sentence = sentence,
                sentenceIndex = sentence.indexInWork,
                sentenceCount = sentences.size,
                session = session,
                pinyinByChar = pinyinCache.toMap()
            )
        }
    }

    private fun restartWithSettings() {
        val sentence = _state.value.sentence ?: return
        viewModelScope.launch {
            val session = engine.start(
                sentenceText = sentence.text,
                workHanChars = workHanChars,
                distractorCount = settings.distractorCount,
                allowedMistakes = settings.memorizeAllowedMistakes,
                hintsPerAttempt = settings.memorizeHintsPerAttempt,
                hintStyle = settings.memorizeHintStyle,
                preservedBest = visitBest
            )
            publish(session)
        }
    }

    private suspend fun publish(session: MemorizeState) {
        visitBest = maxOf(visitBest, session.bestFilled)
        ensurePinyin(session.slots.map { it.expected } + session.pool)
        _state.update {
            it.copy(
                session = session,
                pinyinByChar = pinyinCache.toMap()
            )
        }
    }

    private suspend fun ensurePinyin(chars: Iterable<Char>) {
        for (ch in chars) {
            if (!MemorizeSession.isHanish(ch) || pinyinCache.containsKey(ch)) continue
            val reading = preferredPinyin(ch) ?: continue
            pinyinCache[ch] = reading
        }
    }

    private suspend fun preferredPinyin(ch: Char): String? {
        return PinyinResolver.resolveCharacter(ch, glossDao).firstOrNull()
    }

    class Factory(
        private val sentenceId: Long,
        private val workId: Long,
        private val textRepository: TextRepository,
        private val glossDao: GlossDao,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MemorizeViewModel(
                sentenceId,
                workId,
                textRepository,
                glossDao,
                settingsRepository
            ) as T
    }
}
