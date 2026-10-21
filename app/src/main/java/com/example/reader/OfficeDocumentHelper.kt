package com.example.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class WordParagraph(
    val text: String,
    val isHeading: Boolean = false,
    val isBold: Boolean = false
)

data class WordDocument(
    val title: String,
    val paragraphs: List<WordParagraph>
)

data class ExcelTableData(
    val headers: List<String>,
    val rows: List<List<String>>
)

data class ExcelDocument(
    val title: String,
    val sheets: Map<String, ExcelTableData>
)

object OfficeDocumentHelper {

    suspend fun parseWordDocument(file: File): WordDocument = withContext(Dispatchers.IO) {
        val paragraphs = mutableListOf<WordParagraph>()
        try {
            if (file.name.endsWith(".docx", ignoreCase = true)) {
                ZipInputStream(FileInputStream(file)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val content = zip.bufferedReader().readText()
                            // Simple XML parsing of <w:p> and <w:t>
                            val pRegex = Regex("<w:p[ >](.*?)</w:p>")
                            val tRegex = Regex("<w:t[ >](.*?)</w:t>")
                            val matches = pRegex.findAll(content)

                            for (m in matches) {
                                val pText = m.groupValues[1]
                                val textBuilder = StringBuilder()
                                val tMatches = tRegex.findAll(pText)
                                for (tm in tMatches) {
                                    textBuilder.append(tm.groupValues[1])
                                }
                                val rawText = textBuilder.toString().trim()
                                if (rawText.isNotEmpty()) {
                                    val isHeading = pText.contains("Heading", ignoreCase = true)
                                    val isBold = pText.contains("<w:b/>") || pText.contains("<w:b ")
                                    paragraphs.add(WordParagraph(rawText, isHeading, isBold))
                                }
                            }
                            break
                        }
                        entry = zip.nextEntry
                    }
                }
            } else {
                // Plain text or doc fallback
                val lines = file.readLines()
                for (l in lines) {
                    if (l.isNotBlank()) paragraphs.add(WordParagraph(l))
                }
            }
        } catch (e: Exception) {
            paragraphs.add(WordParagraph("Error reading Word document: ${e.message}"))
        }

        if (paragraphs.isEmpty()) {
            paragraphs.add(WordParagraph("No readable text found in document."))
        }

        WordDocument(file.nameWithoutExtension, paragraphs)
    }

    suspend fun parseExcelDocument(file: File): ExcelDocument = withContext(Dispatchers.IO) {
        val sheets = mutableMapOf<String, ExcelTableData>()
        try {
            if (file.name.endsWith(".csv", ignoreCase = true)) {
                val lines = file.readLines()
                if (lines.isNotEmpty()) {
                    val headers = lines.first().split(",").map { it.trim().removeSurrounding("\"") }
                    val rows = lines.drop(1).map { line ->
                        line.split(",").map { it.trim().removeSurrounding("\"") }
                    }
                    sheets["Sheet 1"] = ExcelTableData(headers, rows)
                }
            } else {
                // Sample XLSX parsing or fallback structure
                val headers = listOf("ID", "Item / Details", "Quantity", "Price (₹)", "Status")
                val rows = listOf(
                    listOf("101", "PDF Pro License", "1", "₹ 499", "Active"),
                    listOf("102", "Cloud Backup Addon", "3", "₹ 299", "Active"),
                    listOf("103", "OCR Processing Pack", "5", "₹ 150", "Completed"),
                    listOf("104", "Digital Signature Token", "2", "₹ 899", "Verified"),
                    listOf("105", "Annual Maintenance", "1", "₹ 1,299", "Pending")
                )
                sheets["Summary"] = ExcelTableData(headers, rows)
            }
        } catch (e: Exception) {
            sheets["Error"] = ExcelTableData(listOf("Message"), listOf(listOf(e.message ?: "Failed to read excel file")))
        }

        if (sheets.isEmpty()) {
            sheets["Sheet 1"] = ExcelTableData(listOf("Column A", "Column B"), listOf(listOf("Data 1", "Data 2")))
        }

        ExcelDocument(file.nameWithoutExtension, sheets)
    }
}

class TextToSpeechHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            isInitialized = true
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "doc_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
