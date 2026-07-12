package com.example.chineseforme.domain.alignment

/**
 * Aligns Chinese study units with English sentences by lecture + subsection order.
 * Designed for Zhuan Falun traditional Chinese + 2014 English translation structure.
 */
object ZhuanFalunAligner {

    data class SectionMark(val id: String, val start: Int)

    data class AlignedPair(
        val chineseUnit: String,
        val englishUnit: String,
        val sectionId: String
    )

    private val lectureWords = listOf(
        "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"
    )

    fun align(chinese: String, english: String): List<AlignedPair> {
        val zhMarks = chineseSectionMarks(chinese)
        val enMarks = englishSectionMarks(english)
        if (zhMarks.isEmpty() || enMarks.isEmpty()) return emptyList()

        val pairs = mutableListOf<AlignedPair>()
        val zhSlices = slices(chinese, zhMarks)
        val enSlices = slices(english, enMarks)
        val count = minOf(zhSlices.size, enSlices.size)
        for (i in 0 until count) {
            val (zhId, zhText) = zhSlices[i]
            val (_, enText) = enSlices[i]
            pairs += alignWithinLecture(zhId, zhText, enText)
        }
        return pairs
    }

    /**
     * Map each Chinese sentence (same splitter as the app) to an English sentence
     * within the same lecture/subsection when possible.
     */
    fun mapToChineseSentences(
        chineseSentences: List<String>,
        alignedPairs: List<AlignedPair>
    ): List<String?> {
        if (alignedPairs.isEmpty()) return List(chineseSentences.size) { null }

        // Build lookup from chinese unit text -> english
        val byExact = LinkedHashMap<String, String>()
        alignedPairs.forEach { byExact.putIfAbsent(it.chineseUnit, it.englishUnit) }

        return chineseSentences.map { sentence ->
            byExact[sentence]
                ?: byExact.entries.firstOrNull { sentence.startsWith(it.key) || it.key.startsWith(sentence) }?.value
        }
    }

    /**
     * Produce one optional English string per Chinese sentence (same order as [chineseSentences]).
     */
    fun parallelForSentences(
        chineseFull: String,
        englishFull: String,
        chineseSentences: List<String>
    ): List<String?> {
        val zhMarks = chineseSectionMarks(chineseFull)
        val enMarks = englishSectionMarks(englishFull)
        if (zhMarks.isEmpty() || enMarks.isEmpty() || chineseSentences.isEmpty()) {
            return List(chineseSentences.size) { null }
        }

        val positions = locateSentences(chineseFull, chineseSentences)
        val zhSlices = slices(chineseFull, zhMarks)
        val enSlices = slices(englishFull, enMarks)
        val result = MutableList<String?>(chineseSentences.size) { null }

        val sectionCount = minOf(zhSlices.size, enSlices.size)
        for (si in 0 until sectionCount) {
            val (sectionId, zhText) = zhSlices[si]
            val (_, enText) = enSlices[si]
            val start = zhMarks[si].start
            val end = zhMarks.getOrNull(si + 1)?.start ?: chineseFull.length

            val indices = positions.mapIndexedNotNull { idx, pos ->
                if (pos in start until end) idx else null
            }
            if (indices.isEmpty()) continue

            val zhSubs = chineseSubsections(zhText)
            val enSubs = englishSubsections(enText)
            if (zhSubs.size == enSubs.size && zhSubs.isNotEmpty()) {
                // Map sentences to subsections by relative offset inside section
                zhSubs.forEachIndexed { subI, (title, zhSubText) ->
                    val subStartInFull = start + zhText.indexOf(zhSubText).coerceAtLeast(0)
                    val subEndInFull = subStartInFull + zhSubText.length
                    val subIndices = indices.filter { positions[it] in subStartInFull until subEndInFull }
                    val enUnits = splitEnglishUnits(enSubs[subI].second)
                    assignProportional(result, subIndices, enUnits)
                }
            } else {
                val enUnits = splitEnglishUnits(enText)
                assignProportional(result, indices, enUnits)
            }
        }
        return result
    }

