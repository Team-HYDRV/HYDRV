package app.hydra.manager

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import java.io.File

object DownloadStore {

    private const val FILE_NAME = "downloads_state.json"
    private const val LEGACY_PREFS_NAME = "downloads"
    private const val LEGACY_KEY_DATA = "data"

    fun write(context: Context, data: String) {
        val appContext = context.applicationContext
        val target = File(appContext.filesDir, FILE_NAME)
        val temp = File(appContext.filesDir, "$FILE_NAME.tmp")

        temp.writeText(data)
        if (target.exists() && !target.delete()) {
            temp.delete()
            return
        }
        if (!temp.renameTo(target)) {
            target.writeText(data)
            temp.delete()
        }
    }

    fun read(context: Context): String? {
        val appContext = context.applicationContext
        val target = File(appContext.filesDir, FILE_NAME)
        if (target.exists()) {
            return try {
                target.readText()
            } catch (_: Exception) {
                null
            }
        }

        val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LEGACY_KEY_DATA, null)
            ?: return null

        return try {
            JSONArray(legacy)
            write(appContext, legacy)
            appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .edit { remove(LEGACY_KEY_DATA) }
            legacy
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
        context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(LEGACY_KEY_DATA) }
    }
}
