package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdMobManager {
    private const val TAG = "AdMobManager"

    // Real AdMob Production Ad Unit IDs
    const val REAL_BANNER_ID = "ca-app-pub-9247143643125748/5366325906"
    const val REAL_INTERSTITIAL_ID = "ca-app-pub-9247143643125748/3927267520"
    const val REAL_REWARDED_ID = "ca-app-pub-9247143643125748/5688861742"
    const val REAL_NATIVE_ID = "ca-app-pub-9247143643125748/6648093340"

    var isInitialized = false
        private set

    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var lastInterstitialShowTime = 0L
    private const val INTERSTITIAL_COOLDOWN_MS = 10_000L // 10s cooldown for smooth UX and testing

    private var rewardedAd: RewardedAd? = null
    private var isRewardedLoading = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            MobileAds.initialize(context) { status ->
                isInitialized = true
                Log.d(TAG, "AdMob production initialized: $status")
                loadInterstitial(context.applicationContext)
                loadRewarded(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MobileAds", e)
        }
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        try {
            val configuredId = try { context.getString(R.string.admob_interstitial) } catch (e: Exception) { "" }
            val adUnitId = if (configuredId.isNotBlank()) configuredId else REAL_INTERSTITIAL_ID

            Log.d(TAG, "Loading real interstitial ad with ID: $adUnitId")
            val adRequest = AdRequest.Builder().build()

            InterstitialAd.load(
                context,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialLoading = false
                        Log.d(TAG, "Real Interstitial loaded successfully ($adUnitId)")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(TAG, "Real Interstitial failed ($adUnitId): ${loadAdError.message} (code ${loadAdError.code})")
                        interstitialAd = null
                        isInterstitialLoading = false
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading real interstitial", e)
            interstitialAd = null
            isInterstitialLoading = false
        }
    }

    /**
     * Specifically designed for Back Button press with frequency capping & cooldown.
     * If ad is ready and cooldown has passed: shows ad and invokes onDismiss when closed.
     * If not ready or on cooldown: IMMEDIATELY invokes onDismiss without blocking user!
     */
    fun showBackPressInterstitial(activity: Activity, onDismiss: () -> Unit) {
        try {
            val now = SystemClock.elapsedRealtime()
            val ad = interstitialAd

            if (ad != null && (lastInterstitialShowTime == 0L || (now - lastInterstitialShowTime >= INTERSTITIAL_COOLDOWN_MS))) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        interstitialAd = null
                        lastInterstitialShowTime = SystemClock.elapsedRealtime()
                        loadInterstitial(activity.applicationContext)
                        onDismiss()
                    }

                    override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                        Log.w(TAG, "Interstitial show failed: ${error.message}")
                        interstitialAd = null
                        loadInterstitial(activity.applicationContext)
                        onDismiss()
                    }

                    override fun onAdShowedFullScreenContent() {
                        lastInterstitialShowTime = SystemClock.elapsedRealtime()
                        Log.d(TAG, "Interstitial displayed on screen")
                    }
                }
                ad.show(activity)
            } else {
                onDismiss()
                if (ad == null) {
                    loadInterstitial(activity.applicationContext)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing back press interstitial", e)
            onDismiss()
        }
    }

    fun showTaskCompletionInterstitial(activity: Activity, onDismiss: () -> Unit) {
        showBackPressInterstitial(activity, onDismiss)
    }

    fun loadRewarded(context: Context) {
        if (rewardedAd != null || isRewardedLoading) return
        isRewardedLoading = true

        try {
            val configuredId = try { context.getString(R.string.admob_rewarded) } catch (e: Exception) { "" }
            val adUnitId = if (configuredId.isNotBlank()) configuredId else REAL_REWARDED_ID

            Log.d(TAG, "Loading real rewarded ad with ID: $adUnitId")
            val adRequest = AdRequest.Builder().build()

            RewardedAd.load(
                context,
                adUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isRewardedLoading = false
                        Log.d(TAG, "Real Rewarded ad loaded successfully ($adUnitId)")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(TAG, "Real Rewarded ad failed ($adUnitId): ${loadAdError.message} (code ${loadAdError.code})")
                        rewardedAd = null
                        isRewardedLoading = false
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading real rewarded ad", e)
            rewardedAd = null
            isRewardedLoading = false
        }
    }

    fun isRewardedAdReady(): Boolean = rewardedAd != null

    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: () -> Unit,
        onAdClosed: () -> Unit
    ) {
        try {
            val ad = rewardedAd
            if (ad != null) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadRewarded(activity.applicationContext)
                        onAdClosed()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        Log.w(TAG, "Rewarded ad show failed: ${adError.message}")
                        rewardedAd = null
                        loadRewarded(activity.applicationContext)
                        onAdClosed()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Rewarded ad displayed on screen")
                    }
                }

                ad.show(activity) { rewardItem ->
                    Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                    onUserEarnedReward()
                }
            } else {
                // Ad was not ready yet - load it in background and still reward user gracefully
                Toast.makeText(activity, "Loading reward ad... Pro features unlocked!", Toast.LENGTH_SHORT).show()
                onUserEarnedReward()
                loadRewarded(activity.applicationContext)
                onAdClosed()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showRewardedAd", e)
            onUserEarnedReward()
            onAdClosed()
        }
    }
}

/**
 * Adaptive Banner anchored at the bottom of main screens using real AdMob banner ad unit.
 */
@Composable
fun AdaptiveBannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuredBannerId = remember {
        val id = try { context.getString(R.string.admob_banner) } catch (e: Exception) { "" }
        if (id.isNotBlank()) id else AdMobManager.REAL_BANNER_ID
    }
    var adViewRef by remember { mutableStateOf<AdView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                adViewRef?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    adUnitId = configuredBannerId
                    setAdSize(AdSize.BANNER)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d("AdMobManager", "Real Banner ad loaded successfully on screen: $configuredBannerId")
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            Log.w("AdMobManager", "Real Banner ad failed to load ($adUnitId): ${loadAdError.message} (code: ${loadAdError.code})")
                        }
                    }
                    try {
                        loadAd(AdRequest.Builder().build())
                    } catch (e: Exception) {
                        Log.e("AdMobManager", "Real Banner initial load error", e)
                    }
                    adViewRef = this
                }
            }
        )
    }
}
