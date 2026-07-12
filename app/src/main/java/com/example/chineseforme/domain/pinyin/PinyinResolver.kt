package com.example.chineseforme.domain.pinyin

import com.example.chineseforme.data.db.GlossDao
import com.example.chineseforme.data.db.GlossEntryEntity

/**
 * Shared pinyin resolution for tiles across Study / Memorize / Stroke.
 *
 * FG word list is phrase-heavy and often lacks a direct single-character row.
 * Fallbacks: direct gloss → first syllable of multi-syllable gloss → syllable
 * inferred from a multi-character entry that contains the character → group syllable.
 */
object PinyinResolver {
    fun candidatesFromDirectGlosses(glosses: List<GlossEntryEntity>): List<String> {
        val raw = glosses.map { it.pinyin.trim() }.filter { it.isNotBlank() }
        if (raw.isEmpty()) return emptyList()
        val singles = raw.filter { !containsWhitespace(it) }
        val preferredSingles = preferNonSurname(singles.distinct())
        if (preferredSingles.isNotEmpty()) return preferredSingles

        // Char entries that wrongly store a full phrase reading — take first syllable.
        val firstSyllables = raw.mapNotNull { splitSyllables(it).firstOrNull() }.distinct()
        return preferNonSurname(firstSyllables)
    }

    /**
     * Infer a reading from multi-character dictionary rows that contain [ch].
     */
    fun fromContainingEntries(ch: Char, entries: List<GlossEntryEntity>): String? {
        val nonSurname = mutableListOf<String>()
        val surname = mutableListOf<String>()
        for (entry in entries) {
            val surfaces = listOf(entry.traditional, entry.simplified).filter { it.isNotBlank() }
            for (surface in surfaces) {
                val idx = surface.indexOf(ch)
                if (idx < 0) continue
                val syllables = splitSyllables(entry.pinyin)
                val syllable = syllables.getOrNull(idx)?.trim().orEmpty()
                if (syllable.isEmpty()) continue
                if (looksLikeSurname(syllable)) surname.add(syllable) else nonSurname.add(syllable)
                break
            }
        }
        return (nonSurname.ifEmpty { surname }).firstOrNull()
    }

    suspend fun resolveCharacter(
        ch: Char,
        glossDao: GlossDao,
        groupSyllable: String? = null
    ): List<String> {
        val direct = candidatesFromDirectGlosses(glossDao.lookup(ch.toString()))
        if (direct.isNotEmpty()) return direct
        if (!groupSyllable.isNullOrBlank()) return listOf(groupSyllable.trim())
        val inferred = fromContainingEntries(ch, glossDao.entriesContaining(ch.toString()))
        if (!inferred.isNullOrBlank()) return listOf(inferred)
        return emptyList()
    }

    fun preferNonSurname(candidates: List<String>): List<String> {
        if (candidates.size <= 1) return candidates
        val nonSurname = candidates.filterNot { looksLikeSurname(it) }
        if (nonSurname.isEmpty()) return candidates
        return nonSurname + candidates.filter { looksLikeSurname(it) }
    }

    fun looksLikeSurname(pinyin: String): Boolean {
        val first = pinyin.trim().firstOrNull() ?: return false
        return first.isUpperCase() && first.isLetter()
    }

    fun splitSyllables(pinyin: String): List<String> =
        pinyin.trim().split(Regex("""[\s　]+""")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun containsWhitespace(s: String): Boolean =
        s.any { it.isWhitespace() || it == '　' }
}
