package app.hydra.manager

import android.content.Context

object DownloadNetworkPreferences {

    const val PREFS_NAME = "download_network_preferences"
    const val KEY_NETWORK_MODE = "network_mode"

    const val WIFI_ONLY = "wifi_only"
    const val WIFI_OR_MOBILE = "wifi_or_mobile"

    fun getMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NETWORK_MODE, WIFI_OR_MOBILE) ?: WIFI_OR_MOBILE
    }

    fun setMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NETWORK_MODE, sanitizeMode(mode))
            .apply()
    }

    fun label(context: Context, mode: String): String {
        return when (sanitizeMode(mode)) {
            WIFI_ONLY -> context.getString(R.string.option_wifi_only)
            else -> context.getString(R.string.option_wifi_or_mobile)
        }
    }

    private fun sanitizeMode(mode: String): String {
        return when (mode) {
            WIFI_ONLY -> WIFI_ONLY
            WIFI_OR_MOBILE -> WIFI_OR_MOBILE
            else -> WIFI_OR_MOBILE
        }
    }
}
