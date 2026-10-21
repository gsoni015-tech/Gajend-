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
        val sample = text.take(100)
        val krutiChars = listOf("ç", "˜", "ø", "ù", "ú", "û", "ü", "ý", "þ", "å", "æ", "è", "é", "ê", "ë", "ì", "í", "î", "ï", "ð", "ñ", "ò", "ó", "ô", "õ", "ö", "÷", "vks", "vkS", "vk", "bZ")
        return krutiChars.any { sample.contains(it) }
    }
}