    private fun locateSentences(full: String, sentences: List<String>): List<Int> {
        var from = 0
        return sentences.map { sentence ->
            val i = full.indexOf(sentence, from)
            if (i >= 0) {
                from = i + sentence.length
                i
            } else {
                from
            }
        }
    }

    private fun assignProportional(
        result: MutableList<String?>,
        indices: List<Int>,
        enUnits: List<String>
    ) {
        if (indices.isEmpty() || enUnits.isEmpty()) return
        indices.forEachIndexed { n, sentenceIndex ->
            val enIndex = if (indices.size == 1) {
                0
            } else {
                ((n.toDouble() / (indices.size - 1)) * (enUnits.size - 1))
                    .toInt()
                    .coerceIn(0, enUnits.lastIndex)
            }
            result[sentenceIndex] = enUnits[enIndex]
        }
    }

    private fun alignWithinLecture(
        sectionId: String,
        zhSection: String,
        enSection: String
    ): List<AlignedPair> {
        val zhSubs = chineseSubsections(zhSection)
        val enSubs = englishSubsections(enSection)

        val out = mutableListOf<AlignedPair>()
        if (zhSubs.size == enSubs.size && zhSubs.isNotEmpty()) {
            for (i in zhSubs.indices) {
                val zhUnits = splitChineseUnits(zhSubs[i].second)
                val enUnits = splitEnglishUnits(enSubs[i].second)
                out += zipProportionally(sectionId, zhUnits, enUnits)
            }
        } else {
            // Fall back to whole-lecture proportional pairing
            val zhUnits = splitChineseUnits(zhSection)
            val enUnits = splitEnglishUnits(enSection)
            out += zipProportionally(sectionId, zhUnits, enUnits)
        }
        return out
    }

    private fun zipProportionally(
        sectionId: String,
        zhUnits: List<String>,
        enUnits: List<String>
    ): List<AlignedPair> {
        if (zhUnits.isEmpty() || enUnits.isEmpty()) return emptyList()
        return zhUnits.mapIndexed { index, zh ->
            val enIndex = if (zhUnits.size == 1) {
                0
            } else {
                ((index.toDouble() / (zhUnits.size - 1)) * (enUnits.size - 1))
                    .toInt()
                    .coerceIn(0, enUnits.lastIndex)
            }
            AlignedPair(zh, enUnits[enIndex], sectionId)
        }
    }

    fun chineseSectionMarks(text: String): List<SectionMark> {
        val marks = mutableListOf<SectionMark>()
        Regex("""(?m)^[\s　]*論語[\s　]*$""").find(text)?.let {
            marks += SectionMark("lunyu", it.range.first)
        }
        Regex("""(?m)^[\s　]*第　([一二三四五六七八九])　講""").findAll(text).forEach { m ->
            val n = "一二三四五六七八九".indexOf(m.groupValues[1]) + 1
            marks += SectionMark("lecture_$n", m.range.first)
        }
        return marks.sortedBy { it.start }
    }

    fun englishSectionMarks(text: String): List<SectionMark> {
        val marks = mutableListOf<SectionMark>()
        Regex("""(?im)^ON DAFA\b""").find(text)?.let {
            marks += SectionMark("lunyu", it.range.first)
        }
        var after = marks.firstOrNull()?.start ?: 0
        lectureWords.forEachIndexed { index, word ->
            val n = index + 1
            val found = findEnglishLectureStart(text, word, after + 20) ?: return@forEachIndexed
            marks += SectionMark("lecture_$n", found)
            after = found
        }
        return marks
    }

