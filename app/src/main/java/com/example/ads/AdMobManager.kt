package com.example.ads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class AdvancedTool(val id: String, val title: String) {
    EDIT("edit", "Edit PDF"),
    MERGE("merge", "Merge & Split"),
    COMPRESS("compress", "Compress PDF"),
    PDF_TO_JPG("pdf_to_jpg", "PDF to JPG"),
    LOCK("lock", "Lock/Unlock PIN"),
    OCR("ocr", "OCR Extract Text");

    companion object {
        fun fromId(id: String?): AdvancedTool? = entries.find { it.id == id }
    }
}

data class NativeAdItem(
    val headline: String = "",
    val body: String = "",
    val callToAction: String = "",
    val advertiser: String = ""
)

object AdMobManager {
    var isInitialized = false

    fun initialize(context: Context) {
        isInitialized = true
    }

    fun showInterstitial(context: Context, onDismiss: () -> Unit) {
        onDismiss()
    }
}

@Composable
fun TopBannerAdView(modifier: Modifier = Modifier) {
    // Top banner ad is disabled / removed per user request
}

@Composable
fun InListAdView(modifier: Modifier = Modifier) {
    // In-between document ads are disabled / removed per user request
}

@Composable
fun NativeToolAdCard(modifier: Modifier = Modifier) {
    // Native ad in PDF tools is disabled / removed per user request
}
