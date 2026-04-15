package app.hydra.manager

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardedAdManager {
    private const val NO_ADS_AVAILABLE_REASON = "No ads available yet"
    private const val PENDING_SHOW_TIMEOUT_MS = 3000L
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var initialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextPendingToken = 0L
    private var pendingShowRequest: PendingShowRequest? = null

    private data class PendingShowRequest(
        val token: Long,
        val activity: Activity,
        val onRewardEarned: () -> Unit,
        val onAdUnavailable: () -> Unit
    )

    fun initialize(context: Context) {
        if (!AdsPreferences.areRewardedAdsEnabled(context)) {
            clear()
            return
        }
        if (initialized) {
            preload(context)
            return
        }

        MobileAds.initialize(context) {
            initialized = true
            preload(context)
        }
    }

    fun preload(context: Context) {
        if (!AdsPreferences.areRewardedAdsEnabled(context)) {
            clear()
            return
        }
        if (isLoading || rewardedAd != null) return

        isLoading = true
        RewardedAd.load(
            context,
            activeAdUnitId(),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    consumePendingShow()?.let { request ->
                        mainHandler.post {
                            if (isActivityUnavailable(request.activity)) return@post
                            showThen(
                                activity = request.activity,
                                onRewardEarned = request.onRewardEarned,
                                onAdUnavailable = request.onAdUnavailable
                            )
                        }
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    consumePendingShow()?.let { request ->
                        mainHandler.post {
                            if (isActivityUnavailable(request.activity)) return@post
                            request.onAdUnavailable()
                        }
                    }
                }
            }
        )
    }

    fun showThen(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdUnavailable: () -> Unit
    ) {
        if (!AdsPreferences.areRewardedAdsEnabled(activity)) {
            onAdUnavailable()
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            queuePendingShow(activity, onRewardEarned, onAdUnavailable)
            if (!initialized) {
                initialize(activity.applicationContext)
            } else {
                preload(activity.applicationContext)
            }
            return
        }

        rewardedAd = null
        var rewardEarned = false
        var completionHandled = false

        fun finishWithReward() {
            if (completionHandled) return
            completionHandled = true
            onRewardEarned()
        }

        fun finishUnavailable() {
            if (completionHandled) return
            completionHandled = true
            onAdUnavailable()
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity.applicationContext)
                if (rewardEarned) {
                    finishWithReward()
                } else {
                    finishUnavailable()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                preload(activity.applicationContext)
                finishUnavailable()
            }
        }

        ad.show(activity) { _: RewardItem ->
            rewardEarned = true
        }
    }

    fun isAdLoaded(): Boolean = rewardedAd != null

    fun runtimeAvailabilityReason(): String {
        return if (shouldUseTestAdsForRuntime()) {
            when {
                isLoading -> "Using test ads (loading)"
                rewardedAd != null -> "Using test ads (loaded)"
                !initialized -> "Using test ads (not initialized)"
                else -> "Using test ads ($NO_ADS_AVAILABLE_REASON)"
            }
        } else if (isLoading) {
            "Loading"
        } else if (rewardedAd != null) {
            "Loaded"
        } else if (!initialized) {
            "Not initialized"
        } else {
            NO_ADS_AVAILABLE_REASON
        }
    }

    fun shouldBypassRewardGate(): Boolean {
        return runtimeAvailabilityReason().contains(NO_ADS_AVAILABLE_REASON, ignoreCase = true)
    }

    fun clear() {
        rewardedAd = null
        isLoading = false
        initialized = false
        consumePendingShow()
    }

    private fun activeAdUnitId(): String {
        return if (shouldUseTestAdsForRuntime()) {
            RuntimeConfig.rewardedTestAdUnitId
        } else {
            RuntimeConfig.rewardedAdUnitId
        }
    }

    private fun shouldUseTestAdsForRuntime(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val abis = Build.SUPPORTED_ABIS.joinToString(",").lowercase()

        return fingerprint.contains("generic")
            || fingerprint.contains("emulator")
            || model.contains("sdk")
            || model.contains("emulator")
            || manufacturer.contains("genymotion")
            || product.contains("sdk")
            || product.contains("emulator")
            || brand.startsWith("generic")
            || device.startsWith("generic")
            || abis.contains("x86")
    }

    private fun queuePendingShow(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdUnavailable: () -> Unit
    ) {
        val request = PendingShowRequest(
            token = ++nextPendingToken,
            activity = activity,
            onRewardEarned = onRewardEarned,
            onAdUnavailable = onAdUnavailable
        )
        pendingShowRequest = request
        mainHandler.postDelayed({
            val pending = pendingShowRequest
            if (pending?.token != request.token) return@postDelayed
            pendingShowRequest = null
            if (isActivityUnavailable(pending.activity)) return@postDelayed
            pending.onAdUnavailable()
        }, PENDING_SHOW_TIMEOUT_MS)
    }

    private fun consumePendingShow(): PendingShowRequest? {
        val request = pendingShowRequest ?: return null
        pendingShowRequest = null
        return request
    }

    private fun isActivityUnavailable(activity: Activity): Boolean {
        return activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
    }
}
