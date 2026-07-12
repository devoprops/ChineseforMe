package com.example.chineseforme.domain.memorize

/**
 * How memorize hints behave. Exhausting the hint budget resets the attempt.
 */
enum class MemorizeHintStyle {
    /** Each hint removes distractors from the pool (never the correct character). */
    NarrowPool,
    /** Briefly highlight up to three candidates including the correct one. */
    FlashThree;

    companion object {
        fun fromStorage(raw: String?): MemorizeHintStyle =
            entries.find { it.name == raw } ?: NarrowPool
    }
}

data class MemorizeSlot(
    val index: Int,
    val expected: Char,
    val isPunctuation: Boolean,
    /** Null means blank (han only). Punctuation is always shown via [expected]. */
    val filled: Char? = null
) {
    val display: Char
        get() = when {
            isPunctuation -> expected
            filled != null -> filled
            else -> '　'
        }

    val isBlankHan: Boolean get() = !isPunctuation && filled == null
}

data class MemorizeState(
    val slots: List<MemorizeSlot>,
    /** Slot index of the next blank han character, or null when complete. */
    val focusIndex: Int?,
    val pool: List<Char>,
    /** Characters briefly highlighted after a FlashThree hint. */
    val flashChars: Set<Char> = emptySet(),
    val hintsRemaining: Int,
    val currentFilled: Int,
    val bestFilled: Int,
    val totalHan: Int,
    val complete: Boolean,
    val statusMessage: String? = null
)

/**
 * Pure memorize engine: blank slots, character pool, hints, restart-on-mistake.
 */
class MemorizeSession {
    private var sentence: String = ""
    private var workChars: Set<Char> = emptySet()
    private var distractorCount: Int = 8
    private var restartOnMistake: Boolean = true
    private var hintsPerAttempt: Int = 3
    private var hintStyle: MemorizeHintStyle = MemorizeHintStyle.NarrowPool
    private var bestFilled: Int = 0
    private var state: MemorizeState = emptyState()

    fun current(): MemorizeState = state

    fun start(
        sentenceText: String,
        workHanChars: Set<Char>,
        distractorCount: Int,
        restartOnMistake: Boolean,
        hintsPerAttempt: Int,
        hintStyle: MemorizeHintStyle,
        preservedBest: Int = 0
    ): MemorizeState {
        sentence = sentenceText
        workChars = workHanChars
        this.distractorCount = distractorCount.coerceIn(2, 20)
        this.restartOnMistake = restartOnMistake
        this.hintsPerAttempt = hintsPerAttempt.coerceIn(0, 10)
        this.hintStyle = hintStyle
        bestFilled = preservedBest.coerceAtLeast(0)
        state = buildFreshAttempt(statusMessage = null)
        return state
    }

    fun pick(char: Char): MemorizeState {
        if (state.complete) return state
        val focus = state.focusIndex ?: return state
        val expected = state.slots[focus].expected
        return if (char == expected) {
            applyCorrect(focus)
        } else {
            applyWrong()
        }
    }

    /**
     * Consume one hint, or reset the attempt if none remain.
     * For [MemorizeHintStyle.FlashThree], [MemorizeState.flashChars] is set;
     * the UI should clear flash after a short delay via [clearFlash].
     */
    fun hint(): MemorizeState {
        if (state.complete) return state
        if (state.hintsRemaining <= 0) {
            state = buildFreshAttempt(statusMessage = "Hints exhausted — attempt reset")
            return state
        }
        val focus = state.focusIndex ?: return state
        val correct = state.slots[focus].expected
        val remaining = state.hintsRemaining - 1
        state = when (hintStyle) {
            MemorizeHintStyle.NarrowPool -> {
                val distractors = state.pool.filter { it != correct }
                if (distractors.isEmpty()) {
                    // Nothing left to remove; still spend the hint.
                    state.copy(
                        hintsRemaining = remaining,
                        flashChars = emptySet(),
                        statusMessage = if (remaining == 0) "No distractors left to remove" else null
                    )
                } else {
                    val removeCount = (distractors.size / 2).coerceAtLeast(1)
                    val toRemove = distractors.shuffled().take(removeCount).toSet()
                    val narrowed = state.pool.filter { it == correct || it !in toRemove }
                    state.copy(
                        pool = narrowed.shuffled(),
                        hintsRemaining = remaining,
                        flashChars = emptySet(),
                        statusMessage = null
                    )
                }
            }
            MemorizeHintStyle.FlashThree -> {
                val distractors = state.pool.filter { it != correct }.shuffled()
                val flash = (listOf(correct) + distractors.take(2)).toSet()
                state.copy(
                    hintsRemaining = remaining,
                    flashChars = flash,
                    statusMessage = null
                )
            }
        }
        return state
    }

