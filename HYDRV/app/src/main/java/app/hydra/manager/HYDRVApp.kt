package app.hydra.manager

import android.app.Application
import android.os.Process
import kotlin.system.exitProcess

class HYDRVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        AppIconPreferences.ensureDefaultOnFirstLaunch(this)
        val prefs = getSharedPreferences(ThemePreferences.PREFS_NAME, MODE_PRIVATE)
        val mode = ThemePreferences.sanitizeMode(
            prefs.getInt(ThemePreferences.KEY_THEME, ThemePreferences.DEFAULT_THEME_MODE)
        )
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        LanguagePreferences.applySavedLanguage(this)
    }

    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                AppDiagnostics.log(
                    applicationContext,
                    "CRASH",
                    "Uncaught exception on ${thread.name}",
                    throwable
                )
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
