package com.example.chineseforme.domain.analysis

import com.example.chineseforme.data.db.GlossDao
import com.example.chineseforme.data.db.GlossEntryEntity
import com.example.chineseforme.data.db.OverrideDao
import com.example.chineseforme.data.db.SentenceEntity
import com.example.chineseforme.domain.model.CharTile
import com.example.chineseforme.domain.model.GroupSpan
import com.example.chineseforme.domain.model.SentenceAnalysis
import com.example.chineseforme.domain.model.SentenceReading
import com.example.chineseforme.domain.model.WordGroup
import com.example.chineseforme.domain.segmentation.DictSegmenter
import com.example.chineseforme.domain.segmentation.GroupEdit
import org.json.JSONArray
import org.json.JSONObject

class SentenceAnalyzer(
    private val glossDao: GlossDao,
    private val overrideDao: OverrideDao,
    private val segmenter: DictSegmenter
) {
    suspend fun analyze(sentence: SentenceEntity): SentenceAnalysis {
        val text = sentence.text
        val override = overrideDao.getForSentence(sentence.id)
        val spans = if (override != null) {
            decodeSpans(override.spansJson)
        } else {
            segmenter.segment(text)
        }
        return buildAnalysis(sentence, spans)
    }

    suspend fun saveOverride(sentenceId: Long, spans: List<GroupSpan>) {
        overrideDao.upsertSpans(sentenceId, encodeSpans(spans))
    }

    suspend fun mergeAndSave(
        sentence: SentenceEntity,
        currentSpans: List<GroupSpan>,
        start: Int,
        endExclusive: Int
    ): SentenceAnalysis {
        val merged = GroupEdit.merge(currentSpans, start, endExclusive)
        saveOverride(sentence.id, merged)
        return buildAnalysis(sentence, merged)
    }

    suspend fun splitAndSave(
        sentence: SentenceEntity,
        currentSpans: List<GroupSpan>,
        groupStart: Int,
        groupEndExclusive: Int
    ): SentenceAnalysis {
        val split = GroupEdit.splitGroup(currentSpans, groupStart, groupEndExclusive)
        saveOverride(sentence.id, split)
        return buildAnalysis(sentence, split)
    }

    private suspend fun buildAnalysis(
        sentence: SentenceEntity,
        spans: List<GroupSpan>
    ): SentenceAnalysis {
        val text = sentence.text
        val tiles = mutableListOf<CharTile>()
        val groups = mutableListOf<WordGroup>()

        spans.forEachIndexed { groupId, span ->
            val surface = text.substring(span.start, span.endExclusive)
            val glosses = glossDao.lookup(surface)
            val senses = mergeSenses(glosses)
            val pinyins = glosses.map { it.pinyin }.distinct().filter { it.isNotBlank() }
            val groupTiles = mutableListOf<CharTile>()
            for (i in span.start until span.endExclusive) {
                val ch = text[i].toString()
                val isPunct = !isHanish(text[i])
                val charGlosses = if (isPunct) emptyList() else glossDao.lookup(ch)
                val tile = CharTile(
                    char = ch,
                    index = i,
                    pinyinCandidates = if (isPunct) emptyList() else {
                        charGlosses.map { it.pinyin }.distinct().ifEmpty { pinyins }
                    },
                    senses = if (isPunct) emptyList() else mergeSenses(charGlosses),
                    groupId = groupId,
                    groupSurface = surface,
                    isPunctuation = isPunct
                )
                groupTiles.add(tile)
                tiles.add(tile)
            }
            groups.add(
                WordGroup(
                    groupId = groupId,
                    surface = surface,
                    startIndex = span.start,
                    endIndexExclusive = span.endExclusive,
                    pinyinCandidates = pinyins,
                    senses = senses,
                    tiles = groupTiles
                )
            )
        }

        val readings = mutableListOf<SentenceReading>()
        sentence.parallelEnglish?.takeIf { it.isNotBlank() }?.let {
            readings.add(
                SentenceReading(
                    source = "official",
                    text = it,
                    kind = SentenceReading.Kind.FullSentence
                )
            )
        }
        // Phrase-gloss chain is a study aid, not a sentence translation.
        val glossChain = groups
            .filter { g -> g.tiles.any { !it.isPunctuation } }
            .mapNotNull { g ->
                val sense = g.senses.firstOrNull() ?: return@mapNotNull null
                "${g.surface} ($sense)"
            }
            .joinToString(" ")
        if (glossChain.isNotBlank()) {
            readings.add(
                SentenceReading(
                    source = "gloss_chain",
                    text = glossChain,
                    kind = SentenceReading.Kind.GlossChain
                )
            )
        }

        return SentenceAnalysis(
            sentenceId = sentence.id,
            text = text,
            tiles = tiles,
            groups = groups,
            readings = readings
        )
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

    private fun isHanish(c: Char): Boolean =
        c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf'

    companion object {
        fun encodeSpans(spans: List<GroupSpan>): String {
            val arr = JSONArray()
            spans.forEach { span ->
                arr.put(JSONObject().put("start", span.start).put("end", span.endExclusive))
            }
            return arr.toString()
        }

        fun decodeSpans(json: String): List<GroupSpan> {
            val arr = JSONArray(json)
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(GroupSpan(obj.getInt("start"), obj.getInt("end")))
                }
            }
        }
    }
}
