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
    val MuktaFontFamily = FontFamily(Font(R.font.mukta, FontWeight.Normal))
    val HindFontFamily = FontFamily(Font(R.font.hind, FontWeight.Normal))
    val PoppinsFontFamily = FontFamily(Font(R.font.poppins, FontWeight.Normal))
    val RobotoFontFamily = FontFamily(Font(R.font.roboto, FontWeight.Normal))

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
            languageCategory = "Auto",
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
            displayName = "Noto Sans Hindi (हिंदी)",
            languageCategory = "Hindi Devanagari",
            sampleText = "सत्यमेव जयते • सभी दस्तावेज़ सुरक्षित",
            fontFamily = NotoSansDevanagariFontFamily,
            fontResId = R.font.noto_sans_devanagari
        ),
        HINDI_MUKTA(
            id = "HINDI_MUKTA",
            displayName = "Mukta (मुक्ता)",
            languageCategory = "Hindi / English",
            sampleText = "नमस्ते भारत • Fast & Smart PDF Reader",
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
}
