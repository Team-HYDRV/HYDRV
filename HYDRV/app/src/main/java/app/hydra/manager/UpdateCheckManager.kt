package app.hydra.manager

import android.content.Context

object UpdateCheckManager {

    data class Result(
        val hasChanges: Boolean,
        val latestHash: Int,
        val checkedAt: Long,
        val latestAppName: String?,
        val latestVersionName: String?
    )

    fun runCheck(
        context: Context,
        onResult: (Result?) -> Unit
    ) {
        AppCatalogService.fetchApps(
            context,
            allowCacheFallback = false,
            bypassRemoteCache = true
        ) { result ->
            result.onSuccess { fetchResult ->
                val apps = fetchResult.apps
                val hash = CatalogFingerprint.hash(apps)
                val previousHash = AppUpdateState.getLastSeenHash(context)
                val latestApp = apps.maxByOrNull { it.latestVersionSortKey() }
                val latestVersion = latestApp?.versions?.maxByOrNull { it.version }
                val now = System.currentTimeMillis()

                AppUpdateState.setLastSeenHash(context, hash)
                onResult(
                    Result(
                        hasChanges = previousHash != 0 && hash != previousHash,
                        latestHash = hash,
                        checkedAt = now,
                        latestAppName = latestApp?.name,
                        latestVersionName = latestVersion?.version_name
                    )
                )
            }.onFailure {
                onResult(null)
            }
        }
    }
}
