package app.hydra.manager

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

object InstallAliasStore {

    private const val PREFS = "install_aliases"
    private const val KEY_APP_NAME = "app_name_aliases"
    private const val KEY_PACKAGE = "package_aliases"

    fun saveAlias(
        context: Context,
        appName: String,
        backendPackage: String,
        actualPackage: String
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_APP_NAME, putAlias(prefs.getString(KEY_APP_NAME, "{}"), appName, actualPackage))
            putString(KEY_PACKAGE, putAlias(prefs.getString(KEY_PACKAGE, "{}"), backendPackage, actualPackage))
        }
    }

    fun resolveForAppName(context: Context, appName: String): String? {
        return readAlias(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APP_NAME, "{}"),
            appName
        )
    }

    fun resolveForPackage(context: Context, backendPackage: String): String? {
        return readAlias(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PACKAGE, "{}"),
            backendPackage
        )
    }

    fun findAppNameForActualPackage(context: Context, actualPackage: String): String? {
        return findKeyForValue(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APP_NAME, "{}"),
            actualPackage
        )
    }

    private fun putAlias(rawJson: String?, key: String, value: String): String {
        if (key.isBlank() || value.isBlank()) return rawJson.orEmpty().ifBlank { "{}" }
        val json = runCatching { JSONObject(rawJson.orEmpty().ifBlank { "{}" }) }
            .getOrDefault(JSONObject())
        json.put(key.trim(), value.trim())
        return json.toString()
    }

    private fun readAlias(rawJson: String?, key: String): String? {
        if (key.isBlank()) return null
        val json = runCatching { JSONObject(rawJson.orEmpty().ifBlank { "{}" }) }.getOrNull() ?: return null
        return json.optString(key.trim()).ifBlank { null }
    }

    private fun findKeyForValue(rawJson: String?, value: String): String? {
        if (value.isBlank()) return null
        val json = runCatching { JSONObject(rawJson.orEmpty().ifBlank { "{}" }) }.getOrNull() ?: return null
        val target = value.trim()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (json.optString(key).equals(target, ignoreCase = true)) {
                return key.ifBlank { null }
            }
        }
        return null
    }
}
