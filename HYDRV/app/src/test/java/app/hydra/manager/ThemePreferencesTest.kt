package app.hydra.manager

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferencesTest {

    @Test
    fun sanitizeMode_fallsBackToSystemForUnknownValues() {
        assertEquals(
            ThemePreferences.DEFAULT_THEME_MODE,
            ThemePreferences.sanitizeMode(12345)
        )
    }

    @Test
    fun modeToThemeOptionId_mapsAllSupportedModes() {
        assertEquals(
            R.id.themeOptionSystem,
            ThemePreferences.modeToThemeOptionId(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
        assertEquals(
            R.id.themeOptionLight,
            ThemePreferences.modeToThemeOptionId(AppCompatDelegate.MODE_NIGHT_NO)
        )
        assertEquals(
            R.id.themeOptionDark,
            ThemePreferences.modeToThemeOptionId(AppCompatDelegate.MODE_NIGHT_YES)
        )
    }

    @Test
    fun themeOptionIdToMode_mapsAllThemeOptions() {
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            ThemePreferences.themeOptionIdToMode(R.id.themeOptionSystem)
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_NO,
            ThemePreferences.themeOptionIdToMode(R.id.themeOptionLight)
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_YES,
            ThemePreferences.themeOptionIdToMode(R.id.themeOptionDark)
        )
    }
}

