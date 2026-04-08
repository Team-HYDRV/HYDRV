package app.hydra.manager

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit

object AppIconPreferences {

    private const val ICON_SWITCH_DISABLE_DELAY_MS = 2000L

    private const val PREFS_NAME = "app_icon_prefs"
    private const val KEY_ICON = "icon_choice"
    private const val KEY_ICON_INITIALIZED = "icon_initialized"
    private const val KEY_ICON_SYNC_PENDING = "icon_sync_pending"

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

    fun ensureDefaultOnFirstLaunch(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ICON_INITIALIZED, false)) return

        prefs.edit {
            putString(KEY_ICON, ICON_DEFAULT)
            putBoolean(KEY_ICON_INITIALIZED, true)
            putBoolean(KEY_ICON_SYNC_PENDING, true)
        }
    }

    fun hasPendingIconSync(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ICON_SYNC_PENDING, false)
    }

    fun clearPendingIconSync(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ICON_SYNC_PENDING, false) }
    }

    fun consumePendingIconSync(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ICON_SYNC_PENDING, false)) return false

        prefs.edit { putBoolean(KEY_ICON_SYNC_PENDING, false) }
        return true
    }

    fun isAlternativeSelected(context: Context): Boolean {
        return currentIcon(context) == ICON_ALTERNATIVE
    }

    fun setIcon(context: Context, choice: String) {
        val selected = if (choice in allowedIcons) choice else ICON_DEFAULT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_ICON, selected)
                putBoolean(KEY_ICON_INITIALIZED, true)
            }
        applyIcon(context, selected)
    }

    fun applySavedIcon(context: Context) {
        applyIcon(context, currentIcon(context))
    }

    fun applyIcon(context: Context, choice: String) {
        val packageManager = context.packageManager
        val selected = if (choice in allowedIcons) choice else ICON_DEFAULT

        when (selected) {
            ICON_DEFAULT -> applyComponentStateBatch(
                packageManager,
                listOf(defaultAlias(context)),
                listOf(alternativeAlias(context), legacyAlias(context), legacyGradientAlias(context))
            )
            ICON_ALTERNATIVE -> applyComponentStateBatch(
                packageManager,
                listOf(alternativeAlias(context)),
                listOf(defaultAlias(context), legacyAlias(context), legacyGradientAlias(context))
            )
            ICON_LEGACY -> applyComponentStateBatch(
                packageManager,
                listOf(legacyAlias(context)),
                listOf(defaultAlias(context), alternativeAlias(context), legacyGradientAlias(context))
            )
            ICON_LEGACY_GRADIENT -> applyComponentStateBatch(
                packageManager,
                listOf(legacyGradientAlias(context)),
                listOf(defaultAlias(context), alternativeAlias(context), legacyAlias(context))
            )
        }
    }

    private fun applyComponentStateBatch(
        packageManager: PackageManager,
        enabled: List<ComponentName>,
        disabled: List<ComponentName>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val settings = buildList {
                enabled.forEach { component ->
                    add(
                        PackageManager.ComponentEnabledSetting(
                            component,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    )
                }
                disabled.forEach { component ->
                    add(
                        PackageManager.ComponentEnabledSetting(
                            component,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    )
                }
            }
            packageManager.setComponentEnabledSettings(settings)
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        enabled.forEach { component ->
            setComponentEnabled(packageManager, component, true)
        }
        mainHandler.postDelayed({
            disabled.forEach { component ->
                setComponentEnabled(packageManager, component, false)
            }
        }, ICON_SWITCH_DISABLE_DELAY_MS)
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
