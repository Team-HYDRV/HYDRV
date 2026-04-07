package app.hydra.manager

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class AppModel(
    val name: String,
    val packageName: String,
    val icon: String,
    @SerializedName(value = "tags", alternate = ["categories", "categoryTags", "labels"])
    val tags: List<String> = emptyList(),
    val versions: List<Version>
) : Serializable {
    private fun safeVersions(): List<Version> {
        return runCatching { versions }.getOrDefault(emptyList())
    }

    private fun uniqueVersions(): List<Version> {
        return safeVersions().distinctBy { versionDisplayKey(it) }
    }

    private fun safeTags(): List<String> {
        return runCatching { tags }.getOrDefault(emptyList())
    }

    fun sortedVersionsNewestFirst(): List<Version> {
        return uniqueVersions().sortedWith(
            compareByDescending<Version> { it.version }
                .thenByDescending { it.releaseTimestampMillis() ?: Long.MIN_VALUE }
                .thenByDescending { versionSortTextKey(it.version_name) }
        )
    }

    fun latestVersion(): Version? {
        return uniqueVersions().maxWithOrNull(
            compareBy<Version> { it.version }
                .thenBy { it.releaseTimestampMillis() ?: Long.MIN_VALUE }
                .thenBy { versionSortTextKey(it.version_name) }
        )
    }

    fun latestVersionSortKey(): Long {
        return safeVersions().maxOfOrNull { it.version.toLong() } ?: 0L
    }

    fun normalizedTags(): List<String> {
        return safeTags()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.US) }
    }
}

data class Version(
    val version: Int,
    val version_name: String,
    val url: String,
    val changelog: String? = "",
    @SerializedName(
        value = "released_at",
        alternate = [
            "release_date",
            "added_at",
            "date_added",
            "published_at",
            "time",
            "date",
            "datetime",
            "added",
            "released",
            "created_at",
            "createdAt",
            "updated_at"
        ]
    )
    val releasedAt: String? = null,
    @SerializedName(
        value = "timestamp",
        alternate = [
            "release_timestamp",
            "added_timestamp",
            "published_timestamp",
            "created_timestamp",
            "createdAtTimestamp",
            "addedAt",
            "ts"
        ]
    )
    val timestamp: Long? = null
) : Serializable {
    fun downloadHost(): String? {
        return try {
            URI(url.trim()).host?.removePrefix("www.")?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun downloadSourceLabel(isOfficialBackend: Boolean = true): String {
        if (!isOfficialBackend) {
            return "Custom backend"
        }
        val host = downloadHost()?.lowercase(Locale.US).orEmpty()
        val normalizedUrl = url.trim().lowercase(Locale.US)
        return when {
            host == "api.hydrv.app" ||
                    host == "downloads.hydrv.app" ||
                    normalizedUrl.startsWith("https://downloads.hydrv.app/") ||
                    normalizedUrl.startsWith("https://api.hydrv.app/") ||
                    normalizedUrl == "https://downloads.hydrv.app" ||
                    normalizedUrl == "https://api.hydrv.app" ||
            normalizedUrl.contains("/token/") ||
            normalizedUrl.contains("/download/") -> "HYDRV Verified"
            host.contains("cloudflare") || host.endsWith(".r2.dev") -> "Cloudflare"
            host.endsWith(".githubusercontent.com") || host == "github.com" -> "GitHub"
            host.contains("we.tl") || host.contains("wetransfer") -> "Mirror"
            url.isApkUrl() -> "Direct download"
            host.isNotBlank() -> "Direct link"
            else -> "Unknown source"
        }
    }

    fun releaseTimestampMillis(): Long? {
        timestamp?.let { raw ->
            return when {
                raw <= 0L -> null
                raw < 100000000000L -> raw * 1000L
                else -> raw
            }
        }

        val raw = releasedAt?.trim().orEmpty()
        if (raw.isEmpty()) return null

        raw.toLongOrNull()?.let { numeric ->
            return if (numeric < 100000000000L) numeric * 1000L else numeric
        }

        return parseIsoTimestamp(raw)
    }

    private fun parseIsoTimestamp(value: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd"
        )
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = true
            }
            try {
                return formatter.parse(value)?.time
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}

private fun versionSortTextKey(value: String): String {
    return value.trim().lowercase(Locale.US)
}

internal fun versionIdentityKey(version: Version): String {
    return buildString {
        append(version.version)
        append('|')
        append(version.version_name.trim().lowercase(Locale.US))
        append('|')
        append(version.url.trim().lowercase(Locale.US))
    }
}

internal fun versionDisplayKey(version: Version): String {
    return buildString {
        append(version.version)
        append('|')
        append(version.version_name.trim().lowercase(Locale.US))
    }
}

internal fun String.isApkUrl(): Boolean {
    return try {
        URI(trim()).path?.endsWith(".apk", ignoreCase = true) == true
    } catch (_: Exception) {
        trim().substringBefore('?', trim()).substringBefore('#', trim()).endsWith(".apk", ignoreCase = true)
    }
}

internal fun String.cleanDuplicateSuffix(): String {
    val trimmed = trim()
    val cleaned = trimmed.replace(Regex("""\s*\((\d+)\)$"""), "")
    return cleaned.ifBlank { trimmed }
}

internal fun Version.displayVersionName(): String {
    return version_name.cleanDuplicateSuffix()
}

data class BackendSource(
    val name: String,
    val url: String,
    val enabled: Boolean = true
) : Serializable
