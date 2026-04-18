package app.hydra.manager

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object InstallStatusCenter {

    private const val DOWNLOADS_POST_CONFIRMATION_HOLD_PROGRESS = 58

    enum class InstallStage {
        PREPARING,
        WAITING_CONFIRMATION,
        SUCCESS,
        FAILURE
    }

    data class Event(
        val message: String,
        val indefinite: Boolean = false,
        val refreshInstalledState: Boolean = false,
        val installStage: InstallStage? = null,
        val appName: String? = null,
        val progress: Int? = null,
        val token: Long = System.currentTimeMillis()
    )

    data class ActiveInstallState(
        val versionKey: String,
        val stage: InstallStage,
        val progress: Int = 0,
        val confirmationRequested: Boolean = false,
        val postConfirmationBaseline: Int? = null
    )

    private val _events = MutableLiveData<Event>()
    val events: LiveData<Event> = _events
    private val activeInstalls = linkedMapOf<String, ActiveInstallState>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingClear = linkedMapOf<String, Runnable>()

    private fun detailFor(
        appName: String?,
        stage: InstallStage?,
        progress: Int?,
        confirmationRequested: Boolean? = null,
        extra: String = ""
    ): String {
        val parts = mutableListOf<String>()
        if (!appName.isNullOrBlank()) parts += appName
        if (stage != null) parts += "stage=$stage"
        if (progress != null) parts += "progress=$progress"
        if (confirmationRequested != null) parts += "confirmed=$confirmationRequested"
        if (extra.isNotBlank()) parts += extra
        return parts.joinToString(" | ")
    }

    fun post(
        message: String,
        indefinite: Boolean = false,
        refreshInstalledState: Boolean = false,
        installStage: InstallStage? = null,
        appName: String? = null,
        versionKey: String? = null,
        progress: Int? = null
    ) {
        val normalized = appName?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val existing = synchronized(this) { normalized?.let { activeInstalls[it] } }
        val normalizedProgress = progress?.coerceIn(0, 100)
        val suppressDuplicatePreConfirmationPreparing =
            installStage == InstallStage.PREPARING &&
                !refreshInstalledState &&
                existing?.confirmationRequested != true &&
                existing?.stage == InstallStage.PREPARING &&
                normalizedProgress != null &&
                existing.progress == normalizedProgress
        if (suppressDuplicatePreConfirmationPreparing) {
            HYDRVApp.appContext?.let {
                AppDiagnostics.traceLimited(
                    it,
                    "INSTALL_UI",
                    "install_status_post_suppressed",
                    detailFor(
                        appName = appName,
                        stage = installStage,
                        progress = normalizedProgress,
                        confirmationRequested = existing?.confirmationRequested,
                        extra = "reason=duplicate_pre_confirmation_preparing"
                    ),
                    dedupeKey = "install_status_post_suppressed:${normalized ?: "unknown"}:${installStage}:${normalizedProgress}",
                    cooldownMs = 1200L
                )
            }
            return
        }
        val shouldTracePost =
            message.isNotBlank() ||
                installStage != null ||
                !appName.isNullOrBlank() ||
                progress != null
        if (shouldTracePost) {
            HYDRVApp.appContext?.let {
                AppDiagnostics.trace(
                    it,
                    "INSTALL_UI",
                    "install_status_post",
                    detailFor(
                        appName = appName,
                        stage = installStage,
                        progress = progress,
                        confirmationRequested = existing?.confirmationRequested,
                        extra = if (message.isNotBlank()) "message=$message" else ""
                    )
                )
            }
        }
        _events.postValue(
            Event(
                message = message,
                indefinite = indefinite,
                refreshInstalledState = refreshInstalledState,
                installStage = installStage,
                appName = appName,
                progress = normalizedProgress
            )
        )
        updateActiveInstall(appName, installStage, versionKey = null, progress = normalizedProgress)
    }

    @Synchronized
    fun markActive(appName: String, versionKey: String, stage: InstallStage, progress: Int = 0) {
        if (appName.isBlank() || versionKey.isBlank()) return
        val normalized = appName.trim().lowercase()
        pendingClear.remove(normalized)?.let(mainHandler::removeCallbacks)
        activeInstalls[normalized] = ActiveInstallState(
            versionKey = versionKey,
            stage = stage,
            progress = progress.coerceIn(0, 100),
            confirmationRequested = false,
            postConfirmationBaseline = null
        )
        HYDRVApp.appContext?.let {
            AppDiagnostics.trace(
                it,
                "INSTALL_UI",
                "install_mark_active",
                detailFor(appName, stage, progress, confirmationRequested = false)
            )
        }
    }

    @Synchronized
    fun activeStateForApp(appName: String?): ActiveInstallState? {
        val normalized = appName?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return activeInstalls[normalized]
    }

    @Synchronized
    fun hasActiveInstalls(): Boolean = activeInstalls.isNotEmpty()

    @Synchronized
    private fun updateActiveInstall(
        appName: String?,
        stage: InstallStage?,
        versionKey: String?,
        progress: Int?
    ) {
        val normalized = appName?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return
        when (stage) {
            InstallStage.PREPARING,
            InstallStage.WAITING_CONFIRMATION -> {
                pendingClear.remove(normalized)?.let(mainHandler::removeCallbacks)
                val existing = activeInstalls[normalized]
                val resolvedKey = versionKey?.takeIf { it.isNotBlank() } ?: existing?.versionKey ?: return
                val rawProgress = progress?.coerceIn(0, 100) ?: existing?.progress ?: 0
                val confirmationRequested = when (stage) {
                    InstallStage.WAITING_CONFIRMATION -> true
                    InstallStage.PREPARING -> existing?.confirmationRequested ?: false
                }
                val baseline = when {
                    stage == InstallStage.WAITING_CONFIRMATION -> null
                    stage == InstallStage.PREPARING &&
                        existing?.stage == InstallStage.WAITING_CONFIRMATION &&
                        confirmationRequested -> rawProgress
                    stage == InstallStage.PREPARING &&
                        confirmationRequested -> existing?.postConfirmationBaseline
                    else -> null
                }
                val visualProgress = when {
                    stage == InstallStage.WAITING_CONFIRMATION -> 0
                    stage == InstallStage.PREPARING && confirmationRequested ->
                        DOWNLOADS_POST_CONFIRMATION_HOLD_PROGRESS
                    else -> rawProgress
                }
                activeInstalls[normalized] = ActiveInstallState(
                    versionKey = resolvedKey,
                    stage = stage,
                    progress = visualProgress,
                    confirmationRequested = confirmationRequested,
                    postConfirmationBaseline = baseline
                )
                HYDRVApp.appContext?.let {
                    AppDiagnostics.trace(
                        it,
                        "INSTALL_UI",
                        "install_active_updated",
                        detailFor(
                            appName = appName,
                            stage = stage,
                            progress = visualProgress,
                            confirmationRequested = confirmationRequested,
                            extra = "baseline=${baseline ?: -1}"
                        )
                    )
                }
            }
            InstallStage.SUCCESS -> {
                pendingClear.remove(normalized)?.let(mainHandler::removeCallbacks)
                val existing = activeInstalls[normalized]
                val resolvedKey = versionKey?.takeIf { it.isNotBlank() } ?: existing?.versionKey ?: return
                activeInstalls[normalized] = ActiveInstallState(
                    versionKey = resolvedKey,
                    stage = InstallStage.SUCCESS,
                    progress = 100,
                    confirmationRequested = existing?.confirmationRequested ?: true,
                    postConfirmationBaseline = existing?.postConfirmationBaseline
                )
                HYDRVApp.appContext?.let {
                    AppDiagnostics.trace(
                        it,
                        "INSTALL_UI",
                        "install_success_latched",
                        detailFor(
                            appName = appName,
                            stage = InstallStage.SUCCESS,
                            progress = 100,
                            confirmationRequested = existing?.confirmationRequested ?: true
                        )
                    )
                }
                val clearRunnable = Runnable {
                    synchronized(this) {
                        activeInstalls.remove(normalized)
                        pendingClear.remove(normalized)
                    }
                    HYDRVApp.appContext?.let {
                        AppDiagnostics.trace(
                            it,
                            "INSTALL_UI",
                            "install_success_cleared",
                            detailFor(appName = appName, stage = InstallStage.SUCCESS, progress = 100)
                        )
                    }
                    _events.postValue(
                        Event(
                            message = "",
                            refreshInstalledState = true,
                            appName = appName
                        )
                    )
                }
                pendingClear[normalized] = clearRunnable
                mainHandler.postDelayed(clearRunnable, 650L)
            }
            InstallStage.FAILURE -> {
                pendingClear.remove(normalized)?.let(mainHandler::removeCallbacks)
                activeInstalls.remove(normalized)
                HYDRVApp.appContext?.let {
                    AppDiagnostics.trace(
                        it,
                        "INSTALL_UI",
                        "install_failure_cleared",
                        detailFor(appName = appName, stage = InstallStage.FAILURE, progress = 0)
                    )
                }
            }
            null -> Unit
        }
    }
}
