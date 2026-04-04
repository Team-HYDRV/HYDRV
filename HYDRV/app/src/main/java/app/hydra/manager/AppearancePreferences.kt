package app.hydra.manager

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View

object AppearancePreferences {

    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_PURE_BLACK = "pure_black"
    private const val DEFAULT_DYNAMIC_COLOR = false
    private const val DEFAULT_PURE_BLACK = false

    fun isDynamicColorEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC_COLOR, DEFAULT_DYNAMIC_COLOR)
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DYNAMIC_COLOR, enabled)
            .apply()
    }

    fun isPureBlackEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PURE_BLACK, DEFAULT_PURE_BLACK)
    }

    fun setPureBlackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PURE_BLACK, enabled)
            .apply()
    }

    fun isPureBlackActive(context: Context): Boolean {
        if (!isPureBlackEnabled(context)) return false
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun applyPureBlackBackgroundIfNeeded(view: View) {
        if (isPureBlackActive(view.context)) {
            view.setBackgroundColor(Color.BLACK)
        }
    }
}
