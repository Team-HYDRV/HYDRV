package app.hydra.manager

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class AppUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val result = AppCatalogService.fetchAppsSync(
            applicationContext,
            allowCacheFallback = false,
            bypassRemoteCache = true
        )
        val apps = result.getOrElse { return Result.retry() }.apps
        val newHash = CatalogFingerprint.hash(apps)
        val lastSeenHash = AppUpdateState.getLastSeenHash(applicationContext)
        val lastNotifiedHash = AppUpdateState.getLastNotifiedHash(applicationContext)

        if (lastSeenHash != 0 && newHash != lastSeenHash && newHash != lastNotifiedHash) {
            AppNotificationHelper.showBackendUpdateNotification(applicationContext)
            AppUpdateState.setLastNotifiedHash(applicationContext, newHash)
        }

        if (newHash != 0) {
            AppUpdateState.setLastSeenHash(applicationContext, newHash)
        }
        AppUpdateState.setLastCheckedAt(applicationContext, System.currentTimeMillis())

        return Result.success()
    }
}
