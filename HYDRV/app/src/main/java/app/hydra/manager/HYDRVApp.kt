package app.hydra.manager

import android.app.Application

class HYDRVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(ThemePreferences.PREFS_NAME, MODE_PRIVATE)
        val mode = ThemePreferences.sanitizeMode(
            prefs.getInt(ThemePreferences.KEY_THEME, ThemePreferences.DEFAULT_THEME_MODE)
        )
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        LanguagePreferences.applySavedLanguage(this)
        AppIconPreferences.applySavedIcon(this)
    }
}
