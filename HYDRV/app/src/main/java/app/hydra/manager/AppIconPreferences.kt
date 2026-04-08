package app.hydra.manager

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit

object AppIconPreferences {

    private const val ICON_SWITCH_DISABLE_DELAY_MS = 2000L

    private const val PREFS_NAME = "app_icon_prefs"
    private const val KEY_ICON = "icon_choice"

    const val ICON_DEFAULT = "default"
    const val ICON_ALTERNATIVE = "alternative"
    const val ICON_LEGACY = "legacy"
    const val ICON_LEGACY_GRADIENT = "legacy_gradient"

    private val allowedIcons = setOf(
        ICON_DEFAULT,
        ICON_ALTERNATIVE,
        ICON_LEGACY,
        ICON_LEGACY_GRADIENT
    )

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

    private fun legacyAlias(context: Context): ComponentName {
        return ComponentName(
            context.packageName,
            "${context.packageName}.LauncherLegacyAlias"
        )
    }

    private fun legacyGradientAlias(context: Context): ComponentName {
        return ComponentName(
            context.packageName,
            "${context.packageName}.LauncherLegacyGradientAlias"
        )
    }

    fun currentIcon(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ICON, ICON_DEFAULT)
            ?.takeIf { it in allowedIcons }
            ?: ICON_DEFAULT
    }

    fun isAlternativeSelected(context: Context): Boolean {
        return currentIcon(context) == ICON_ALTERNATIVE
    }

    fun setIcon(context: Context, choice: String) {
        val selected = if (choice in allowedIcons) choice else ICON_DEFAULT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_ICON, selected) }
        applySavedIcon(context)
    }

    fun applySavedIcon(context: Context) {
        applyIcon(context, currentIcon(context))
    }

    fun applyIcon(context: Context, choice: String) {
        val packageManager = context.packageManager
        val selected = if (choice in allowedIcons) choice else ICON_DEFAULT
        val mainHandler = Handler(Looper.getMainLooper())

        when (selected) {
            ICON_DEFAULT -> {
                setComponentEnabled(packageManager, defaultAlias(context), true)
                mainHandler.postDelayed({
                    setComponentEnabled(packageManager, alternativeAlias(context), false)
                    setComponentEnabled(packageManager, legacyAlias(context), false)
                    setComponentEnabled(packageManager, legacyGradientAlias(context), false)
                }, ICON_SWITCH_DISABLE_DELAY_MS)
            }
            ICON_ALTERNATIVE -> {
                setComponentEnabled(packageManager, alternativeAlias(context), true)
                mainHandler.postDelayed({
                    setComponentEnabled(packageManager, defaultAlias(context), false)
                    setComponentEnabled(packageManager, legacyAlias(context), false)
                    setComponentEnabled(packageManager, legacyGradientAlias(context), false)
                }, ICON_SWITCH_DISABLE_DELAY_MS)
            }
            ICON_LEGACY -> {
                setComponentEnabled(packageManager, legacyAlias(context), true)
                mainHandler.postDelayed({
                    setComponentEnabled(packageManager, defaultAlias(context), false)
                    setComponentEnabled(packageManager, alternativeAlias(context), false)
                    setComponentEnabled(packageManager, legacyGradientAlias(context), false)
                }, ICON_SWITCH_DISABLE_DELAY_MS)
            }
            ICON_LEGACY_GRADIENT -> {
                setComponentEnabled(packageManager, legacyGradientAlias(context), true)
                mainHandler.postDelayed({
                    setComponentEnabled(packageManager, defaultAlias(context), false)
                    setComponentEnabled(packageManager, alternativeAlias(context), false)
                    setComponentEnabled(packageManager, legacyAlias(context), false)
                }, ICON_SWITCH_DISABLE_DELAY_MS)
            }
        }
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

