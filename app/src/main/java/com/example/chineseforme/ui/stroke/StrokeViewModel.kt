package com.example.chineseforme.ui.stroke

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chineseforme.data.db.GlossDao
import com.example.chineseforme.data.db.GlossEntryEntity
import com.example.chineseforme.data.db.SentenceEntity
import com.example.chineseforme.data.db.StrokeCharStatsEntity
import com.example.chineseforme.data.db.StrokeStatsDao
import com.example.chineseforme.data.repo.TextRepository
import com.example.chineseforme.data.settings.AppSettings
import com.example.chineseforme.data.settings.SettingsRepository
import com.example.chineseforme.data.stroke.CharacterStrokeData
import com.example.chineseforme.data.stroke.KangxiRadicalRepository
import com.example.chineseforme.data.stroke.StrokeDataRepository
import com.example.chineseforme.domain.memorize.MemorizeSession
import com.example.chineseforme.domain.model.CharTile
import com.example.chineseforme.domain.pinyin.PinyinResolver
import com.example.chineseforme.domain.stroke.StrokePracticeSession
import com.example.chineseforme.domain.stroke.StrokePracticeState
import com.example.chineseforme.domain.translation.NotionalTranslationService
import com.example.chineseforme.ui.components.GlossPopup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StrokeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val sentence: SentenceEntity? = null,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val characters: List<Char> = emptyList(),
    val selectedIndex: Int = 0,
    val strokeData: CharacterStrokeData? = null,
    val practice: StrokePracticeState? = null,
    val pinyin: String = "",
    val senses: List<String> = emptyList(),
    val sensesFromOnline: Boolean = false,
    val sensesLoading: Boolean = false,
    val radicalLabel: String = "",
    val showGlossPopup: Boolean = false,
    val stats: StrokeCharStatsEntity? = null,
    val loadingChar: Boolean = false
)

