package app.hydra.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import java.util.concurrent.Executors

object AppStateCacheManager {

    private const val PREFS_FAVORITES = "fav"
    private const val INSTALLED_REFRESH_MIN_INTERVAL_MS = 15_000L

    private val favoriteNames = linkedSetOf<String>()
    private val installedPackages = linkedSetOf<String>()
    private val installedVersions = mutableMapOf<String, Int>()
    private val pendingInstalledCallbacks = mutableListOf<() -> Unit>()
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile
    private var initialized = false

    @Volatile
    private var installedLoaded = false

    @Volatile
    private var installedRefreshInFlight = false

    @Volatile
    private var lastInstalledRefreshAt = 0L

    fun initialize(context: Context) {
        if (initialized) return
        refreshFavorites(context)
        warmInstalledPackages(context)
        initialized = true
    }

    fun refreshAll(context: Context, onInstalledReady: (() -> Unit)? = null) {
        refreshFavorites(context)
        warmInstalledPackages(context, onInstalledReady)
    }

    fun forceRefreshInstalledPackages(context: Context, onComplete: (() -> Unit)? = null) {
        warmInstalledPackages(context, onComplete = onComplete, force = true)
    }

    fun refreshFavorites(context: Context) {
        synchronized(lock) {
            favoriteNames.clear()
            favoriteNames.addAll(
                context.getSharedPreferences(PREFS_FAVORITES, Context.MODE_PRIVATE)
                    .all
                    .filterValues { it == true }
                    .keys
            )
        }
    }

    fun favoriteNames(context: Context): Set<String> {
        initialize(context)
        synchronized(lock) {
            return favoriteNames.toSet()
        }
    }

    fun replaceFavorites(context: Context, names: Collection<String>) {
        val prefs = context.getSharedPreferences(PREFS_FAVORITES, Context.MODE_PRIVATE)
        prefs.edit {
            clear()
            names
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { putBoolean(it, true) }
        }
        refreshFavorites(context)
    }

    fun warmInstalledPackages(
        context: Context,
        onComplete: (() -> Unit)? = null,
        force: Boolean = false
    ) {
        if (!force && installedLoaded && !shouldRefreshInstalledPackages()) {
            onComplete?.let { callback -> mainHandler.post(callback) }
            return
        }
        if (installedRefreshInFlight) {
            onComplete?.let { callback ->
                synchronized(lock) {
                    pendingInstalledCallbacks.add(callback)
                }
            }
            return
        }

        val appContext = context.applicationContext
        onComplete?.let { callback ->
            synchronized(lock) {
                pendingInstalledCallbacks.add(callback)
            }
        }
        installedRefreshInFlight = true
        executor.execute {
            val packages = linkedSetOf<String>()
            val versions = mutableMapOf<String, Int>()
            val callbacks: List<() -> Unit>

            appContext.packageManager.getInstalledPackages(0).forEach { pkg ->
                packages.add(pkg.packageName)
                versions[pkg.packageName] = pkg.versionCodeCompat()
            }

            synchronized(lock) {
                installedPackages.clear()
                installedPackages.addAll(packages)
                installedVersions.clear()
                installedVersions.putAll(versions)
                installedLoaded = true
                lastInstalledRefreshAt = System.currentTimeMillis()
                callbacks = pendingInstalledCallbacks.toList()
                pendingInstalledCallbacks.clear()
            }
            installedRefreshInFlight = false

            callbacks.forEach { callback ->
                mainHandler.post(callback)
            }
        }
    }

    fun isFavorite(context: Context, name: String): Boolean {
        initialize(context)
        synchronized(lock) {
            return favoriteNames.contains(name)
        }
    }

    fun setFavorite(context: Context, name: String, isFavorite: Boolean) {
        initialize(context)
        context.getSharedPreferences(PREFS_FAVORITES, Context.MODE_PRIVATE)
            .edit { putBoolean(name, isFavorite) }

        synchronized(lock) {
            if (isFavorite) {
                favoriteNames.add(name)
            } else {
                favoriteNames.remove(name)
            }
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        initialize(context)
        synchronized(lock) {
            return installedPackages.contains(packageName)
        }
    }

    fun isInstalled(context: Context, packageName: String, appName: String): Boolean {
        initialize(context)
        val actualPackage = resolveKnownPackage(context, packageName, appName)
        synchronized(lock) {
            return installedPackages.contains(packageName) || installedPackages.contains(actualPackage)
        }
    }

    fun installedVersion(context: Context, packageName: String): Int {
        initialize(context)
        synchronized(lock) {
            return installedVersions[packageName] ?: -1
        }
    }

    fun installedVersion(context: Context, packageName: String, appName: String): Int {
        initialize(context)
        val actualPackage = resolveKnownPackage(context, packageName, appName)
        synchronized(lock) {
            return installedVersions[packageName]
                ?: installedVersions[actualPackage]
                ?: -1
        }
    }

    fun installedPackageSet(context: Context): Set<String> {
        initialize(context)
        synchronized(lock) {
            return installedPackages.toSet()
        }
    }

    fun isInstalledStateReady(context: Context): Boolean {
        initialize(context)
        return installedLoaded
    }

    private fun shouldRefreshInstalledPackages(): Boolean {
        return System.currentTimeMillis() - lastInstalledRefreshAt >= INSTALLED_REFRESH_MIN_INTERVAL_MS
    }

    private fun resolveKnownPackage(context: Context, packageName: String, appName: String): String {
        return InstallAliasStore.resolveForAppName(context, appName)
            ?: InstallAliasStore.resolveForPackage(context, packageName)
            ?: packageName
    }
}
