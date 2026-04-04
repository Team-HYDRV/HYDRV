package app.hydra.manager

import android.content.Context

object ReleaseUpdateState {

    private const val PREFS_NAME = "release_update_state"
    private const val KEY_LAST_SEEN_TAG = "last_seen_release_tag"
    private const val KEY_LAST_NOTIFIED_TAG = "last_notified_release_tag"
    private const val KEY_LAST_CHECKED_AT = "last_checked_at"

    fun getLastSeenTag(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SEEN_TAG, "")
            .orEmpty()
    }

    fun setLastSeenTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SEEN_TAG, tag.trim())
            .apply()
    }

    fun getLastNotifiedTag(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_NOTIFIED_TAG, "")
            .orEmpty()
    }

    fun setLastNotifiedTag(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_NOTIFIED_TAG, tag.trim())
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
