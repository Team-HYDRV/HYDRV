package app.hydra.manager

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit

object AppIconPreferences {

    private const val PREFS_NAME = "app_icon_prefs"
    private const val KEY_ICON = "icon_choice"

    const val ICON_DEFAULT = "default"
    const val ICON_ALTERNATIVE = "alternative"

    private fun defaultAlias(context: Context): ComponentName {
        return ComponentName(
            context.packageName,
            "${context.packageName}.LauncherDefaultAlias"
        )
    }

    private fun alternativeAlias(context: Context): ComponentName {
        return ComponentName(
            context.packageName,
            "${context.packageName}.LauncherAltAlias"
        )
    }

    fun currentIcon(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ICON, ICON_DEFAULT)
            ?.takeIf { it == ICON_ALTERNATIVE }
            ?: ICON_DEFAULT
    }

    fun isAlternativeSelected(context: Context): Boolean {
        return currentIcon(context) == ICON_ALTERNATIVE
    }

    fun setIcon(context: Context, choice: String) {
        val selected = if (choice == ICON_ALTERNATIVE) ICON_ALTERNATIVE else ICON_DEFAULT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_ICON, selected) }
        applySavedIcon(context)
    }

    fun applySavedIcon(context: Context) {
        applyIcon(context, currentIcon(context))
    }

    fun applyIcon(context: Context, choice: String) {
        val packageManager = context.packageManager
        val useAlternative = choice == ICON_ALTERNATIVE

        setComponentEnabled(
            packageManager,
            defaultAlias(context),
            !useAlternative
        )
        setComponentEnabled(
            packageManager,
            alternativeAlias(context),
            useAlternative
        )
    }

    private fun setComponentEnabled(
        packageManager: PackageManager,
        component: ComponentName,
        enabled: Boolean
    ) {
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(component) == newState) return
        packageManager.setComponentEnabledSetting(
            component,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}
