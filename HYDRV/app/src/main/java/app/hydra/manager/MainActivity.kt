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
import android.view.animation.DecelerateInterpolator
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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_ONBOARDING = "onboarding"
        private const val KEY_QUICK_START_SHOWN = "quick_start_shown"
        private const val KEY_ACTIVE_TAG = "active_tag"
        private const val TAG_HOME = "home"
        private const val TAG_DOWNLOAD = "download"
        private const val TAG_SETTINGS = "settings"
        private const val FOREGROUND_CATALOG_REFRESH_MS = 60000L
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
    private var shouldShowLaunchOverlay = true
    private val mainHandler = Handler(Looper.getMainLooper())
    private var packageChangeReceiverRegistered = false
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null) return
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED -> {
                    val packageName = intent.data?.schemeSpecificPart.orEmpty()
                    val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                    AppStateCacheManager.forceRefreshInstalledPackages(this@MainActivity) {
                        if (
                            intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                            !isReplacing &&
                            packageName.isNotBlank()
                        ) {
                            val appName = resolveDisplayNameForPackage(packageName)
                            InstallStatusCenter.post(
                                getString(R.string.uninstall_success_format, appName)
                            )
                        }
                        InstallStatusCenter.post("", refreshInstalledState = true)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences.applyActivityTheme(this)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)

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
        AppIconPreferences.applySavedIcon(this)
        val homeTab = findViewById<View>(R.id.nav_home_tab)
        val downloadTab = findViewById<View>(R.id.nav_download_tab)
        val settingsTab = findViewById<View>(R.id.nav_settings_tab)
        val home = findViewById<ImageView>(R.id.nav_home)
        val download = findViewById<ImageView>(R.id.nav_download)
        val settings = findViewById<ImageView>(R.id.nav_settings)

        badgeDot = findViewById(R.id.nav_badge_dot)
        launchOverlay = findViewById(R.id.launchOverlay)
        shouldShowLaunchOverlay = savedInstanceState == null
        if (!shouldShowLaunchOverlay) {
            launchOverlay.visibility = View.GONE
        }

        val navTabs = arrayOf(home, download, settings)

        fun setActive(view: ImageView) {
            navTabs.forEach {
                it.setColorFilter(inactiveColor)
                it.alpha = 1f
            }

            view.setColorFilter(activeColor)
            view.animate()
                .alpha(0.6f)
                .setDuration(80)
                .withEndAction {
                    view.animate().alpha(1f).setDuration(80)
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

        InstallStatusCenter.events.observe(this) { event ->
            showInstallSnackbar(event)
        }

        if (savedInstanceState == null) {
            val initialHomeFragment = createFragment(TAG_HOME)
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, initialHomeFragment, TAG_HOME)
                .commitNow()

            activeFragment = initialHomeFragment
            activeTag = TAG_HOME
            setActive(home)
        } else {
            activeTag = savedInstanceState.getString(KEY_ACTIVE_TAG, TAG_HOME)
            activeFragment = supportFragmentManager.findFragmentByTag(activeTag)
                ?: supportFragmentManager.findFragmentByTag(TAG_HOME)

            when (activeTag) {
                TAG_DOWNLOAD -> setActive(download)
                TAG_SETTINGS -> setActive(settings)
                else -> setActive(home)
            }
        }

        updateBadge()

        fun switchFragment(targetTag: String) {
            if (targetTag == activeTag) return

            val current = activeFragment ?: return
            val existingTarget = supportFragmentManager.findFragmentByTag(targetTag)
            val target = existingTarget ?: createFragment(targetTag)
            val transaction = supportFragmentManager.beginTransaction()
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
            setActive(home)
            switchFragment(TAG_HOME)
        }

        downloadTab.setOnClickListener {
            setActive(download)
            switchFragment(TAG_DOWNLOAD)
        }

        settingsTab.setOnClickListener {
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
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                launchOverlay.visibility = View.GONE
                launchOverlay.alpha = 1f
            }
            .start()
    }

    fun onHomeFirstRenderComplete() {
        homeReady = true
        dismissLaunchOverlay()
        runDeferredStartupIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        AppStateCacheManager.forceRefreshInstalledPackages(this) {
            PendingUninstallTracker.consumeIfRemoved(this)?.let { appName ->
                InstallStatusCenter.post(getString(R.string.uninstall_success_format, appName))
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

    private fun showInstallSnackbar(event: InstallStatusCenter.Event) {
        if (event.message.isBlank()) return
        installSnackbar?.dismiss()
        installSnackbar = AppSnackbar.show(
            findViewById(R.id.rootLayout),
            event.message,
            if (event.indefinite) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG
        )
    }

    private fun ensurePackageChangeReceiverRegistered() {
        if (packageChangeReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
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
        AppCatalogService.fetchApps(
            this,
            allowCacheFallback = true,
            bypassRemoteCache = true
        ) { result ->
            result.onSuccess { fetchResult ->
                val apps = fetchResult.apps
                val newHash = CatalogFingerprint.hash(apps)
                val previousHash = AppUpdateState.getLastSeenHash(this)
                val hasChanges = previousHash != 0 && newHash != previousHash

                CatalogStateCenter.update(apps)
                AppUpdateState.setLastCheckedAt(this, System.currentTimeMillis())
                if (newHash != 0) {
                    AppUpdateState.setLastSeenHash(this, newHash)
                }

                if (hasChanges && !fetchResult.fromCache) {
                    AppUpdateState.setLastNotifiedHash(this, newHash)
                    AppSnackbar.show(
                        findViewById(R.id.rootLayout),
                        getString(R.string.new_changes_available),
                        Snackbar.LENGTH_LONG
                    )
                    if (NotificationPreferences.areUpdateNotificationsEnabled(this)) {
                        AppNotificationHelper.showBackendUpdateNotification(this)
                    }
                }
            }
        }
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



