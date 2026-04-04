package app.hydra.manager

import android.content.Context

object NotificationPreferences {

    private const val PREFS_NAME = "notification_prefs"
    private const val KEY_UPDATES_ENABLED = "updates_enabled"

    fun areUpdateNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_UPDATES_ENABLED, true)
    }

    fun setUpdateNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_UPDATES_ENABLED, enabled)
            .apply()
    }
}
