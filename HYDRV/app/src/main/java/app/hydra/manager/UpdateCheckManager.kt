package app.hydra.manager

import android.content.Context

object UpdateCheckManager {

    data class Result(
        val hasChanges: Boolean,
        val checkedAt: Long,
        val latestReleaseTag: String?,
        val latestReleaseName: String?,
        val latestReleaseUrl: String?
    )

    fun runCheck(
        context: Context,
        onResult: (Result?) -> Unit
    ) {
        GitHubRepository.fetchLatestRelease { result ->
            result.onSuccess { latestRelease ->
                val now = System.currentTimeMillis()
                val currentVersion = getCurrentVersionName(context)
                val latestTag = latestRelease.tagName.trim()
                val latestName = latestRelease.displayLabel()
                val hasChanges = ReleaseVersionComparator.isNewer(currentVersion, latestTag)
                val latestVersionLabel = if (latestTag.isNotBlank()) latestTag else latestName

                ReleaseUpdateState.setLastSeenTag(context, latestTag)
                ReleaseUpdateState.setLastCheckedAt(context, now)

                onResult(
                    Result(
                        hasChanges = hasChanges,
                        checkedAt = now,
                        latestReleaseTag = latestTag,
                        latestReleaseName = latestVersionLabel,
                        latestReleaseUrl = latestRelease.htmlUrl
                    )
                )
            }.onFailure {
                onResult(null)
            }
        }
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
}
