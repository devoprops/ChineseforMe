package com.example.chineseforme.data.importing

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

enum class DocumentFormat {
    PlainText,
    Pdf,
    Docx,
    Unknown
}

data class ImportedDocument(
    val title: String,
    val content: String,
    val format: DocumentFormat
)

object DocumentFormatDetector {
    fun fromName(name: String?, mime: String? = null): DocumentFormat {
        val lower = name?.lowercase().orEmpty()
        val mimeLower = mime?.lowercase().orEmpty()
        return when {
            lower.endsWith(".pdf") || mimeLower.contains("pdf") -> DocumentFormat.Pdf
            lower.endsWith(".docx") ||
                mimeLower.contains("wordprocessingml") ||
                mimeLower.contains("officedocument.wordprocessingml") -> DocumentFormat.Docx
            lower.endsWith(".txt") ||
                lower.endsWith(".md") ||
                mimeLower.startsWith("text/") -> DocumentFormat.PlainText
            else -> DocumentFormat.Unknown
        }
    }
}

class DocumentTextExtractor(private val context: Context) {
    @Volatile private var pdfReady = false

    private fun ensurePdf() {
        if (!pdfReady) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfReady = true
        }
    }

    fun extract(bytes: ByteArray, format: DocumentFormat, fallbackName: String = "Untitled"): ImportedDocument {
        val content = when (format) {
            DocumentFormat.PlainText, DocumentFormat.Unknown ->
                bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
            DocumentFormat.Pdf -> extractPdf(bytes)
            DocumentFormat.Docx -> extractDocx(bytes)
        }
        if (content.isBlank()) {
            throw IllegalArgumentException("No readable text found in document")
        }
        return ImportedDocument(
            title = fallbackName.substringBeforeLast('.').ifBlank { "Untitled" },
            content = content,
            format = format
        )
    }

    fun extractFromUri(uri: Uri, displayName: String?, mime: String?): ImportedDocument {
        val format = DocumentFormatDetector.fromName(displayName, mime)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not open file")
        val resolved = if (format == DocumentFormat.Unknown) sniff(bytes) else format
        return extract(bytes, resolved, displayName ?: "Imported")
    }

    fun extractFromUrl(urlString: String): ImportedDocument {
        val url = URL(urlString.trim())
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalArgumentException("Download failed (HTTP $code)")
            }
            val mime = connection.contentType
            val nameFromUrl = url.path.substringAfterLast('/').ifBlank { "download" }
            val bytes = BufferedInputStream(connection.inputStream).use { it.readBytes() }
            val format = DocumentFormatDetector.fromName(nameFromUrl, mime).let {
                if (it == DocumentFormat.Unknown) sniff(bytes) else it
            }
            return extract(bytes, format, nameFromUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun sniff(bytes: ByteArray): DocumentFormat {
        if (bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        ) {
            return DocumentFormat.Pdf
        }
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            // ZIP — likely docx
            return DocumentFormat.Docx
        }
        return DocumentFormat.PlainText
    }

    private fun extractPdf(bytes: ByteArray): String {
        ensurePdf()
        PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
            return PDFTextStripper().getText(doc).trim()
        }
    }

    private fun extractDocx(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    return parseDocxXml(zip).trim()
                }
                entry = zip.nextEntry
            }
        }
        throw IllegalArgumentException("DOCX is missing word/document.xml")
    }

    private fun parseDocxXml(input: InputStream): String {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        val out = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.substringAfter(':')
                when (name) {
                    "t" -> out.append(parser.nextText())
                    "tab" -> out.append('\t')
                    "br", "cr" -> out.append('\n')
                    "p" -> {
                        if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                    }
                }
            }
            event = parser.next()
        }
        return out.toString()
    }
}
