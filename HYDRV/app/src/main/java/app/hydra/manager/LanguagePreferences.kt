package app.hydra.manager

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguagePreferences {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val FILIPINO = "fil"
    const val SPANISH = "es"
    const val INDONESIAN = "id"
    const val FRENCH = "fr"
    const val GERMAN = "de"
    const val PORTUGUESE_BRAZIL = "pt-BR"
    const val CHINESE_SIMPLIFIED = "zh-CN"
    const val HINDI = "hi"
    const val ARABIC = "ar"
    const val RUSSIAN = "ru"
    const val JAPANESE = "ja"
    const val KOREAN = "ko"

    fun applySavedLanguage(context: Context) {
        AppCompatDelegate.setApplicationLocales(localeListFor(getLanguage(context)))
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, SYSTEM)
            ?: SYSTEM
    }

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()

        AppCompatDelegate.setApplicationLocales(localeListFor(language))
    }

    fun label(context: Context, language: String): String {
        return when (language) {
            ENGLISH -> context.getString(R.string.language_english)
            FILIPINO -> context.getString(R.string.language_filipino)
            SPANISH -> context.getString(R.string.language_spanish)
            INDONESIAN -> context.getString(R.string.language_indonesian)
            FRENCH -> context.getString(R.string.language_french)
            GERMAN -> context.getString(R.string.language_german)
            PORTUGUESE_BRAZIL -> context.getString(R.string.language_portuguese_brazil)
            CHINESE_SIMPLIFIED -> context.getString(R.string.language_chinese_simplified)
            HINDI -> context.getString(R.string.language_hindi)
            ARABIC -> context.getString(R.string.language_arabic)
            RUSSIAN -> context.getString(R.string.language_russian)
            JAPANESE -> context.getString(R.string.language_japanese)
            KOREAN -> context.getString(R.string.language_korean)
            else -> context.getString(R.string.language_system_default)
        }
    }

    private fun localeListFor(language: String): LocaleListCompat {
        return if (language == SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }
    }
}
