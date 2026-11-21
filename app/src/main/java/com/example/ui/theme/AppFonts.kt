package com.example.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.example.R

object AppFonts {
    val KrutiDevFontFamily = FontFamily(Font(R.font.kruti_dev_010, FontWeight.Normal))
    val NotoSansDevanagariFontFamily = FontFamily(Font(R.font.noto_sans_devanagari, FontWeight.Normal))
    val TiroDevanagariFontFamily = FontFamily(Font(R.font.tiro_devanagari_hindi, FontWeight.Normal))
    val MuktaFontFamily = FontFamily(Font(R.font.mukta, FontWeight.Normal))
    val HindFontFamily = FontFamily(Font(R.font.hind, FontWeight.Normal))
    val OpenSansFontFamily = FontFamily(Font(R.font.open_sans, FontWeight.Normal))
    val NotoSerifFontFamily = FontFamily(Font(R.font.noto_serif, FontWeight.Normal))
    val PoppinsFontFamily = FontFamily(Font(R.font.poppins, FontWeight.Normal))
    val RobotoFontFamily = FontFamily(Font(R.font.roboto, FontWeight.Normal))
    val RobotoMonoFontFamily = FontFamily(Font(R.font.roboto_mono, FontWeight.Normal))

    enum class SupportedFont(
        val id: String,
        val displayName: String,
        val languageCategory: String,
        val sampleText: String,
        val fontFamily: FontFamily,
        @FontRes val fontResId: Int?
    ) {
        SYSTEM_DEFAULT(
            id = "DEFAULT",
            displayName = "System Default",
            languageCategory = "Standard",
            sampleText = "The quick brown fox • नमस्ते भारत",
            fontFamily = FontFamily.Default,
            fontResId = null
        ),
        KRUTI_DEV(
            id = "KRUTI_DEV",
            displayName = "Kruti Dev 010 (कुर्तीदेव)",
            languageCategory = "Hindi Typewriter",
            sampleText = "jkT; ljdkj • d`f",
            fontFamily = KrutiDevFontFamily,
            fontResId = R.font.kruti_dev_010
        ),
        HINDI_NOTO(
            id = "HINDI_NOTO",
            displayName = "Noto Sans Hindi (मंगल / हिंदी)",
            languageCategory = "Hindi Unicode",
            sampleText = "सत्यमेव जयते • सभी दस्तावेज़ सुरक्षित",
            fontFamily = NotoSansDevanagariFontFamily,
            fontResId = R.font.noto_sans_devanagari
        ),
        HINDI_TIRO(
            id = "HINDI_TIRO",
            displayName = "Tiro Devanagari (पुस्तक हिंदी)",
            languageCategory = "Hindi Serif",
            sampleText = "अधिकारिक पत्र एवं दस्तावेज़",
            fontFamily = TiroDevanagariFontFamily,
            fontResId = R.font.tiro_devanagari_hindi
        ),
        HINDI_MUKTA(
            id = "HINDI_MUKTA",
            displayName = "Mukta (मुक्ता)",
            languageCategory = "Hindi / English",
            sampleText = "नमस्ते भारत • Fast & Smart Reader",
            fontFamily = MuktaFontFamily,
            fontResId = R.font.mukta
        ),
        HINDI_HIND(
            id = "HINDI_HIND",
            displayName = "Hind (हिन्द)",
            languageCategory = "Hindi Devanagari",
            sampleText = "डिजिटल हस्ताक्षर एवं सुरक्षित फाइलें",
            fontFamily = HindFontFamily,
            fontResId = R.font.hind
        ),
        ENGLISH_OPEN_SANS(
            id = "ENGLISH_OPEN_SANS",
            displayName = "Open Sans (Calibri / Arial)",
            languageCategory = "Word Sans",
            sampleText = "Clean Office Document Body Text",
            fontFamily = OpenSansFontFamily,
            fontResId = R.font.open_sans
        ),
        ENGLISH_NOTO_SERIF(
            id = "ENGLISH_NOTO_SERIF",
            displayName = "Noto Serif (Times New Roman)",
            languageCategory = "Word Serif",
            sampleText = "Formal Agreement & Editorial Style",
            fontFamily = NotoSerifFontFamily,
            fontResId = R.font.noto_serif
        ),
        ENGLISH_POPPINS(
            id = "ENGLISH_POPPINS",
            displayName = "Poppins",
            languageCategory = "English Modern",
            sampleText = "Professional PDF & Document Reader",
            fontFamily = PoppinsFontFamily,
            fontResId = R.font.poppins
        ),
        ENGLISH_ROBOTO(
            id = "ENGLISH_ROBOTO",
            displayName = "Roboto",
            languageCategory = "English Clean",
            sampleText = "Clean & High Performance Document Viewer",
            fontFamily = RobotoFontFamily,
            fontResId = R.font.roboto
        ),
        ENGLISH_MONO(
            id = "ENGLISH_MONO",
            displayName = "Roboto Mono (Courier)",
            languageCategory = "Monospace",
            sampleText = "Code, Tabular & Fixed-Width Text",
            fontFamily = RobotoMonoFontFamily,
            fontResId = R.font.roboto_mono
        );

        fun getAndroidTypeface(context: Context): Typeface {
            return if (fontResId != null) {
                try {
                    ResourcesCompat.getFont(context, fontResId) ?: Typeface.DEFAULT
                } catch (e: Exception) {
                    Typeface.DEFAULT
                }
            } else {
                Typeface.DEFAULT
            }
        }

        companion object {
            fun fromId(id: String?): SupportedFont {
                return entries.find { it.id.equals(id, ignoreCase = true) } ?: SYSTEM_DEFAULT
            }
        }
    }

