package app.hydra.manager

import android.content.Context

object AppUpdateState {

    private const val PREFS_NAME = "app_update_state"
    private const val KEY_LAST_SEEN_HASH = "last_seen_hash"
    private const val KEY_LAST_NOTIFIED_HASH = "last_notified_hash"
    private const val KEY_LAST_CHECKED_AT = "last_checked_at"

    fun getLastSeenHash(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_SEEN_HASH, 0)
    }

    fun setLastSeenHash(context: Context, hash: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_SEEN_HASH, hash)
            .apply()
    }

    fun getLastNotifiedHash(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_NOTIFIED_HASH, 0)
    }

    fun setLastNotifiedHash(context: Context, hash: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LAST_NOTIFIED_HASH, hash)
            .apply()
    }

    fun getLastCheckedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECKED_AT, 0L)
    }

    fun setLastCheckedAt(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CHECKED_AT, timestamp)
            .apply()
    }
}
