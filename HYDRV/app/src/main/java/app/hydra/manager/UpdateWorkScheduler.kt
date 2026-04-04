package app.hydra.manager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UpdateWorkScheduler {

    const val WORK_APP_UPDATES_PERIODIC = "app_updates_periodic"
    const val WORK_APP_UPDATES_IMMEDIATE = "app_updates_immediate"

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_APP_UPDATES_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleImmediateCatchUp(context: Context) {
        val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_APP_UPDATES_IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun ensureScheduled(context: Context, runImmediateCatchUp: Boolean) {
        schedulePeriodic(context)
        if (runImmediateCatchUp) {
            scheduleImmediateCatchUp(context)
        }
    }

    fun workerSummary(context: Context): String {
        val workManager = WorkManager.getInstance(context)
        val periodic = runCatching {
            workManager.getWorkInfosForUniqueWork(WORK_APP_UPDATES_PERIODIC).get()
        }.getOrNull().orEmpty()
        val immediate = runCatching {
            workManager.getWorkInfosForUniqueWork(WORK_APP_UPDATES_IMMEDIATE).get()
        }.getOrNull().orEmpty()

        val periodicState = periodic.firstOrNull()?.state?.name ?: "NONE"
        val immediateState = immediate.firstOrNull()?.state?.name ?: "NONE"
        return "Periodic: $periodicState\nImmediate: $immediateState"
    }
}
