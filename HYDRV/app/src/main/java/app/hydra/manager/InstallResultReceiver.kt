package app.hydra.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper

class InstallResultReceiver : BroadcastReceiver() {

    companion object {
        private const val MAX_INSTALL_REFRESH_RETRIES = 12
        private const val INSTALL_REFRESH_DELAY_MS = 500L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != InstallSessionManager.ACTION_INSTALL_COMMIT) return

        val appName = intent.getStringExtra(InstallSessionManager.EXTRA_APP_NAME).orEmpty()
        val apkPath = intent.getStringExtra(InstallSessionManager.EXTRA_APK_PATH).orEmpty()
        val backendPackage = intent.getStringExtra(InstallSessionManager.EXTRA_BACKEND_PACKAGE).orEmpty()
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                AppDiagnostics.log(context, "INSTALL", "Waiting for user confirmation for $appName")
                InstallStatusCenter.post(
                    context.getString(R.string.install_waiting_confirmation_format, appName)
                )
                val confirmIntent =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmIntent != null) {
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                AppDiagnostics.log(context, "INSTALL", "Install completed for $appName")
                val pendingResult = goAsync()
                val expectedPackage = resolveArchivePackageName(context, apkPath)
                DownloadRepository.handleInstallSuccess(context, appName, apkPath, backendPackage)
                waitForInstalledPackage(
                    context = context.applicationContext,
                    appName = appName,
                    backendPackage = backendPackage,
                    expectedPackage = expectedPackage,
                    attempt = 0
                ) {
                    InstallStatusCenter.post(
                        context.getString(R.string.install_success_format, appName),
                        refreshInstalledState = true
                    )
                    pendingResult.finish()
                }
            }
            else -> {
                val failureMessage = installFailureMessage(context, status, message)
                AppDiagnostics.log(
                    context,
                    "INSTALL",
                    "Install failed for $appName with status=$status message=$message"
                )
                InstallStatusCenter.post(failureMessage)
            }
        }
    }

    private fun installFailureMessage(context: Context, status: Int, message: String): String {
        if (message.isNotBlank()) {
            return message
        }

        return when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> context.getString(R.string.install_failed_cancelled)
            PackageInstaller.STATUS_FAILURE_BLOCKED -> context.getString(R.string.install_failed_blocked)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> context.getString(R.string.install_failed_conflict)
            PackageInstaller.STATUS_FAILURE_INVALID -> context.getString(R.string.install_failed_invalid_apk)
            PackageInstaller.STATUS_FAILURE_STORAGE -> context.getString(R.string.install_failed_storage)
            else -> context.getString(R.string.install_failed)
        }
    }

    private fun waitForInstalledPackage(
        context: Context,
        appName: String,
        backendPackage: String,
        expectedPackage: String?,
        attempt: Int,
        onReady: () -> Unit
    ) {
        AppStateCacheManager.forceRefreshInstalledPackages(context) {
            val isInstalled = if (!expectedPackage.isNullOrBlank()) {
                AppStateCacheManager.isInstalled(context, expectedPackage)
            } else {
                AppStateCacheManager.isInstalled(context, backendPackage, appName)
            }

            if (isInstalled || attempt >= MAX_INSTALL_REFRESH_RETRIES) {
                onReady()
            } else {
                Handler(Looper.getMainLooper()).postDelayed(
                    {
                        waitForInstalledPackage(
                            context = context,
                            appName = appName,
                            backendPackage = backendPackage,
                            expectedPackage = expectedPackage,
                            attempt = attempt + 1,
                            onReady = onReady
                        )
                    },
                    INSTALL_REFRESH_DELAY_MS
                )
            }
        }
    }

    private fun resolveArchivePackageName(context: Context, apkPath: String): String? {
        return try {
            context.packageManager
                .getPackageArchiveInfo(apkPath, 0)
                ?.packageName
                ?.trim()
                ?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
