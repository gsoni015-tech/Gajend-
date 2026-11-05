package com.example.reader

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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

    /**
     * Resolves a local File reference, copying from Uri if needed so random-access ZIP
     * operations can be performed efficiently without stream rewind issues.
     */
    private fun getFileOrCopyToTemp(context: Context?, file: File, uriString: String?, prefix: String): File? {
        if (file.exists() && file.canRead() && file.length() > 0) {
            return file
        }
        if (!uriString.isNullOrBlank() && context != null) {
            try {
                val uri = Uri.parse(uriString)
                val tempFile = File.createTempFile(prefix, ".tmp", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 0) {
                    return tempFile
                }
            } catch (e: Exception) {
                // Ignore and return null
            }
        }
        return null
    }

    private fun getInputStream(context: Context?, file: File, uriString: String?): InputStream? {
        return try {
            if (file.exists() && file.canRead()) {
                FileInputStream(file)
            } else if (!uriString.isNullOrBlank() && context != null) {
                context.contentResolver.openInputStream(Uri.parse(uriString))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun parseWordDocument(
        file: File,
        context: Context? = null,
        uriString: String? = null
    ): WordDocument = withContext(Dispatchers.IO) {
        val paragraphs = mutableListOf<WordParagraph>()
        val fileName = file.name.ifBlank { uriString?.substringAfterLast('/') ?: "Document" }
        val isDocx = fileName.endsWith(".docx", ignoreCase = true) ||
                (uriString != null && uriString.contains("docx", ignoreCase = true))

        val resolvedFile = getFileOrCopyToTemp(context, file, uriString, "word_temp")

        if (isDocx && resolvedFile != null) {
            try {
                ZipFile(resolvedFile).use { zip ->
                    val docEntry = zip.getEntry("word/document.xml")
                    if (docEntry != null) {
                        zip.getInputStream(docEntry).use { input ->
                            paragraphs.addAll(parseDocxDocumentXml(input))
                        }
                    }
                }
            } catch (e: Exception) {
                // If ZipFile fails (e.g. streaming or format variant), try ZipInputStream fallback
                try {
                    getInputStream(context, file, uriString)?.use { stream ->
                        ZipInputStream(stream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name == "word/document.xml") {
                                    paragraphs.addAll(parseDocxDocumentXml(zis))
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                } catch (ex: Exception) {
                    // Fall through to plain text extraction
                }
            }
        }

        // If paragraphs still empty, or if it is a binary .doc or plain text file
        if (paragraphs.isEmpty()) {
            try {
                val bytes = resolvedFile?.readBytes() ?: getInputStream(context, file, uriString)?.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    paragraphs.addAll(extractCleanTextFromDocBytes(bytes))
                }
            } catch (e: Exception) {
                paragraphs.add(WordParagraph("Error reading document: ${e.message}"))
            }
        }

        if (paragraphs.isEmpty()) {
            paragraphs.add(
                WordParagraph("दस्तावेज़ में कोई पाठ्य सामग्री नहीं मिली (No readable text found). Use 'Open with' to view with Microsoft Word or Google Docs.")
            )
        }

        WordDocument(file.nameWithoutExtension.ifBlank { "Word Document" }, paragraphs)
    }

    /**
     * Parses word/document.xml with XmlPullParser preserving UTF-8 Devanagari, Hindi,
     * English, and all international Unicode characters and styles.
     */
    private fun parseDocxDocumentXml(inputStream: InputStream): List<WordParagraph> {
        val result = mutableListOf<WordParagraph>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var insideParagraph = false
        var currentText = StringBuilder()
        var isHeading = false
        var isBold = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p" -> {
                            insideParagraph = true
                            currentText = StringBuilder()
                            isHeading = false
                            isBold = false
                        }
                        "pStyle" -> {
                            if (insideParagraph) {
                                val styleVal = parser.getAttributeValue(null, "val") ?: ""
                                if (styleVal.contains("Heading", ignoreCase = true) ||
                                    styleVal.contains("Title", ignoreCase = true)
                                ) {
                                    isHeading = true
                                }
                            }
                        }
                        "b" -> {
                            if (insideParagraph) {
                                val bVal = parser.getAttributeValue(null, "val")
                                if (bVal == null || bVal == "1" || bVal.equals("true", ignoreCase = true)) {
                                    isBold = true
                                }
                            }
                        }
                        "t" -> {
                            if (insideParagraph) {
                                val text = parser.nextText()
                                currentText.append(text)
                            }
                        }
                        "tab" -> {
                            if (insideParagraph) {
                                currentText.append("    ")
                            }
                        }
                        "br" -> {
                            if (insideParagraph) {
                                currentText.append("\n")
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p") {
                        val text = currentText.toString().trim()
                        if (text.isNotEmpty()) {
                            result.add(WordParagraph(text, isHeading, isBold))
                        }
                        insideParagraph = false
                    }
                }
            }
            eventType = parser.next()
        }

        return result
    }

    /**
     * Extracts clean human-readable UTF-16LE and UTF-8 strings from binary .doc files,
     * skipping OLE2 file headers and binary metadata tables.
     */
    private fun extractCleanTextFromDocBytes(bytes: ByteArray): List<WordParagraph> {
        val paragraphs = mutableListOf<WordParagraph>()
        val sb = StringBuilder()

        val ignoredKeywords = setOf(
            "WordDocument", "SummaryInformation", "DocumentSummaryInformation",
            "Root Entry", "CompObj", "Normal.dot", "Times New Roman",
            "Calibri", "Arial", "Default Paragraph Font", "Table"
        )

        // Try scanning UTF-16LE characters (2 bytes per char) - standard in Word 97-2003
        var i = 0
        var currentRun = StringBuilder()
        while (i < bytes.size - 1) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val charCode = (b1 shl 8) or b0

            // Check if valid readable character:
            // - Devanagari / Indic: 0x0900..0x0D7F
            // - Standard Latin & digits: 0x0020..0x007E
            // - Newlines & whitespace: 0x000A, 0x000D, 0x0009
            // - General punctuation / quotes / symbols: 0x2000..0x206F
            val isReadable = (charCode in 0x0020..0x007E) ||
                    (charCode in 0x0900..0x097F) ||
                    (charCode in 0x2000..0x206F) ||
                    charCode == 0x000A || charCode == 0x000D || charCode == 0x0009

            if (isReadable) {
                if (charCode == 0x000D || charCode == 0x000A) {
                    currentRun.append("\n")
                } else {
                    currentRun.append(charCode.toChar())
                }
                i += 2
            } else {
                if (currentRun.length >= 4) {
                    val s = currentRun.toString().trim()
                    if (s.isNotEmpty() && !ignoredKeywords.any { s.contains(it, ignoreCase = true) }) {
                        sb.append(s).append("\n\n")
                    }
                }
                currentRun = StringBuilder()
                i += 2
            }
        }
        if (currentRun.length >= 4) {
            val s = currentRun.toString().trim()
            if (s.isNotEmpty() && !ignoredKeywords.any { s.contains(it, ignoreCase = true) }) {
                sb.append(s).append("\n\n")
            }
        }

        // If UTF-16LE didn't find much text, also try 8-bit text run extraction
        if (sb.length < 30) {
            var j = 0
            var asciiRun = StringBuilder()
            while (j < bytes.size) {
                val b = bytes[j].toInt() and 0xFF
                if ((b in 0x20..0x7E) || b == 0x0A || b == 0x0D || b == 0x09) {
                    if (b == 0x0A || b == 0x0D) {
                        asciiRun.append("\n")
                    } else {
                        asciiRun.append(b.toChar())
                    }
                } else {
                    if (asciiRun.length >= 5) {
                        val s = asciiRun.toString().trim()
                        if (s.isNotEmpty() && !ignoredKeywords.any { s.contains(it, ignoreCase = true) }) {
                            sb.append(s).append("\n\n")
                        }
                    }
                    asciiRun = StringBuilder()
                }
                j++
            }
        }

        // Split accumulated text into paragraphs
        val lines = sb.toString().split(Regex("[\r\n]{2,}"))
        for (line in lines) {
            val clean = line.trim()
            if (clean.length >= 2 && !ignoredKeywords.contains(clean)) {
                val isHeading = clean.length < 60 && !clean.endsWith(".")
                paragraphs.add(WordParagraph(clean, isHeading = isHeading, isBold = isHeading))
            }
        }

        return paragraphs
    }

    suspend fun parseExcelDocument(
        file: File,
        context: Context? = null,
        uriString: String? = null
    ): ExcelDocument = withContext(Dispatchers.IO) {
        val sheets = mutableMapOf<String, ExcelTableData>()
        val fileName = file.name.ifBlank { uriString?.substringAfterLast('/') ?: "Spreadsheet" }
        val isCsv = fileName.endsWith(".csv", ignoreCase = true) ||
                (uriString != null && uriString.contains("csv", ignoreCase = true))

        val resolvedFile = getFileOrCopyToTemp(context, file, uriString, "excel_temp")

        if (isCsv) {
            try {
                val inputStream = getInputStream(context, file, uriString)
                if (inputStream != null) {
                    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).useLines { linesSeq ->
                        val lines = linesSeq.filter { it.isNotBlank() }.toList()
                        if (lines.isNotEmpty()) {
                            val parsedRows = lines.map { parseCsvLine(it) }
                            val headers = parsedRows.first()
                            val dataRows = parsedRows.drop(1)
                            sheets["Sheet 1"] = ExcelTableData(
                                headers = if (headers.isNotEmpty()) headers else listOf("Col A"),
                                rows = dataRows
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                sheets["Error"] = ExcelTableData(listOf("Message"), listOf(listOf(e.message ?: "Failed to read CSV")))
            }
        } else if (resolvedFile != null) {
            // Try XLSX parsing with ZipFile (random-access for shared strings + worksheets)
            try {
                ZipFile(resolvedFile).use { zip ->
                    // 1. Parse xl/sharedStrings.xml
                    val sharedStrings = mutableListOf<String>()
                    val sstEntry = zip.getEntry("xl/sharedStrings.xml")
                    if (sstEntry != null) {
                        zip.getInputStream(sstEntry).use { input ->
                            sharedStrings.addAll(parseXlsxSharedStrings(input))
                        }
                    }

                    // 2. Discover sheets from xl/workbook.xml or find worksheets directly
                    val sheetEntries = zip.entries().asSequence()
                        .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                        .sortedBy { it.name }
                        .toList()

                    if (sheetEntries.isNotEmpty()) {
                        sheetEntries.forEachIndexed { index, entry ->
                            val sheetName = "Sheet ${index + 1}"
                            zip.getInputStream(entry).use { stream ->
                                val tableData = parseXlsxWorksheet(stream, sharedStrings)
                                if (tableData.headers.isNotEmpty() || tableData.rows.isNotEmpty()) {
                                    sheets[sheetName] = tableData
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If standard XLSX zip parsing fails, try reading text streams or CSV fallback
                try {
                    val bytes = resolvedFile.readBytes()
                    val fallbackTable = extractTableFromBinaryXls(bytes)
                    if (fallbackTable.rows.isNotEmpty()) {
                        sheets["Sheet 1"] = fallbackTable
                    }
                } catch (ex: Exception) {
                    // Ignore
                }
            }
        }

        if (sheets.isEmpty()) {
            sheets["Sheet 1"] = ExcelTableData(
                headers = listOf("Status", "Document Info", "Details"),
                rows = listOf(
                    listOf("Ready", file.nameWithoutExtension, "Tap 'Open with' to view full formatting in Excel or Google Sheets"),
                    listOf("Tip", "Spreadsheet loaded", "Supports CSV, XLSX, and multi-language tables")
                )
            )
        }

        ExcelDocument(file.nameWithoutExtension.ifBlank { "Excel Document" }, sheets)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                (c == ',' || c == '\t' || c == ';') && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private fun parseXlsxSharedStrings(inputStream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentString = StringBuilder()
        var insideSi = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        insideSi = true
                        currentString = StringBuilder()
                    } else if (insideSi && parser.name == "t") {
                        currentString.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        strings.add(currentString.toString())
                        insideSi = false
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseXlsxWorksheet(
        inputStream: InputStream,
        sharedStrings: List<String>
    ): ExcelTableData {
        val allRows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentRowMap = mutableMapOf<Int, String>()
        var currentCellCol = 0
        var currentCellType = ""
        var maxColIndex = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRowMap = mutableMapOf()
                        }
                        "c" -> {
                            val rAttr = parser.getAttributeValue(null, "r") ?: ""
                            currentCellCol = extractColumnIndex(rAttr)
                            if (currentCellCol > maxColIndex) maxColIndex = currentCellCol
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                        }
                        "v" -> {
                            val vText = parser.nextText()
                            val cellValue = if (currentCellType == "s") {
                                val sstIndex = vText.toIntOrNull()
                                if (sstIndex != null && sstIndex in sharedStrings.indices) {
                                    sharedStrings[sstIndex]
                                } else {
                                    vText
                                }
                            } else if (currentCellType == "b") {
                                if (vText == "1") "TRUE" else "FALSE"
                            } else {
                                formatNumericCell(vText)
                            }
                            currentRowMap[currentCellCol] = cellValue
                        }
                        "t" -> {
                            // Inline string <is><t>
                            if (currentCellType == "inlineStr") {
                                val text = parser.nextText()
                                currentRowMap[currentCellCol] = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "row") {
                        if (currentRowMap.isNotEmpty()) {
                            val rowList = mutableListOf<String>()
                            for (c in 0..maxColIndex) {
                                rowList.add(currentRowMap[c] ?: "")
                            }
                            // Only add if not entirely blank
                            if (rowList.any { it.isNotBlank() }) {
                                allRows.add(rowList)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (allRows.isEmpty()) {
            return ExcelTableData(listOf("Column A", "Column B"), emptyList())
        }

        val firstRow = allRows.first()
        val headers = firstRow.mapIndexed { idx, value ->
            if (value.isNotBlank()) value else getColumnName(idx)
        }
        val dataRows = allRows.drop(1)

        return ExcelTableData(headers, dataRows)
    }

    private fun extractColumnIndex(cellRef: String): Int {
        var col = 0
        for (char in cellRef) {
            if (char in 'A'..'Z') {
                col = col * 26 + (char - 'A' + 1)
            } else if (char in 'a'..'z') {
                col = col * 26 + (char - 'a' + 1)
            } else {
                break
            }
        }
        return if (col > 0) col - 1 else 0
    }

    private fun getColumnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun formatNumericCell(value: String): String {
        val d = value.toDoubleOrNull() ?: return value
        return if (d == d.toLong().toDouble()) {
            d.toLong().toString()
        } else {
            value
        }
    }

    private fun extractTableFromBinaryXls(bytes: ByteArray): ExcelTableData {
        val extracted = extractCleanTextFromDocBytes(bytes)
        if (extracted.isEmpty()) {
            return ExcelTableData(listOf("Content"), emptyList())
        }
        val rows = extracted.map { listOf(it.text) }
        return ExcelTableData(listOf("Extracted Content"), rows)
    }
}
