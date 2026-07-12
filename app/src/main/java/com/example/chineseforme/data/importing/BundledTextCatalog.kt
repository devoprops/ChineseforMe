package com.example.chineseforme.data.importing

import android.content.Context

data class BundledText(
    val sourceKey: String,
    val title: String,
    val assetPath: String,
    /** Optional paired English asset used for notional sentence translations. */
    val parallelEnglishAssetPath: String? = null
)

class BundledTextCatalog(private val context: Context) {
    fun list(): List<BundledText> {
        val names = context.assets.list(ASSET_DIR)?.sorted().orEmpty()
        return names
            .filter { it.endsWith(".txt", ignoreCase = true) }
            .filter { !it.contains("English", ignoreCase = true) }
            .map { fileName ->
                val title = fileName.removeSuffix(".txt").removeSuffix(".TXT")
                BundledText(
                    sourceKey = "bundled:$fileName",
                    title = title,
                    assetPath = "$ASSET_DIR/$fileName",
                    parallelEnglishAssetPath = parallelFor(fileName)
                )
            }
    }

    fun read(assetPath: String): String {
        return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use {
            it.readText().removePrefix("\uFEFF").trim()
        }
    }

    private fun parallelFor(chineseFileName: String): String? {
        return when {
            chineseFileName.equals("Zhuan Falun.txt", ignoreCase = true) ->
                "$ASSET_DIR/Zhuan Falun English 2014.txt"
            else -> null
        }
    }

    companion object {
        const val ASSET_DIR = "texts"
    }
}
