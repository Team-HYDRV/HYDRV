package app.hydra.manager

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object BackendPreferences {
    private const val PREFS_NAME = "backend_prefs"
    private const val KEY_CATALOG_URL = "catalog_url"
    private const val KEY_CUSTOM_BACKENDS = "custom_backends"

    private val gson = Gson()
    private val customBackendListType = object : TypeToken<List<BackendSource>>() {}.type

    fun getCatalogUrl(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CATALOG_URL, "")
            .orEmpty()
            .trim()

        return if (saved.isBlank()) RuntimeConfig.defaultCatalogUrl else saved
    }

    fun getCatalogUrlCandidates(context: Context): List<String> {
        val saved = getCustomCatalogUrl(context)
        return if (saved.isBlank()) RuntimeConfig.defaultCatalogUrls else listOf(saved)
    }

    fun getCustomCatalogUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CATALOG_URL, "")
            .orEmpty()
            .trim()
    }

    fun getCustomBackendSources(context: Context): List<BackendSource> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CUSTOM_BACKENDS, "").orEmpty().trim()
        if (stored.isBlank()) return emptyList()

        return runCatching {
            gson.fromJson<List<BackendSource>>(stored, customBackendListType)
                .orEmpty()
                .mapNotNull { it.normalizedOrNull() }
        }.getOrDefault(emptyList())
    }

    fun setCatalogUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CATALOG_URL, sanitize(url)) }
    }

    fun setCustomBackendSources(context: Context, sources: List<BackendSource>) {
        val sanitized = sources
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.url.lowercase() }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_CUSTOM_BACKENDS, gson.toJson(sanitized))
            }
    }

    fun addCustomBackendSource(context: Context, source: BackendSource) {
        setCustomBackendSources(
            context,
            getCustomBackendSources(context) + source
        )
    }

    fun updateCustomBackendSource(context: Context, index: Int, source: BackendSource) {
        val current = getCustomBackendSources(context).toMutableList()
        if (index !in current.indices) return
        current[index] = source
        setCustomBackendSources(context, current)
    }

    fun removeCustomBackendSource(context: Context, index: Int) {
        val current = getCustomBackendSources(context).toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        setCustomBackendSources(context, current)
    }

    fun isUsingDefault(context: Context): Boolean {
        return getCustomCatalogUrl(context).isBlank() && getCustomBackendSources(context).isEmpty()
    }

    private fun sanitize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("https://")) trimmed else ""
    }

    private fun BackendSource.normalizedOrNull(): BackendSource? {
        val cleanName = name.trim().ifBlank {
            if (url.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true)) {
                "HYDRV Backend"
            } else {
                "Custom backend"
            }
        }
        val cleanUrl = sanitize(url)
        if (cleanUrl.isBlank()) return null
        return copy(
            name = cleanName,
            url = cleanUrl,
            enabled = enabled
        )
    }
}
