package com.example.pdf.text

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.InflaterInputStream

data class TextSearchMatch(
    val pageIndex: Int,
    val pageNumber: Int,
    val matchSnippet: String,
    val occurrenceIndex: Int,
    val totalMatchesOnPage: Int
)

data class PageTextContent(
    val pageIndex: Int,
    val pageNumber: Int,
    val text: String,
    val wordCount: Int,
    val characterCount: Int
)

object PdfTextExtractor {

    private val textCache = mutableMapOf<String, Map<Int, String>>()

    /**
     * Extracts text from all pages in the PDF file asynchronously.
     * Caches results in memory for instantaneous subsequent searches.
     */
    suspend fun extractAllText(file: File): Map<Int, String> = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}_${file.lastModified()}"
        textCache[cacheKey]?.let { return@withContext it }

        val pageTextMap = mutableMapOf<Int, String>()
        try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val rawText = parsePdfContentStreams(bytes)

            // Split into page chunks if /Page markers exist, or assign sequentially
            val pageDelimiters = Regex("/Type\\s*/Page[^s]")
            val parts = rawText.split(Regex("(?=/Type\\s*/Page[^s])"))

            if (parts.size > 1) {
                parts.forEachIndexed { index, partText ->
                    val clean = cleanExtractedText(partText)
                    if (clean.isNotBlank()) {
                        pageTextMap[index] = clean
                    }
                }
            } else {
                // Single document stream or unchunked
                val streams = extractStreams(bytes)
                if (streams.isNotEmpty()) {
                    streams.forEachIndexed { idx, s ->
                        val clean = cleanExtractedText(s)
                        if (clean.isNotBlank()) {
                            pageTextMap[idx] = clean
                        }
                    }
                } else {
                    val clean = cleanExtractedText(rawText)
                    if (clean.isNotBlank()) {
                        pageTextMap[0] = clean
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        textCache[cacheKey] = pageTextMap
        pageTextMap
    }

    /**
     * Extracts readable text for a specific page.
     */
    suspend fun extractPageText(file: File, pageIndex: Int, totalPages: Int): PageTextContent = withContext(Dispatchers.IO) {
        val allText = extractAllText(file)
        val text = allText[pageIndex] 
            ?: allText.values.elementAtOrNull(pageIndex % allText.size.coerceAtLeast(1))
            ?: ""

        val words = if (text.isNotBlank()) text.trim().split(Regex("\\s+")).size else 0
        val chars = text.length

        PageTextContent(
            pageIndex = pageIndex,
            pageNumber = pageIndex + 1,
            text = text,
            wordCount = words,
            characterCount = chars
        )
    }

    /**
     * Searches for a keyword query across the PDF text, returning matched snippets with page numbers.
     */
    fun search(pageTextMap: Map<Int, String>, query: String, totalPages: Int = 1): List<TextSearchMatch> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return emptyList()

        val results = mutableListOf<TextSearchMatch>()

        // Search through mapped pages
        for ((pageIdx, text) in pageTextMap.entries.sortedBy { it.key }) {
            val pageNum = pageIdx + 1
            var startIndex = 0
            var occurrencesOnPage = 0
            val tempPageMatches = mutableListOf<Pair<Int, String>>()

            while (startIndex < text.length) {
                val matchIdx = text.indexOf(cleanQuery, startIndex, ignoreCase = true)
                if (matchIdx == -1) break

                occurrencesOnPage++
                val snippetStart = (matchIdx - 35).coerceAtLeast(0)
                val snippetEnd = (matchIdx + cleanQuery.length + 35).coerceAtMost(text.length)
                val snippet = (if (snippetStart > 0) "..." else "") +
                        text.substring(snippetStart, snippetEnd).replace("\n", " ").trim() +
                        (if (snippetEnd < text.length) "..." else "")

                tempPageMatches.add(Pair(matchIdx, snippet))
                startIndex = matchIdx + cleanQuery.length
            }

            tempPageMatches.forEachIndexed { occIdx, (_, snippet) ->
                results.add(
                    TextSearchMatch(
                        pageIndex = pageIdx,
                        pageNumber = pageNum,
                        matchSnippet = snippet,
                        occurrenceIndex = occIdx + 1,
                        totalMatchesOnPage = occurrencesOnPage
                    )
                )
            }
        }

        return results
    }

    private fun parsePdfContentStreams(bytes: ByteArray): String {
        val sb = StringBuilder()
        val streams = extractStreams(bytes)
        for (stream in streams) {
            val text = extractTextOperators(stream)
            if (text.isNotBlank()) {
                sb.append(text).append("\n\n")
            }
        }
        return sb.toString()
    }

    private fun extractStreams(bytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val streamKeyword = "stream".toByteArray(Charsets.US_ASCII)
        val endstreamKeyword = "endstream".toByteArray(Charsets.US_ASCII)

        while (i < bytes.size - 6) {
            val streamPos = indexOf(bytes, streamKeyword, i)
            if (streamPos == -1) break

            var contentStart = streamPos + 6
            if (contentStart < bytes.size && bytes[contentStart] == '\r'.code.toByte()) contentStart++
            if (contentStart < bytes.size && bytes[contentStart] == '\n'.code.toByte()) contentStart++

            val endstreamPos = indexOf(bytes, endstreamKeyword, contentStart)
            if (endstreamPos == -1) break

            val streamData = bytes.copyOfRange(contentStart, endstreamPos)

            // Try decompressing via Inflater (FlateDecode)
            var decompressed: String? = null
            try {
                InflaterInputStream(ByteArrayInputStream(streamData)).use { inflater ->
                    val decompressedBytes = inflater.readBytes()
                    decompressed = String(decompressedBytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                // If not compressed with FlateDecode, try raw ASCII/UTF-8
                decompressed = try {
                    String(streamData, Charsets.UTF_8)
                } catch (e2: Exception) {
                    null
                }
            }

            decompressed?.let {
                if (it.contains("BT") || it.contains("Tj") || it.contains("TJ")) {
                    result.add(it)
                }
            }

            i = endstreamPos + 9
        }

        return result
    }

    private fun extractTextOperators(content: String): String {
        val sb = StringBuilder()

        // Match BT (Begin Text) ... ET (End Text) blocks
        val btEtRegex = Regex("BT([\\s\\S]*?)ET")
        val matches = btEtRegex.findAll(content)

        for (match in matches) {
            val block = match.groupValues[1]

            // 1. Match [...] TJ (Array text with kerning)
            val tjArrayRegex = Regex("\\[([\\s\\S]*?)\\]\\s*TJ")
            val arrayMatches = tjArrayRegex.findAll(block)
            for (arrMatch in arrayMatches) {
                val arrayContent = arrMatch.groupValues[1]
                val stringInArr = Regex("\\((.*?)(?<!\\\\)\\)").findAll(arrayContent)
                val line = stringInArr.joinToString("") { unescapePdfString(it.groupValues[1]) }
                if (line.isNotBlank()) {
                    sb.append(line).append(" ")
                }
            }

            // 2. Match (...) Tj (Direct string)
            val tjStringRegex = Regex("\\((.*?)(?<!\\\\)\\)\\s*Tj")
            val strMatches = tjStringRegex.findAll(block)
            for (strMatch in strMatches) {
                val text = unescapePdfString(strMatch.groupValues[1])
                if (text.isNotBlank()) {
                    sb.append(text).append(" ")
                }
            }

            // 3. Hexadecimal encoded strings: <48656C6C6F> Tj
            val hexStringRegex = Regex("<([0-9a-fA-F]+)>\\s*Tj")
            val hexMatches = hexStringRegex.findAll(block)
            for (hexMatch in hexMatches) {
                val hex = hexMatch.groupValues[1]
                val decoded = decodeHexPdfString(hex)
                if (decoded.isNotBlank()) {
                    sb.append(decoded).append(" ")
                }
            }

            sb.append("\n")
        }

        return sb.toString()
    }

    private fun unescapePdfString(raw: String): String {
        return raw.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
    }

    private fun decodeHexPdfString(hex: String): String {
        return try {
            val sb = StringBuilder()
            var i = 0
            while (i < hex.length - 1) {
                val byteVal = hex.substring(i, i + 2).toInt(16)
                if (byteVal in 32..126) {
                    sb.append(byteVal.toChar())
                }
                i += 2
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun cleanExtractedText(text: String): String {
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (fromIndex >= source.size || target.isEmpty()) return -1
        outer@ for (i in fromIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
