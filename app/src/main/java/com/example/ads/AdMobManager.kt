package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.ContextWrapper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.AdBadgeAmber
import com.example.ui.theme.AdBadgeAmberContainer
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandPrimaryContainer
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixBorder
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.theme.WhiteMixSurfaceVariant
import com.google.android.gms.ads.AdError
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backward-compatible alias so existing AdMobManager references resolve to AdManager.
 */
typealias AdMobManager = AdManager

enum class BannerAdState {
    LOADING,
    LOADED,
    RETRYING,
    FAILED
}

private data class PendingRewardedRequest(
    val activityRef: WeakReference<Activity>,
    val onUserEarnedReward: () -> Unit,
    val onAdClosed: () -> Unit
)

/**
 * Centralized singleton managing AdMob initialization, production unit selection,
 * bounded auto-retry preloading, 25-second frequency capping, App-Open & periodic
 * impressions, and Rewarded ad on-demand fallback.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Exact Production AdMob Ad Unit IDs
    const val BANNER_AD_UNIT_ID = "ca-app-pub-9247143643125748/5366325906"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-9247143643125748/3927267520"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-9247143643125748/5688861742"

    // Official Google Test AdMob Ad Unit IDs (retained only as explicit test reference constants)
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    // Use official Google Test Ad Units in Debug builds (preventing NO_FILL on unpublished debug/emulator builds)
    // and exact production AdMob Ad Units in Release builds.
    const val USE_PRODUCTION_ADS_ALWAYS = false

    // Legacy constant aliases kept for compatibility
    const val REAL_BANNER_ID = BANNER_AD_UNIT_ID
    const val REAL_INTERSTITIAL_ID = INTERSTITIAL_AD_UNIT_ID
    const val REAL_REWARDED_ID = REWARDED_AD_UNIT_ID
    const val REAL_NATIVE_ID = "ca-app-pub-9247143643125748/6648093340"

    // 25-second cooldown between Interstitial displays for periodic & action triggers
    const val INTERSTITIAL_COOLDOWN_MS = 25_000L

    // Delayed mid-reading Interstitial wait duration inside Document Viewers
    const val DOCUMENT_READING_AD_DELAY_MS = 15_000L

    // Bounded exponential retry schedule
    const val MAX_LOAD_RETRIES = 3
    private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L)
    private const val INIT_WATCHDOG_MS = 2_500L
    private const val PENDING_REWARDED_TIMEOUT_MS = 10_000L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val initStarted = AtomicBoolean(false)
    private val _isInitializedFlow = MutableStateFlow(false)
    val isInitializedFlow: StateFlow<Boolean> = _isInitializedFlow.asStateFlow()

    val isInitialized: Boolean
        get() = _isInitializedFlow.value

    private val _isInterstitialReadyFlow = MutableStateFlow(false)
    val isInterstitialReadyFlow: StateFlow<Boolean> = _isInterstitialReadyFlow.asStateFlow()

    private val _isRewardedLoadingFlow = MutableStateFlow(false)
    val isRewardedLoadingFlow: StateFlow<Boolean> = _isRewardedLoadingFlow.asStateFlow()

    private val hasAttemptedLaunchInterstitial = AtomicBoolean(false)
    private val userNavigatedFromHome = AtomicBoolean(false)

    @Volatile
    private var initWatchdogRunnable: Runnable? = null

    @Volatile
    private var interstitialAd: InterstitialAd? = null
    @Volatile
    private var isInterstitialLoading = false
    @Volatile
    private var interstitialRetryCount = 0
    @Volatile
    private var lastInterstitialFailureMs: Long = 0L
    @Volatile
    private var interstitialRetryRunnable: Runnable? = null

    @Volatile
    private var isShowingFullScreenAd = false
    @Volatile
    private var lastInterstitialShownTimestampMs: Long = 0L

    @Volatile
    private var rewardedAd: RewardedAd? = null
    @Volatile
    private var isRewardedLoading = false
    @Volatile
    private var rewardedRetryCount = 0
    @Volatile
    private var lastRewardedFailureMs: Long = 0L
    @Volatile
    private var rewardedRetryRunnable: Runnable? = null
    @Volatile
    private var pendingRewardedRequest: PendingRewardedRequest? = null
    @Volatile
    private var pendingRewardedTimeoutRunnable: Runnable? = null
    @Volatile
    private var lastRewardedErrorReason: String? = null

    fun resolveAdUnitId(
        isDebug: Boolean,
        productionId: String,
        testId: String,
        forceProduction: Boolean = USE_PRODUCTION_ADS_ALWAYS
    ): String = if (forceProduction || !isDebug) productionId else testId

    fun describeAdErrorCode(code: Int): String = when (code) {
        0 -> "Internal Error (0)"
        1 -> "Invalid Request (1)"
        2 -> "Network Error (2)"
        3 -> "No Ad Fill (3)"
        8 -> "Invalid App ID (8)"
        else -> "Error $code"
    }

    fun retryDelayForAttempt(attemptIndex: Int): Long {
        if (RETRY_DELAYS_MS.isEmpty()) return 5_000L
        return RETRY_DELAYS_MS[attemptIndex.coerceIn(0, RETRY_DELAYS_MS.lastIndex)]
    }

    fun initialize(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context

        // Watchdog fallback in case MobileAds.initialize callback is delayed on restricted emulators
        try {
            val watchdog = Runnable {
                initWatchdogRunnable = null
                if (!_isInitializedFlow.value) {
                    _isInitializedFlow.value = true
                    Log.w(TAG, "MobileAds init watchdog fired; proceeding with ad preloads")
                    loadInterstitial(appContext)
                    loadRewarded(appContext)
                }
            }
            initWatchdogRunnable = watchdog
            mainHandler.postDelayed(watchdog, INIT_WATCHDOG_MS)
        } catch (_: Throwable) {}

        try {
            if (!USE_PRODUCTION_ADS_ALWAYS && BuildConfig.DEBUG) {
                val requestConfiguration = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(requestConfiguration)
            } else {
                MobileAds.setRequestConfiguration(RequestConfiguration.Builder().build())
            }

            MobileAds.initialize(appContext) { status ->
                try {
                    initWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
                    initWatchdogRunnable = null
                } catch (_: Throwable) {}
                _isInitializedFlow.value = true
                Log.d(TAG, "MobileAds initialized (debug=${BuildConfig.DEBUG}): $status")
                if (interstitialAd == null && !isInterstitialLoading) {
                    interstitialRetryCount = 0
                    loadInterstitial(appContext)
                }
                if (rewardedAd == null && !isRewardedLoading) {
                    rewardedRetryCount = 0
                    loadRewarded(appContext)
                }
            }
        } catch (t: Throwable) {
            _isInitializedFlow.value = true
            Log.e(TAG, "Failed to initialize MobileAds, continuing gracefully", t)
            loadInterstitial(appContext)
            loadRewarded(appContext)
        }
    }

    private fun isActivityValid(activity: Activity?): Boolean {
        return activity != null && !activity.isFinishing && !activity.isDestroyed
    }

    /**
     * Timestamp-based 60-second anti-spam check for Interstitial triggers.
     */
    fun canShowInterstitial(nowMs: Long = System.currentTimeMillis()): Boolean {
        return remainingInterstitialCooldownMs(nowMs) == 0L
    }

    /**
     * Returns remaining milliseconds inside the 60-second cooldown window (0L when ready).
     */
    fun remainingInterstitialCooldownMs(nowMs: Long = System.currentTimeMillis()): Long {
        val lastShown = lastInterstitialShownTimestampMs
        if (lastShown == 0L) return 0L
        val elapsed = nowMs - lastShown
        return if (elapsed >= INTERSTITIAL_COOLDOWN_MS) 0L else (INTERSTITIAL_COOLDOWN_MS - elapsed)
    }

    /**
     * Marks that the user has already opened a document / navigated off Home during launch,
     * ensuring the App-Open ad does not surprise the user after returning from a document.
     */
    fun markUserNavigatedFromHome() {
        userNavigatedFromHome.set(true)
    }

    /**
     * Pure guard check to verify whether an App-Open Interstitial should trigger.
     */
    internal fun shouldTriggerLaunchInterstitial(
        isHomeActive: Boolean,
        hasAttemptedLaunch: Boolean,
        hasNavigatedFromHome: Boolean,
        isAdReady: Boolean,
        isShowingAd: Boolean,
        canShowByCooldown: Boolean
    ): Boolean {
        return isHomeActive &&
            !hasAttemptedLaunch &&
            !hasNavigatedFromHome &&
            isAdReady &&
            !isShowingAd &&
            canShowByCooldown
    }

    /**
     * Impression #1: Triggers a one-shot App-Open Interstitial once the first Interstitial finishes
     * loading on cold launch, strictly while Home is still active.
     */
    fun tryShowLaunchInterstitial(activity: Activity?, isHomeActive: Boolean): Boolean {
        if (!isActivityValid(activity)) return false
        val canTrigger = shouldTriggerLaunchInterstitial(
            isHomeActive = isHomeActive,
            hasAttemptedLaunch = hasAttemptedLaunchInterstitial.get(),
            hasNavigatedFromHome = userNavigatedFromHome.get(),
            isAdReady = interstitialAd != null,
            isShowingAd = isShowingFullScreenAd,
            canShowByCooldown = canShowInterstitial()
        )
        if (!canTrigger) return false

        if (!hasAttemptedLaunchInterstitial.compareAndSet(false, true)) return false
        showInterstitialAd(activity) {}
        return true
    }

    /**
     * Exposes state reset strictly for unit testing.
     */
    internal fun resetForTesting() {
        try {
            initWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
            interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingRewardedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {
            // Looper may not be prepared in plain JVM unit tests
        }
        initWatchdogRunnable = null
        interstitialRetryRunnable = null
        rewardedRetryRunnable = null
        pendingRewardedTimeoutRunnable = null
        lastRewardedErrorReason = null
        interstitialAd = null
        rewardedAd = null
        isInterstitialLoading = false
        isRewardedLoading = false
        interstitialRetryCount = 0
        rewardedRetryCount = 0
        lastInterstitialFailureMs = 0L
        lastRewardedFailureMs = 0L
        isShowingFullScreenAd = false
        lastInterstitialShownTimestampMs = 0L
        pendingRewardedRequest = null
        hasAttemptedLaunchInterstitial.set(false)
        userNavigatedFromHome.set(false)
        _isInterstitialReadyFlow.value = false
        _isRewardedLoadingFlow.value = false
    }

    internal fun setLastInterstitialShownTimestampForTesting(timestampMs: Long) {
        lastInterstitialShownTimestampMs = timestampMs
    }

    fun onAppResumed(context: Context) {
        val appContext = context.applicationContext ?: context
        if (interstitialAd == null && !isInterstitialLoading) {
            if (interstitialRetryCount >= MAX_LOAD_RETRIES) {
                interstitialRetryCount = 0
            }
            loadInterstitial(appContext)
        }
        if (rewardedAd == null && !isRewardedLoading) {
            if (rewardedRetryCount >= MAX_LOAD_RETRIES) {
                rewardedRetryCount = 0
            }
            loadRewarded(appContext)
        }
    }

    private fun scheduleInterstitialRetry(appContext: Context) {
        val currentAttempt = interstitialRetryCount
        if (currentAttempt >= MAX_LOAD_RETRIES) return
        val delayMs = retryDelayForAttempt(currentAttempt)
        interstitialRetryCount = currentAttempt + 1

        interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            interstitialRetryRunnable = null
            loadInterstitial(appContext)
        }
        interstitialRetryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
        Log.d(TAG, "Scheduled Interstitial retry #$interstitialRetryCount in ${delayMs}ms")
    }

    private fun scheduleRewardedRetry(appContext: Context) {
        val currentAttempt = rewardedRetryCount
        if (currentAttempt >= MAX_LOAD_RETRIES) return
        val delayMs = retryDelayForAttempt(currentAttempt)
        rewardedRetryCount = currentAttempt + 1

        rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            rewardedRetryRunnable = null
            loadRewarded(appContext)
        }
        rewardedRetryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
        Log.d(TAG, "Scheduled Rewarded retry #$rewardedRetryCount in ${delayMs}ms")
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return
        val appContext = context.applicationContext ?: context
        val now = System.currentTimeMillis()
        if (interstitialRetryCount >= MAX_LOAD_RETRIES &&
            interstitialRetryRunnable == null &&
            now - lastInterstitialFailureMs >= 30_000L
        ) {
            interstitialRetryCount = 0
        }
        isInterstitialLoading = true
        _isInterstitialReadyFlow.value = false

        try {
            val configuredId = try { appContext.getString(R.string.admob_interstitial) } catch (_: Exception) { "" }
            val productionId = configuredId.ifBlank { INTERSTITIAL_AD_UNIT_ID }
            val adUnitId = resolveAdUnitId(
                isDebug = BuildConfig.DEBUG,
                productionId = productionId,
                testId = TEST_INTERSTITIAL_AD_UNIT_ID
            )
            val adRequest = AdRequest.Builder().build()

            InterstitialAd.load(
                appContext,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isInterstitialLoading = false
                        interstitialRetryCount = 0
                        lastInterstitialFailureMs = 0L
                        _isInterstitialReadyFlow.value = true
                        Log.d(TAG, "Interstitial preloaded ($adUnitId)")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isInterstitialLoading = false
                        lastInterstitialFailureMs = System.currentTimeMillis()
                        _isInterstitialReadyFlow.value = false
                        Log.w(TAG, "Interstitial failed to load ($adUnitId): ${loadAdError.message} (${loadAdError.code})")
                        scheduleInterstitialRetry(appContext)
                    }
                }
            )
        } catch (t: Throwable) {
            interstitialAd = null
            isInterstitialLoading = false
            lastInterstitialFailureMs = System.currentTimeMillis()
            _isInterstitialReadyFlow.value = false
            Log.e(TAG, "Exception while loading interstitial", t)
        }
    }

    /**
     * Displays a preloaded Interstitial ONLY if 60 seconds have elapsed since the last shown ad
     * and the Activity is active. Otherwise skips immediately without blocking UX.
     */
    fun showInterstitialAd(activity: Activity?, onAdClosedOrSkipped: () -> Unit) {
        var callbackInvoked = false
        fun completeOnce() {
            if (!callbackInvoked) {
                callbackInvoked = true
                onAdClosedOrSkipped()
            }
        }

        if (!isActivityValid(activity)) {
            completeOnce()
            return
        }
        val validActivity = activity!!
        val appContext = validActivity.applicationContext

        try {
            val now = System.currentTimeMillis()
            if (!canShowInterstitial(now)) {
                if (interstitialAd == null) {
                    loadInterstitial(appContext)
                }
                completeOnce()
                return
            }

            val ad = interstitialAd
            if (ad == null || isShowingFullScreenAd) {
                if (ad == null) {
                    loadInterstitial(appContext)
                }
                completeOnce()
                return
            }

            interstitialAd = null
            _isInterstitialReadyFlow.value = false
            isShowingFullScreenAd = true

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    lastInterstitialShownTimestampMs = System.currentTimeMillis()
                    Log.d(TAG, "Interstitial displayed")
                }

                override fun onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false
                    lastInterstitialShownTimestampMs = System.currentTimeMillis()
                    interstitialRetryCount = 0
                    // Immediately preload the next interstitial in the background
                    loadInterstitial(appContext)
                    completeOnce()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    isShowingFullScreenAd = false
                    Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                    loadInterstitial(appContext)
                    completeOnce()
                }
            }

            ad.show(validActivity)
        } catch (t: Throwable) {
            isShowingFullScreenAd = false
            Log.e(TAG, "Error showing interstitial", t)
            completeOnce()
        }
    }

    /**
     * Impression #2: Displays a delayed mid-document Interstitial while viewing PDF/Word/Excel,
     * strictly observing the 60s anti-spam cooldown.
     */
    fun showDelayedDocumentInterstitial(activity: Activity?) {
        showInterstitialAd(activity) {}
    }

    fun showOpenDocumentInterstitial(activity: Activity?, onContinue: () -> Unit) {
        showInterstitialAd(activity, onContinue)
    }

    fun showBackPressInterstitial(activity: Activity?, onDismiss: () -> Unit) {
        showInterstitialAd(activity, onDismiss)
    }

    fun showTaskCompletionInterstitial(activity: Activity?, onDismiss: () -> Unit) {
        showInterstitialAd(activity, onDismiss)
    }

    fun loadRewarded(context: Context) {
        if (rewardedAd != null || isRewardedLoading) return
        val appContext = context.applicationContext ?: context
        val now = System.currentTimeMillis()
        if (rewardedRetryCount >= MAX_LOAD_RETRIES &&
            rewardedRetryRunnable == null &&
            now - lastRewardedFailureMs >= 30_000L
        ) {
            rewardedRetryCount = 0
        }
        isRewardedLoading = true

        try {
            val configuredId = try { appContext.getString(R.string.admob_rewarded) } catch (_: Exception) { "" }
            val productionId = configuredId.ifBlank { REWARDED_AD_UNIT_ID }
            val adUnitId = resolveAdUnitId(
                isDebug = BuildConfig.DEBUG,
                productionId = productionId,
                testId = TEST_REWARDED_AD_UNIT_ID
            )
            val adRequest = AdRequest.Builder().build()

            RewardedAd.load(
                appContext,
                adUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        clearPendingRewardedTimeout()
                        lastRewardedErrorReason = null
                        lastRewardedFailureMs = 0L
                        rewardedAd = ad
                        isRewardedLoading = false
                        rewardedRetryCount = 0
                        Log.d(TAG, "Rewarded ad preloaded ($adUnitId)")

                        val pending = pendingRewardedRequest
                        if (pending != null) {
                            pendingRewardedRequest = null
                            _isRewardedLoadingFlow.value = false
                            val pendingActivity = pending.activityRef.get()
                            if (isActivityValid(pendingActivity)) {
                                showRewardedAd(
                                    activity = pendingActivity,
                                    onUserEarnedReward = pending.onUserEarnedReward,
                                    onAdClosed = pending.onAdClosed
                                )
                            } else {
                                pending.onAdClosed()
                            }
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        clearPendingRewardedTimeout()
                        rewardedAd = null
                        isRewardedLoading = false
                        lastRewardedFailureMs = System.currentTimeMillis()
                        val reason = describeAdErrorCode(loadAdError.code)
                        lastRewardedErrorReason = reason
                        Log.w(TAG, "Rewarded failed to load ($adUnitId): ${loadAdError.message} ($reason)")

                        val pending = pendingRewardedRequest
                        if (pending != null) {
                            pendingRewardedRequest = null
                            _isRewardedLoadingFlow.value = false
                            val pendingActivity = pending.activityRef.get()
                            if (isActivityValid(pendingActivity)) {
                                Toast.makeText(
                                    pendingActivity,
                                    "Rewarded ad unavailable: $reason",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            pending.onAdClosed()
                        }

                        scheduleRewardedRetry(appContext)
                    }
                }
            )
        } catch (t: Throwable) {
            clearPendingRewardedTimeout()
            rewardedAd = null
            isRewardedLoading = false
            lastRewardedFailureMs = System.currentTimeMillis()
            lastRewardedErrorReason = t.message ?: "Load exception"
            pendingRewardedRequest?.let { pending ->
                pendingRewardedRequest = null
                _isRewardedLoadingFlow.value = false
                pending.onAdClosed()
            }
            Log.e(TAG, "Exception while loading rewarded ad", t)
        }
    }

    private fun clearPendingRewardedTimeout() {
        try {
            pendingRewardedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {}
        pendingRewardedTimeoutRunnable = null
    }

    private fun schedulePendingRewardedTimeout() {
        clearPendingRewardedTimeout()
        val runnable = Runnable {
            pendingRewardedTimeoutRunnable = null
            val pending = pendingRewardedRequest
            if (pending != null) {
                pendingRewardedRequest = null
                _isRewardedLoadingFlow.value = false
                isRewardedLoading = false
                val pendingActivity = pending.activityRef.get()
                if (isActivityValid(pendingActivity)) {
                    val reason = lastRewardedErrorReason ?: "Check Internet or Google Play Services"
                    Toast.makeText(
                        pendingActivity,
                        "Rewarded ad unavailable ($reason)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                pending.onAdClosed()
            }
        }
        pendingRewardedTimeoutRunnable = runnable
        try {
            mainHandler.postDelayed(runnable, PENDING_REWARDED_TIMEOUT_MS)
        } catch (_: Throwable) {}
    }

    fun cancelPendingRewardedAd() {
        clearPendingRewardedTimeout()
        val pending = pendingRewardedRequest
        pendingRewardedRequest = null
        _isRewardedLoadingFlow.value = false
        pending?.onAdClosed?.invoke()
    }

    fun isRewardedAdReady(): Boolean = rewardedAd != null

    /**
     * Clean wrapper to unlock Pro features (e.g., High PDF Compression) ONLY when
     * onUserEarnedReward is confirmed by the AdMob SDK.
     */
    fun showRewardedAd(
        activity: Activity?,
        onRewarded: () -> Unit
    ) {
        showRewardedAd(
            activity = activity,
            onUserEarnedReward = onRewarded,
            onAdClosed = {}
        )
    }

    fun showRewardedAd(
        activity: Activity?,
        onUserEarnedReward: () -> Unit,
        onAdClosed: () -> Unit
    ) {
        if (!isActivityValid(activity)) {
            onAdClosed()
            return
        }
        val validActivity = activity!!
        val appContext = validActivity.applicationContext

        try {
            val ad = rewardedAd
            if (isShowingFullScreenAd) {
                Toast.makeText(
                    validActivity,
                    "Another ad is currently open. Please try again in a moment.",
                    Toast.LENGTH_SHORT
                ).show()
                onAdClosed()
                return
            }

            if (ad == null) {
                // Start / await an on-demand load and auto-launch when ready (with 10s safety timeout)
                pendingRewardedRequest = PendingRewardedRequest(
                    activityRef = WeakReference(validActivity),
                    onUserEarnedReward = onUserEarnedReward,
                    onAdClosed = onAdClosed
                )
                _isRewardedLoadingFlow.value = true
                // Allow a fresh load attempt immediately when user explicitly requests Rewarded Ad
                rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                rewardedRetryRunnable = null
                schedulePendingRewardedTimeout()
                loadRewarded(appContext)
                return
            }

            clearPendingRewardedTimeout()
            rewardedAd = null
            pendingRewardedRequest = null
            _isRewardedLoadingFlow.value = false
            isShowingFullScreenAd = true

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    isShowingFullScreenAd = false
                    rewardedRetryCount = 0
                    loadRewarded(appContext)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    isShowingFullScreenAd = false
                    Log.w(TAG, "Rewarded failed to show: ${adError.message}")
                    loadRewarded(appContext)
                    onAdClosed()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad displayed")
                }
            }

            // Feature unlock callback triggers ONLY when SDK confirms reward completion
            ad.show(validActivity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onUserEarnedReward()
            }
        } catch (t: Throwable) {
            isShowingFullScreenAd = false
            _isRewardedLoadingFlow.value = false
            pendingRewardedRequest = null
            Log.e(TAG, "Error showing rewarded ad", t)
            onAdClosed()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Responsive Anchored Adaptive Banner anchored inside Scaffold bottomBar
 * without overlapping reading content. Always reserves a clearly styled framed ad container
 * while loading or retrying, and reveals the AdView cleanly once loaded.
 */
@Composable
fun AdaptiveBannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isSdkInitialized by AdManager.isInitializedFlow.collectAsState()

    val resolvedBannerId = remember(context) {
        val configuredId = try { context.getString(R.string.admob_banner) } catch (_: Exception) { "" }
        val productionId = configuredId.ifBlank { AdManager.BANNER_AD_UNIT_ID }
        AdManager.resolveAdUnitId(
            isDebug = BuildConfig.DEBUG,
            productionId = productionId,
            testId = AdManager.TEST_BANNER_AD_UNIT_ID
        )
    }

    var adViewRef by remember { mutableStateOf<AdView?>(null) }
    var bannerState by remember { mutableStateOf(BannerAdState.LOADING) }
    var retryCount by remember { mutableIntStateOf(0) }
    var lastErrorCode by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(bannerState, retryCount, isSdkInitialized) {
        if (isSdkInitialized &&
            bannerState == BannerAdState.RETRYING &&
            retryCount in 1..AdManager.MAX_LOAD_RETRIES
        ) {
            val delayMs = AdManager.retryDelayForAttempt(retryCount - 1)
            delay(delayMs)
            val currentAdView = adViewRef
            if (currentAdView != null && bannerState == BannerAdState.RETRYING) {
                bannerState = BannerAdState.LOADING
                try {
                    currentAdView.loadAd(AdRequest.Builder().build())
                } catch (t: Throwable) {
                    Log.e("AdManager", "Adaptive Banner retry error", t)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try { adViewRef?.resume() } catch (_: Exception) {}
                    if (bannerState == BannerAdState.FAILED) {
                        retryCount = 0
                        lastErrorCode = null
                        bannerState = BannerAdState.LOADING
                        try {
                            adViewRef?.loadAd(AdRequest.Builder().build())
                        } catch (_: Throwable) {}
                    }
                }
                Lifecycle.Event.ON_PAUSE -> try { adViewRef?.pause() } catch (_: Exception) {}
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                adViewRef?.destroy()
                adViewRef = null
            } catch (_: Exception) {}
        }
    }

    Surface(
        color = if (bannerState == BannerAdState.LOADED) WhiteMixSurfaceVariant else Color(0xFFE8EFFC),
        modifier = modifier
            .fillMaxWidth()
            .testTag("banner_ad_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = BrandPrimary.copy(alpha = 0.25f), thickness = 1.dp)
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val availableWidth = maxWidth
                val containerWidthDp = availableWidth.value.toInt().coerceIn(320, 728)
                val adaptiveAdSize = remember(containerWidthDp) {
                    try {
                        val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, containerWidthDp)
                        if (size.width > 0 && size.height > 0) size else AdSize.BANNER
                    } catch (_: Throwable) {
                        AdSize.BANNER
                    }
                }
                val reservedHeightDp = adaptiveAdSize.height.coerceAtLeast(56).dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(reservedHeightDp),
                    contentAlignment = Alignment.Center
                ) {
                    // Keep AdView View.VISIBLE so GMA WebView attaches & measures properly; control visual reveal via alpha
                    if (isSdkInitialized && availableWidth > 0.dp) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(reservedHeightDp)
                                .alpha(if (bannerState == BannerAdState.LOADED) 1f else 0.01f),
                            factory = { ctx ->
                                val adContext = ctx.findActivity() ?: ctx
                                AdView(adContext).apply {
                                    visibility = View.VISIBLE
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    adUnitId = resolvedBannerId
                                    setAdSize(adaptiveAdSize)
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                    adListener = object : AdListener() {
                                        override fun onAdLoaded() {
                                            bannerState = BannerAdState.LOADED
                                            retryCount = 0
                                            lastErrorCode = null
                                            visibility = View.VISIBLE
                                            Log.d("AdManager", "Adaptive Banner loaded ($resolvedBannerId)")
                                        }

                                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                            lastErrorCode = loadAdError.code
                                            Log.w(
                                                "AdManager",
                                                "Adaptive Banner failed ($adUnitId): ${loadAdError.message} (${loadAdError.code})"
                                            )
                                            if (retryCount < AdManager.MAX_LOAD_RETRIES) {
                                                retryCount += 1
                                                bannerState = BannerAdState.RETRYING
                                            } else {
                                                bannerState = BannerAdState.FAILED
                                            }
                                        }
                                    }
                                    post {
                                        try {
                                            loadAd(AdRequest.Builder().build())
                                        } catch (t: Throwable) {
                                            bannerState = BannerAdState.FAILED
                                            Log.e("AdManager", "Adaptive Banner load error", t)
                                        }
                                    }
                                    adViewRef = this
                                }
                            },
                            update = { adView ->
                                adView.visibility = View.VISIBLE
                            }
                        )
                    }

                    if (bannerState != BannerAdState.LOADED) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(enabled = bannerState == BannerAdState.FAILED) {
                                        retryCount = 0
                                        lastErrorCode = null
                                        bannerState = BannerAdState.LOADING
                                        try {
                                            AdManager.initialize(context)
                                            adViewRef?.loadAd(AdRequest.Builder().build())
                                        } catch (_: Throwable) {}
                                    }
                                    .testTag("banner_ad_placeholder"),
                                shape = RoundedCornerShape(8.dp),
                                color = WhiteMixSurface,
                                border = BorderStroke(1.dp, BrandPrimary.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AdBadgeAmberContainer,
                                        border = BorderStroke(0.8.dp, AdBadgeAmber.copy(alpha = 0.7f))
                                    ) {
                                        Text(
                                            text = "AD",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = AdBadgeAmber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (bannerState) {
                                            BannerAdState.RETRYING -> "Banner Ad Space • Retrying ($retryCount/${AdManager.MAX_LOAD_RETRIES})..."
                                            BannerAdState.FAILED -> {
                                                val errText = lastErrorCode?.let { AdManager.describeAdErrorCode(it) }
                                                    ?: "Unavailable"
                                                "Banner Ad • $errText • Tap to retry"
                                            }
                                            else -> "Banner Ad Space • Loading..."
                                        },
                                        fontSize = 12.sp,
                                        color = if (bannerState == BannerAdState.FAILED) BrandPrimary else TextSecondary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = WhiteMixBorder, thickness = 0.8.dp)
        }
    }
}
