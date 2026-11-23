package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.example.ui.theme.AdBadgeAmber
import com.example.ui.theme.AdBadgeAmberContainer
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WhiteMixSurface
import com.example.ui.theme.WhiteMixSurfaceVariant
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

enum class BannerAdState {
    LOADING,
    LOADED,
    RETRYING,
    FAILED
}

private data class PendingRewardedRequest(
    val activityRef: WeakReference<Activity>,
    val onRewarded: () -> Unit,
    val onUnavailable: () -> Unit
)

/**
 * Centralized Start.io (StartApp In-App SDK) monetization singleton managing:
 * - Single-call SDK initialization with App ID 210114038 and return ads disabled
 * - Debug-only test ads (`BuildConfig.DEBUG`) and automatic live production ads in release
 * - Action-triggered Interstitial preloading, bounded retries, and strict 60s frequency capping
 * - On-demand Rewarded Video playback unlocking Pro features strictly inside VideoListener
 */
object StartIoAdManager {
    private const val TAG = "StartIoAdManager"

    // Exact Start.io App ID
    const val START_IO_APP_ID = "210114038"

    // Strict 60-second anti-spam cooldown between action-triggered Interstitial displays
    const val INTERSTITIAL_COOLDOWN_MS = 60_000L

    // Bounded exponential retry schedule
    const val MAX_LOAD_RETRIES = 3
    private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 30_000L)
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

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var interstitialAd: StartAppAd? = null
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
    private var rewardedAd: StartAppAd? = null
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

    fun shouldEnableTestAds(isDebug: Boolean): Boolean = isDebug

    fun retryDelayForAttempt(attemptIndex: Int): Long {
        if (RETRY_DELAYS_MS.isEmpty()) return 5_000L
        return RETRY_DELAYS_MS[attemptIndex.coerceIn(0, RETRY_DELAYS_MS.lastIndex)]
    }

    private fun rememberActivityIfAvailable(context: Context?) {
        val activity = context?.findActivity()
        if (isActivityValid(activity)) {
            currentActivityRef = WeakReference(activity)
        }
    }

    private fun resolveAdContext(context: Context): Context {
        rememberActivityIfAvailable(context)
        val activity = currentActivityRef?.get()
        if (isActivityValid(activity)) {
            return activity!!
        }
        return context.applicationContext ?: context
    }

    fun initialize(context: Context) {
        rememberActivityIfAvailable(context)
        if (!initStarted.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context

        try {
            StartAppSDK.initParams(appContext, START_IO_APP_ID)
                .setReturnAdsEnabled(false)
                .init()
            StartAppSDK.setTestAdsEnabled(shouldEnableTestAds(BuildConfig.DEBUG))
            Log.d(
                TAG,
                "Start.io SDK initialized (appId=$START_IO_APP_ID, testAds=${shouldEnableTestAds(BuildConfig.DEBUG)})"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Start.io SDK, continuing gracefully", t)
        }

        _isInitializedFlow.value = true
        loadInterstitial(context)
        loadRewarded(context)
    }

    private fun isActivityValid(activity: Activity?): Boolean {
        return activity != null && !activity.isFinishing && !activity.isDestroyed
    }

    /**
     * Timestamp-based 60-second anti-spam check for action-triggered Interstitial displays.
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
     * Exposes state reset strictly for unit testing.
     */
    internal fun resetForTesting() {
        try {
            interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingRewardedTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Throwable) {
            // Looper may not be prepared in plain JVM unit tests
        }
        interstitialRetryRunnable = null
        rewardedRetryRunnable = null
        pendingRewardedTimeoutRunnable = null
        currentActivityRef = null
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
        _isInterstitialReadyFlow.value = false
        _isRewardedLoadingFlow.value = false
    }

    internal fun setLastInterstitialShownTimestampForTesting(timestampMs: Long) {
        lastInterstitialShownTimestampMs = timestampMs
    }

    fun onAppResumed(context: Context) {
        rememberActivityIfAvailable(context)
        val adContext = resolveAdContext(context)
        if (interstitialAd == null && !isInterstitialLoading) {
            if (interstitialRetryCount >= MAX_LOAD_RETRIES) {
                interstitialRetryCount = 0
            }
            loadInterstitial(adContext)
        }
        if (rewardedAd == null && !isRewardedLoading) {
            if (rewardedRetryCount >= MAX_LOAD_RETRIES) {
                rewardedRetryCount = 0
            }
            loadRewarded(adContext)
        }
    }

    private fun scheduleInterstitialRetry(context: Context) {
        val currentAttempt = interstitialRetryCount
        if (currentAttempt >= MAX_LOAD_RETRIES) return
        val delayMs = retryDelayForAttempt(currentAttempt)
        interstitialRetryCount = currentAttempt + 1

        interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        val appContext = context.applicationContext ?: context
        val runnable = Runnable {
            interstitialRetryRunnable = null
            loadInterstitial(resolveAdContext(appContext))
        }
        interstitialRetryRunnable = runnable
        try {
            mainHandler.postDelayed(runnable, delayMs)
            Log.d(TAG, "Scheduled Start.io interstitial retry #$interstitialRetryCount in ${delayMs}ms")
        } catch (_: Throwable) {}
    }

    private fun scheduleRewardedRetry(context: Context) {
        val currentAttempt = rewardedRetryCount
        if (currentAttempt >= MAX_LOAD_RETRIES) return
        val delayMs = retryDelayForAttempt(currentAttempt)
        rewardedRetryCount = currentAttempt + 1

        rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        val appContext = context.applicationContext ?: context
        val runnable = Runnable {
            rewardedRetryRunnable = null
            loadRewarded(resolveAdContext(appContext))
        }
        rewardedRetryRunnable = runnable
        try {
            mainHandler.postDelayed(runnable, delayMs)
            Log.d(TAG, "Scheduled Start.io rewarded retry #$rewardedRetryCount in ${delayMs}ms")
        } catch (_: Throwable) {}
    }

    fun loadInterstitial(context: Context) {
        val adContext = resolveAdContext(context)
        val existing = interstitialAd
        if ((existing != null && _isInterstitialReadyFlow.value) || isInterstitialLoading) return

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
            val ad = StartAppAd(adContext)
            ad.loadAd(object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    interstitialRetryCount = 0
                    lastInterstitialFailureMs = 0L
                    _isInterstitialReadyFlow.value = true
                    Log.d(TAG, "Start.io interstitial preloaded")
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    lastInterstitialFailureMs = System.currentTimeMillis()
                    _isInterstitialReadyFlow.value = false
                    Log.w(TAG, "Start.io interstitial failed to load: ${failedAd?.errorMessage ?: "Unknown"}")
                    scheduleInterstitialRetry(adContext)
                }
            })
        } catch (t: Throwable) {
            interstitialAd = null
            isInterstitialLoading = false
            lastInterstitialFailureMs = System.currentTimeMillis()
            _isInterstitialReadyFlow.value = false
            Log.e(TAG, "Exception while loading Start.io interstitial", t)
        }
    }

    /**
     * Displays a preloaded Start.io Interstitial ONLY if 60 seconds have elapsed since the last
     * shown ad, an ad is ready, and the Activity is active. Otherwise skips immediately without blocking UX.
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
        rememberActivityIfAvailable(validActivity)

        try {
            val now = System.currentTimeMillis()
            if (!canShowInterstitial(now)) {
                if (interstitialAd == null) {
                    loadInterstitial(validActivity)
                }
                completeOnce()
                return
            }

            val ad = interstitialAd
            val isReady = try {
                ad != null && _isInterstitialReadyFlow.value && ad.isReady
            } catch (_: Throwable) {
                false
            }
            if (!isReady || isShowingFullScreenAd) {
                if (ad == null || !isReady) {
                    interstitialAd = null
                    _isInterstitialReadyFlow.value = false
                    loadInterstitial(validActivity)
                }
                completeOnce()
                return
            }

            interstitialAd = null
            _isInterstitialReadyFlow.value = false
            isShowingFullScreenAd = true

            val shown = ad!!.showAd(object : AdDisplayListener {
                override fun adDisplayed(displayedAd: Ad) {
                    lastInterstitialShownTimestampMs = System.currentTimeMillis()
                    Log.d(TAG, "Start.io interstitial displayed")
                }

                override fun adHidden(hiddenAd: Ad) {
                    isShowingFullScreenAd = false
                    lastInterstitialShownTimestampMs = System.currentTimeMillis()
                    interstitialRetryCount = 0
                    loadInterstitial(validActivity)
                    completeOnce()
                }

                override fun adNotDisplayed(notDisplayedAd: Ad) {
                    isShowingFullScreenAd = false
                    Log.w(TAG, "Start.io interstitial not displayed: ${notDisplayedAd.errorMessage}")
                    loadInterstitial(validActivity)
                    completeOnce()
                }

                override fun adClicked(clickedAd: Ad) {
                    Log.d(TAG, "Start.io interstitial clicked")
                }
            })

            if (!shown) {
                isShowingFullScreenAd = false
                loadInterstitial(validActivity)
                completeOnce()
            }
        } catch (t: Throwable) {
            isShowingFullScreenAd = false
            Log.e(TAG, "Error showing Start.io interstitial", t)
            completeOnce()
        }
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
        val adContext = resolveAdContext(context)
        if ((rewardedAd != null && rewardedAd?.isReady == true) || isRewardedLoading) return

        val now = System.currentTimeMillis()
        if (rewardedRetryCount >= MAX_LOAD_RETRIES &&
            rewardedRetryRunnable == null &&
            now - lastRewardedFailureMs >= 30_000L
        ) {
            rewardedRetryCount = 0
        }
        isRewardedLoading = true

        try {
            val ad = StartAppAd(adContext)
            ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(receivedAd: Ad) {
                    clearPendingRewardedTimeout()
                    lastRewardedFailureMs = 0L
                    rewardedAd = ad
                    isRewardedLoading = false
                    rewardedRetryCount = 0
                    Log.d(TAG, "Start.io rewarded video preloaded")

                    val pending = pendingRewardedRequest
                    if (pending != null) {
                        pendingRewardedRequest = null
                        _isRewardedLoadingFlow.value = false
                        val pendingActivity = pending.activityRef.get()
                        if (isActivityValid(pendingActivity)) {
                            showRewardedAd(
                                activity = pendingActivity,
                                onRewarded = pending.onRewarded,
                                onUnavailable = pending.onUnavailable
                            )
                        } else {
                            pending.onUnavailable()
                        }
                    }
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    clearPendingRewardedTimeout()
                    rewardedAd = null
                    isRewardedLoading = false
                    lastRewardedFailureMs = System.currentTimeMillis()
                    Log.w(TAG, "Start.io rewarded video failed to load: ${failedAd?.errorMessage ?: "Unknown"}")

                    val pending = pendingRewardedRequest
                    if (pending != null) {
                        pendingRewardedRequest = null
                        _isRewardedLoadingFlow.value = false
                        pending.onUnavailable()
                    }

                    scheduleRewardedRetry(adContext)
                }
            })
        } catch (t: Throwable) {
            clearPendingRewardedTimeout()
            rewardedAd = null
            isRewardedLoading = false
            lastRewardedFailureMs = System.currentTimeMillis()
            pendingRewardedRequest?.let { pending ->
                pendingRewardedRequest = null
                _isRewardedLoadingFlow.value = false
                pending.onUnavailable()
            }
            Log.e(TAG, "Exception while loading Start.io rewarded video", t)
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
                pending.onUnavailable()
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
        pending?.onUnavailable?.invoke()
    }

    fun isRewardedAdReady(): Boolean = try {
        rewardedAd?.isReady == true
    } catch (_: Throwable) {
        false
    }

    /**
     * Plays a Start.io Rewarded Video (REWARDED_VIDEO). Supports on-demand preloading when not yet
     * cached, and grants the reward strictly inside VideoListener.onVideoCompleted().
     */
    fun showRewardedAd(
        activity: Activity?,
        onRewarded: () -> Unit,
        onUnavailable: () -> Unit = {}
    ) {
        if (!isActivityValid(activity)) {
            onUnavailable()
            return
        }
        val validActivity = activity!!
        rememberActivityIfAvailable(validActivity)

        try {
            if (isShowingFullScreenAd) {
                onUnavailable()
                return
            }

            val ad = rewardedAd
            val isReady = try {
                ad != null && ad.isReady
            } catch (_: Throwable) {
                false
            }

            if (ad == null || !isReady) {
                rewardedAd = null
                pendingRewardedRequest = PendingRewardedRequest(
                    activityRef = WeakReference(validActivity),
                    onRewarded = onRewarded,
                    onUnavailable = onUnavailable
                )
                _isRewardedLoadingFlow.value = true
                rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                rewardedRetryRunnable = null
                schedulePendingRewardedTimeout()
                loadRewarded(validActivity)
                return
            }

            clearPendingRewardedTimeout()
            rewardedAd = null
            pendingRewardedRequest = null
            _isRewardedLoadingFlow.value = false
            isShowingFullScreenAd = true

            var rewardGranted = false
            var unavailableNotified = false
            fun notifyUnavailableOnce() {
                if (!rewardGranted && !unavailableNotified) {
                    unavailableNotified = true
                    onUnavailable()
                }
            }

            // Grant Pro/Tool unlock strictly upon full video completion
            ad.setVideoListener {
                rewardGranted = true
                Log.d(TAG, "Start.io rewarded video completed; granting reward")
                onRewarded()
            }

            val shown = ad.showAd(object : AdDisplayListener {
                override fun adDisplayed(displayedAd: Ad) {
                    Log.d(TAG, "Start.io rewarded video displayed")
                }

                override fun adHidden(hiddenAd: Ad) {
                    isShowingFullScreenAd = false
                    rewardedRetryCount = 0
                    loadRewarded(validActivity)
                    if (!rewardGranted) {
                        notifyUnavailableOnce()
                    }
                }

                override fun adNotDisplayed(notDisplayedAd: Ad) {
                    isShowingFullScreenAd = false
                    Log.w(TAG, "Start.io rewarded video failed to display: ${notDisplayedAd.errorMessage}")
                    loadRewarded(validActivity)
                    notifyUnavailableOnce()
                }

                override fun adClicked(clickedAd: Ad) {
                    Log.d(TAG, "Start.io rewarded video clicked")
                }
            })

            if (!shown) {
                isShowingFullScreenAd = false
                loadRewarded(validActivity)
                notifyUnavailableOnce()
            }
        } catch (t: Throwable) {
            isShowingFullScreenAd = false
            _isRewardedLoadingFlow.value = false
            pendingRewardedRequest = null
            Log.e(TAG, "Error showing Start.io rewarded video", t)
            onUnavailable()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Anchored bottom Start.io Banner container with a reserved minHeight = 50.dp slot,
 * subtle loading placeholder, bounded failure retry, and optional navigationBarsPadding.
 */
@Composable
fun StartIoBannerAdView(
    modifier: Modifier = Modifier,
    applyNavigationBarsPadding: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isSdkInitialized by StartIoAdManager.isInitializedFlow.collectAsState()

    var bannerRef by remember { mutableStateOf<Banner?>(null) }
    var bannerState by remember { mutableStateOf(BannerAdState.LOADING) }
    var retryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(bannerState, retryCount, isSdkInitialized) {
        if (isSdkInitialized &&
            bannerState == BannerAdState.RETRYING &&
            retryCount in 1..StartIoAdManager.MAX_LOAD_RETRIES
        ) {
            val delayMs = StartIoAdManager.retryDelayForAttempt(retryCount - 1)
            delay(delayMs)
            val currentBanner = bannerRef
            if (currentBanner != null && bannerState == BannerAdState.RETRYING) {
                bannerState = BannerAdState.LOADING
                try {
                    currentBanner.loadAd()
                } catch (t: Throwable) {
                    bannerState = BannerAdState.FAILED
                    Log.e("StartIoAdManager", "Start.io banner retry error", t)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    try { bannerRef?.showBanner() } catch (_: Throwable) {}
                    if (bannerState == BannerAdState.FAILED) {
                        retryCount = 0
                        bannerState = BannerAdState.LOADING
                        try {
                            bannerRef?.loadAd()
                        } catch (_: Throwable) {}
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    try { bannerRef?.hideBanner() } catch (_: Throwable) {}
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                bannerRef?.hideBanner()
                bannerRef = null
            } catch (_: Throwable) {}
        }
    }

    val containerModifier = if (applyNavigationBarsPadding) {
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    } else {
        modifier.fillMaxWidth()
    }

    Surface(
        color = if (bannerState == BannerAdState.LOADED) WhiteMixSurfaceVariant else Color(0xFFE8EFFC),
        modifier = containerModifier.testTag("banner_ad_container")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = BrandPrimary.copy(alpha = 0.25f), thickness = 1.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 50.dp)
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSdkInitialized) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .height(56.dp)
                            .alpha(if (bannerState == BannerAdState.LOADED) 1f else 0.01f),
                        factory = { ctx ->
                            val adContext = ctx.findActivity() ?: ctx
                            Banner(adContext, object : BannerListener {
                                override fun onReceiveAd(view: View) {
                                    bannerState = BannerAdState.LOADED
                                    retryCount = 0
                                    Log.d("StartIoAdManager", "Start.io banner loaded")
                                }

                                override fun onFailedToReceiveAd(view: View?) {
                                    Log.w("StartIoAdManager", "Start.io banner failed to load")
                                    // Never call loadAd() synchronously here; bounded retry runs in LaunchedEffect
                                    if (retryCount < StartIoAdManager.MAX_LOAD_RETRIES) {
                                        retryCount += 1
                                        bannerState = BannerAdState.RETRYING
                                    } else {
                                        bannerState = BannerAdState.FAILED
                                    }
                                }

                                override fun onImpression(view: View) {}

                                override fun onClick(view: View) {}
                            }).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                bannerRef = this
                            }
                        },
                        update = { banner ->
                            if (bannerState == BannerAdState.LOADED) {
                                try { banner.showBanner() } catch (_: Throwable) {}
                            }
                        }
                    )
                }

                if (bannerState != BannerAdState.LOADED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(enabled = bannerState == BannerAdState.FAILED) {
                                    retryCount = 0
                                    bannerState = BannerAdState.LOADING
                                    try {
                                        StartIoAdManager.initialize(context)
                                        bannerRef?.loadAd()
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
                                        BannerAdState.RETRYING -> "Banner Ad Space • Retrying ($retryCount/${StartIoAdManager.MAX_LOAD_RETRIES})..."
                                        BannerAdState.FAILED -> "Banner Ad • Unavailable • Tap to retry"
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
    }
}
