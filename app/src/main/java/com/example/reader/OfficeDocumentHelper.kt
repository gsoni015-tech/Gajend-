package com.example.reader

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.ui.theme.AppFonts
import com.example.util.KrutiDevConverter
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
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class WordRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val fontName: String? = null,
    val detectedFont: AppFonts.SupportedFont = AppFonts.SupportedFont.SYSTEM_DEFAULT
)

data class WordParagraph(
    val text: String,
    val isHeading: Boolean = false,
    val isBold: Boolean = false,
    val fontName: String? = null,
    val detectedFont: AppFonts.SupportedFont = AppFonts.SupportedFont.SYSTEM_DEFAULT,
    val runs: List<WordRun> = emptyList()
)

data class WordDocument(
    val title: String,
    val paragraphs: List<WordParagraph>,
    val primaryDetectedFont: AppFonts.SupportedFont = AppFonts.SupportedFont.SYSTEM_DEFAULT,
    val detectedFontNames: List<String> = emptyList()
)

data class ExcelTableData(
    val headers: List<String>,
    val rows: List<List<String>>,
    val headerFonts: List<AppFonts.SupportedFont> = emptyList(),
    val rowFonts: List<List<AppFonts.SupportedFont>> = emptyList()
)

data class ExcelDocument(
    val title: String,
    val sheets: Map<String, ExcelTableData>,
    val primaryDetectedFont: AppFonts.SupportedFont = AppFonts.SupportedFont.ENGLISH_OPEN_SANS,
    val detectedFontNames: List<String> = emptyList()
)

private data class DocxStyleInfo(
    val defaultFont: String? = null,
    val styleFonts: Map<String, String> = emptyMap()
)

