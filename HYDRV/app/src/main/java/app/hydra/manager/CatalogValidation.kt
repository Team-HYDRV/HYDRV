package app.hydra.manager

import java.util.Locale

object CatalogValidation {

    fun validate(apps: List<AppModel>): Result<List<AppModel>> {
        return runCatching {
            val packageNameCounts = mutableMapOf<String, Int>()
            val sanitized = mutableListOf<AppModel>()

            apps.forEach { app ->
                val appName = runCatching { app.name.trim() }.getOrDefault("")
                val rawPackageName = runCatching { app.packageName.trim() }.getOrDefault("")
                val icon = runCatching { app.icon.trim() }.getOrDefault("")

                if (appName.isEmpty()) {
                    return@forEach
                }

                val basePackageName = rawPackageName.ifBlank {
                    "missing.${appName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), ".").trim('.')}"
                }
                val packageKey = basePackageName.lowercase(Locale.US)
                val duplicateIndex = packageNameCounts[packageKey] ?: 0
                packageNameCounts[packageKey] = duplicateIndex + 1
                val packageName = if (duplicateIndex == 0) {
                    basePackageName
                } else {
                    "$basePackageName.dup$duplicateIndex"
                }

                val versionNumbers = mutableSetOf<Int>()
                val versions = app.versions.filter { version ->
                    val versionName = runCatching { version.version_name.trim() }.getOrDefault("")
                    val versionUrl = runCatching { version.url.trim() }.getOrDefault("")
                    version.version > 0 &&
                        versionName.isNotEmpty() &&
                        versionUrl.isNotEmpty() &&
                        versionNumbers.add(version.version)
                }

                if (versions.isEmpty()) {
                    return@forEach
                }

                sanitized.add(
                    app.copy(
                        packageName = packageName,
                        icon = icon,
                        versions = versions
                    )
                )
            }

            require(sanitized.isNotEmpty()) { "Catalog contains no valid apps." }
            sanitized
        }
    }
}
