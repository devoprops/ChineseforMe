package com.example.chineseforme.domain.segmentation

import com.example.chineseforme.data.db.GlossDao
import com.example.chineseforme.data.db.PersonalPhraseDao
import com.example.chineseforme.domain.model.GroupSpan

/**
 * Frequency-weighted longest-match segmenter (jieba-userdict style, without HMM).
 */
class DictSegmenter(
    private val glossDao: GlossDao,
    private val personalPhraseDao: PersonalPhraseDao
) {
    @Volatile
    private var phraseFreq: Map<String, Double>? = null

    suspend fun ensureIndex() {
        if (phraseFreq != null) return
        val map = HashMap<String, Double>()
        glossDao.multiCharSurfaces().forEach { row ->
            val key = row.traditional
            val existing = map[key]
            if (existing == null || row.frequency > existing) {
                map[key] = row.frequency
            }
            if (row.simplified != row.traditional) {
                val sExisting = map[row.simplified]
                if (sExisting == null || row.frequency > sExisting) {
                    map[row.simplified] = row.frequency
                }
            }
        }
        personalPhraseDao.all().forEach { phrase ->
            map[phrase.surface] = phrase.frequencyBoost
        }
        phraseFreq = map
    }

    suspend fun invalidateIndex() {
        phraseFreq = null
        ensureIndex()
    }

    suspend fun segment(text: String): List<GroupSpan> {
        ensureIndex()
        val dict = phraseFreq.orEmpty()
        val spans = mutableListOf<GroupSpan>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (!isHan(ch) && !isHanExt(ch)) {
                spans.add(GroupSpan(i, i + 1))
                i++
                continue
            }
            var bestEnd = i + 1
            var bestScore = -1.0
            val maxLook = minOf(text.length, i + 8)
            for (end in (i + 2)..maxLook) {
                val candidate = text.substring(i, end)
                if (!candidate.all { isHan(it) || isHanExt(it) }) break
                val score = dict[candidate] ?: continue
                if (score > bestScore || (score == bestScore && end > bestEnd)) {
                    bestScore = score
                    bestEnd = end
                }
            }
            spans.add(GroupSpan(i, bestEnd))
            i = bestEnd
        }
        return spans
    }

    private fun isHan(c: Char): Boolean = c in '\u4e00'..'\u9fff'
    private fun isHanExt(c: Char): Boolean = c in '\u3400'..'\u4dbf'
}