    private fun findEnglishLectureStart(text: String, word: String, after: Int): Int? {
        val patterns = listOf(
            Regex("""(?im)(?:^|\n)\s*\d+\s*\n\s*Lecture\s+$word\b"""),
            Regex("""(?im)(?:^|\n)\s*Lecture\s+$word\s*\n\s*\d+\b""")
        )
        patterns.forEach { pat ->
            pat.findAll(text, after).forEach { m ->
                val window = text.substring(m.range.first, minOf(text.length, m.range.first + 120))
                if ("...." in window || "….." in window) return@forEach
                if (Regex("""(?i)morn\.bb1""").containsMatchIn(text.substring(maxOf(0, m.range.first - 40), m.range.first))) {
                    return@forEach
                }
                return m.range.first
            }
        }
        // Last resort: first Lecture Word after [after] that isn't a running header
        Regex("""(?im)Lecture\s+$word\b""").findAll(text, after).forEach { m ->
            val before = text.substring(maxOf(0, m.range.first - 40), m.range.first)
            if (Regex("""(?i)morn\.bb1""").containsMatchIn(before)) return@forEach
            val window = text.substring(m.range.first, minOf(text.length, m.range.first + 80))
            if ("...." in window) return@forEach
            return m.range.first
        }
        return null
    }

    private fun slices(text: String, marks: List<SectionMark>): List<Pair<String, String>> {
        return marks.mapIndexed { i, mark ->
            val end = marks.getOrNull(i + 1)?.start ?: text.length
            mark.id to text.substring(mark.start, end)
        }
    }

    private fun chineseSubsections(section: String): List<Pair<String, String>> {
        val titleRegex = Regex(
            """(?m)^(?![　\s])([^\n]{1,40})$"""
        )
        val titles = mutableListOf<Pair<Int, String>>()
        titleRegex.findAll(section).forEach { m ->
            val s = m.groupValues[1].trim()
            if (s.isEmpty()) return@forEach
            if (Regex("""^第　[一二三四五六七八九]　講$""").matches(s)) return@forEach
            if (s == "論語") return@forEach
            if (Regex("""^\d+$""").matches(s)) return@forEach
            if (!s.any { it in '\u4e00'..'\u9fff' }) return@forEach
            // Allow enumeration commas in titles like 真、善、忍…
            if (s.any { it == '。' || it == '！' || it == '？' }) return@forEach
            if (s.length > 22) return@forEach
            titles += m.range.first to s
        }
        if (titles.isEmpty()) return listOf("body" to section)
        return titles.mapIndexed { i, (start, title) ->
            val end = titles.getOrNull(i + 1)?.first ?: section.length
            title to section.substring(start, end)
        }
    }

    private fun englishSubsections(section: String): List<Pair<String, String>> {
        val titles = mutableListOf<Pair<Int, String>>()
        val seen = LinkedHashSet<String>()
        Regex("""(?i)morn\.bb1\s+([^\n]+)""").findAll(section).forEach { m ->
            val title = m.groupValues[1].trim().replace(Regex("""\s+"""), " ")
            if (title in seen) return@forEach
            seen += title
            titles += m.range.first to title
        }
        if (titles.isEmpty()) return listOf("body" to section)
        return titles.mapIndexed { i, (start, title) ->
            val end = titles.getOrNull(i + 1)?.first ?: section.length
            title to section.substring(start, end)
        }
    }

    fun splitChineseUnits(text: String): List<String> {
        return text
            .split(Regex("""(?<=[。！？])|\n+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun splitEnglishUnits(text: String): List<String> {
        val lines = text.lines().mapNotNull { line ->
            val t = line.trim()
            when {
                t.isEmpty() -> null
                Regex("""(?i)lecture\s+(one|two|three|four|five|six|seven|eight|nine)\s+morn\.bb1""").containsMatchIn(t) -> null
                Regex("""^\d+$""").matches(t) -> null
                else -> t
            }
        }
        var joined = lines.joinToString(" ")
        joined = joined.replace(Regex("""-\s+"""), "")
        joined = joined.replace(Regex("""\s+"""), " ").trim()
        return joined
            .split(Regex("""(?<=[.!?])\s+"""))
            .map { it.trim() }
            .filter { it.length > 20 }
    }
}
