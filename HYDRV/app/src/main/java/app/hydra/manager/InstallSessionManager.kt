package app.hydra.manager

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileInputStream

object InstallSessionManager {

    const val ACTION_INSTALL_COMMIT = "app.hydra.manager.INSTALL_COMMIT"
    const val EXTRA_APP_NAME = "extra_app_name"
    const val EXTRA_APK_PATH = "extra_apk_path"
    const val EXTRA_BACKEND_PACKAGE = "extra_backend_package"
    const val EXTRA_SESSION_ID = "extra_session_id"

    private data class SessionInstallInfo(
        val appName: String,
        val apkPath: String
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackedSessions = linkedMapOf<Int, SessionInstallInfo>()
    @Volatile
    private var callbackRegistered = false

    private val sessionCallback = object : PackageInstaller.SessionCallback() {
        override fun onCreated(sessionId: Int) = Unit

        override fun onBadgingChanged(sessionId: Int) = Unit

        override fun onActiveChanged(sessionId: Int, active: Boolean) = Unit

        override fun onFinished(sessionId: Int, success: Boolean) {
            synchronized(trackedSessions) {
                trackedSessions.remove(sessionId)
            }
        }

        override fun onProgressChanged(sessionId: Int, progress: Float) {
            val info = synchronized(trackedSessions) { trackedSessions[sessionId] } ?: return
            val percent = (progress.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
            InstallStatusCenter.post(
                message = "",
                installStage = InstallStatusCenter.InstallStage.PREPARING,
                appName = info.appName,
                progress = percent
            )
        }
    }

    private fun ensureSessionCallbackRegistered(context: Context) {
        if (callbackRegistered) return
        synchronized(this) {
            if (callbackRegistered) return
            context.packageManager.packageInstaller.registerSessionCallback(sessionCallback, mainHandler)
            callbackRegistered = true
        }
    }

    fun installApk(context: Context, apkPath: String, appName: String, backendPackage: String) {
        val file = File(apkPath)
        if (!file.exists()) {
            AppDiagnostics.log(context, "INSTALL", "APK file missing before install: $apkPath")
            AppDiagnostics.trace(context, "INSTALL", "request_missing_apk", appName)
            InstallStatusCenter.post(context.getString(R.string.install_failed_file_not_found))
            return
        }

        AppDiagnostics.trace(
            context,
            "INSTALL",
            "request_start",
            "$appName | size=${file.length()} | backend=$backendPackage"
        )

        InstallStatusCenter.post(
            context.getString(R.string.install_preparing_format, appName),
            indefinite = true,
            installStage = InstallStatusCenter.InstallStage.PREPARING,
            appName = appName
        )

        Thread {
            try {
                ensureSessionCallbackRegistered(context.applicationContext)
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                params.setAppPackageName(null)
                params.setSize(file.length())

                val sessionId = packageInstaller.createSession(params)
                AppDiagnostics.trace(context, "INSTALL", "session_created", "$appName | sessionId=$sessionId")
                synchronized(trackedSessions) {
                    trackedSessions[sessionId] = SessionInstallInfo(
                        appName = appName,
                        apkPath = apkPath
                    )
                }
                packageInstaller.openSession(sessionId).use { session ->
                    FileInputStream(file).use { input ->
                        session.openWrite("base.apk", 0, file.length()).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            val totalBytes = file.length().coerceAtLeast(1L)
                            var copiedBytes = 0L
                            var read = input.read(buffer)
                            while (read >= 0) {
                                if (read > 0) {
                                    output.write(buffer, 0, read)
                                    copiedBytes += read
                                    val copyProgress = ((copiedBytes.toDouble() / totalBytes.toDouble()) * 70.0)
                                        .toInt()
                                        .coerceIn(0, 70)
                                    session.setStagingProgress(copyProgress / 100f)
                                    InstallStatusCenter.post(
                                        message = context.getString(R.string.install_preparing_format, appName),
                                        indefinite = true,
                                        installStage = InstallStatusCenter.InstallStage.PREPARING,
                                        appName = appName,
                                        progress = copyProgress
                                    )
                                }
                                read = input.read(buffer)
                            }
                            session.fsync(output)
                        }
                    }
                    session.setStagingProgress(0.78f)
                    InstallStatusCenter.post(
                        message = context.getString(R.string.install_preparing_format, appName),
                        indefinite = true,
                        installStage = InstallStatusCenter.InstallStage.PREPARING,
                        appName = appName,
                        progress = 78
                    )

                    val callbackIntent = Intent(context, InstallResultReceiver::class.java).apply {
                        action = ACTION_INSTALL_COMMIT
                        putExtra(EXTRA_APP_NAME, appName)
                        putExtra(EXTRA_APK_PATH, apkPath)
                        putExtra(EXTRA_BACKEND_PACKAGE, backendPackage)
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        callbackIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

                    AppDiagnostics.trace(context, "INSTALL", "session_commit", "$appName | sessionId=$sessionId")
                    session.commit(pendingIntent.intentSender)
                }
            } catch (e: Exception) {
                synchronized(trackedSessions) {
                    trackedSessions.entries.removeAll { it.value.appName == appName && it.value.apkPath == apkPath }
                }
                AppDiagnostics.log(context, "INSTALL", "Session install failed for $appName", e)
                AppDiagnostics.trace(
                    context,
                    "INSTALL",
                    "session_failed",
                    "$appName | ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                )
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
