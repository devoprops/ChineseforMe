package com.example.chineseforme.domain.translation

import android.util.Log
import com.example.chineseforme.data.db.GlossDao
import com.example.chineseforme.domain.segmentation.DictSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sentence-level notional English, matching Translate_and_memorize priority:
 * 1. Exact local dictionary hit (words / short phrases)
 * 2. Whole-sentence Google Translate draft (gtx)
 * 3. Segmented gloss compose (last resort)
 *
 * Results are stored per sentence and user-editable.
 */
class NotionalTranslationService(
    private val glossDao: GlossDao,
    private val segmenter: DictSegmenter
) {
    suspend fun translateSentence(text: String): String = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext ""

        // Exact dict only helps for short surfaces; full sentences almost never hit
        // and would skip the whole-sentence online draft.
        if (hanCount(trimmed) in 1..4) {
            glossDao.lookup(trimmed).firstOrNull()?.let { entry ->
                return@withContext firstSense(entry.definition)
            }
        }

        try {
            val online = googleTranslate(trimmed)
            if (online.isNotBlank()) return@withContext online
        } catch (e: Exception) {
            Log.w(TAG, "Online translate failed; using gloss compose", e)
        }

        composeFromSegments(trimmed)
    }

    private suspend fun composeFromSegments(text: String): String {
        val spans = segmenter.segment(text)
        val parts = spans.mapNotNull { span ->
            val surface = text.substring(span.start, span.endExclusive)
            if (surface.isBlank()) return@mapNotNull null
            if (surface.all { !isHanish(it) }) return@mapNotNull surface
            val sense = glossDao.lookup(surface).firstOrNull()?.let { firstSense(it.definition) }
            sense ?: surface
        }
        return parts.joinToString(" ").trim()
    }

    private fun firstSense(definition: String): String {
        return definition
            .split(Regex("""\d+\.\s*|;\s*"""))
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: definition.trim()
    }

    private fun googleTranslate(text: String): String {
        val q = URLEncoder.encode(text, Charsets.UTF_8.name())
        val url = URL(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh&tl=en&dt=t&q=$q"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            // Without a browser-like UA, gtx often rejects Android HttpURLConnection.
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        try {
            if (connection.responseCode !in 200..299) {
                val err = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                throw IllegalStateException("HTTP ${connection.responseCode}: ${err.orEmpty()}")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONArray(body)
            val chunks = root.optJSONArray(0) ?: return ""
            val out = StringBuilder()
            for (i in 0 until chunks.length()) {
                val part = chunks.optJSONArray(i)?.optString(0)
                if (!part.isNullOrBlank()) out.append(part)
            }
            return out.toString().trim()
        } finally {
            connection.disconnect()
        }
    }

    private fun hanCount(text: String): Int = text.count { isHanish(it) }

    private fun isHanish(c: Char): Boolean =
        c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf'

    companion object {
        private const val TAG = "NotionalTranslate"
    }
}
