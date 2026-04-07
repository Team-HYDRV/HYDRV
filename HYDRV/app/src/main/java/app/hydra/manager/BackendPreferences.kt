package app.hydra.manager

import android.content.Context
import androidx.core.content.edit

object BackendPreferences {
    private const val PREFS_NAME = "backend_prefs"
    private const val KEY_CATALOG_URL = "catalog_url"

    fun getCatalogUrl(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CATALOG_URL, "")
            .orEmpty()
            .trim()

        return if (saved.isBlank()) RuntimeConfig.defaultCatalogUrl else saved
    }

    fun getCatalogUrlCandidates(context: Context): List<String> {
        val saved = getCustomCatalogUrl(context)
        return if (saved.isBlank()) {
            RuntimeConfig.defaultCatalogUrls
        } else {
            listOf(saved) + RuntimeConfig.defaultCatalogUrls
        }
    }

    fun getCustomBackendSources(context: Context): List<BackendSource> {
        val saved = getCustomCatalogUrl(context)
        return if (saved.isBlank()) emptyList() else listOf(BackendSource("Custom backend", saved))
    }

    fun setCustomBackendSources(context: Context, sources: List<BackendSource>) {
        setCatalogUrl(
            context,
            sources.firstNotNullOfOrNull { it.url.trim().takeIf(String::isNotBlank) }.orEmpty()
        )
    }

    fun getCustomCatalogUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CATALOG_URL, "")
            .orEmpty()
            .trim()
    }

    fun setCatalogUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_CATALOG_URL, sanitize(url)) }
    }

    fun isUsingDefault(context: Context): Boolean {
        return getCustomCatalogUrl(context).isBlank()
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
}
