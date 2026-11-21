package com.example.util

object KrutiDevConverter {
    private val krutiToUnicodeRules: List<Pair<String, String>> = listOf(
        "ç" to "fa", "˜" to "ज़्", "ø" to "फ़्", "ù" to "झ्", "ú" to "झ्", "û" to "ख्",
        "ü" to "ख्", "ý" to "दृ", "þ" to "दृ", "å" to "ट", "æ" to "ठ", "è" to "ढ",
        "é" to "ण", "ê" to "त", "ë" to "थ", "ì" to "द", "í" to "ध", "î" to "न",
        "ï" to "प", "ð" to "फ", "ñ" to "ब", "ò" to "भ", "ó" to "म", "ô" to "य",
        "õ" to "र", "ö" to "ल", "÷" to "व", "‘" to "ष्", "’" to "bd", "“" to "ड्ड",
        "”" to "द्ध", "•" to "द्द", "—" to "दृ", "™" to "ऋ", "=" to "त्र", "«" to "त्र",
        "|" to "।", "»" to "हृ", "±" to "ह्न", "²" to "ह्म", "³" to "ह्य", "´" to "ह्र",
        "µ" to "क्त", "¶" to "क्र", "·" to "द्ग", "¸" to "द्घ", "¹" to "त्त", "º" to "त्थ",
        "½" to "द्र", "¾" to "ध्र", "¿" to "श्र", "À" to "द्य", "Á" to "द्व", "Â" to "ट्र",
        "Ã" to "ड्र", "Ä" to "ढ्र", "Å" to "ऊ", "Æ" to "ठ्ठ", "Ç" to "ठ", "È" to "ड",
        "É" to "ढ", "Ê" to "ण", "Q+" to "फ़", "K+" to "क़", "x+" to "ग़", "t+" to "ज़",
        "M+" to "ड़", "<+" to "ढ़", "vks" to "ओ", "vkS" to "औ", "vk" to "आ", "v" to "अ",
        "bZ" to "ई", "b" to "इ", "m" to "उ", "D" to "क्", "d" to "क", "[" to "ख्",
        "[k" to "ख", "X" to "ग्", "x" to "ग", "?" to "घ्", "?k" to "घ", "P" to "च्",
        "p" to "च", "N" to "छ", "T" to "ट्", "V" to "ट", "B" to "ठ", "M" to "ड",
        "<" to "ढ", "." to "ण्", ".k" to "ण", "R" to "त्", "r" to "त", "F" to "थ्",
        "Fk" to "थ", "n" to "द", "G" to "ध्", "/k" to "ध", "/" to "ध्", "U" to "न्",
        "u" to "न", "I" to "प्", "i" to "प", "Q" to "फ्", "Qk" to "फ", "Y" to "ब्",
        "c" to "ब", "H" to "भ्", "Hk" to "भ", "E" to "म्", "e" to "म", ";" to "य",
        "j" to "र", "y" to "ल", "O" to "व्", "o" to "व", "'" to "श्", "'k" to "श",
        "\"" to "ष्", "\"k" to "ष", "L" to "स्", "l" to "स", "g" to "ह", "K" to "ज्ञ",
        "J" to "श्र", "k" to "ा", "h" to "ी", "q" to "ु", "w" to "ू", "`" to "ृ",
        "s" to "े", "S" to "ै", "a" to "ं", "¡" to "ँ", "%" to "ः", "W" to "ॉ",
        "~" to "्", "+" to "़", "0" to "०", "1" to "१", "2" to "२", "3" to "३",
        "4" to "४", "5" to "५", "6" to "६", "7" to "७", "8" to "८", "9" to "९"
    )

    fun krutiDevToUnicode(krutiText: String): String {
        var modified = krutiText
        for ((k, u) in krutiToUnicodeRules) {
            modified = modified.replace(k, u)
        }
        return modified
    }

    fun isLikelyKrutiDev(text: String): Boolean {
        if (text.isBlank()) return false
        val sample = text.take(600)
        // If text already contains Unicode Devanagari, it is not legacy Kruti Dev encoding
        if (sample.any { it in '\u0900'..'\u097F' }) return false

        // 1. High-confidence special characters used in Kruti Dev 010
        val specialKrutiChars = listOf(
            "ç", "˜", "ø", "ù", "ú", "û", "ü", "ý", "þ", "å", "æ", "è",
            "é", "ê", "ë", "ì", "í", "î", "ï", "ð", "ñ", "ò", "ó", "ô",
            "õ", "ö", "÷", "»", "±", "²", "³", "´", "µ", "¶", "·", "¸",
            "¹", "º", "½", "¾", "¿", "À", "Á", "Â", "Ã", "Ä", "Å", "Æ",
            "Ç", "È", "É", "Ê"
        )
        if (specialKrutiChars.any { sample.contains(it) }) return true

        // 2. Common ASCII-only Kruti Dev 010 letter sequences & punctuation inside words
        val asciiKrutiTokens = listOf(
            "vks", "vkS", "bZ", "lsok esa", "egksn;", "dk;kZy;", "ljdkj",
            "izfrfyfi", "fnukad", "i=kad", "foHkkx", "vf/kdkjh", "jktLFkku",
            "dk;Z", "vkns'k", "fu;e", "gLrk{kj", "mijksDr", "d`i;k", "blfy,",
            "gSa", "tkrk gS", "fd;k", "fy,", "esa "
        )
        var score = 0
        for (token in asciiKrutiTokens) {
            if (sample.contains(token)) score += 2
        }
        // Semicolon, '[' or '/' directly attached to letters is a classic Kruti Dev marker (e.g., ';', '[k', '/k')
        val midWordPunctuationRegex = Regex("[A-Za-z][;/\\[\\\\=][A-Za-z]|[;/\\[\\\\=][A-Za-z]{2,}")
        score += midWordPunctuationRegex.findAll(sample).count()

        return score >= 2
    }
}
