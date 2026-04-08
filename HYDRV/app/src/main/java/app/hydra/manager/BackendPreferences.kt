package app.hydra.manager

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object BackendPreferences {
    private const val PREFS_NAME = "backend_prefs"
    private const val KEY_CATALOG_URL = "catalog_url"
    private const val KEY_CUSTOM_BACKENDS = "custom_backends"
    private const val KEY_ACTIVE_BACKEND_URL = "active_backend_url"

    private val gson = Gson()
    private val customBackendListType = object : TypeToken<List<BackendSource>>() {}.type

    fun getCatalogUrl(context: Context): String {
        return getCatalogUrlCandidates(context).firstOrNull() ?: RuntimeConfig.defaultCatalogUrl
    }

    fun getMonitoredBackendSources(context: Context): List<BackendSource> {
        val defaultSource = BackendSource(
            name = context.getString(R.string.backend_default_label),
            url = RuntimeConfig.defaultCatalogUrl,
            enabled = true
        )
        val activeUrl = getActiveBackendUrlValue(context).trim()
        val customSources = getCustomBackendSources(context)
        val activeCustom = customSources.firstOrNull {
            it.url.equals(activeUrl, ignoreCase = true)
        }

        return buildList {
            if (activeCustom != null) {
                add(activeCustom)
            }

            add(defaultSource)

            customSources.asSequence()
                .filter { it.enabled || it.url.equals(activeUrl, ignoreCase = true) }
                .filterNot { it.url.equals(defaultSource.url, ignoreCase = true) }
                .filterNot { it.url.equals(activeUrl, ignoreCase = true) && activeCustom != null }
                .forEach { add(it) }
        }
            .distinctBy { it.url.lowercase() }
    }

    fun getCatalogUrlCandidates(context: Context): List<String> {
        val active = getActiveBackendUrl(context).trim()
        val sources = getCustomBackendSources(context)

        val ordered = buildList {
            if (active.isBlank() || active.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true)) {
                add(RuntimeConfig.defaultCatalogUrl)
            } else {
                add(active)
            }
            addAll(
                sources.asSequence()
                    .filterNot { active.isNotBlank() && it.url.equals(active, ignoreCase = true) }
                    .map { it.url }
                    .toList()
            )
            if (active.isNotBlank() && !active.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true)) {
                add(RuntimeConfig.defaultCatalogUrl)
            }
        }

        return ordered
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    fun getCustomCatalogUrl(context: Context): String {
        return getActiveCustomBackendSource(context)?.url.orEmpty()
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
        val sanitized = sanitize(url)
        if (sanitized.isBlank()) {
            setCustomBackendSources(context, emptyList())
            return
        }
        setCustomBackendSources(
            context,
            listOf(BackendSource("Custom backend", sanitized, true))
        )
    }

    fun setCustomBackendSources(context: Context, sources: List<BackendSource>) {
        val sanitized = sources
            .mapNotNull { it.normalizedOrNull() }
            .distinctBy { it.url.lowercase() }
        val activeUrl = getActiveBackendUrl(context)
        val nextActive = when {
            sanitized.isEmpty() -> ""
            activeUrl.isBlank() -> ""
            activeUrl.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true) -> RuntimeConfig.defaultCatalogUrl
            activeUrl.isNotBlank() && sanitized.any { it.url.equals(activeUrl, ignoreCase = true) } -> activeUrl
            else -> sanitized.firstOrNull()?.url.orEmpty()
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_CUSTOM_BACKENDS, gson.toJson(sanitized))
                putString(KEY_CATALOG_URL, nextActive)
                putString(KEY_ACTIVE_BACKEND_URL, nextActive)
            }
    }

    fun addCustomBackendSource(context: Context, source: BackendSource) {
        setCustomBackendSources(context, getCustomBackendSources(context) + source)
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
        val active = getActiveBackendUrl(context).trim()
        return active.isBlank() || active.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true)
    }

    fun setActiveBackendUrl(context: Context, url: String) {
        val cleaned = sanitize(url)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_ACTIVE_BACKEND_URL, cleaned) }
    }

    private fun sanitize(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("https://")) trimmed else ""
    }

    private fun getActiveBackendUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = prefs.getString(KEY_ACTIVE_BACKEND_URL, "").orEmpty().trim()
        if (active.isNotBlank()) return active

        val legacy = prefs.getString(KEY_CATALOG_URL, "").orEmpty().trim()
        return legacy
    }

    fun getActiveCustomBackendSource(context: Context): BackendSource? {
        val activeUrl = getActiveBackendUrl(context)
        if (activeUrl.isBlank()) return null
        return getCustomBackendSources(context)
            .firstOrNull { it.url.equals(activeUrl, ignoreCase = true) }
    }

    fun getActiveBackendUrlValue(context: Context): String {
        return getActiveBackendUrl(context)
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
