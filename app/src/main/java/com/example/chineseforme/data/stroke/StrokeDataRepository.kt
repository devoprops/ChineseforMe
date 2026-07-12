package com.example.chineseforme.data.stroke

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Hanzi Writer / Make Me a Hanzi graphics for one character.
 * Paths use the MMAH 1024 coordinate system (y decreases downward in raw data).
 */
data class CharacterStrokeData(
    val character: Char,
    val strokePaths: List<String>,
    val medians: List<List<Pair<Float, Float>>>
) {
    val strokeCount: Int get() = strokePaths.size
}

/**
 * Loads stroke JSON from on-device cache, then jsDelivr hanzi-writer-data.
 * Graphics are Arphic Public License — see assets/licenses/ARPHICPL.TXT.
 */
class StrokeDataRepository(private val context: Context) {
    private val memory = mutableMapOf<Char, CharacterStrokeData>()

    suspend fun get(character: Char): CharacterStrokeData? = withContext(Dispatchers.IO) {
        memory[character]?.let { return@withContext it }
        val cached = readCache(character)
        if (cached != null) {
            memory[character] = cached
            return@withContext cached
        }
        val downloaded = download(character) ?: return@withContext null
        writeCache(character, downloaded.rawJson)
        val parsed = parse(character, downloaded.rawJson) ?: return@withContext null
        memory[character] = parsed
        parsed
    }

    private fun cacheFile(character: Char): File {
        val dir = File(context.filesDir, "strokes")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "%04x.json".format(character.code))
    }

    private fun readCache(character: Char): CharacterStrokeData? {
        val file = cacheFile(character)
        if (!file.exists()) return null
        return parse(character, file.readText(Charsets.UTF_8))
    }

    private fun writeCache(character: Char, json: String) {
        cacheFile(character).writeText(json, Charsets.UTF_8)
    }

    private fun download(character: Char): Downloaded? {
        val encoded = URLEncoder.encode(character.toString(), Charsets.UTF_8.name())
        val url = URL("$CDN_BASE$encoded.json")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) ChineseForMe/1.0"
            )
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (body.isBlank() || !body.trimStart().startsWith("{")) return null
            return Downloaded(body)
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(character: Char, json: String): CharacterStrokeData? {
        return try {
            val root = JSONObject(json)
            val strokesJson = root.getJSONArray("strokes")
            val mediansJson = root.getJSONArray("medians")
            val strokes = mutableListOf<String>()
            for (i in 0 until strokesJson.length()) {
                strokes.add(strokesJson.getString(i))
            }
            val medians = mutableListOf<List<Pair<Float, Float>>>()
            for (i in 0 until mediansJson.length()) {
                val strokeMedian = mediansJson.getJSONArray(i)
                val points = mutableListOf<Pair<Float, Float>>()
                for (j in 0 until strokeMedian.length()) {
                    val pt = strokeMedian.getJSONArray(j)
                    points.add(pt.getDouble(0).toFloat() to pt.getDouble(1).toFloat())
                }
                medians.add(points)
            }
            if (strokes.isEmpty() || strokes.size != medians.size) return null
            CharacterStrokeData(character, strokes, medians)
        } catch (_: Exception) {
            null
        }
    }

    private data class Downloaded(val rawJson: String)

    companion object {
        private const val CDN_BASE =
            "https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1/"
    }
}

/** Map MMAH raw point into viewBox [0,1024]×[0,1024] (y down). */
fun mmahToView(x: Float, y: Float): Pair<Float, Float> = x to (900f - y)
