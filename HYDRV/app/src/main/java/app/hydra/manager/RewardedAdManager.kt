package app.hydra.manager

import android.app.Activity
import android.os.Build
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object RewardedAdManager {
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var initialized = false

    fun initialize(context: Context) {
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
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    fun showThen(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            preload(activity.applicationContext)
            onAdUnavailable()
            return
        }

        rewardedAd = null
        var rewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity.applicationContext)
                if (!rewardEarned) onAdUnavailable()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                preload(activity.applicationContext)
                onAdUnavailable()
            }
        }

        ad.show(activity) { _: RewardItem ->
            rewardEarned = true
            onRewardEarned()
        }
    }

    fun isAdLoaded(): Boolean = rewardedAd != null

    fun runtimeAvailabilityReason(): String {
        return if (shouldUseTestAdsForRuntime()) {
            when {
                isLoading -> "Using test ads (loading)"
                rewardedAd != null -> "Using test ads (loaded)"
                !initialized -> "Using test ads (not initialized)"
                else -> "Using test ads (no fill yet)"
            }
        } else if (isLoading) {
            "Loading"
        } else if (rewardedAd != null) {
            "Loaded"
        } else if (!initialized) {
            "Not initialized"
        } else {
            "No fill yet"
        }
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
}