    /**
     * Maps an MS Word font family name (e.g., from w:rFonts) to the closest bundled SupportedFont.
     */
    fun resolveFromWordFontName(fontName: String?): SupportedFont? {
        if (fontName.isNullOrBlank()) return null
        val lower = fontName.trim().lowercase()
        return when {
            lower.contains("kruti") || lower.contains("krutidev") || lower.contains("devlys") ||
                lower.contains("chanakya") || lower.contains("agra") || lower.contains("kiran") -> SupportedFont.KRUTI_DEV
            lower.contains("tiro") || lower.contains("noto serif devanagari") ||
                lower.contains("siddhanta") || lower.contains("kokila") -> SupportedFont.HINDI_TIRO
            lower.contains("mukta") -> SupportedFont.HINDI_MUKTA
            lower.contains("hind") -> SupportedFont.HINDI_HIND
            lower.contains("mangal") || lower.contains("aparajita") || lower.contains("utsaah") ||
                lower.contains("nirmala") || lower.contains("devanagari") || lower.contains("lohit") ||
                lower.contains("gargi") || lower.contains("akshar") -> SupportedFont.HINDI_NOTO
            lower.contains("courier") || lower.contains("consolas") || lower.contains("mono") ||
                lower.contains("lucida console") -> SupportedFont.ENGLISH_MONO
            lower.contains("times") || lower.contains("georgia") || lower.contains("cambria") ||
                lower.contains("garamond") || lower.contains("palatino") || lower.contains("book") ||
                lower.contains("serif") -> SupportedFont.ENGLISH_NOTO_SERIF
            lower.contains("poppins") -> SupportedFont.ENGLISH_POPPINS
            lower.contains("roboto") -> SupportedFont.ENGLISH_ROBOTO
            lower.contains("calibri") || lower.contains("arial") || lower.contains("helvetica") ||
                lower.contains("verdana") || lower.contains("tahoma") || lower.contains("segoe") ||
                lower.contains("open sans") || lower.contains("trebuchet") || lower.contains("sans") -> SupportedFont.ENGLISH_OPEN_SANS
            else -> null
        }
    }

    fun containsDevanagariUnicode(text: String): Boolean {
        for (ch in text) {
            if (ch in '\u0900'..'\u097F') return true
        }
        return false
    }

    /**
     * Automatically determines the optimal bundled SupportedFont using explicit Word metadata first,
     * falling back to script & Kruti Dev 010 pattern detection.
     */
    fun detectFontForText(
        text: String,
        declaredFontName: String? = null,
        isKrutiLikely: Boolean = false
    ): SupportedFont {
        val hasDevanagariUnicode = containsDevanagariUnicode(text)
        val mapped = resolveFromWordFontName(declaredFontName)

        if (mapped != null) {
            // If Word specified a Latin-only font (e.g. Calibri) on Unicode Hindi text, upgrade to Hindi Unicode font
            if (hasDevanagariUnicode && mapped != SupportedFont.HINDI_NOTO &&
                mapped != SupportedFont.HINDI_TIRO && mapped != SupportedFont.HINDI_MUKTA &&
                mapped != SupportedFont.HINDI_HIND
            ) {
                return SupportedFont.HINDI_NOTO
            }
            if (isKrutiLikely && !hasDevanagariUnicode && mapped != SupportedFont.KRUTI_DEV) {
                return SupportedFont.KRUTI_DEV
            }
            return mapped
        }

        if (hasDevanagariUnicode) {
            return SupportedFont.HINDI_NOTO
        }
        if (isKrutiLikely) {
            return SupportedFont.KRUTI_DEV
        }
        return SupportedFont.ENGLISH_OPEN_SANS
    }
}