private data class XlsxStyleInfo(
    val fontNamesById: List<String?> = emptyList(),
    val xfToFontName: List<String?> = emptyList()
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

    private fun getXmlAttr(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == localName || attrName.endsWith(":$localName")) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun extractFontNameFromRFonts(parser: XmlPullParser): String? {
        val ascii = getXmlAttr(parser, "ascii")
        val hAnsi = getXmlAttr(parser, "hAnsi")
        val cs = getXmlAttr(parser, "cs")
        val eastAsia = getXmlAttr(parser, "eastAsia")
        return listOfNotNull(ascii, hAnsi, cs, eastAsia)
            .firstOrNull { it.isNotBlank() }
            ?.trim()
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
        var styleInfo = DocxStyleInfo()

        if (isDocx && resolvedFile != null) {
            try {
                ZipFile(resolvedFile).use { zip ->
                    val stylesEntry = zip.getEntry("word/styles.xml")
                    if (stylesEntry != null) {
                        zip.getInputStream(stylesEntry).use { input ->
                            styleInfo = parseDocxStylesXml(input)
                        }
                    }
                    val docEntry = zip.getEntry("word/document.xml")
                    if (docEntry != null) {
                        zip.getInputStream(docEntry).use { input ->
                            paragraphs.addAll(parseDocxDocumentXml(input, styleInfo))
                        }
                    }
                }
            } catch (e: Exception) {
                // If ZipFile fails (e.g. streaming or format variant), try ZipInputStream fallback
                try {
                    var docXmlBytes: ByteArray? = null
                    var stylesXmlBytes: ByteArray? = null
                    getInputStream(context, file, uriString)?.use { stream ->
                        ZipInputStream(stream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                when (entry.name) {
                                    "word/styles.xml" -> stylesXmlBytes = zis.readBytes()
                                    "word/document.xml" -> docXmlBytes = zis.readBytes()
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }
                    if (stylesXmlBytes != null) {
                        styleInfo = parseDocxStylesXml(ByteArrayInputStream(stylesXmlBytes))
                    }
                    if (docXmlBytes != null) {
                        paragraphs.addAll(parseDocxDocumentXml(ByteArrayInputStream(docXmlBytes), styleInfo))
                    }
                } catch (ex: Exception) {
                    // Fall through to plain text extraction
                }
            }
        }

        // If paragraphs still empty, or if it is a binary .doc or plain text file
        var binaryDetectedFonts = emptyList<String>()
        if (paragraphs.isEmpty()) {
            try {
                val bytes = resolvedFile?.readBytes() ?: getInputStream(context, file, uriString)?.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val extracted = extractCleanTextFromDocBytes(bytes)
                    paragraphs.addAll(extracted.first)
                    binaryDetectedFonts = extracted.second
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

        // Compute primary detected font across all paragraphs & runs
        val fontFrequency = mutableMapOf<AppFonts.SupportedFont, Int>()
        val rawFontNames = linkedSetOf<String>()
        styleInfo.defaultFont?.let { rawFontNames.add(it) }
        rawFontNames.addAll(binaryDetectedFonts)

        for (para in paragraphs) {
            para.fontName?.let { rawFontNames.add(it) }
            if (para.runs.isNotEmpty()) {
                for (run in para.runs) {
                    run.fontName?.let { rawFontNames.add(it) }
                    val weight = run.text.length.coerceAtLeast(1)
                    fontFrequency[run.detectedFont] = (fontFrequency[run.detectedFont] ?: 0) + weight
                }
            } else {
                val weight = para.text.length.coerceAtLeast(1)
                fontFrequency[para.detectedFont] = (fontFrequency[para.detectedFont] ?: 0) + weight
            }
        }

        // If Kruti Dev was detected anywhere strongly or in font table on non-Unicode text, prefer it cleanly
        val combinedSample = paragraphs.take(12).joinToString(" ") { it.text }
        val hasUnicodeDevanagari = AppFonts.containsDevanagariUnicode(combinedSample)
        val hasKrutiMetadata = rawFontNames.any { it.contains("kruti", ignoreCase = true) || it.contains("devlys", ignoreCase = true) }

        val primaryFont = when {
            !hasUnicodeDevanagari && hasKrutiMetadata -> AppFonts.SupportedFont.KRUTI_DEV
            !hasUnicodeDevanagari && KrutiDevConverter.isLikelyKrutiDev(combinedSample) -> AppFonts.SupportedFont.KRUTI_DEV
            else -> fontFrequency.maxByOrNull { it.value }?.key
                ?: AppFonts.detectFontForText(combinedSample, rawFontNames.firstOrNull())
        }

        // Normalize paragraphs that relied on document default font if primaryFont is Kruti Dev
        val finalParagraphs = if (primaryFont == AppFonts.SupportedFont.KRUTI_DEV) {
            paragraphs.map { para ->
                if (!AppFonts.containsDevanagariUnicode(para.text) &&
                    para.fontName == null &&
                    para.detectedFont == AppFonts.SupportedFont.ENGLISH_OPEN_SANS
                ) {
                    val updatedRuns = para.runs.map { run ->
                        if (run.fontName == null && !AppFonts.containsDevanagariUnicode(run.text)) {
                            run.copy(detectedFont = AppFonts.SupportedFont.KRUTI_DEV)
                        } else run
                    }
                    para.copy(detectedFont = AppFonts.SupportedFont.KRUTI_DEV, runs = updatedRuns)
                } else para
            }
        } else {
            paragraphs
        }

        WordDocument(
            title = file.nameWithoutExtension.ifBlank { "Word Document" },
            paragraphs = finalParagraphs,
            primaryDetectedFont = primaryFont,
            detectedFontNames = rawFontNames.toList()
        )
    }

    private fun parseDocxStylesXml(inputStream: InputStream): DocxStyleInfo {
        var defaultFont: String? = null
        val styleFonts = mutableMapOf<String, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var insideDocDefaults = false
            var currentStyleId: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "docDefaults" -> insideDocDefaults = true
                            "style" -> currentStyleId = getXmlAttr(parser, "styleId")
                            "rFonts" -> {
                                val font = extractFontNameFromRFonts(parser)
                                if (!font.isNullOrBlank()) {
                                    if (insideDocDefaults && defaultFont == null) {
                                        defaultFont = font
                                    } else if (currentStyleId != null && !styleFonts.containsKey(currentStyleId)) {
                                        styleFonts[currentStyleId] = font
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "docDefaults" -> insideDocDefaults = false
                            "style" -> currentStyleId = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Styles optional; ignore malformed style XML
        }
        return DocxStyleInfo(defaultFont, styleFonts)
    }

    /**
     * Parses word/document.xml with XmlPullParser preserving UTF-8 Devanagari, Hindi,
     * English, run-level font metadata (w:rFonts), bold/italic styles, and Kruti Dev 010 detection.
     */
    private fun parseDocxDocumentXml(
        inputStream: InputStream,
        styleInfo: DocxStyleInfo
    ): List<WordParagraph> {
        val result = mutableListOf<WordParagraph>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var insideParagraph = false
        var insideParagraphProps = false
        var insideRun = false
        var currentStyleId: String? = null
        var isHeading = false
        var paraDefaultBold = false
        var paraDefaultFont: String? = null

        var runBold = false
        var runItalic = false
        var runFont: String? = null
        var currentRunText = StringBuilder()
        val currentParagraphRuns = mutableListOf<WordRun>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p" -> {
                            insideParagraph = true
                            insideParagraphProps = false
                            insideRun = false
                            currentStyleId = null
                            isHeading = false
                            paraDefaultBold = false
                            paraDefaultFont = null
                            currentParagraphRuns.clear()
                        }
                        "pPr" -> {
                            if (insideParagraph && !insideRun) {
                                insideParagraphProps = true
                            }
                        }
                        "pStyle" -> {
                            if (insideParagraph) {
                                val styleVal = getXmlAttr(parser, "val") ?: ""
                                currentStyleId = styleVal
                                if (styleVal.contains("Heading", ignoreCase = true) ||
                                    styleVal.contains("Title", ignoreCase = true)
                                ) {
                                    isHeading = true
                                }
                            }
                        }
                        "r" -> {
                            if (insideParagraph) {
                                insideRun = true
                                runBold = paraDefaultBold
                                runItalic = false
                                runFont = null
                                currentRunText = StringBuilder()
                            }
                        }
                        "rFonts" -> {
                            val font = extractFontNameFromRFonts(parser)
                            if (!font.isNullOrBlank()) {
                                if (insideRun) {
                                    runFont = font
                                } else if (insideParagraphProps) {
                                    paraDefaultFont = font
                                }
                            }
                        }
                        "b" -> {
                            val bVal = getXmlAttr(parser, "val")
                            val enabled = bVal == null || bVal == "1" || bVal.equals("true", ignoreCase = true)
                            if (insideRun) {
                                runBold = enabled
                            } else if (insideParagraphProps) {
                                paraDefaultBold = enabled
                            }
                        }
                        "i" -> {
                            val iVal = getXmlAttr(parser, "val")
                            val enabled = iVal == null || iVal == "1" || iVal.equals("true", ignoreCase = true)
                            if (insideRun) {
                                runItalic = enabled
                            }
                        }
                        "t" -> {
                            if (insideParagraph) {
                                val text = parser.nextText()
                                if (insideRun) {
                                    currentRunText.append(text)
                                } else {
                                    currentParagraphRuns.add(
                                        WordRun(
                                            text = text,
                                            isBold = paraDefaultBold,
                                            fontName = paraDefaultFont,
                                            detectedFont = AppFonts.detectFontForText(
                                                text = text,
                                                declaredFontName = paraDefaultFont ?: currentStyleId?.let { styleInfo.styleFonts[it] } ?: styleInfo.defaultFont,
                                                isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(text)
                                            )
                                        )
                                    )
                                }
                            }
                        }
                        "tab" -> {
                            if (insideRun) currentRunText.append("    ")
                        }
                        "br" -> {
                            if (insideRun) currentRunText.append("\n")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "pPr" -> insideParagraphProps = false
                        "r" -> {
                            if (insideRun) {
                                val rText = currentRunText.toString()
                                if (rText.isNotEmpty()) {
                                    val effectiveFontName = runFont
                                        ?: paraDefaultFont
                                        ?: currentStyleId?.let { styleInfo.styleFonts[it] }
                                        ?: styleInfo.defaultFont
                                    val detected = AppFonts.detectFontForText(
                                        text = rText,
                                        declaredFontName = effectiveFontName,
                                        isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(rText)
                                    )
                                    currentParagraphRuns.add(
                                        WordRun(
                                            text = rText,
                                            isBold = runBold,
                                            isItalic = runItalic,
                                            fontName = effectiveFontName,
                                            detectedFont = detected
                                        )
                                    )
                                }
                                insideRun = false
                            }
                        }
                        "p" -> {
                            val fullText = currentParagraphRuns.joinToString("") { it.text }.trim()
                            if (fullText.isNotEmpty()) {
                                val isParaKruti = KrutiDevConverter.isLikelyKrutiDev(fullText)
                                val paraRawFont = paraDefaultFont
                                    ?: currentParagraphRuns.firstOrNull { !it.fontName.isNullOrBlank() }?.fontName
                                    ?: currentStyleId?.let { styleInfo.styleFonts[it] }
                                    ?: styleInfo.defaultFont
                                val paraDetectedFont = AppFonts.detectFontForText(
                                    text = fullText,
                                    declaredFontName = paraRawFont,
                                    isKrutiLikely = isParaKruti
                                )
                                val normalizedRuns = if (paraDetectedFont == AppFonts.SupportedFont.KRUTI_DEV &&
                                    !AppFonts.containsDevanagariUnicode(fullText)
                                ) {
                                    currentParagraphRuns.map { run ->
                                        if (run.detectedFont == AppFonts.SupportedFont.ENGLISH_OPEN_SANS &&
                                            run.fontName == null
                                        ) {
                                            run.copy(detectedFont = AppFonts.SupportedFont.KRUTI_DEV)
                                        } else run
                                    }
                                } else {
                                    currentParagraphRuns.toList()
                                }

                                val allBold = normalizedRuns.isNotEmpty() && normalizedRuns.all { it.isBold }
                                result.add(
                                    WordParagraph(
                                        text = fullText,
                                        isHeading = isHeading,
                                        isBold = allBold || paraDefaultBold,
                                        fontName = paraRawFont,
                                        detectedFont = paraDetectedFont,
                                        runs = normalizedRuns
                                    )
                                )
                            }
                            insideParagraph = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return result
    }

    private fun decodeWindows1252Byte(b: Int): Char? {
        return when (b) {
            in 0x20..0x7E -> b.toChar()
            0x80 -> '\u20AC'
            0x82 -> '\u201A'
            0x83 -> '\u0192'
            0x84 -> '\u201E'
            0x85 -> '\u2026'
            0x86 -> '\u2020'
            0x87 -> '\u2021'
            0x88 -> '\u02C6'
            0x89 -> '\u2030'
            0x8A -> '\u0160'
            0x8B -> '\u2039'
            0x8C -> '\u0152'
            0x8E -> '\u017D'
            0x91 -> '\u2018'
            0x92 -> '\u2019'
            0x93 -> '\u201C'
            0x94 -> '\u201D'
            0x95 -> '\u2022'
            0x96 -> '\u2013'
            0x97 -> '\u2014'
            0x98 -> '\u02DC' // ˜ used in Kruti Dev
            0x99 -> '\u2122'
            0x9A -> '\u0161'
            0x9B -> '\u203A'
            0x9C -> '\u0153'
            0x9E -> '\u017E'
            0x9F -> '\u0178'
            in 0xA1..0xFF -> b.toChar() // Includes ç, ø, å, æ, è, é, ê, ë, etc.
            else -> null
        }
    }

    /**
     * Extracts clean human-readable UTF-16LE and Windows-1252 strings from binary .doc files,
     * detects embedded font names (e.g. Kruti Dev 010, Mangal, Times New Roman), and skips OLE2 metadata.
     */
    private fun extractCleanTextFromDocBytes(bytes: ByteArray): Pair<List<WordParagraph>, List<String>> {
        val paragraphs = mutableListOf<WordParagraph>()
        val sb = StringBuilder()
        val foundFontNames = linkedSetOf<String>()

        val knownFontNames = listOf(
            "Kruti Dev 010", "Kruti Dev 011", "Kruti Dev 020", "Kruti Dev",
            "DevLys 010", "DevLys", "Chanakya", "Mangal", "Kokila",
            "Aparajita", "Utsaah", "Nirmala UI", "Times New Roman",
            "Calibri", "Cambria", "Arial", "Courier New", "Georgia"
        )

        val ignoredKeywords = setOf(
            "WordDocument", "SummaryInformation", "DocumentSummaryInformation",
            "Root Entry", "CompObj", "Normal.dot", "Default Paragraph Font",
            "Table", "Microsoft Word", "MSWordDoc", "1Table", "0Table"
        ) + knownFontNames

        fun checkFontMetadata(runText: String) {
            for (font in knownFontNames) {
                if (runText.contains(font, ignoreCase = true)) {
                    foundFontNames.add(font)
                }
            }
        }

        // Try scanning UTF-16LE characters (2 bytes per char) - standard in Word 97-2003
        var i = 0
        var currentRun = StringBuilder()
        while (i < bytes.size - 1) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val charCode = (b1 shl 8) or b0

            val isReadable = (charCode in 0x0020..0x007E) ||
                    (charCode in 0x00A1..0x00FF) ||
                    (charCode in 0x02C0..0x02DF) || // Includes ˜ (0x02DC)
                    (charCode in 0x0900..0x097F) ||
                    (charCode in 0x2000..0x206F) ||
                    charCode == 0x2122 ||
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
                    checkFontMetadata(s)
                    if (s.isNotEmpty() && !ignoredKeywords.any { s.equals(it, ignoreCase = true) || (s.length < 40 && s.contains(it, ignoreCase = true)) }) {
                        sb.append(s).append("\n\n")
                    }
                }
                currentRun = StringBuilder()
                i += 2
            }
        }
        if (currentRun.length >= 4) {
            val s = currentRun.toString().trim()
            checkFontMetadata(s)
            if (s.isNotEmpty() && !ignoredKeywords.any { s.equals(it, ignoreCase = true) || (s.length < 40 && s.contains(it, ignoreCase = true)) }) {
                sb.append(s).append("\n\n")
            }
        }

        // If UTF-16LE didn't find much text, scan 8-bit Windows-1252 text runs (important for legacy ANSI / Kruti Dev .doc!)
        if (sb.length < 30) {
            var j = 0
            var ansiRun = StringBuilder()
            while (j < bytes.size) {
                val b = bytes[j].toInt() and 0xFF
                if (b == 0x0A || b == 0x0D || b == 0x09) {
                    ansiRun.append(if (b == 0x09) "    " else "\n")
                } else {
                    val ch = decodeWindows1252Byte(b)
                    if (ch != null) {
                        ansiRun.append(ch)
                    } else {
                        if (ansiRun.length >= 5) {
                            val s = ansiRun.toString().trim()
                            checkFontMetadata(s)
                            if (s.isNotEmpty() && !ignoredKeywords.any { s.equals(it, ignoreCase = true) || (s.length < 40 && s.contains(it, ignoreCase = true)) }) {
                                sb.append(s).append("\n\n")
                            }
                        }
                        ansiRun = StringBuilder()
                    }
                }
                j++
            }
            if (ansiRun.length >= 5) {
                val s = ansiRun.toString().trim()
                checkFontMetadata(s)
                if (s.isNotEmpty() && !ignoredKeywords.any { s.equals(it, ignoreCase = true) || (s.length < 40 && s.contains(it, ignoreCase = true)) }) {
                    sb.append(s).append("\n\n")
                }
            }
        }

        val primaryBinaryFontName = foundFontNames.firstOrNull {
            it.contains("Kruti", ignoreCase = true) || it.contains("DevLys", ignoreCase = true)
        } ?: foundFontNames.firstOrNull()

        // Split accumulated text into paragraphs
        val lines = sb.toString().split(Regex("[\r\n]{2,}"))
        for (line in lines) {
            val clean = line.trim()
            if (clean.length >= 2 && !ignoredKeywords.any { clean.equals(it, ignoreCase = true) }) {
                val isHeading = clean.length < 60 && !clean.endsWith(".")
                val detected = AppFonts.detectFontForText(
                    text = clean,
                    declaredFontName = primaryBinaryFontName,
                    isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(clean)
                )
                paragraphs.add(
                    WordParagraph(
                        text = clean,
                        isHeading = isHeading,
                        isBold = isHeading,
                        fontName = primaryBinaryFontName,
                        detectedFont = detected
                    )
                )
            }
        }

        return Pair(paragraphs, foundFontNames.toList())
    }

    suspend fun parseExcelDocument(
        file: File,
        context: Context? = null,
        uriString: String? = null
    ): ExcelDocument = withContext(Dispatchers.IO) {
        val sheets = linkedMapOf<String, ExcelTableData>()
        val fileName = file.name.ifBlank { uriString?.substringAfterLast('/') ?: "Spreadsheet" }
        val isCsv = fileName.endsWith(".csv", ignoreCase = true) ||
                (uriString != null && uriString.contains("csv", ignoreCase = true))

        val resolvedFile = getFileOrCopyToTemp(context, file, uriString, "excel_temp")
        val rawFontNames = linkedSetOf<String>()

        if (isCsv) {
            try {
                val inputStream = getInputStream(context, file, uriString)
                if (inputStream != null) {
                    BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).useLines { linesSeq ->
                        val lines = linesSeq.filter { it.isNotBlank() }.toList()
                        if (lines.isNotEmpty()) {
                            val parsedRows = lines.map { parseCsvLine(it) }
                            val headers = parsedRows.first().ifEmpty { listOf("Col A") }
                            val dataRows = parsedRows.drop(1)
                            val headerFonts = headers.map { cell ->
                                AppFonts.detectFontForText(
                                    text = cell,
                                    isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(cell)
                                )
                            }
                            val rowFonts = dataRows.map { row ->
                                row.map { cell ->
                                    AppFonts.detectFontForText(
                                        text = cell,
                                        isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(cell)
                                    )
                                }
                            }
                            sheets["Sheet 1"] = ExcelTableData(
                                headers = headers,
                                rows = dataRows,
                                headerFonts = headerFonts,
                                rowFonts = rowFonts
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                sheets["Error"] = ExcelTableData(listOf("Message"), listOf(listOf(e.message ?: "Failed to read CSV")))
            }
        } else if (resolvedFile != null) {
            // Try XLSX parsing with ZipFile (random-access for styles + shared strings + worksheets)
            try {
                ZipFile(resolvedFile).use { zip ->
                    var styleInfo = XlsxStyleInfo()
                    val stylesEntry = zip.getEntry("xl/styles.xml")
                    if (stylesEntry != null) {
                        zip.getInputStream(stylesEntry).use { input ->
                            styleInfo = parseXlsxStylesXml(input)
                        }
                        styleInfo.fontNamesById.filterNotNull().forEach { rawFontNames.add(it) }
                    }

                    val workbookSheetNames = mutableListOf<String>()
                    val workbookEntry = zip.getEntry("xl/workbook.xml")
                    if (workbookEntry != null) {
                        zip.getInputStream(workbookEntry).use { input ->
                            workbookSheetNames.addAll(parseXlsxWorkbookSheetNames(input))
                        }
                    }

                    // Parse xl/sharedStrings.xml
                    val sharedStrings = mutableListOf<Pair<String, String?>>()
                    val sstEntry = zip.getEntry("xl/sharedStrings.xml")
                    if (sstEntry != null) {
                        zip.getInputStream(sstEntry).use { input ->
                            sharedStrings.addAll(parseXlsxSharedStrings(input))
                        }
                        sharedStrings.mapNotNull { it.second }.forEach { rawFontNames.add(it) }
                    }

                    // Discover worksheets sorted numerically (sheet1, sheet2, ..., sheet10)
                    val sheetEntries = zip.entries().asSequence()
                        .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                        .sortedBy { entry ->
                            Regex("\\d+").find(entry.name.substringAfterLast('/'))?.value?.toIntOrNull()
                                ?: Int.MAX_VALUE
                        }
                        .toList()

                    if (sheetEntries.isNotEmpty()) {
                        sheetEntries.forEachIndexed { index, entry ->
                            val sheetName = workbookSheetNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                                ?: "Sheet ${index + 1}"
                            zip.getInputStream(entry).use { stream ->
                                val tableData = parseXlsxWorksheet(stream, sharedStrings, styleInfo)
                                if (tableData.headers.isNotEmpty() || tableData.rows.isNotEmpty()) {
                                    sheets[sheetName] = tableData
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // ZipInputStream fallback for non-random-access streams, then binary .xls fallback
                try {
                    var stylesBytes: ByteArray? = null
                    var workbookBytes: ByteArray? = null
                    var sstBytes: ByteArray? = null
                    val sheetBytesMap = linkedMapOf<String, ByteArray>()

                    getInputStream(context, file, uriString)?.use { stream ->
                        ZipInputStream(stream).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                when {
                                    entry.name == "xl/styles.xml" -> stylesBytes = zis.readBytes()
                                    entry.name == "xl/workbook.xml" -> workbookBytes = zis.readBytes()
                                    entry.name == "xl/sharedStrings.xml" -> sstBytes = zis.readBytes()
                                    entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                                        sheetBytesMap[entry.name] = zis.readBytes()
                                    }
                                }
                                entry = zis.nextEntry
                            }
                        }
                    }

                    if (sheetBytesMap.isNotEmpty()) {
                        val styleInfo = stylesBytes?.let { parseXlsxStylesXml(ByteArrayInputStream(it)) }
                            ?: XlsxStyleInfo()
                        styleInfo.fontNamesById.filterNotNull().forEach { rawFontNames.add(it) }

                        val workbookSheetNames = workbookBytes?.let {
                            parseXlsxWorkbookSheetNames(ByteArrayInputStream(it))
                        } ?: emptyList()

                        val sharedStrings = sstBytes?.let {
                            parseXlsxSharedStrings(ByteArrayInputStream(it))
                        } ?: emptyList()
                        sharedStrings.mapNotNull { it.second }.forEach { rawFontNames.add(it) }

                        val sortedEntries = sheetBytesMap.entries.sortedBy { (name, _) ->
                            Regex("\\d+").find(name.substringAfterLast('/'))?.value?.toIntOrNull()
                                ?: Int.MAX_VALUE
                        }
                        sortedEntries.forEachIndexed { index, (_, bytes) ->
                            val sheetName = workbookSheetNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                                ?: "Sheet ${index + 1}"
                            val tableData = parseXlsxWorksheet(ByteArrayInputStream(bytes), sharedStrings, styleInfo)
                            if (tableData.headers.isNotEmpty() || tableData.rows.isNotEmpty()) {
                                sheets[sheetName] = tableData
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fall through to binary .xls extraction
                }

                if (sheets.isEmpty()) {
                    try {
                        val bytes = resolvedFile.readBytes()
                        val (fallbackTable, binaryFonts) = extractTableFromBinaryXls(bytes)
                        rawFontNames.addAll(binaryFonts)
                        if (fallbackTable.rows.isNotEmpty()) {
                            sheets["Sheet 1"] = fallbackTable
                        }
                    } catch (_: Exception) {
                        // Ignore
                    }
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

        // Compute primary detected font across all sheets
        val fontFrequency = mutableMapOf<AppFonts.SupportedFont, Int>()
        val sampleBuilder = StringBuilder()
        var sampleCount = 0

        for (table in sheets.values) {
            table.headers.forEachIndexed { colIdx, text ->
                if (sampleCount < 40 && text.isNotBlank()) {
                    sampleBuilder.append(text).append(' ')
                    sampleCount++
                }
                val f = table.headerFonts.getOrNull(colIdx)
                    ?: AppFonts.detectFontForText(text, isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(text))
                val weight = text.length.coerceAtLeast(1)
                fontFrequency[f] = (fontFrequency[f] ?: 0) + weight
            }
            table.rows.forEachIndexed { rowIdx, row ->
                val rowFontList = table.rowFonts.getOrNull(rowIdx)
                row.forEachIndexed { colIdx, text ->
                    if (sampleCount < 80 && text.isNotBlank()) {
                        sampleBuilder.append(text).append(' ')
                        sampleCount++
                    }
                    val f = rowFontList?.getOrNull(colIdx)
                        ?: AppFonts.detectFontForText(text, isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(text))
                    val weight = text.length.coerceAtLeast(1)
                    fontFrequency[f] = (fontFrequency[f] ?: 0) + weight
                }
            }
        }

        val combinedSample = sampleBuilder.toString()
        val hasUnicodeDevanagari = AppFonts.containsDevanagariUnicode(combinedSample)
        val hasKrutiMetadata = rawFontNames.any {
            it.contains("kruti", ignoreCase = true) ||
                it.contains("devlys", ignoreCase = true) ||
                it.contains("chanakya", ignoreCase = true)
        }

        val primaryFont = when {
            !hasUnicodeDevanagari && hasKrutiMetadata -> AppFonts.SupportedFont.KRUTI_DEV
            !hasUnicodeDevanagari && KrutiDevConverter.isLikelyKrutiDev(combinedSample) -> AppFonts.SupportedFont.KRUTI_DEV
            else -> fontFrequency.maxByOrNull { it.value }?.key
                ?: AppFonts.detectFontForText(combinedSample, rawFontNames.firstOrNull())
        }

        // Normalize cells that defaulted to ENGLISH_OPEN_SANS when workbook primary font is Kruti Dev
        val normalizedSheets = if (primaryFont == AppFonts.SupportedFont.KRUTI_DEV) {
            sheets.mapValues { (_, table) ->
                val updatedHeaderFonts = table.headers.mapIndexed { idx, text ->
                    val existing = table.headerFonts.getOrNull(idx) ?: AppFonts.SupportedFont.ENGLISH_OPEN_SANS
                    if (!AppFonts.containsDevanagariUnicode(text) && existing == AppFonts.SupportedFont.ENGLISH_OPEN_SANS) {
                        AppFonts.SupportedFont.KRUTI_DEV
                    } else existing
                }
                val updatedRowFonts = table.rows.mapIndexed { rIdx, row ->
                    val existingRow = table.rowFonts.getOrNull(rIdx)
                    row.mapIndexed { cIdx, text ->
                        val existing = existingRow?.getOrNull(cIdx) ?: AppFonts.SupportedFont.ENGLISH_OPEN_SANS
                        if (!AppFonts.containsDevanagariUnicode(text) && existing == AppFonts.SupportedFont.ENGLISH_OPEN_SANS) {
                            AppFonts.SupportedFont.KRUTI_DEV
                        } else existing
                    }
                }
                table.copy(headerFonts = updatedHeaderFonts, rowFonts = updatedRowFonts)
            }
        } else {
            sheets
        }

        ExcelDocument(
            title = file.nameWithoutExtension.ifBlank { "Excel Document" },
            sheets = normalizedSheets,
            primaryDetectedFont = primaryFont,
            detectedFontNames = rawFontNames.toList()
        )
    }

    private fun parseXlsxStylesXml(inputStream: InputStream): XlsxStyleInfo {
        val fontNames = mutableListOf<String?>()
        val xfFontNames = mutableListOf<String?>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var insideFonts = false
            var insideFont = false
            var currentFontName: String? = null
            var insideCellXfs = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "fonts" -> insideFonts = true
                            "font" -> if (insideFonts) {
                                insideFont = true
                                currentFontName = null
                            }
                            "name" -> if (insideFonts && insideFont) {
                                currentFontName = getXmlAttr(parser, "val")?.trim()
                            }
                            "cellXfs" -> insideCellXfs = true
                            "xf" -> if (insideCellXfs) {
                                val fontId = getXmlAttr(parser, "fontId")?.toIntOrNull()
                                val fontName = if (fontId != null && fontId in fontNames.indices) {
                                    fontNames[fontId]
                                } else null
                                xfFontNames.add(fontName)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "font" -> if (insideFonts && insideFont) {
                                fontNames.add(currentFontName)
                                insideFont = false
                            }
                            "fonts" -> insideFonts = false
                            "cellXfs" -> insideCellXfs = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Optional styles; ignore malformed XML
        }
        return XlsxStyleInfo(fontNamesById = fontNames, xfToFontName = xfFontNames)
    }

    private fun parseXlsxWorkbookSheetNames(inputStream: InputStream): List<String> {
        val names = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = getXmlAttr(parser, "name")?.trim()
                    if (!name.isNullOrBlank()) {
                        names.add(name)
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Optional sheet names
        }
        return names
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

    private fun parseXlsxSharedStrings(inputStream: InputStream): List<Pair<String, String?>> {
        val strings = mutableListOf<Pair<String, String?>>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentString = StringBuilder()
        var firstRunFont: String? = null
        var insideSi = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            insideSi = true
                            currentString = StringBuilder()
                            firstRunFont = null
                        }
                        "rFont" -> if (insideSi && firstRunFont == null) {
                            firstRunFont = getXmlAttr(parser, "val")?.trim()
                        }
                        "t" -> if (insideSi) {
                            currentString.append(parser.nextText())
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        strings.add(Pair(currentString.toString(), firstRunFont))
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
        sharedStrings: List<Pair<String, String?>>,
        styleInfo: XlsxStyleInfo = XlsxStyleInfo()
    ): ExcelTableData {
        val allRows = mutableListOf<List<Pair<String, AppFonts.SupportedFont>>>()
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentRowMap = mutableMapOf<Int, Pair<String, AppFonts.SupportedFont>>()
        var currentCellCol = 0
        var currentCellType = ""
        var currentStyleFont: String? = null
        var maxColIndex = 0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRowMap = mutableMapOf()
                        }
                        "c" -> {
                            val rAttr = getXmlAttr(parser, "r") ?: ""
                            currentCellCol = extractColumnIndex(rAttr)
                            if (currentCellCol > maxColIndex) maxColIndex = currentCellCol
                            currentCellType = getXmlAttr(parser, "t") ?: ""
                            val styleIdx = getXmlAttr(parser, "s")?.toIntOrNull()
                            currentStyleFont = if (styleIdx != null) {
                                styleInfo.xfToFontName.getOrNull(styleIdx)
                            } else null
                        }
                        "v" -> {
                            val vText = parser.nextText()
                            var fontName = currentStyleFont
                            val cellValue = when (currentCellType) {
                                "s" -> {
                                    val sstIndex = vText.toIntOrNull()
                                    if (sstIndex != null && sstIndex in sharedStrings.indices) {
                                        val (sstText, sstFont) = sharedStrings[sstIndex]
                                        if (!sstFont.isNullOrBlank()) fontName = sstFont
                                        sstText
                                    } else {
                                        vText
                                    }
                                }
                                "b" -> if (vText == "1") "TRUE" else "FALSE"
                                "str", "e" -> vText
                                else -> formatNumericCell(vText)
                            }
                            val detected = AppFonts.detectFontForText(
                                text = cellValue,
                                declaredFontName = fontName,
                                isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(cellValue)
                            )
                            currentRowMap[currentCellCol] = Pair(cellValue, detected)
                        }
                        "t" -> {
                            // Inline string <is><t>
                            if (currentCellType == "inlineStr") {
                                val text = parser.nextText()
                                val detected = AppFonts.detectFontForText(
                                    text = text,
                                    declaredFontName = currentStyleFont,
                                    isKrutiLikely = KrutiDevConverter.isLikelyKrutiDev(text)
                                )
                                currentRowMap[currentCellCol] = Pair(text, detected)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "row") {
                        if (currentRowMap.isNotEmpty()) {
                            val rowList = mutableListOf<Pair<String, AppFonts.SupportedFont>>()
                            for (c in 0..maxColIndex) {
                                rowList.add(
                                    currentRowMap[c] ?: Pair("", AppFonts.SupportedFont.ENGLISH_OPEN_SANS)
                                )
                            }
                            if (rowList.any { it.first.isNotBlank() }) {
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

        val normalizedColCount = maxOf(maxColIndex + 1, allRows.maxOfOrNull { it.size } ?: 0)
        val firstRow = allRows.first()
        val headers = (0 until normalizedColCount).map { idx ->
            val value = firstRow.getOrNull(idx)?.first ?: ""
            if (value.isNotBlank()) value else getColumnName(idx)
        }
        val headerFonts = (0 until normalizedColCount).map { idx ->
            firstRow.getOrNull(idx)?.second ?: AppFonts.SupportedFont.ENGLISH_OPEN_SANS
        }

        val dataEntries = allRows.drop(1)
        val dataRows = dataEntries.map { row ->
            (0 until normalizedColCount).map { idx -> row.getOrNull(idx)?.first ?: "" }
        }
        val rowFonts = dataEntries.map { row ->
            (0 until normalizedColCount).map { idx ->
                row.getOrNull(idx)?.second ?: AppFonts.SupportedFont.ENGLISH_OPEN_SANS
            }
        }

        return ExcelTableData(
            headers = headers,
            rows = dataRows,
            headerFonts = headerFonts,
            rowFonts = rowFonts
        )
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

    private fun extractTableFromBinaryXls(bytes: ByteArray): Pair<ExcelTableData, List<String>> {
        val (extracted, fontNames) = extractCleanTextFromDocBytes(bytes)
        if (extracted.isEmpty()) {
            return Pair(ExcelTableData(listOf("Content"), emptyList()), fontNames)
        }
        val rows = extracted.map { listOf(it.text) }
        val rowFonts = extracted.map { listOf(it.detectedFont) }
        return Pair(
            ExcelTableData(
                headers = listOf("Extracted Content"),
                rows = rows,
                headerFonts = listOf(AppFonts.SupportedFont.ENGLISH_OPEN_SANS),
                rowFonts = rowFonts
            ),
            fontNames
        )
    }
}
