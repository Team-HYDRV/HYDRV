package app.hydra.manager

import java.util.Locale

object ReleaseVersionComparator {

    fun isNewer(currentVersion: String, latestVersion: String): Boolean {
        val currentParts = parseParts(currentVersion)
        val latestParts = parseParts(latestVersion)

        if (currentParts.isNotEmpty() && latestParts.isNotEmpty()) {
            val size = maxOf(currentParts.size, latestParts.size)
            for (index in 0 until size) {
                val currentPart = currentParts.getOrElse(index) { 0 }
                val latestPart = latestParts.getOrElse(index) { 0 }
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
            return false
        }

        return false
    }

    fun displayLabel(tag: String, name: String): String {
        val releaseName = name.trim()
        val releaseTag = tag.trim()
        if (releaseName.isBlank()) {
            return releaseTag.ifBlank { "Latest release" }
        }
        if (releaseTag.isBlank()) {
            return releaseName
        }

        val normalizedName = releaseName.lowercase(Locale.US)
        val normalizedTag = releaseTag.lowercase(Locale.US)
        val tagWithoutPrefix = normalizedTag.removePrefix("v")

        if (
            normalizedName == normalizedTag ||
            normalizedName.contains(" $normalizedTag") ||
            (tagWithoutPrefix.isNotBlank() && normalizedName.contains(" $tagWithoutPrefix"))
        ) {
            return releaseName
        }

        return "$releaseName $releaseTag"
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.US)
            .removePrefix("v")
    }

    private fun parseParts(value: String): List<Int> {
        val cleaned = normalize(value)
        val parts = cleaned.split('.', '-', '_')
            .mapNotNull { segment ->
                segment.takeIf { it.isNotBlank() }?.toIntOrNull()
            }
        return if (parts.isNotEmpty()) parts else emptyList()
    }
}
