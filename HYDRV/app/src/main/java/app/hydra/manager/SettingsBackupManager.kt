package app.hydra.manager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SettingsBackupManager {

    private const val BACKUP_VERSION = 2

    fun exportBackup(context: Context): String {
        val root = JSONObject()
        root.put("backup_version", BACKUP_VERSION)
        root.put("favorites", JSONArray(AppStateCacheManager.favoriteNames(context).sorted()))

        val settings = JSONObject().apply {
            put("theme_mode", ThemePreferences.getSavedMode(context))
            put("language", LanguagePreferences.getLanguage(context))
            put("download_network", DownloadNetworkPreferences.getMode(context))
            put("dynamic_color", AppearancePreferences.isDynamicColorEnabled(context))
            put("pure_black", AppearancePreferences.isPureBlackEnabled(context))
            put("rewarded_ads", AdsPreferences.areRewardedAdsEnabled(context))
            put("update_notifications", NotificationPreferences.areUpdateNotificationsEnabled(context))
            put("check_on_launch", UpdatePreferences.isCheckOnLaunchEnabled(context))
            put("show_launch_message", UpdatePreferences.isLaunchMessageEnabled(context))
            put("home_sort", ListSortPreferences.getHomeSort(context))
            put("favorites_sort", ListSortPreferences.getFavoritesSort(context))
            put("installed_sort", ListSortPreferences.getInstalledSort(context))
            put("version_sort", ListSortPreferences.getVersionSort(context))
            put("download_sort", ListSortPreferences.getDownloadSort(context))
        }
        val backendSources = BackendPreferences.getCustomBackendSources(context)
        val activeBackendUrl = BackendPreferences.getActiveBackendUrlValue(context).trim()
        settings.put(
            "active_backend_url",
            if (activeBackendUrl.isNotBlank() &&
                !activeBackendUrl.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true) &&
                backendSources.any { it.url.equals(activeBackendUrl, ignoreCase = true) }
            ) {
                activeBackendUrl
            } else {
                ""
            }
        )
        settings.put(
            "backend_sources",
            JSONArray().apply {
                backendSources.forEach { source ->
                    put(
                        JSONObject().apply {
                            put("name", source.name)
                            put("url", source.url)
                            put("enabled", source.enabled)
                        }
                    )
                }
            }
        )
        root.put("settings", settings)
        return root.toString(2)
    }

    fun importBackup(context: Context, raw: String): Result<Unit> {
        return runCatching {
            val root = JSONObject(raw)
            val settings = root.optJSONObject("settings")
                ?: error("Backup file is missing the settings block.")

            val favorites = root.optJSONArray("favorites") ?: JSONArray()

            ThemePreferences.saveAndApplyTheme(
                context,
                settings.optInt("theme_mode", ThemePreferences.DEFAULT_THEME_MODE)
            )
            LanguagePreferences.setLanguage(
                context,
                settings.optString("language", LanguagePreferences.SYSTEM)
            )
            DownloadNetworkPreferences.setMode(
                context,
                settings.optString("download_network", DownloadNetworkPreferences.WIFI_OR_MOBILE)
            )
            AppearancePreferences.setDynamicColorEnabled(
                context,
                settings.optBoolean("dynamic_color", false)
            )
            AppearancePreferences.setPureBlackEnabled(
                context,
                settings.optBoolean("pure_black", false)
            )
            AdsPreferences.setRewardedAdsEnabled(
                context,
                settings.optBoolean("rewarded_ads", true)
            )
            NotificationPreferences.setUpdateNotificationsEnabled(
                context,
                settings.optBoolean("update_notifications", true)
            )
            UpdatePreferences.setCheckOnLaunchEnabled(
                context,
                settings.optBoolean("check_on_launch", true)
            )
            UpdatePreferences.setLaunchMessageEnabled(
                context,
                settings.optBoolean("show_launch_message", true)
            )
            ListSortPreferences.setHomeSort(
                context,
                settings.optString("home_sort", ListSortPreferences.HOME_SORT_NAME_ASC)
            )
            ListSortPreferences.setFavoritesSort(
                context,
                settings.optString("favorites_sort", ListSortPreferences.HOME_SORT_NAME_ASC)
            )
            ListSortPreferences.setInstalledSort(
                context,
                settings.optString("installed_sort", ListSortPreferences.HOME_SORT_NAME_ASC)
            )
            ListSortPreferences.setVersionSort(
                context,
                settings.optString("version_sort", ListSortPreferences.VERSION_SORT_NEWEST)
            )
            ListSortPreferences.setDownloadSort(
                context,
                settings.optString("download_sort", ListSortPreferences.DOWNLOAD_SORT_NEWEST)
            )

            val backendSources = buildList {
                val rawSources = settings.optJSONArray("backend_sources") ?: JSONArray()
                for (index in 0 until rawSources.length()) {
                    val source = rawSources.optJSONObject(index) ?: continue
                    val name = source.optString("name").trim()
                    val url = source.optString("url").trim()
                    if (url.isBlank()) continue
                    add(
                        BackendSource(
                            name = name.ifBlank { "Custom backend" },
                            url = url,
                            enabled = source.optBoolean("enabled", true)
                        )
                    )
                }
            }
            BackendPreferences.setCustomBackendSources(context, backendSources)
            val activeBackendUrl = settings.optString("active_backend_url", "").trim()
            if (activeBackendUrl.isNotBlank() &&
                !activeBackendUrl.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true) &&
                backendSources.any { it.url.equals(activeBackendUrl, ignoreCase = true) }
            ) {
                BackendPreferences.setActiveBackendUrl(context, activeBackendUrl)
            }

            val favoriteList = buildList {
                for (index in 0 until favorites.length()) {
                    val value = favorites.optString(index).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
            AppStateCacheManager.replaceFavorites(context, favoriteList)
        }
    }
}
