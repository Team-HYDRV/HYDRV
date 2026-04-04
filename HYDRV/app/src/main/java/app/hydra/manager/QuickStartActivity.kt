package app.hydra.manager

import android.Manifest
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.color.DynamicColors

class QuickStartActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_ONBOARDING = "onboarding"
        private const val KEY_QUICK_START_SHOWN = "quick_start_shown"
    }

    private lateinit var internetStatus: TextView
    private lateinit var installStatus: TextView
    private lateinit var installAction: Button
    private lateinit var notificationsStatus: TextView
    private lateinit var notificationsAction: Button
    private lateinit var continueButton: Button

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshPermissionState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_HYDRV)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)

        if (getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
                .getBoolean(KEY_QUICK_START_SHOWN, false)
        ) {
            openMainApp()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_quick_start)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(findViewById(android.R.id.content))

        internetStatus = findViewById(R.id.permissionInternetStatus)
        installStatus = findViewById(R.id.permissionInstallStatus)
        installAction = findViewById(R.id.permissionInstallAction)
        notificationsStatus = findViewById(R.id.permissionNotificationsStatus)
        notificationsAction = findViewById(R.id.permissionNotificationsAction)
        continueButton = findViewById(R.id.permissionContinueButton)
        restoreButtonColors()

        installAction.setOnClickListener {
            openUnknownAppsPermissionSettings()
        }
        notificationsAction.setOnClickListener {
            requestNotificationPermission()
        }
        continueButton.setOnClickListener {
            getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_QUICK_START_SHOWN, true)
                .apply()
            openMainApp()
        }

        refreshPermissionState()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun openMainApp() {
        AppStateCacheManager.refreshFavorites(this)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        finish()
    }

    private fun refreshPermissionState() {
        val isConnected = isInternetAvailable()
        internetStatus.text = if (isConnected) getString(R.string.permission_online) else getString(R.string.permission_offline)
        internetStatus.setBackgroundResource(R.drawable.permission_status_info)
        internetStatus.setTextColor(
            getColor(if (isConnected) R.color.accent else R.color.text_on_dark_chip)
        )

        val canInstall = canInstallUnknownApps()
        installStatus.text =
            if (canInstall) getString(R.string.permission_allowed) else getString(R.string.permission_not_allowed)
        installStatus.setBackgroundResource(R.drawable.permission_status_info)
        installStatus.setTextColor(
            getColor(if (canInstall) R.color.accent else android.R.color.white)
        )
        installAction.text =
            if (canInstall) getString(R.string.permission_enabled) else getString(R.string.permission_open_settings)
        installAction.isEnabled = !canInstall
        installAction.alpha = if (canInstall) 0.6f else 1f

        val canNotify = AppNotificationHelper.canPostNotifications(this)
        notificationsStatus.text =
            if (canNotify) getString(R.string.permission_allowed) else getString(R.string.permission_not_allowed)
        notificationsStatus.setBackgroundResource(R.drawable.permission_status_info)
        notificationsStatus.setTextColor(
            getColor(if (canNotify) R.color.accent else android.R.color.white)
        )
        notificationsAction.text =
            if (canNotify) getString(R.string.permission_enabled) else getString(R.string.permission_enable)
        notificationsAction.isEnabled = !canNotify
        notificationsAction.alpha = if (canNotify) 0.6f else 1f

        continueButton.text = if (canInstall && canNotify) {
            getString(R.string.continue_label)
        } else {
            getString(R.string.skip_label)
        }
    }

    private fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun openUnknownAppsPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (AppNotificationHelper.canPostNotifications(this)) {
            refreshPermissionState()
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val connectivityManager =
                getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
        } catch (_: SecurityException) {
            false
        }
    }

    private fun restoreButtonColors() {
        val primaryGreen = getColor(R.color.permission_primary_button)
        val textColor = getColor(R.color.text_on_accent_chip)

        listOf(installAction, notificationsAction, continueButton).forEach { button ->
            button.backgroundTintList = null
            button.background = getDrawable(R.drawable.button_install)
            button.setTextColor(textColor)
        }

        continueButton.backgroundTintList = null
        continueButton.background = getDrawable(R.drawable.button_install)
        continueButton.backgroundTintList = ColorStateList.valueOf(primaryGreen)
        continueButton.setTextColor(textColor)
    }

}