class StrokeViewModel(
    private val sentenceId: Long,
    private val workId: Long,
    private val textRepository: TextRepository,
    private val strokeDataRepository: StrokeDataRepository,
    private val glossDao: GlossDao,
    private val strokeStatsDao: StrokeStatsDao,
    private val notionalTranslationService: NotionalTranslationService,
    private val kangxiRadicalRepository: KangxiRadicalRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val engine = StrokePracticeSession()
    private var sentences: List<SentenceEntity> = emptyList()
    private var settings: AppSettings = AppSettings()
    private var hintJob: Job? = null
    private var hintsUsedThisAttempt: Int = 0
    private var attemptRecorded: Boolean = false

    private val _state = MutableStateFlow(StrokeUiState())
    val state: StateFlow<StrokeUiState> = _state.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings = it }
        }
        viewModelScope.launch { loadSentence(sentenceId) }
    }

    fun selectCharacter(index: Int) {
        val chars = _state.value.characters
        if (index !in chars.indices) return
        viewModelScope.launch { loadCharacter(chars[index], index) }
    }

    fun submitStroke(points: List<Pair<Float, Float>>) {
        val before = engine.current()
        if (before.failed || before.complete) return
        val after = engine.submitStroke(points)
        _state.update { it.copy(practice = after) }
        if (after.complete && !before.complete) {
            viewModelScope.launch { recordOutcome(success = true) }
        } else if (after.failed && !before.failed) {
            viewModelScope.launch { recordOutcome(success = false) }
        }
    }

    fun hint() {
        hintJob?.cancel()
        val before = engine.current()
        if (before.hintsRemaining > 0) hintsUsedThisAttempt += 1
        val after = engine.hint()
        _state.update { it.copy(practice = after) }
        if (after.failed && !before.failed) {
            viewModelScope.launch { recordOutcome(success = false) }
            return
        }
        if (after.hintFlashStrokeIndex != null) {
            hintJob = viewModelScope.launch {
                delay(1_100)
                _state.update { it.copy(practice = engine.clearHintFlash()) }
            }
        }
    }

    fun toggleGlossPopup() {
        _state.update { it.copy(showGlossPopup = !it.showGlossPopup) }
    }

    fun dismissGlossPopup() {
        _state.update { it.copy(showGlossPopup = false) }
    }

    fun glossPopup(): GlossPopup? {
        val s = _state.value
        val ch = s.strokeData?.character?.toString() ?: return null
        return GlossPopup.Character(
            CharTile(
                char = ch,
                index = s.selectedIndex,
                pinyinCandidates = listOfNotNull(s.pinyin.takeIf { it.isNotBlank() }),
                senses = s.senses,
                groupId = 0,
                groupSurface = ch,
                isPunctuation = false
            )
        )
    }

    fun goPrevSentence() {
        val idx = _state.value.sentenceIndex
        if (idx <= 0) return
        val prev = sentences.getOrNull(idx - 1) ?: return
        viewModelScope.launch { loadSentence(prev.id) }
    }

    fun goNextSentence() {
        val idx = _state.value.sentenceIndex
        if (idx >= sentences.lastIndex) return
        val next = sentences.getOrNull(idx + 1) ?: return
        viewModelScope.launch { loadSentence(next.id) }
    }

    private suspend fun loadSentence(targetId: Long) {
        hintJob?.cancel()
        _state.update { it.copy(loading = true, error = null, showGlossPopup = false) }
        sentences = textRepository.listSentences(workId)
        val sentence = sentences.find { it.id == targetId }
            ?: textRepository.getSentence(targetId)
        if (sentence == null) {
            _state.update { it.copy(loading = false, error = "Sentence not found") }
            return
        }
        textRepository.setLastSentence(workId, sentence.indexInWork)
        val chars = sentence.text.filter { MemorizeSession.isHanish(it) }.toList()
        _state.update {
            it.copy(
                loading = false,
                sentence = sentence,
                sentenceIndex = sentence.indexInWork,
                sentenceCount = sentences.size,
                characters = chars,
                selectedIndex = 0,
                strokeData = null,
                practice = null
            )
        }
        if (chars.isNotEmpty()) {
            loadCharacter(chars[0], 0)
        } else {
            _state.update { it.copy(error = "No characters to practice in this sentence") }
        }
    }

    private suspend fun loadCharacter(ch: Char, index: Int) {
        hintJob?.cancel()
        hintsUsedThisAttempt = 0
        attemptRecorded = false
        _state.update { it.copy(loadingChar = true, selectedIndex = index, showGlossPopup = false) }
        val data = strokeDataRepository.get(ch)
        if (data == null) {
            _state.update {
                it.copy(
                    loadingChar = false,
                    strokeData = null,
                    practice = null,
                    radicalLabel = "",
                    error = "No stroke data for $ch (offline or missing)"
                )
            }
            return
        }
        val glosses = glossDao.lookup(ch.toString())
        val practice = engine.start(
            data = data,
            hintsPerAttempt = settings.strokeHintsPerAttempt,
            allowedMistakes = settings.strokeAllowedMistakes,
            guideDimLevel = 0
        )
        val stats = strokeStatsDao.get(ch.toString())
        val pinyin = preferredPinyinForChar(ch)
        val dictSenses = mergeSenses(glosses)
        val radical = kangxiRadicalRepository.get(ch)?.shortLabel.orEmpty()
        _state.update {
            it.copy(
                loadingChar = false,
                error = null,
                strokeData = data,
                practice = practice,
                pinyin = pinyin,
                senses = dictSenses,
                sensesFromOnline = false,
                sensesLoading = dictSenses.isEmpty(),
                radicalLabel = radical,
                stats = stats
            )
        }
        if (dictSenses.isEmpty()) {
            val draft = notionalTranslationService.translateSurface(ch.toString())
            _state.update { cur ->
                if (cur.strokeData?.character != ch) return@update cur
                if (draft.isNotBlank()) {
                    cur.copy(
                        senses = listOf(draft),
                        sensesFromOnline = true,
                        sensesLoading = false
                    )
                } else {
                    cur.copy(sensesLoading = false)
                }
            }
        }
    }

    fun retry() {
        hintJob?.cancel()
        hintsUsedThisAttempt = 0
        attemptRecorded = false
        _state.update { it.copy(practice = engine.retrySameLevel()) }
    }

    fun advanceAndRetry() {
        hintJob?.cancel()
        hintsUsedThisAttempt = 0
        attemptRecorded = false
        _state.update { it.copy(practice = engine.advanceDimAndRetry()) }
    }

    fun resetAttempt() {
        retry()
    }

    private suspend fun recordOutcome(success: Boolean) {
        if (attemptRecorded) return
        attemptRecorded = true
        val ch = _state.value.strokeData?.character?.toString() ?: return
        val prev = strokeStatsDao.get(ch) ?: StrokeCharStatsEntity(character = ch)
        val streak = if (success) prev.currentStreak + 1 else 0
        val updated = prev.copy(
            attempts = prev.attempts + 1,
            successes = prev.successes + if (success) 1 else 0,
            fails = prev.fails + if (success) 0 else 1,
            hintsUsed = prev.hintsUsed + hintsUsedThisAttempt,
            currentStreak = streak,
            bestStreak = maxOf(prev.bestStreak, streak),
            lastPracticedEpochMs = System.currentTimeMillis()
        )
        strokeStatsDao.upsert(updated)
        _state.update { it.copy(stats = updated) }
    }

    private fun preferredPinyin(glosses: List<GlossEntryEntity>): String {
        return PinyinResolver.candidatesFromDirectGlosses(glosses).firstOrNull().orEmpty()
    }

    private suspend fun preferredPinyinForChar(ch: Char): String {
        val direct = preferredPinyin(glossDao.lookup(ch.toString()))
        if (direct.isNotBlank()) return direct
        return PinyinResolver.resolveCharacter(ch, glossDao).firstOrNull().orEmpty()
    }

    private fun mergeSenses(entries: List<GlossEntryEntity>): List<String> {
        val out = LinkedHashSet<String>()
        for (entry in entries) {
            entry.definition
                .split(Regex("""\d+\.\s*|;\s*"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { out.add(it) }
        }
        return out.toList()
    }

    class Factory(
        private val sentenceId: Long,
        private val workId: Long,
        private val textRepository: TextRepository,
        private val strokeDataRepository: StrokeDataRepository,
        private val glossDao: GlossDao,
        private val strokeStatsDao: StrokeStatsDao,
        private val notionalTranslationService: NotionalTranslationService,
        private val kangxiRadicalRepository: KangxiRadicalRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StrokeViewModel(
                sentenceId,
                workId,
                textRepository,
                strokeDataRepository,
                glossDao,
                strokeStatsDao,
                notionalTranslationService,
                kangxiRadicalRepository,
                settingsRepository
            ) as T
    }
}
