package app.hydra.manager

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AppUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val result = GitHubRepository.fetchLatestReleaseSync()
        val latestRelease = result.getOrElse { return Result.retry() }
        val currentVersion = try {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        val latestTag = latestRelease.tagName.trim()
        val hasUpdate = ReleaseVersionComparator.isNewer(currentVersion, latestTag)
        val lastSeenTag = ReleaseUpdateState.getLastSeenTag(applicationContext)
        val lastNotifiedTag = ReleaseUpdateState.getLastNotifiedTag(applicationContext)

        if (latestTag.isNotBlank()) {
            if (hasUpdate && latestTag != lastSeenTag && latestTag != lastNotifiedTag) {
                AppNotificationHelper.showReleaseUpdateNotification(
                    applicationContext,
                    latestRelease.displayLabel(),
                    latestRelease.htmlUrl
                )
                ReleaseUpdateState.setLastNotifiedTag(applicationContext, latestTag)
            }
            ReleaseUpdateState.setLastSeenTag(applicationContext, latestTag)
        }

        ReleaseUpdateState.setLastCheckedAt(applicationContext, System.currentTimeMillis())

        return Result.success()
    }
}
