package app.hydra.manager

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest

object AppUpdateState {

    private const val PREFS_NAME = "app_update_state"
    private const val KEY_LAST_SEEN_HASH = "last_seen_hash"
    private const val KEY_LAST_NOTIFIED_HASH = "last_notified_hash"
    private const val KEY_LAST_CHECKED_AT = "last_checked_at"
    private const val KEY_BACKEND_LAST_SEEN_HASH_PREFIX = "backend_last_seen_hash_"
    private const val KEY_BACKEND_LAST_NOTIFIED_HASH_PREFIX = "backend_last_notified_hash_"

    fun getLastSeenHash(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_SEEN_HASH, 0)
    }

    fun setLastSeenHash(context: Context, hash: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_LAST_SEEN_HASH, hash)
            }
    }

    fun getLastNotifiedHash(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_NOTIFIED_HASH, 0)
    }

    fun setLastNotifiedHash(context: Context, hash: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_LAST_NOTIFIED_HASH, hash)
            }
    }

    fun getLastCheckedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECKED_AT, 0L)
    }

    fun setLastCheckedAt(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_LAST_CHECKED_AT, timestamp)
            }
    }

    fun getBackendLastSeenHash(context: Context, backendUrl: String): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(backendKey(KEY_BACKEND_LAST_SEEN_HASH_PREFIX, backendUrl), 0)
    }

    fun setBackendLastSeenHash(context: Context, backendUrl: String, hash: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putInt(backendKey(KEY_BACKEND_LAST_SEEN_HASH_PREFIX, backendUrl), hash)
            }
    }

    fun getBackendLastNotifiedHash(context: Context, backendUrl: String): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(backendKey(KEY_BACKEND_LAST_NOTIFIED_HASH_PREFIX, backendUrl), 0)
    }

    fun setBackendLastNotifiedHash(context: Context, backendUrl: String, hash: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putInt(backendKey(KEY_BACKEND_LAST_NOTIFIED_HASH_PREFIX, backendUrl), hash)
            }
    }

    private fun backendKey(prefix: String, backendUrl: String): String {
        return prefix + sha256Hex(backendUrl.trim()).take(24)
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append(byte.toUByte().toString(16).padStart(2, '0'))
            }
        }
    }
}
