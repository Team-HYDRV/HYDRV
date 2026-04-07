package app.hydra.manager

object CatalogFingerprint {

    fun hash(apps: List<AppModel>): Int {
        var result = 17
        apps.forEach { app ->
            result = 31 * result + safe(app.name).hashCode()
            result = 31 * result + safe(app.packageName).hashCode()
            result = 31 * result + safe(app.icon).hashCode()
            result = 31 * result + safe(app.catalogSourceUrl).hashCode()
            result = 31 * result + app.normalizedTags().joinToString("|").hashCode()
            app.sortedVersionsNewestFirst().forEach { version ->
                result = 31 * result + version.version
                result = 31 * result + safe(version.version_name).hashCode()
                result = 31 * result + safe(version.url).hashCode()
                result = 31 * result + safe(version.changelog).hashCode()
                result = 31 * result + (version.releaseTimestampMillis()?.hashCode() ?: 0)
            }
        }
        return result
    }

    private fun safe(value: String?): String = value?.trim().orEmpty()
}
