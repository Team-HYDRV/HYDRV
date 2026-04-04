package app.hydra.manager

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemePreferences {
    const val PREFS_NAME = "settings"
    const val KEY_THEME = "theme"
    const val DEFAULT_THEME_MODE = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    fun sanitizeMode(mode: Int): Int {
        return when (mode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES -> mode
            else -> DEFAULT_THEME_MODE
        }
    }

    fun modeToThemeOptionId(mode: Int): Int {
        return when (sanitizeMode(mode)) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.themeOptionLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.themeOptionDark
            else -> R.id.themeOptionSystem
        }
    }

    fun themeOptionIdToMode(checkedId: Int): Int {
        return when (checkedId) {
            R.id.themeOptionLight -> AppCompatDelegate.MODE_NIGHT_NO
            R.id.themeOptionDark -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }

    fun getSavedMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sanitizeMode(prefs.getInt(KEY_THEME, DEFAULT_THEME_MODE))
    }

    fun applySavedTheme(context: Context) {
        applyThemeMode(context, getSavedMode(context))
    }

    fun saveAndApplyTheme(context: Context, mode: Int) {
        val sanitizedMode = sanitizeMode(mode)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME, sanitizedMode)
            .apply()
        applyThemeMode(context, sanitizedMode)
    }

    fun applyThemeMode(context: Context, mode: Int) {
        val sanitizedMode = sanitizeMode(mode)
        AppCompatDelegate.setDefaultNightMode(sanitizedMode)
    }
}

