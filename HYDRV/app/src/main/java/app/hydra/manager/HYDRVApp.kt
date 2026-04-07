package app.hydra.manager

import android.app.Application
import androidx.core.content.edit

class HYDRVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(ThemePreferences.PREFS_NAME, MODE_PRIVATE)
        val mode = ThemePreferences.sanitizeMode(
            prefs.getInt(ThemePreferences.KEY_THEME, ThemePreferences.DEFAULT_THEME_MODE)
        )
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        LanguagePreferences.applySavedLanguage(this)

        val migrationPrefs = getSharedPreferences("app_migrations", MODE_PRIVATE)
        if (!migrationPrefs.getBoolean("reset_backend_url_v1", false)) {
            BackendPreferences.setCatalogUrl(this, "")
            migrationPrefs.edit().putBoolean("reset_backend_url_v1", true).apply()
        }
    }
}
