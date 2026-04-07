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
        return getCatalogSources(context).firstOrNull()?.url ?: RuntimeConfig.defaultCatalogUrl
    }

    fun getCatalogUrlCandidates(context: Context): List<String> {
        return getCatalogSources(context).map { it.url }.distinct()
    }

    fun getCatalogSources(context: Context): List<BackendSource> {
        val sources = buildList {
            add(defaultSource())
            addAll(getCustomBackendSources(context))
        }
        return sources
            .map { it.normalized() }
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url.lowercase() }
    }

    fun getCustomBackendSources(context: Context): List<BackendSource> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CUSTOM_BACKENDS, "").orEmpty().trim()
        if (stored.isNotBlank()) {
            return runCatching {
                gson.fromJson<List<BackendSource>>(stored, customBackendListType)
                    .orEmpty()
                    .mapNotNull { it.normalizedOrNull() }
            }.getOrDefault(emptyList())
        }

        val legacyUrl = prefs.getString(KEY_CATALOG_URL, "").orEmpty().trim()
        if (legacyUrl.isBlank()) return emptyList()
        return listOf(
            BackendSource(
                name = "Custom backend",
                url = legacyUrl,
                enabled = true
            )
        )
    }

    fun setCustomBackendSources(context: Context, sources: List<BackendSource>) {
        val sanitized = sources
            .mapNotNull { it.normalizedOrNull() }
            .filter { !it.url.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true) }
            .distinctBy { it.url.lowercase() }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_CUSTOM_BACKENDS, gson.toJson(sanitized))
                putString(KEY_CATALOG_URL, "")
            }
    }

    fun setCatalogUrl(context: Context, url: String) {
        val sanitized = sanitize(url)
        setCustomBackendSources(
            context,
            if (sanitized.isBlank()) emptyList() else listOf(BackendSource("Custom backend", sanitized))
        )
    }

    fun isUsingDefault(context: Context): Boolean {
        return getCustomBackendSources(context).isEmpty()
    }

    fun defaultSource(): BackendSource {
        return BackendSource(
            name = "HYDRV Backend",
            url = RuntimeConfig.defaultCatalogUrl,
            enabled = true
        )
    }

    private fun sanitize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("https://")) trimmed else ""
    }

    private fun BackendSource.normalized(): BackendSource {
        return normalizedOrNull() ?: defaultSource()
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
