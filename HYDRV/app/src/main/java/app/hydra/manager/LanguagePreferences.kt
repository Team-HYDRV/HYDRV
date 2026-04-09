package app.hydra.manager

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.edit

object LanguagePreferences {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "app_language"

    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val FILIPINO = "fil"
    const val SPANISH = "es"
    const val CATALAN = "ca"
    const val INDONESIAN = "id"
    const val FRENCH = "fr"
    const val GERMAN = "de"
    const val GREEK = "el"
    const val HEBREW = "he"
    const val HUNGARIAN = "hu"
    const val PORTUGUESE_BRAZIL = "pt-BR"
    const val PORTUGUESE_PORTUGAL = "pt-PT"
    const val ITALIAN = "it"
    const val TURKISH = "tr"
    const val VIETNAMESE = "vi"
    const val THAI = "th"
    const val POLISH = "pl"
    const val DUTCH = "nl"
    const val DANISH = "da"
    const val NORWEGIAN = "no"
    const val MALAY = "ms"
    const val UKRAINIAN = "uk"
    const val CZECH = "cs"
    const val ROMANIAN = "ro"
    const val CHINESE_SIMPLIFIED = "zh-CN"
    const val CHINESE_TRADITIONAL = "zh-TW"
    const val HINDI = "hi"
    const val ARABIC = "ar"
    const val RUSSIAN = "ru"
    const val JAPANESE = "ja"
    const val KOREAN = "ko"
    const val SERBIAN = "sr"
    const val SWEDISH = "sv"

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
            .edit { putString(KEY_LANGUAGE, language) }

        AppCompatDelegate.setApplicationLocales(localeListFor(language))
    }

    fun label(context: Context, language: String): String {
        return when (language) {
            ENGLISH -> context.getString(R.string.language_english)
            FILIPINO -> context.getString(R.string.language_filipino)
            SPANISH -> context.getString(R.string.language_spanish)
            CATALAN -> context.getString(R.string.language_catalan)
            INDONESIAN -> context.getString(R.string.language_indonesian)
            FRENCH -> context.getString(R.string.language_french)
            GERMAN -> context.getString(R.string.language_german)
            GREEK -> context.getString(R.string.language_greek)
            HEBREW -> context.getString(R.string.language_hebrew)
            HUNGARIAN -> context.getString(R.string.language_hungarian)
            PORTUGUESE_BRAZIL -> context.getString(R.string.language_portuguese_brazil)
            PORTUGUESE_PORTUGAL -> context.getString(R.string.language_portuguese_portugal)
            ITALIAN -> context.getString(R.string.language_italian)
            TURKISH -> context.getString(R.string.language_turkish)
            VIETNAMESE -> context.getString(R.string.language_vietnamese)
            THAI -> context.getString(R.string.language_thai)
            POLISH -> context.getString(R.string.language_polish)
            DUTCH -> context.getString(R.string.language_dutch)
            DANISH -> context.getString(R.string.language_danish)
            NORWEGIAN -> context.getString(R.string.language_norwegian)
            MALAY -> context.getString(R.string.language_malay)
            UKRAINIAN -> context.getString(R.string.language_ukrainian)
            CZECH -> context.getString(R.string.language_czech)
            ROMANIAN -> context.getString(R.string.language_romanian)
            CHINESE_SIMPLIFIED -> context.getString(R.string.language_chinese_simplified)
            CHINESE_TRADITIONAL -> context.getString(R.string.language_chinese_traditional)
            HINDI -> context.getString(R.string.language_hindi)
            ARABIC -> context.getString(R.string.language_arabic)
            RUSSIAN -> context.getString(R.string.language_russian)
            JAPANESE -> context.getString(R.string.language_japanese)
            KOREAN -> context.getString(R.string.language_korean)
            SERBIAN -> context.getString(R.string.language_serbian)
            SWEDISH -> context.getString(R.string.language_swedish)
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
