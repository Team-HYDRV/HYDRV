package app.hydra.manager

import android.content.Context
import androidx.core.content.edit

object AdsPreferences {

    private const val PREFS_NAME = "ads_prefs"
    private const val KEY_REWARDED_ADS_ENABLED = "rewarded_ads_enabled"

    fun areRewardedAdsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REWARDED_ADS_ENABLED, true)
    }

    fun setRewardedAdsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_REWARDED_ADS_ENABLED, enabled) }
    }
}
