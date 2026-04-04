package app.hydra.manager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import java.io.FileInputStream

object InstallSessionManager {

    const val ACTION_INSTALL_COMMIT = "app.hydra.manager.INSTALL_COMMIT"
    const val EXTRA_APP_NAME = "extra_app_name"
    const val EXTRA_APK_PATH = "extra_apk_path"
    const val EXTRA_BACKEND_PACKAGE = "extra_backend_package"

    fun installApk(context: Context, apkPath: String, appName: String, backendPackage: String) {
        val file = File(apkPath)
        if (!file.exists()) {
            AppDiagnostics.log(context, "INSTALL", "APK file missing before install: $apkPath")
            InstallStatusCenter.post(context.getString(R.string.install_failed_file_not_found))
            return
        }

        InstallStatusCenter.post(
            context.getString(R.string.install_preparing_format, appName),
            indefinite = true
        )

        Thread {
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                params.setAppPackageName(null)
                params.setSize(file.length())

                val sessionId = packageInstaller.createSession(params)
                packageInstaller.openSession(sessionId).use { session ->
                    FileInputStream(file).use { input ->
                        session.openWrite("base.apk", 0, file.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }

                    val callbackIntent = Intent(context, InstallResultReceiver::class.java).apply {
                        action = ACTION_INSTALL_COMMIT
                        putExtra(EXTRA_APP_NAME, appName)
                        putExtra(EXTRA_APK_PATH, apkPath)
                        putExtra(EXTRA_BACKEND_PACKAGE, backendPackage)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        callbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    session.commit(pendingIntent.intentSender)
                }
            } catch (e: Exception) {
                AppDiagnostics.log(context, "INSTALL", "Session install failed for $appName", e)
                InstallStatusCenter.post(
                    context.getString(
                        R.string.install_failed_error_format,
                        e.message ?: context.getString(R.string.unknown_error)
                    )
                )
            }
        }.start()
    }
}
