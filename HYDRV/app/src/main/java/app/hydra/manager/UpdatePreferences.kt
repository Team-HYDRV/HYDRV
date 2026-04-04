package app.hydra.manager

import android.content.Context

object UpdatePreferences {

    private const val PREFS_NAME = "update_prefs"
    private const val KEY_CHECK_ON_LAUNCH = "check_on_launch"
    private const val KEY_SHOW_MESSAGE_ON_LAUNCH = "show_message_on_launch"

    fun isCheckOnLaunchEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHECK_ON_LAUNCH, true)
    }

    fun setCheckOnLaunchEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CHECK_ON_LAUNCH, enabled)
            .apply()
    }

    fun isLaunchMessageEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_MESSAGE_ON_LAUNCH, true)
    }

    fun setLaunchMessageEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_MESSAGE_ON_LAUNCH, enabled)
            .apply()
    }
}
