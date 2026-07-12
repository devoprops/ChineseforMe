package com.example.chineseforme.domain.segmentation

import com.example.chineseforme.domain.model.GroupSpan

object SentenceSplitter {
    private val boundary = Regex("""(?<=[。！？])|(?:\n\s*\n)|\n+""")

    fun split(rawText: String): List<String> {
        return rawText
            .split(boundary)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

object GroupEdit {
    fun merge(spans: List<GroupSpan>, start: Int, endExclusive: Int): List<GroupSpan> {
        require(start < endExclusive)
        val result = mutableListOf<GroupSpan>()
        var merged = false
        for (span in spans) {
            val overlaps = span.start < endExclusive && span.endExclusive > start
            if (!overlaps) {
                result.add(span)
            } else if (!merged) {
                result.add(GroupSpan(minOf(span.start, start), maxOf(span.endExclusive, endExclusive)))
                merged = true
            }
        }
        if (!merged) {
            result.add(GroupSpan(start, endExclusive))
        }
        return normalize(result)
    }

    fun splitGroup(spans: List<GroupSpan>, groupStart: Int, groupEndExclusive: Int): List<GroupSpan> {
        val result = mutableListOf<GroupSpan>()
        for (span in spans) {
            if (span.start == groupStart && span.endExclusive == groupEndExclusive) {
                for (i in span.start until span.endExclusive) {
                    result.add(GroupSpan(i, i + 1))
                }
            } else {
                result.add(span)
            }
        }
        return normalize(result)
    }

    fun spansFromLengths(textLength: Int, groupLengths: List<Int>): List<GroupSpan> {
        val spans = mutableListOf<GroupSpan>()
        var i = 0
        for (len in groupLengths) {
            spans.add(GroupSpan(i, i + len))
            i += len
        }
        require(i == textLength)
        return spans
    }

    fun normalize(spans: List<GroupSpan>): List<GroupSpan> =
        spans.sortedBy { it.start }
}
