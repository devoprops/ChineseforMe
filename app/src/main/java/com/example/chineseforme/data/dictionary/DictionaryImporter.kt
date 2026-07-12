package com.example.chineseforme.data.dictionary

import android.content.Context
import com.example.chineseforme.data.db.AppDatabase
import com.example.chineseforme.data.db.AppMetaEntity
import com.example.chineseforme.data.db.GlossEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DictionaryImporter(
    private val context: Context,
    private val db: AppDatabase
) {
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        val meta = db.appMetaDao()
        if (meta.get(KEY_LOADED) == "1" && db.glossDao().count() > 0) return@withContext

        val entries = mutableListOf<GlossEntryEntity>()
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLine() // header
            reader.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t')
                if (parts.size < 6) return@forEach
                val simplified = parts[1].trim()
                val traditional = parts[2].trim().ifEmpty { simplified }
                val frequency = parts[3].trim().toDoubleOrNull() ?: 0.0
                val pinyin = parts[4].trim()
                val definition = parts[5].trim()
                if (traditional.isEmpty()) return@forEach
                entries.add(
                    GlossEntryEntity(
                        traditional = traditional,
                        simplified = simplified.ifEmpty { traditional },
                        pinyin = pinyin,
                        definition = definition,
                        frequency = frequency
                    )
                )
            }
        }

        // Domain phrases that may be missing or under-weighted
        entries.addAll(domainExtras())

        entries.chunked(500).forEach { chunk ->
            db.glossDao().insertAll(chunk)
        }
        meta.put(AppMetaEntity(KEY_LOADED, "1"))
    }

    private fun domainExtras(): List<GlossEntryEntity> = listOf(
        GlossEntryEntity(
            traditional = "真善忍",
            simplified = "真善忍",
            pinyin = "zhēn shàn rěn",
            definition = "1. Truthfulness, Compassion, Forbearance",
            frequency = 999_999.0
        ),
        GlossEntryEntity(
            traditional = "釋迦牟尼",
            simplified = "释迦牟尼",
            pinyin = "shì jiā móu ní",
            definition = "1. Shakyamuni (Buddha)",
            frequency = 999_998.0
        ),
        GlossEntryEntity(
            traditional = "法輪大法",
            simplified = "法轮大法",
            pinyin = "fǎ lún dà fǎ",
            definition = "1. Falun Dafa",
            frequency = 999_997.0
        ),
        GlossEntryEntity(
            traditional = "轉法輪",
            simplified = "转法轮",
            pinyin = "zhuàn fǎ lún",
            definition = "1. Zhuan Falun",
            frequency = 999_996.0
        )
    )

    companion object {
        private const val ASSET_NAME = "fg_word_list.txt"
        private const val KEY_LOADED = "fg_dict_loaded_v1"
    }
}