    fun clearFlash(): MemorizeState {
        if (state.flashChars.isEmpty()) return state
        state = state.copy(flashChars = emptySet())
        return state
    }

    fun resetAttempt(): MemorizeState {
        state = buildFreshAttempt(statusMessage = null)
        return state
    }

    private fun applyCorrect(focus: Int): MemorizeState {
        val slots = state.slots.toMutableList()
        val slot = slots[focus]
        slots[focus] = slot.copy(filled = slot.expected)
        val filled = slots.count { !it.isPunctuation && it.filled != null }
        bestFilled = maxOf(bestFilled, filled)
        val nextFocus = slots.indexOfFirst { it.isBlankHan }.takeIf { it >= 0 }
        val complete = nextFocus == null
        state = MemorizeState(
            slots = slots,
            focusIndex = nextFocus,
            pool = if (complete) emptyList() else buildPool(slots[nextFocus!!].expected),
            flashChars = emptySet(),
            hintsRemaining = state.hintsRemaining,
            currentFilled = filled,
            bestFilled = bestFilled,
            totalHan = state.totalHan,
            complete = complete,
            statusMessage = if (complete) "Complete" else null
        )
        return state
    }

    private fun applyWrong(): MemorizeState {
        state = if (restartOnMistake) {
            buildFreshAttempt(statusMessage = "Incorrect — attempt reset")
        } else {
            val focus = state.focusIndex ?: return state
            state.copy(
                pool = buildPool(state.slots[focus].expected),
                flashChars = emptySet(),
                statusMessage = "Incorrect"
            )
        }
        return state
    }

    private fun buildFreshAttempt(statusMessage: String?): MemorizeState {
        val slots = sentence.mapIndexed { index, c ->
            val punct = !isHanish(c)
            MemorizeSlot(
                index = index,
                expected = c,
                isPunctuation = punct,
                filled = if (punct) c else null
            )
        }
        val totalHan = slots.count { !it.isPunctuation }
        val focus = slots.indexOfFirst { it.isBlankHan }.takeIf { it >= 0 }
        val complete = focus == null || totalHan == 0
        return MemorizeState(
            slots = slots,
            focusIndex = focus,
            pool = if (complete) emptyList() else buildPool(slots[focus!!].expected),
            flashChars = emptySet(),
            hintsRemaining = hintsPerAttempt,
            currentFilled = 0,
            bestFilled = bestFilled,
            totalHan = totalHan,
            complete = complete,
            statusMessage = statusMessage
        ).also { state = it }
    }

    private fun buildPool(correct: Char): List<Char> {
        val others = (workChars - correct).shuffled()
        val need = (distractorCount - 1).coerceAtLeast(0)
        val distractors = others.take(need)
        // If work charset is thin, pad by repeating nothing — just smaller pool.
        return (distractors + correct).distinct().shuffled()
    }

    private fun emptyState() = MemorizeState(
        slots = emptyList(),
        focusIndex = null,
        pool = emptyList(),
        hintsRemaining = 0,
        currentFilled = 0,
        bestFilled = 0,
        totalHan = 0,
        complete = true
    )

    companion object {
        fun isHanish(c: Char): Boolean =
            c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf'

        fun collectHanChars(texts: Iterable<String>): Set<Char> =
            texts.flatMap { it.asSequence() }.filter { isHanish(it) }.toSet()
    }
}
