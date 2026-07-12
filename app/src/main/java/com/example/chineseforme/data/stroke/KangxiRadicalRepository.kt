package com.example.chineseforme.data.stroke

import android.content.Context

data class KangxiRadicalInfo(
    val number: Int,
    val residualStrokes: Int,
    val glyph: String
) {
    /** e.g. "水 · R85+8" */
    val shortLabel: String
        get() = "$glyph · R$number+$residualStrokes"
}

/**
 * Kangxi radical-stroke lookup for bundled dictionary characters
 * (from Unicode Unihan kRSUnicode).
 */
class KangxiRadicalRepository(context: Context) {
    private val byChar: Map<Char, KangxiRadicalInfo> = context.assets
        .open(ASSET)
        .bufferedReader(Charsets.UTF_8)
        .useLines { lines ->
            buildMap {
                lines.forEach { line ->
                    if (line.isBlank() || line.startsWith("#")) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 4) return@forEach
                    val ch = parts[0].singleOrNull() ?: return@forEach
                    val number = parts[1].toIntOrNull() ?: return@forEach
                    val residual = parts[2].toIntOrNull() ?: return@forEach
                    val glyph = parts[3].trim()
                    if (glyph.isEmpty()) return@forEach
                    put(ch, KangxiRadicalInfo(number, residual, glyph))
                }
            }
        }

    fun get(character: Char): KangxiRadicalInfo? = byChar[character]

    companion object {
        private const val ASSET = "kangxi_radical_stroke.txt"
    }
}
