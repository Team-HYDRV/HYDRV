package app.hydra.manager

import android.content.Context

object ListSortPreferences {
    const val KEY_HOME_SORT = "home_sort"
    const val KEY_FAVORITES_SORT = "favorites_sort"
    const val KEY_INSTALLED_SORT = "installed_sort"
    const val KEY_VERSION_SORT = "version_sort"
    const val KEY_DOWNLOAD_SORT = "download_sort"

    const val HOME_SORT_NAME_ASC = "name_asc"
    const val HOME_SORT_NAME_DESC = "name_desc"
    const val HOME_SORT_NEWEST = "newest"
    const val HOME_SORT_OLDEST = "oldest"

    const val DOWNLOAD_SORT_NAME_ASC = "name_asc"
    const val DOWNLOAD_SORT_NAME_DESC = "name_desc"
    const val DOWNLOAD_SORT_NEWEST = "newest"
    const val DOWNLOAD_SORT_OLDEST = "oldest"

    const val VERSION_SORT_NEWEST = "newest"
    const val VERSION_SORT_OLDEST = "oldest"

    fun getHomeSort(context: Context): String {
        return context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .getString(KEY_HOME_SORT, HOME_SORT_NAME_ASC) ?: HOME_SORT_NAME_ASC
    }

    fun setHomeSort(context: Context, value: String) {
        context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .edit()
            .putString(KEY_HOME_SORT, value)
            .apply()
    }

    fun getFavoritesSort(context: Context): String {
        return context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .getString(KEY_FAVORITES_SORT, HOME_SORT_NAME_ASC) ?: HOME_SORT_NAME_ASC
    }

    fun setFavoritesSort(context: Context, value: String) {
        context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .edit()
            .putString(KEY_FAVORITES_SORT, value)
            .apply()
    }

    fun getInstalledSort(context: Context): String {
        return context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .getString(KEY_INSTALLED_SORT, HOME_SORT_NAME_ASC) ?: HOME_SORT_NAME_ASC
    }

    fun setInstalledSort(context: Context, value: String) {
        context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .edit()
            .putString(KEY_INSTALLED_SORT, value)
            .apply()
    }

    fun getDownloadSort(context: Context): String {
        return context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .getString(KEY_DOWNLOAD_SORT, DOWNLOAD_SORT_NEWEST) ?: DOWNLOAD_SORT_NEWEST
    }

    fun setDownloadSort(context: Context, value: String) {
        context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .edit()
            .putString(KEY_DOWNLOAD_SORT, value)
            .apply()
    }

    fun getVersionSort(context: Context): String {
        return context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .getString(KEY_VERSION_SORT, VERSION_SORT_NEWEST) ?: VERSION_SORT_NEWEST
    }

    fun setVersionSort(context: Context, value: String) {
        context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            .edit()
            .putString(KEY_VERSION_SORT, value)
            .apply()
    }

    fun sortApps(sortMode: String, list: List<AppModel>): List<AppModel> {
        return when (sortMode) {
            HOME_SORT_NEWEST -> list.sortedByDescending { it.latestVersionSortKey() }
            HOME_SORT_OLDEST -> list.sortedBy { it.latestVersionSortKey() }
            HOME_SORT_NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            else -> list.sortedBy { it.name.lowercase() }
        }
    }

    fun sortDownloads(sortMode: String, list: List<DownloadItem>): List<DownloadItem> {
        fun sortTime(item: DownloadItem): Long {
            return item.completedAt.takeIf { it > 0L } ?: item.createdAt
        }

        return when (sortMode) {
            DOWNLOAD_SORT_NAME_ASC -> list.sortedBy { it.name.lowercase() }
            DOWNLOAD_SORT_NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            DOWNLOAD_SORT_OLDEST -> list.sortedBy { sortTime(it) }
            else -> list.sortedByDescending { sortTime(it) }
        }
    }

    fun sortVersions(sortMode: String, list: List<Version>): List<Version> {
        val uniqueVersions = list.distinctBy { versionDisplayKey(it) }

        return when (sortMode) {
            VERSION_SORT_OLDEST -> uniqueVersions.sortedWith(
                compareBy<Version> { it.version }
                    .thenBy { it.releaseTimestampMillis() ?: Long.MIN_VALUE }
            )
            else -> uniqueVersions.sortedWith(
                compareByDescending<Version> { it.version }
                    .thenByDescending { it.releaseTimestampMillis() ?: Long.MIN_VALUE }
            )
        }
    }

    fun homeSortLabel(context: Context, sortMode: String): String {
        return when (sortMode) {
            HOME_SORT_NEWEST -> context.getString(R.string.sort_newest_first)
            HOME_SORT_OLDEST -> context.getString(R.string.sort_oldest_first)
            HOME_SORT_NAME_DESC -> context.getString(R.string.sort_name_desc)
            else -> context.getString(R.string.sort_name_asc)
        }
    }

    fun downloadSortLabel(context: Context, sortMode: String): String {
        return when (sortMode) {
            DOWNLOAD_SORT_NAME_ASC -> context.getString(R.string.sort_name_asc)
            DOWNLOAD_SORT_NAME_DESC -> context.getString(R.string.sort_name_desc)
            DOWNLOAD_SORT_OLDEST -> context.getString(R.string.sort_date_oldest)
            else -> context.getString(R.string.sort_date_newest)
        }
    }

    fun versionSortLabel(context: Context, sortMode: String): String {
        return when (sortMode) {
            VERSION_SORT_OLDEST -> context.getString(R.string.sort_oldest_first)
            else -> context.getString(R.string.sort_newest_first)
        }
    }
}
