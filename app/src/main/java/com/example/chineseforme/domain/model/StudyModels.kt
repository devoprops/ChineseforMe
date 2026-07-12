package com.example.chineseforme.domain.model

data class CharTile(
    val char: String,
    val index: Int,
    val pinyinCandidates: List<String>,
    val senses: List<String>,
    val groupId: Int,
    val groupSurface: String,
    val isPunctuation: Boolean
)

data class WordGroup(
    val groupId: Int,
    val surface: String,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val pinyinCandidates: List<String>,
    val senses: List<String>,
    val tiles: List<CharTile>
)

data class SentenceAnalysis(
    val sentenceId: Long,
    val text: String,
    val tiles: List<CharTile>,
    val groups: List<WordGroup>,
    val readings: List<SentenceReading>
)

data class SentenceReading(
    val source: String,
    val text: String,
    val kind: Kind = Kind.FullSentence
) {
    enum class Kind { FullSentence, GlossChain }
}

data class GroupSpan(
    val start: Int,
    val endExclusive: Int
)

data class GlossHit(
    val surface: String,
    val pinyin: String,
    val senses: List<String>,
    val frequency: Double
)
