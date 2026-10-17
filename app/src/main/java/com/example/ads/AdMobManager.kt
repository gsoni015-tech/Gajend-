package com.example.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdvancedTool(val id: String, val title: String) {
    COMPRESS("compress", "Compress PDF"),
    OCR("ocr", "OCR Text Extract"),
    MERGE("merge", "PDF Merge & Split")
}

data class NativeAdItem(
    val adId: String,
    val headline: String,
    val body: String,
    val advertiser: String,
    val callToAction: String,
    val starRating: Double,
    val adUnitId: String = AdMobManager.AD_UNIT_NATIVE_ADVANCED,
    val iconResName: String = "ic_app_icon"
)

object AdMobManager {
    // Configured AdMob Ad Unit IDs
    const val AD_UNIT_NATIVE_ADVANCED = "ca-app-pub-9247143643125748/6648093340"
    const val AD_UNIT_INTERSTITIAL = "ca-app-pub-9247143643125748/3927267520"
    const val AD_UNIT_BANNER = "ca-app-pub-9247143643125748/5366325906"
    const val PUBLISHER_ID = "ca-app-pub-9247143643125748"

    // Reward passes or unlocked tools
    private val _unlockedTools = MutableStateFlow<Set<String>>(emptySet())
    val unlockedTools: StateFlow<Set<String>> = _unlockedTools.asStateFlow()

    // Interstitial state
    private val _showInterstitial = MutableStateFlow(false)
    val showInterstitial: StateFlow<Boolean> = _showInterstitial.asStateFlow()
    private var interstitialDismissCallback: (() -> Unit)? = null

    // Rewarded state
    private val _showRewarded = MutableStateFlow(false)
    val showRewarded: StateFlow<Boolean> = _showRewarded.asStateFlow()
    private var pendingRewardTool: AdvancedTool? = null
    private var rewardSuccessCallback: (() -> Unit)? = null

    fun isToolUnlocked(tool: AdvancedTool): Boolean {
        return _unlockedTools.value.contains(tool.id)
    }

    fun unlockTool(tool: AdvancedTool) {
        _unlockedTools.value = _unlockedTools.value + tool.id
    }

    fun showInterstitialAd(onDismissed: () -> Unit = {}) {
        interstitialDismissCallback = onDismissed
        _showInterstitial.value = true
    }

    fun dismissInterstitialAd() {
        _showInterstitial.value = false
        interstitialDismissCallback?.invoke()
        interstitialDismissCallback = null
    }

    fun showRewardedVideoAd(tool: AdvancedTool, onRewardGranted: () -> Unit) {
        pendingRewardTool = tool
        rewardSuccessCallback = onRewardGranted
        _showRewarded.value = true
    }

    fun grantRewardAndDismiss() {
        val tool = pendingRewardTool
        if (tool != null) {
            unlockTool(tool)
        }
        _showRewarded.value = false
        rewardSuccessCallback?.invoke()
        pendingRewardTool = null
        rewardSuccessCallback = null
    }

    fun dismissRewardedAdWithoutReward() {
        _showRewarded.value = false
        pendingRewardTool = null
        rewardSuccessCallback = null
    }

    val sampleNativeAds = listOf(
        NativeAdItem(
            adId = "ad_1",
            headline = "Cloud Storage & Backup Pro",
            body = "Sync all your PDF documents securely across unlimited devices with end-to-end encryption.",
            advertiser = "CloudVault Inc.",
            callToAction = "Install Now",
            starRating = 4.8
        ),
        NativeAdItem(
            adId = "ad_2",
            headline = "Fast E-Signature & Document Sign",
            body = "Fill and sign PDFs with certified legal electronic signatures in under 30 seconds.",
            advertiser = "DocuSign Services",
            callToAction = "Try Free",
            starRating = 4.9
        ),
        NativeAdItem(
            adId = "ad_3",
            headline = "Office Suite AI Document Editor",
            body = "Convert PDF to Word, Excel, and PowerPoint with preserving 100% layout formatting.",
            advertiser = "OfficePro Global",
            callToAction = "Download",
            starRating = 4.7
        )
    )
}
