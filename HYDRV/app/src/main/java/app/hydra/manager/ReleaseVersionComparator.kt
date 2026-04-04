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
        return listOf(name.trim(), tag.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { tag.trim().ifBlank { "Latest release" } }
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
