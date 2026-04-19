package app.hydra.manager

import android.content.Context

object PendingUninstallTracker {

    private var pendingAppName: String? = null
    private var pendingPackageName: String? = null

    fun mark(appName: String, packageName: String) {
        pendingAppName = appName.takeIf { it.isNotBlank() }
        pendingPackageName = packageName.takeIf { it.isNotBlank() }
    }

    fun consumeIfRemoved(context: Context): String? {
        val appName = pendingAppName ?: return null
        val packageName = pendingPackageName ?: return null
        val stillInstalled = AppStateCacheManager.isInstalled(context, packageName, appName)
        if (stillInstalled) return null
        clear()
        return appName
    }

    fun clearIfStillInstalled(context: Context) {
        val appName = pendingAppName ?: return
        val packageName = pendingPackageName ?: return
        if (AppStateCacheManager.isInstalled(context, packageName, appName)) {
            clear()
        }
    }

    fun consumeIfMatchesRemoved(packageName: String): String? {
        if (packageName.isBlank() || pendingPackageName != packageName) return null
        val appName = pendingAppName ?: return null
        clear()
        return appName
    }

    fun hasPending(): Boolean {
        return !pendingAppName.isNullOrBlank() && !pendingPackageName.isNullOrBlank()
    }

    fun matches(appName: String, packageName: String? = null): Boolean {
        val normalizedAppName = appName.trim()
        if (normalizedAppName.isBlank()) return false
        if (!pendingAppName.equals(normalizedAppName, ignoreCase = true)) return false
        val normalizedPackage = packageName?.trim().orEmpty()
        return normalizedPackage.isBlank() || pendingPackageName.equals(normalizedPackage, ignoreCase = true)
    }

    fun clear() {
        pendingAppName = null
        pendingPackageName = null
    }
}
