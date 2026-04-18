package app.hydra.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.R as MaterialR
import com.google.android.material.color.DynamicColors
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_ONBOARDING = "onboarding"
        private const val KEY_QUICK_START_SHOWN = "quick_start_shown"
        private const val KEY_ACTIVE_TAG = "active_tag"
        private const val TAG_HOME = "home"
        private const val TAG_DOWNLOAD = "download"
        private const val TAG_SETTINGS = "settings"
        private const val FOREGROUND_CATALOG_REFRESH_MS = 60000L
        private const val PENDING_UNINSTALL_RETRY_DELAY_MS = 350L
        private const val MAX_PENDING_UNINSTALL_RETRIES = 3
    }

    private lateinit var badgeDot: View
    private lateinit var launchOverlay: View
    private var installSnackbar: Snackbar? = null

    private var activeColor = 0
    private var inactiveColor = 0

    private var activeFragment: Fragment? = null
    private var activeTag: String = TAG_HOME
    private var homeReady = false
    private var deferredStartupRan = false
    private var iconSyncScheduled = false
    private var shouldShowLaunchOverlay = true
    private var isThemeOnlyRecreation = false
    private var skippedThemeRefreshResume = false
    private var installEventObserverStartedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val foregroundRefreshInFlight = AtomicBoolean(false)
    private var packageChangeReceiverRegistered = false
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) return
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val packageName = intent.data?.schemeSpecificPart.orEmpty()
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    AppStateCacheManager.forceRefreshInstalledPackages(this@MainActivity) {
                        var handledSpecificPackageEvent = false
                        if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !isReplacing) {
                            val appName = PendingUninstallTracker.consumeIfMatchesRemoved(packageName)
                            if (appName != null) {
                                handledSpecificPackageEvent = true
                                InstallStatusCenter.post(
                                    getString(R.string.uninstall_success_format, appName),
                                    appName = appName,
                                    refreshInstalledState = true
                                )
                            }
                        }
                        if (!handledSpecificPackageEvent) {
                            InstallStatusCenter.post("", refreshInstalledState = true)
                        }
                    }
                }
            }
        }
    }
    private val foregroundRefreshRunnable = object : Runnable {
        override fun run() {
            refreshCatalogInForeground()
            mainHandler.postDelayed(this, FOREGROUND_CATALOG_REFRESH_MS)
        }
    }

    private data class BackendRefreshResult(
        val source: BackendSource,
        val apps: List<AppModel>,
        val hash: Int,
        val fromCache: Boolean,
        val changed: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        isThemeOnlyRecreation = AppRecreationController.consumeThemeRefresh()
        skippedThemeRefreshResume = false
        val restoredActiveTag = savedInstanceState?.getString(KEY_ACTIVE_TAG, TAG_HOME) ?: TAG_HOME
        val effectiveSavedState = if (isThemeOnlyRecreation && savedInstanceState != null) {
            Bundle(savedInstanceState).apply {
                remove("android:support:fragments")
            }
        } else {
            savedInstanceState
        }

        AppearancePreferences.applyActivityTheme(this)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(effectiveSavedState)

        if (!getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
                .getBoolean(KEY_QUICK_START_SHOWN, false)
        ) {
            startActivity(android.content.Intent(this, QuickStartActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(findViewById(R.id.rootLayout))
        val bottomNav = findViewById<View>(R.id.bottomNav)
        val bottomNavBasePadding = bottomNav.paddingBottom
        val bottomNavContainer = findViewById<View>(R.id.navContainer)
        bottomNavContainer.setBackgroundResource(
            if (AppearancePreferences.isDynamicColorEnabled(this)) {
                R.drawable.nav_bg_material
            } else {
                R.drawable.nav_bg
            }
        )
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bottomNavBasePadding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(bottomNav)
        activeColor = ThemeColors.color(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            R.color.accent
        )
        inactiveColor = ThemeColors.color(
            this,
            MaterialR.attr.colorOnSurfaceVariant,
            R.color.subtext
        )
        supportActionBar?.hide()
        val homeTab = findViewById<View>(R.id.nav_home_tab)
        val downloadTab = findViewById<View>(R.id.nav_download_tab)
        val settingsTab = findViewById<View>(R.id.nav_settings_tab)
        val home = findViewById<ImageView>(R.id.nav_home)
        val download = findViewById<ImageView>(R.id.nav_download)
        val settings = findViewById<ImageView>(R.id.nav_settings)

        badgeDot = findViewById(R.id.nav_badge_dot)
        launchOverlay = findViewById(R.id.launchOverlay)
        shouldShowLaunchOverlay = effectiveSavedState == null && !isThemeOnlyRecreation
        if (!shouldShowLaunchOverlay) {
            launchOverlay.visibility = View.GONE
        }

        val navTabs = arrayOf(home, download, settings)
        arrayOf(homeTab, downloadTab, settingsTab).forEach { tab ->
            UiMotion.attachPress(
                target = tab,
                scaleDown = 0.975f,
                pressedAlpha = 0.96f,
                pressedTranslationYDp = 1f,
                downDuration = 80L,
                upDuration = 160L,
                releaseOvershoot = 0.5f,
                traceLabel = runCatching { resources.getResourceEntryName(tab.id) }.getOrNull()
            )
        }

        fun setActive(view: ImageView, animate: Boolean = true) {
            navTabs.forEach {
                it.animate().cancel()
                it.setColorFilter(inactiveColor)
                it.alpha = 1f
                it.scaleX = 1f
                it.scaleY = 1f
                it.translationY = 0f
            }

            view.setColorFilter(activeColor)
            if (animate) {
                UiMotion.pulse(
                    target = view,
                    scaleUp = 1.12f,
                    alphaDip = 0.82f,
                    riseDp = 1f,
                    expandDuration = 95L,
                    settleDuration = 190L,
                    settleOvershoot = 0.62f,
                    traceLabel = runCatching { resources.getResourceEntryName(view.id) }.getOrNull()
                )
            }
        }

        fun updateBadge() {
            val count = DownloadRepository.downloads.count {
                it.status == "Downloading" || it.status == "Paused"
            }

            if (count > 0) {
                if (badgeDot.visibility != View.VISIBLE) {
                    badgeDot.visibility = View.VISIBLE
                    badgeDot.scaleX = 0f
                    badgeDot.scaleY = 0f
                    badgeDot.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(250)
                        .start()
                }
            } else {
                badgeDot.visibility = View.GONE
            }
        }

        DownloadRepository.downloadsLive.observe(this) {
            updateBadge()
        }

        installEventObserverStartedAt = System.currentTimeMillis()
        InstallStatusCenter.events.observe(this) { event ->
            if (event.token < installEventObserverStartedAt) return@observe
            showInstallSnackbar(event)
        }

        if (effectiveSavedState == null) {
            if (isThemeOnlyRecreation) {
                val initialTag = restoredActiveTag
                val initialFragment = createFragment(initialTag)
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container, initialFragment, initialTag)
                    .commitNow()

                activeFragment = initialFragment
                activeTag = initialTag
                when (initialTag) {
                    TAG_DOWNLOAD -> setActive(download, animate = false)
                    TAG_SETTINGS -> setActive(settings, animate = false)
                    else -> setActive(home, animate = false)
                }
            } else {
                val initialHomeFragment = createFragment(TAG_HOME)
                val initialDownloadFragment = createFragment(TAG_DOWNLOAD)
                val initialSettingsFragment = createFragment(TAG_SETTINGS)
                supportFragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container, initialHomeFragment, TAG_HOME)
                    .add(R.id.fragment_container, initialDownloadFragment, TAG_DOWNLOAD)
                    .hide(initialDownloadFragment)
                    .add(R.id.fragment_container, initialSettingsFragment, TAG_SETTINGS)
                    .hide(initialSettingsFragment)
                    .commitNow()

                activeFragment = initialHomeFragment
                activeTag = TAG_HOME
                setActive(home)
            }
        } else {
            activeTag = effectiveSavedState.getString(KEY_ACTIVE_TAG, TAG_HOME)
            activeFragment = supportFragmentManager.findFragmentByTag(activeTag)
                ?: supportFragmentManager.findFragmentByTag(TAG_HOME)

            when (activeTag) {
                TAG_DOWNLOAD -> setActive(download, animate = false)
                TAG_SETTINGS -> setActive(settings, animate = false)
                else -> setActive(home, animate = false)
            }
        }

        updateBadge()

        fun switchFragment(targetTag: String) {
            if (targetTag == activeTag) return
            AppDiagnostics.traceLimited(
                this,
                "UI",
                "nav_switch_start",
                "$activeTag->$targetTag",
                dedupeKey = "nav_switch:$activeTag:$targetTag",
                cooldownMs = 180L
            )

            val current = activeFragment ?: return
            val existingTarget = supportFragmentManager.findFragmentByTag(targetTag)
            val target = existingTarget ?: createFragment(targetTag)
            val transaction = supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                    R.anim.fragment_fade_in,
                    R.anim.fragment_fade_out,
                    R.anim.fragment_fade_in,
                    R.anim.fragment_fade_out
                )
                .hide(current)

            if (existingTarget == null) {
                transaction.add(R.id.fragment_container, target, targetTag)
            } else {
                transaction.show(target)
            }

            transaction.commit()
            activeFragment = target
            activeTag = targetTag
        }

        homeTab.setOnClickListener {
            AppDiagnostics.traceLimited(
                this,
                "UI",
                "nav_tab_click",
                TAG_HOME,
                dedupeKey = "nav_tab_click:$TAG_HOME"
            )
            setActive(home)
            switchFragment(TAG_HOME)
        }

        downloadTab.setOnClickListener {
            AppDiagnostics.traceLimited(
                this,
                "UI",
                "nav_tab_click",
                TAG_DOWNLOAD,
                dedupeKey = "nav_tab_click:$TAG_DOWNLOAD"
            )
            setActive(download)
            switchFragment(TAG_DOWNLOAD)
        }

        settingsTab.setOnClickListener {
            AppDiagnostics.traceLimited(
                this,
                "UI",
                "nav_tab_click",
                TAG_SETTINGS,
                dedupeKey = "nav_tab_click:$TAG_SETTINGS"
            )
            setActive(settings)
            switchFragment(TAG_SETTINGS)
        }

    }

    private fun dismissLaunchOverlay() {
        if (!this::launchOverlay.isInitialized || !shouldShowLaunchOverlay || launchOverlay.visibility != View.VISIBLE || !homeReady) return

        launchOverlay.animate()
            .alpha(0f)
            .setDuration(85)
            .setStartDelay(0)
            .withEndAction {
                launchOverlay.visibility = View.GONE
                launchOverlay.alpha = 1f
            }
            .start()
    }

    fun onHomeFirstRenderComplete() {
        homeReady = true
        scheduleStartupIconSync()
        dismissLaunchOverlay()
        runDeferredStartupIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (isThemeOnlyRecreation && !skippedThemeRefreshResume) {
            skippedThemeRefreshResume = true
            mainHandler.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                if (!shouldDeferInstalledRefreshForActiveTab()) {
                    AppStateCacheManager.warmInstalledPackages(this)
                }
            }, 450L)
        } else {
            val deferredSystemReason = SystemOperationReturnGate.consume()
            if (deferredSystemReason != null) {
                AppDiagnostics.trace(
                    this,
                    "UI",
                    "main_resume_refresh_suppressed",
                    deferredSystemReason
                )
                mainHandler.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    schedulePendingUninstallSnackbarReconcile()
                }, 900L)
            } else {
                AppStateCacheManager.forceRefreshInstalledPackages(this) {
                    reconcilePendingUninstallSnackbar(clearIfStillInstalled = true)
                }
            }
        }
        ensurePackageChangeReceiverRegistered()
        mainHandler.removeCallbacks(foregroundRefreshRunnable)
        mainHandler.postDelayed(foregroundRefreshRunnable, FOREGROUND_CATALOG_REFRESH_MS)

        val count = DownloadRepository.downloads.count {
            it.status == "Downloading" || it.status == "Paused"
        }

        if (this::badgeDot.isInitialized) {
            badgeDot.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_TAG, activeTag)
    }

    override fun onPause() {
        if (packageChangeReceiverRegistered) {
            unregisterReceiver(packageChangeReceiver)
            packageChangeReceiverRegistered = false
        }
        mainHandler.removeCallbacks(foregroundRefreshRunnable)
        super.onPause()
    }

    private fun createFragment(tag: String): Fragment {
        return when (tag) {
            TAG_DOWNLOAD -> DownloadFragment()
            TAG_SETTINGS -> SettingsFragment()
            else -> HomeFragment()
        }
    }

    private fun runDeferredStartupIfNeeded() {
        if (deferredStartupRan) return
        deferredStartupRan = true
        mainHandler.post {
            if (isFinishing || isDestroyed) return@post
            val appContext = applicationContext
            if (isThemeOnlyRecreation) {
                AppStateCacheManager.initialize(appContext)
                return@post
            }
            AppStateCacheManager.initialize(appContext)
            DownloadRepository.load(appContext)
            AppNotificationHelper.ensureChannels(appContext)
            UpdateWorkScheduler.ensureScheduled(appContext, runImmediateCatchUp = true)
            maybeCheckUpdatesOnLaunch()
            if (CatalogStateCenter.currentApps().isEmpty()) {
                refreshCatalogInForeground()
            }
            if (AdsPreferences.areRewardedAdsEnabled(appContext)) {
                RewardedAdManager.initialize(appContext)
            } else {
                RewardedAdManager.clear()
            }
        }
    }

    private fun scheduleStartupIconSync() {
        if (iconSyncScheduled) return
        iconSyncScheduled = true

        val wasPendingReset = AppIconPreferences.hasPendingIconSync(this)
        mainHandler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            AppIconPreferences.applySavedIcon(this)
            if (wasPendingReset) {
                AppIconPreferences.clearPendingIconSync(this)
            }
        }, if (wasPendingReset) 1500L else 500L)
    }

    private fun showInstallSnackbar(event: InstallStatusCenter.Event) {
        if (event.message.isBlank()) return
        val shouldShowSnackbar =
            event.installStage == null ||
                event.installStage == InstallStatusCenter.InstallStage.SUCCESS ||
                event.installStage == InstallStatusCenter.InstallStage.FAILURE
        if (!shouldShowSnackbar) return
        if (activeTag == TAG_DOWNLOAD) return
        installSnackbar?.dismiss()
        installSnackbar = AppSnackbar.show(
            findViewById(R.id.rootLayout),
            event.message,
            if (event.indefinite) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG
        )
    }

    private fun reconcilePendingUninstallSnackbar() {
        reconcilePendingUninstallSnackbar(clearIfStillInstalled = true)
    }

    private fun reconcilePendingUninstallSnackbar(clearIfStillInstalled: Boolean): Boolean {
        val removedApp = PendingUninstallTracker.consumeIfRemoved(this)
        if (removedApp == null) {
            if (clearIfStillInstalled) {
                PendingUninstallTracker.clearIfStillInstalled(this)
            }
            return false
        }
        InstallStatusCenter.post(
            getString(R.string.uninstall_success_format, removedApp),
            appName = removedApp,
            refreshInstalledState = true
        )
        return true
    }

    private fun schedulePendingUninstallSnackbarReconcile(attempt: Int = 0) {
        if (isFinishing || isDestroyed) return
        AppStateCacheManager.forceRefreshInstalledPackages(this) {
            val resolved = reconcilePendingUninstallSnackbar(
                clearIfStillInstalled = attempt >= MAX_PENDING_UNINSTALL_RETRIES
            )
            if (resolved || !PendingUninstallTracker.hasPending()) return@forceRefreshInstalledPackages
            if (attempt >= MAX_PENDING_UNINSTALL_RETRIES) return@forceRefreshInstalledPackages
            mainHandler.postDelayed({
                schedulePendingUninstallSnackbarReconcile(attempt + 1)
            }, PENDING_UNINSTALL_RETRY_DELAY_MS)
        }
    }

    private fun ensurePackageChangeReceiverRegistered() {
        if (packageChangeReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, filter)
        }
        packageChangeReceiverRegistered = true
    }

    private fun maybeCheckUpdatesOnLaunch() {
        if (!UpdatePreferences.isCheckOnLaunchEnabled(this)) return

        UpdateCheckManager.runCheck(this) { result ->
            result ?: return@runCheck

            if (result.hasChanges && UpdatePreferences.isLaunchMessageEnabled(this)) {
                AppSnackbar.show(
                    findViewById(R.id.rootLayout),
                    getString(R.string.updates_check_live_generic),
                    Snackbar.LENGTH_LONG
                )
            }
        }
    }

    private fun refreshCatalogInForeground() {
        if (!foregroundRefreshInFlight.compareAndSet(false, true)) return

        val appContext = applicationContext
        Thread {
            try {
                val monitoredSources = BackendPreferences.getMonitoredBackendSources(appContext)
                val results = linkedMapOf<String, BackendRefreshResult?>()

                monitoredSources.forEach { source ->
                    val result = AppCatalogService.fetchBackendAppsSync(
                        context = appContext,
                        backendUrl = source.url,
                        allowCacheFallback = true,
                        bypassRemoteCache = true
                    )

                    val backendResult = result.getOrNull()?.let { fetchResult ->
                        val hash = CatalogFingerprint.hash(fetchResult.apps)
                        val previousHash = AppUpdateState.getBackendLastSeenHash(appContext, source.url)
                        val hasChanges = previousHash != 0 && hash != previousHash && !fetchResult.fromCache

                        if (!fetchResult.fromCache && hash != 0) {
                            AppUpdateState.setBackendLastSeenHash(appContext, source.url, hash)
                        }
                        if (hasChanges) {
                            AppUpdateState.setBackendLastNotifiedHash(appContext, source.url, hash)
                        }

                        BackendRefreshResult(
                            source = source,
                            apps = fetchResult.apps,
                            hash = hash,
                            fromCache = fetchResult.fromCache,
                            changed = hasChanges
                        )
                    }

                    results[source.url.lowercase()] = backendResult
                }

                val activeUrl = BackendPreferences.getActiveBackendUrlValue(appContext).trim()
                val activeSource = monitoredSources.firstOrNull {
                    it.url.equals(activeUrl, ignoreCase = true)
                } ?: monitoredSources.firstOrNull {
                    it.url.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true)
                }

                val activeResult = activeSource?.let { results[it.url.lowercase()] }
                val changedNonActiveResults = results.values
                    .filterNotNull()
                    .filter { result ->
                        activeSource == null || !result.source.url.equals(activeSource.url, ignoreCase = true)
                    }
                    .filter { it.changed }

                mainHandler.post {
                    try {
                        if (isFinishing || isDestroyed) return@post

                        AppUpdateState.setLastCheckedAt(this, System.currentTimeMillis())

                        activeResult?.let { result ->
                            CatalogStateCenter.update(result.apps)
                            AppStateCacheManager.refreshFavorites(appContext)
                            if (!shouldDeferInstalledRefreshForActiveTab()) {
                                AppStateCacheManager.warmInstalledPackages(appContext)
                            }
                            if (result.hash != 0) {
                                AppUpdateState.setLastSeenHash(this, result.hash)
                            }

                            if (result.changed) {
                                AppUpdateState.setLastNotifiedHash(this, result.hash)
                                AppSnackbar.show(
                                    findViewById(R.id.rootLayout),
                                    getString(
                                        R.string.backend_update_snackbar_format,
                                        result.source.name.ifBlank { getString(R.string.backend_default_label) }
                                    ),
                                    Snackbar.LENGTH_LONG
                                )
                                AppNotificationHelper.showBackendUpdateNotification(
                                    this,
                                    result.source.name.ifBlank {
                                        getString(R.string.backend_default_label)
                                    },
                                    result.source.url
                                )
                            }
                        }

                        if (NotificationPreferences.areUpdateNotificationsEnabled(this)) {
                            changedNonActiveResults.forEach { result ->
                                AppNotificationHelper.showBackendUpdateNotification(
                                    this,
                                    result.source.name.ifBlank {
                                        getString(R.string.backend_default_label)
                                    },
                                    result.source.url
                                )
                            }
                        }
                    } finally {
                        foregroundRefreshInFlight.set(false)
                    }
                }
            } catch (_: Throwable) {
                foregroundRefreshInFlight.set(false)
            }
        }.start()
    }

    private fun shouldDeferInstalledRefreshForActiveTab(): Boolean {
        return activeTag == TAG_SETTINGS
    }

    private fun resolveDisplayNameForPackage(packageName: String): String {
        InstallAliasStore.findAppNameForActualPackage(this, packageName)?.let { return it }
        CatalogStateCenter.apps.value?.firstOrNull { app ->
            app.packageName.equals(packageName, ignoreCase = true) ||
                InstallAliasStore.resolveForPackage(this, app.packageName)
                    ?.equals(packageName, ignoreCase = true) == true
        }?.name?.let { return it }
        return packageName
    }
}



