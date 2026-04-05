package app.hydra.manager

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.text.InputType
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.work.WorkManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.android.material.R as MaterialR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    private val languageSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                updateLanguageLabel()
                activity?.recreate()
            }
        }
    private var pendingBackupPayload: String? = null
    private val exportSettingsBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val payload = pendingBackupPayload ?: return@registerForActivityResult
            pendingBackupPayload = null
            if (uri == null) return@registerForActivityResult

            runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(payload)
                } ?: error("Could not open backup file.")
            }.onSuccess {
                AppSnackbar.show(
                    requireActivity().findViewById(R.id.rootLayout),
                    getString(R.string.settings_backup_export_done)
                )
            }.onFailure {
                AppSnackbar.show(
                    requireActivity().findViewById(R.id.rootLayout),
                    getString(R.string.settings_backup_export_failed)
                )
            }
        }
    private val importSettingsBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: error("Could not read backup file.")
            }.fold(
                onSuccess = { raw ->
                    SettingsBackupManager.importBackup(requireContext(), raw)
                    .onSuccess {
                        refreshSettingsUi()
                        AppSnackbar.show(
                            requireActivity().findViewById(R.id.rootLayout),
                            getString(R.string.settings_backup_import_done)
                        )
                        mainHandler.postDelayed({
                            if (!isAdded || view == null) return@postDelayed
                            activity?.recreate()
                        }, 250L)
                    }
                        .onFailure {
                            AppSnackbar.show(
                                requireActivity().findViewById(R.id.rootLayout),
                                getString(R.string.settings_backup_import_failed)
                            )
                        }
                },
                onFailure = {
                    AppSnackbar.show(
                        requireActivity().findViewById(R.id.rootLayout),
                        getString(R.string.settings_backup_import_failed)
                    )
                }
            )
        }

    companion object {
        private const val PRESS_SCALE = 0.985f
        private const val UPDATES_DEBUG_REFRESH_MS = 2000L
        private const val STATE_VISIBLE_SECTION = "settings_visible_section"
    }

    private enum class SettingsSection(val titleRes: Int) {
        GENERAL(R.string.settings_general_title),
        UPDATES(R.string.settings_updates_title),
        ADVANCE(R.string.settings_advance_title),
        ABOUT(R.string.settings_about_title)
    }

    private enum class UpdateActionState {
        CHECK,
        DOWNLOAD,
        INSTALL,
        DOWNLOADING,
        RESUME
    }

    private data class UpdateActionUi(
        val state: UpdateActionState,
        val labelRes: Int,
        val enabled: Boolean
    )

    private data class ThemeOptionViews(
        val optionView: View,
        val iconBackground: View,
        val iconView: ImageView,
        val labelView: TextView
    )

    private lateinit var themeOptions: Map<Int, ThemeOptionViews>
    private lateinit var headerTitle: TextView
    private lateinit var backButton: View
    private lateinit var sectionList: View
    private lateinit var generalSection: View
    private lateinit var generalSectionTab: View
    private lateinit var generalSectionIconBg: View
    private lateinit var updatesSection: View
    private lateinit var updatesSectionTab: View
    private lateinit var updatesSectionIconBg: View
    private lateinit var advanceSection: View
    private lateinit var advanceSectionTab: View
    private lateinit var advanceSectionIconBg: View
    private lateinit var aboutSection: View
    private lateinit var aboutSectionTab: View
    private lateinit var aboutSectionIconBg: View
    private lateinit var aboutIssueDivider: View
    private lateinit var aboutContributorsDivider: View
    private lateinit var aboutLicensesDivider: View
    private lateinit var updatesSummaryText: TextView
    private lateinit var updatesDebugText: TextView
    private lateinit var viewChangelogButton: Button
    private lateinit var checkForUpdatesButton: Button
    private lateinit var checkUpdatesOnLaunchSwitchTrack: FrameLayout
    private lateinit var checkUpdatesOnLaunchSwitchThumb: View
    private lateinit var showUpdateMessageSwitchTrack: FrameLayout
    private lateinit var showUpdateMessageSwitchThumb: View
    private lateinit var languageValue: TextView
    private lateinit var downloadNetworkValue: TextView
    private lateinit var dynamicColorSwitchTrack: FrameLayout
    private lateinit var dynamicColorSwitchThumb: View
    private lateinit var pureBlackSwitchTrack: FrameLayout
    private lateinit var pureBlackSwitchThumb: View
    private lateinit var pureBlackSummary: TextView
    private lateinit var batteryOptimizationValue: TextView
    private lateinit var backendUrlValue: TextView
    private lateinit var deviceInfoValue: TextView
    private lateinit var adsSupportSwitchTrack: FrameLayout
    private lateinit var adsSupportSwitchThumb: View
    private lateinit var updateNotificationsSwitchTrack: FrameLayout
    private lateinit var updateNotificationsSwitchThumb: View
    private lateinit var homeSortValue: TextView
    private lateinit var favoritesSortValue: TextView
    private lateinit var installedSortValue: TextView
    private lateinit var versionSortValue: TextView
    private lateinit var downloadSortValue: TextView
    private lateinit var aboutVersionText: TextView
    private var currentVisiblePanel: View? = null
    private var currentSection: SettingsSection? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updatesDebugRefreshRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || view == null) return
            updateUpdatesSummary()
            refreshUpdateActionButton()
            mainHandler.postDelayed(this, UPDATES_DEBUG_REFRESH_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(view)

        applyHeaderInsets(view)
        headerTitle = view.findViewById(R.id.headerTitle)
        backButton = view.findViewById(R.id.backButton)
        sectionList = view.findViewById(R.id.sectionList)
        generalSection = view.findViewById(R.id.generalSection)
        generalSectionTab = view.findViewById(R.id.generalSectionTab)
        generalSectionIconBg = view.findViewById(R.id.generalSectionIconBg)
        updatesSection = view.findViewById(R.id.updatesSection)
        updatesSectionTab = view.findViewById(R.id.updatesSectionTab)
        updatesSectionIconBg = view.findViewById(R.id.updatesSectionIconBg)
        advanceSection = view.findViewById(R.id.advanceSection)
        advanceSectionTab = view.findViewById(R.id.advanceSectionTab)
        advanceSectionIconBg = view.findViewById(R.id.advanceSectionIconBg)
        aboutSection = view.findViewById(R.id.aboutSection)
        aboutSectionTab = view.findViewById(R.id.aboutSectionTab)
        aboutSectionIconBg = view.findViewById(R.id.aboutSectionIconBg)
        aboutIssueDivider = view.findViewById(R.id.aboutIssueDivider)
        aboutContributorsDivider = view.findViewById(R.id.aboutContributorsDivider)
        aboutLicensesDivider = view.findViewById(R.id.aboutLicensesDivider)
        updatesSummaryText = view.findViewById(R.id.updatesSummaryText)
        updatesDebugText = view.findViewById(R.id.updatesDebugText)
        viewChangelogButton = view.findViewById(R.id.viewChangelogButton)
        checkForUpdatesButton = view.findViewById(R.id.checkForUpdatesButton)
        checkUpdatesOnLaunchSwitchTrack = view.findViewById(R.id.checkUpdatesOnLaunchSwitchTrack)
        checkUpdatesOnLaunchSwitchThumb = view.findViewById(R.id.checkUpdatesOnLaunchSwitchThumb)
        showUpdateMessageSwitchTrack = view.findViewById(R.id.showUpdateMessageSwitchTrack)
        showUpdateMessageSwitchThumb = view.findViewById(R.id.showUpdateMessageSwitchThumb)
        languageValue = view.findViewById(R.id.languageValue)
        downloadNetworkValue = view.findViewById(R.id.downloadNetworkValue)
        dynamicColorSwitchTrack = view.findViewById(R.id.dynamicColorSwitchTrack)
        dynamicColorSwitchThumb = view.findViewById(R.id.dynamicColorSwitchThumb)
        pureBlackSwitchTrack = view.findViewById(R.id.pureBlackSwitchTrack)
        pureBlackSwitchThumb = view.findViewById(R.id.pureBlackSwitchThumb)
        pureBlackSummary = view.findViewById(R.id.pureBlackSummary)
        backendUrlValue = view.findViewById(R.id.backendUrlValue)
        deviceInfoValue = view.findViewById(R.id.deviceInfoValue)
        adsSupportSwitchTrack = view.findViewById(R.id.adsSupportSwitchTrack)
        adsSupportSwitchThumb = view.findViewById(R.id.adsSupportSwitchThumb)
        updateNotificationsSwitchTrack = view.findViewById(R.id.updateNotificationsSwitchTrack)
        updateNotificationsSwitchThumb = view.findViewById(R.id.updateNotificationsSwitchThumb)
        aboutVersionText = view.findViewById(R.id.aboutVersionText)

        themeOptions = mapOf(
            R.id.themeOptionSystem to ThemeOptionViews(
                optionView = view.findViewById(R.id.themeOptionSystem),
                iconBackground = view.findViewById(R.id.themeIconSystemBg),
                iconView = view.findViewById(R.id.themeIconSystem),
                labelView = view.findViewById(R.id.themeLabelSystem)
            ),
            R.id.themeOptionLight to ThemeOptionViews(
                optionView = view.findViewById(R.id.themeOptionLight),
                iconBackground = view.findViewById(R.id.themeIconLightBg),
                iconView = view.findViewById(R.id.themeIconLight),
                labelView = view.findViewById(R.id.themeLabelLight)
            ),
            R.id.themeOptionDark to ThemeOptionViews(
                optionView = view.findViewById(R.id.themeOptionDark),
                iconBackground = view.findViewById(R.id.themeIconDarkBg),
                iconView = view.findViewById(R.id.themeIconDark),
                labelView = view.findViewById(R.id.themeLabelDark)
            )
        )
        homeSortValue = view.findViewById(R.id.homeSortValue)
        favoritesSortValue = view.findViewById(R.id.favoritesSortValue)
        installedSortValue = view.findViewById(R.id.installedSortValue)
        versionSortValue = view.findViewById(R.id.versionSortValue)
        downloadSortValue = view.findViewById(R.id.downloadSortValue)

        val prefs = requireContext().getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
        val savedMode = ThemePreferences.getSavedMode(requireContext())
        updateThemeSelection(ThemePreferences.modeToThemeOptionId(savedMode))
        updateLanguageLabel()
        updateDownloadNetworkLabel()
        updateAppearanceSwitches()
        updateSettingsSectionTabs()
        updateSettingsCardSurfaces(view)
        batteryOptimizationValue = view.findViewById(R.id.batteryOptimizationValue)
        updateBackendUrlLabel()
        updateBatteryOptimizationLabel()
        updateAdsSupportLabel()
        updateDeviceInfo()
        updateNotificationLabel()
        updateLaunchUpdateSwitches()
        updateUpdatesSummary()
        updateSortLabels()
        updateAboutVersion()
        val restoredSection = savedInstanceState
            ?.getString(STATE_VISIBLE_SECTION)
            ?.let { sectionName -> runCatching { SettingsSection.valueOf(sectionName) }.getOrNull() }
        if (restoredSection != null) {
            showSection(restoredSection)
        } else {
            showOverview()
        }

        backButton.setOnClickListener {
            showOverview()
        }

        view.findViewById<View>(R.id.generalSectionTab).setOnClickListener {
            showSection(SettingsSection.GENERAL)
        }
        view.findViewById<View>(R.id.updatesSectionTab).setOnClickListener {
            showSection(SettingsSection.UPDATES)
        }
        view.findViewById<View>(R.id.advanceSectionTab).setOnClickListener {
            showSection(SettingsSection.ADVANCE)
        }
        view.findViewById<View>(R.id.aboutSectionTab).setOnClickListener {
            showSection(SettingsSection.ABOUT)
        }

        themeOptions.forEach { (optionId, optionViews) ->
            optionViews.optionView.setOnClickListener {
                val mode = ThemePreferences.themeOptionIdToMode(optionId)
                if (AppCompatDelegate.getDefaultNightMode() == mode) return@setOnClickListener

                if (mode != AppCompatDelegate.MODE_NIGHT_YES &&
                    AppearancePreferences.isPureBlackEnabled(requireContext())
                ) {
                    AppearancePreferences.setPureBlackEnabled(requireContext(), false)
                }

                updateThemeSelection(optionId)
                prefs.edit().putInt(ThemePreferences.KEY_THEME, mode).apply()
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        view.findViewById<View>(R.id.homeSortRow).setOnClickListener {
            showAppSortDialog(
                title = getString(R.string.sort_home_dialog_title),
                currentValue = ListSortPreferences.getHomeSort(requireContext()),
                onSelected = {
                    ListSortPreferences.setHomeSort(requireContext(), it)
                    updateSortLabels()
                }
            )
        }

        view.findViewById<View>(R.id.downloadNetworkRow).setOnClickListener {
            showDownloadNetworkDialog()
        }
        view.findViewById<View>(R.id.dynamicColorRow).setOnClickListener {
            toggleDynamicColor()
        }
        view.findViewById<View>(R.id.pureBlackRow).setOnClickListener {
            togglePureBlackTheme()
        }
        view.findViewById<View>(R.id.backendUrlRow).setOnClickListener {
            showBackendUrlDialog()
        }
        view.findViewById<View>(R.id.backendHealthRow).setOnClickListener {
            showBackendHealthDialog()
        }
        view.findViewById<View>(R.id.batteryOptimizationRow).setOnClickListener {
            openBatteryOptimizationSettings()
        }
        view.findViewById<View>(R.id.adsSupportRow).setOnClickListener {
            toggleAdsSupport()
        }
        view.findViewById<View>(R.id.exportDebugLogsRow).setOnClickListener {
            exportDebugLogs()
        }
        view.findViewById<View>(R.id.exportSettingsBackupRow).setOnClickListener {
            exportSettingsBackup()
        }
        view.findViewById<View>(R.id.importSettingsBackupRow).setOnClickListener {
            importSettingsBackupLauncher.launch(arrayOf("application/json", "text/plain"))
        }
        view.findViewById<View>(R.id.languageRow).setOnClickListener {
            languageSelectionLauncher.launch(
                Intent(requireContext(), LanguageSelectionActivity::class.java)
            )
        }

        view.findViewById<View>(R.id.updateNotificationsRow).setOnClickListener {
            toggleUpdateNotifications()
        }
        view.findViewById<View>(R.id.checkUpdatesOnLaunchRow).setOnClickListener {
            toggleCheckUpdatesOnLaunch()
        }
        view.findViewById<View>(R.id.showUpdateMessageRow).setOnClickListener {
            toggleShowUpdateMessageOnLaunch()
        }
        viewChangelogButton.setOnClickListener {
            showUpdatesChangelogDialog()
        }
        refreshUpdateActionButton()

        view.findViewById<View>(R.id.favoritesSortRow).setOnClickListener {
            showAppSortDialog(
                title = getString(R.string.sort_favorites_dialog_title),
                currentValue = ListSortPreferences.getFavoritesSort(requireContext()),
                onSelected = {
                    ListSortPreferences.setFavoritesSort(requireContext(), it)
                    updateSortLabels()
                }
            )
        }

        view.findViewById<View>(R.id.installedSortRow).setOnClickListener {
            showAppSortDialog(
                title = getString(R.string.sort_installed_dialog_title),
                currentValue = ListSortPreferences.getInstalledSort(requireContext()),
                onSelected = {
                    ListSortPreferences.setInstalledSort(requireContext(), it)
                    updateSortLabels()
                }
            )
        }

        view.findViewById<View>(R.id.downloadSortRow).setOnClickListener {
            showDownloadSortDialog()
        }

        view.findViewById<View>(R.id.versionSortRow).setOnClickListener {
            showVersionSortDialog()
        }
        view.findViewById<View>(R.id.aboutWebsiteButton).setOnClickListener {
            openAboutLink("https://hydrv.app")
        }
        view.findViewById<View>(R.id.aboutDiscordButton).setOnClickListener {
            openAboutLink("https://discord.gg/VvE8shvV")
        }
        view.findViewById<View>(R.id.aboutDonateButton).setOnClickListener {
            openAboutLink("https://ko-fi.com/xc3fff0e")
        }
        view.findViewById<View>(R.id.aboutGithubButton).setOnClickListener {
            openAboutLink("https://github.com/Team-HYDRV/HYDRV")
        }
        view.findViewById<View>(R.id.aboutIssueRow).setOnClickListener {
            contactSupport(
                getString(R.string.about_issue_email_subject),
                getString(R.string.about_issue_email_body)
            )
        }
        view.findViewById<View>(R.id.aboutContributorsRow).setOnClickListener {
            startActivity(Intent(requireContext(), ContributorsActivity::class.java))
        }
        view.findViewById<View>(R.id.aboutLicensesRow).setOnClickListener {
            startActivity(Intent(requireContext(), LibrariesCreditsActivity::class.java))
        }

        listOf(
            R.id.generalSectionTab,
            R.id.updatesSectionTab,
            R.id.advanceSectionTab,
            R.id.aboutSectionTab,
            R.id.themeOptionSystem,
            R.id.themeOptionLight,
            R.id.themeOptionDark,
            R.id.languageRow,
            R.id.downloadNetworkRow,
            R.id.dynamicColorRow,
            R.id.pureBlackRow,
            R.id.updateNotificationsRow,
            R.id.homeSortRow,
            R.id.favoritesSortRow,
            R.id.installedSortRow,
            R.id.versionSortRow,
            R.id.downloadSortRow,
            R.id.checkUpdatesOnLaunchRow,
            R.id.showUpdateMessageRow,
            R.id.backendUrlRow,
            R.id.batteryOptimizationRow,
            R.id.adsSupportRow,
            R.id.backendHealthRow,
            R.id.exportDebugLogsRow,
            R.id.exportSettingsBackupRow,
            R.id.importSettingsBackupRow,
            R.id.aboutWebsiteButton,
            R.id.aboutDiscordButton,
            R.id.aboutDonateButton,
            R.id.aboutGithubButton,
            R.id.aboutIssueRow,
            R.id.aboutContributorsRow,
            R.id.aboutLicensesRow
        ).forEach { id ->
            attachPressAnimation(view.findViewById(id))
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DownloadRepository.downloadsLive.observe(viewLifecycleOwner, Observer {
            refreshUpdateActionButton()
        })
        refreshUpdateActionButton()
    }

    private fun showOverview() {
        currentSection = null
        animateHeaderTitle(getString(R.string.settings_title))
        backButton.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                backButton.visibility = View.GONE
            }
            .start()
        transitionTo(sectionList, currentVisiblePanel, reverse = true)
    }

    private fun showSection(section: SettingsSection) {
        currentSection = section
        val targetView = when (section) {
            SettingsSection.GENERAL -> generalSection
            SettingsSection.UPDATES -> updatesSection
            SettingsSection.ADVANCE -> advanceSection
            SettingsSection.ABOUT -> aboutSection
        }

        animateHeaderTitle(getString(section.titleRes))
        if (backButton.visibility != View.VISIBLE) {
            backButton.alpha = 0f
            backButton.visibility = View.VISIBLE
            backButton.animate()
                .alpha(1f)
                .setDuration(140)
                .start()
        }
        transitionTo(targetView, currentVisiblePanel, reverse = false)
    }

    private fun transitionTo(target: View, current: View?, reverse: Boolean) {
        if (current === target) return

        val distance = if (reverse) -32f else 32f

        current?.animate()?.cancel()
        target.animate().cancel()

        if (current != null) {
            current.animate()
                .alpha(0f)
                .translationX(-distance / 2f)
                .setDuration(100)
                .withEndAction {
                    current.visibility = View.GONE
                    current.alpha = 1f
                    current.translationX = 0f
                }
                .start()
        } else {
            listOf(sectionList, generalSection, updatesSection, advanceSection, aboutSection)
                .filter { it !== target }
                .forEach {
                    it.visibility = View.GONE
                    it.alpha = 1f
                    it.translationX = 0f
                }
        }

        target.visibility = View.VISIBLE
        target.alpha = 0f
        target.translationX = distance
        target.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(120)
            .start()
        currentVisiblePanel = target
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentSection?.name?.let { outState.putString(STATE_VISIBLE_SECTION, it) }
    }

    private fun animateHeaderTitle(title: String) {
        if (headerTitle.text == title) return

        headerTitle.animate()
            .alpha(0f)
            .setDuration(90)
            .withEndAction {
                headerTitle.text = title
                headerTitle.translationY = 8f
                headerTitle.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(140)
                    .start()
            }
            .start()
    }

    private fun updateThemeSelection(selectedOptionId: Int) {
        themeOptions.forEach { (optionId, optionViews) ->
            val isSelected = optionId == selectedOptionId
            optionViews.iconBackground.setBackgroundResource(
                if (AppearancePreferences.isDynamicColorEnabled(requireContext())) {
                    if (isSelected) {
                        R.drawable.theme_option_icon_bg_selected_material
                    } else {
                        R.drawable.theme_option_icon_bg_material
                    }
                } else {
                    if (isSelected) {
                        R.drawable.theme_option_icon_bg_selected
                    } else {
                        R.drawable.theme_option_icon_bg
                    }
                }
            )
            optionViews.iconView.imageTintList =
                android.content.res.ColorStateList.valueOf(
                ThemeColors.color(
                    requireContext(),
                    if (isSelected) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurfaceVariant,
                    if (isSelected) R.color.accent else R.color.subtext
                )
            )
            optionViews.labelView.setTextColor(
                ThemeColors.color(
                    requireContext(),
                    if (isSelected) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnBackground,
                    if (isSelected) R.color.accent else R.color.text
                )
            )
            optionViews.labelView.alpha = if (isSelected) 1f else 0.85f
        }
    }

    private fun updateSettingsSectionTabs() {
        if (!AppearancePreferences.isDynamicColorEnabled(requireContext())) return
        listOf(
            generalSectionTab to generalSectionIconBg,
            updatesSectionTab to updatesSectionIconBg,
            advanceSectionTab to advanceSectionIconBg,
            aboutSectionTab to aboutSectionIconBg
        ).forEach { (section, iconBg) ->
            section.setBackgroundResource(R.drawable.card_material)
            iconBg.setBackgroundResource(R.drawable.settings_section_icon_bg_material)
        }
    }

    private fun updateSettingsCardSurfaces(root: View) {
        if (!AppearancePreferences.isDynamicColorEnabled(requireContext())) return
        listOf(
            R.id.themeOptionsCard,
            R.id.dynamicColorRow,
            R.id.pureBlackRow,
            R.id.languageRow,
            R.id.downloadNetworkRow,
            R.id.updateNotificationsRow,
            R.id.homeSortRow,
            R.id.favoritesSortRow,
            R.id.installedSortRow,
            R.id.versionSortRow,
            R.id.downloadSortRow,
            R.id.checkUpdatesOnLaunchRow,
            R.id.showUpdateMessageRow,
            R.id.adsSupportRow,
            R.id.batteryOptimizationRow,
            R.id.backendUrlRow,
            R.id.backendHealthRow,
            R.id.exportDebugLogsRow,
            R.id.exportSettingsBackupRow,
            R.id.importSettingsBackupRow,
            R.id.generalDeviceInfoCard,
            R.id.aboutLinksCard,
            R.id.aboutIssuesCard,
            R.id.aboutWebsiteButton,
            R.id.aboutDiscordButton,
            R.id.aboutDonateButton,
            R.id.aboutGithubButton,
            R.id.aboutIssueRow,
            R.id.aboutContributorsRow,
            R.id.aboutLicensesRow
        ).forEach { id ->
            root.findViewById<View>(id)?.setBackgroundResource(R.drawable.card_material)
        }

        listOf(
            aboutIssueDivider,
            aboutContributorsDivider,
            aboutLicensesDivider
        ).forEach { divider ->
            divider.visibility = View.VISIBLE
            divider.setBackgroundResource(R.drawable.about_list_divider_material)
        }
    }

    private fun updateSortLabels() {
        homeSortValue.text = ListSortPreferences.homeSortLabel(
            requireContext(),
            ListSortPreferences.getHomeSort(requireContext())
        )
        favoritesSortValue.text = ListSortPreferences.homeSortLabel(
            requireContext(),
            ListSortPreferences.getFavoritesSort(requireContext())
        )
        installedSortValue.text = ListSortPreferences.homeSortLabel(
            requireContext(),
            ListSortPreferences.getInstalledSort(requireContext())
        )
        versionSortValue.text = ListSortPreferences.versionSortLabel(
            requireContext(),
            ListSortPreferences.getVersionSort(requireContext())
        )
        downloadSortValue.text = ListSortPreferences.downloadSortLabel(
            requireContext(),
            ListSortPreferences.getDownloadSort(requireContext())
        )
    }

    private fun updateDownloadNetworkLabel() {
        downloadNetworkValue.text = DownloadNetworkPreferences.label(
            requireContext(),
            DownloadNetworkPreferences.getMode(requireContext())
        )
    }

    private fun updateLanguageLabel() {
        languageValue.text = LanguagePreferences.label(
            requireContext(),
            LanguagePreferences.getLanguage(requireContext())
        )
    }

    private fun updateNotificationLabel() {
        val enabled = NotificationPreferences.areUpdateNotificationsEnabled(requireContext())
        updateToggle(updateNotificationsSwitchTrack, updateNotificationsSwitchThumb, enabled)
    }

    private fun updateBackendUrlLabel() {
        backendUrlValue.text = if (BackendPreferences.isUsingDefault(requireContext())) {
            getString(R.string.backend_default_label)
        } else {
            getString(R.string.backend_custom_label)
        }
    }

    private fun updateBatteryOptimizationLabel() {
        val context = requireContext()
        batteryOptimizationValue.text = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> {
                getString(R.string.battery_optimization_not_needed)
            }

            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true -> {
                getString(R.string.battery_optimization_unrestricted)
            }

            else -> getString(R.string.battery_optimization_optimized)
        }
    }

    private fun updateAppearanceSwitches() {
        val context = requireContext()
        updateToggle(
            dynamicColorSwitchTrack,
            dynamicColorSwitchThumb,
            AppearancePreferences.isDynamicColorEnabled(context)
        )
        updateToggle(
            pureBlackSwitchTrack,
            pureBlackSwitchThumb,
            AppearancePreferences.isPureBlackEnabled(context)
        )
        updatePureBlackSummary(context)
    }

    private fun updatePureBlackSummary(context: android.content.Context) {
        pureBlackSummary.text = if (AppearancePreferences.isPureBlackActive(context)) {
            getString(R.string.pure_black_summary_active)
        } else {
            getString(R.string.pure_black_summary)
        }
    }

    private fun updateAdsSupportLabel() {
        updateToggle(
            adsSupportSwitchTrack,
            adsSupportSwitchThumb,
            AdsPreferences.areRewardedAdsEnabled(requireContext())
        )
    }

    private fun updateDeviceInfo() {
        val context = requireContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "1.0.1"
        val versionCode = packageInfo.versionCodeCompat()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val buildType = if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "debug"
        } else {
            "release"
        }
        val supportedAbis = Build.SUPPORTED_ABIS
            ?.filter { it.isNotBlank() }
            ?.joinToString(", ")
            .orEmpty()
            .ifBlank { getString(R.string.device_info_unknown) }
        val memoryClass = activityManager?.memoryClass ?: 0
        val largeMemoryClass = activityManager?.largeMemoryClass ?: memoryClass

        deviceInfoValue.text = buildString {
            appendLine(getString(R.string.device_info_version_format, versionName, versionCode))
            appendLine(getString(R.string.device_info_build_type_format, buildType))
            appendLine(getString(R.string.device_info_model_format, Build.MODEL ?: getString(R.string.device_info_unknown)))
            appendLine(
                getString(
                    R.string.device_info_android_version_format,
                    Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                    Build.VERSION.SDK_INT
                )
            )
            appendLine(getString(R.string.device_info_archs_format, supportedAbis))
            append(
                getString(
                    R.string.device_info_memory_format,
                    memoryClass,
                    largeMemoryClass
                )
            )
        }
    }

    private fun toggleUpdateNotifications() {
        val context = requireContext()
        val enabled = !NotificationPreferences.areUpdateNotificationsEnabled(context)
        NotificationPreferences.setUpdateNotificationsEnabled(context, enabled)
        updateNotificationLabel()
    }

    private fun toggleAdsSupport() {
        val context = requireContext()
        val enabled = !AdsPreferences.areRewardedAdsEnabled(context)
        AdsPreferences.setRewardedAdsEnabled(context, enabled)
        updateAdsSupportLabel()
    }

    private fun toggleDynamicColor() {
        val context = requireContext()
        AppearancePreferences.setDynamicColorEnabled(
            context,
            !AppearancePreferences.isDynamicColorEnabled(context)
        )
        updateAppearanceSwitches()
        activity?.recreate()
    }

    private fun togglePureBlackTheme() {
        val context = requireContext()
        val enabled = !AppearancePreferences.isPureBlackEnabled(context)
        AppearancePreferences.setPureBlackEnabled(
            context,
            enabled
        )

        if (enabled && !isDarkThemeEffective(context)) {
            updateThemeSelection(R.id.themeOptionDark)
            val prefs = context.getSharedPreferences(ThemePreferences.PREFS_NAME, 0)
            prefs.edit()
                .putInt(ThemePreferences.KEY_THEME, AppCompatDelegate.MODE_NIGHT_YES)
                .apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            return
        }

        updateAppearanceSwitches()
        activity?.recreate()
    }

    private fun isDarkThemeEffective(context: android.content.Context): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun openBatteryOptimizationSettings() {
        val activity = activity ?: return
        val context = requireContext()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            AppSnackbar.show(
                activity.findViewById(R.id.rootLayout),
                getString(R.string.battery_optimization_not_needed_message)
            )
            return
        }

        val packageUri = Uri.parse("package:${context.packageName}")
        val powerManager = context.getSystemService(PowerManager::class.java)
        val intent = if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = packageUri
            }
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::batteryOptimizationValue.isInitialized) {
            updateBatteryOptimizationLabel()
        }
        refreshSettingsUi()
        mainHandler.removeCallbacks(updatesDebugRefreshRunnable)
        mainHandler.postDelayed(updatesDebugRefreshRunnable, UPDATES_DEBUG_REFRESH_MS)
    }

    override fun onPause() {
        mainHandler.removeCallbacks(updatesDebugRefreshRunnable)
        super.onPause()
    }

    private fun exportDebugLogs() {
        val context = requireContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "1.0.1"
        val versionCode = packageInfo.versionCodeCompat()
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val buildType = if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            "debug"
        } else {
            "release"
        }
        val supportedAbis = Build.SUPPORTED_ABIS
            ?.filter { it.isNotBlank() }
            ?.joinToString(", ")
            .orEmpty()
            .ifBlank { getString(R.string.device_info_unknown) }
        val memoryClass = activityManager?.memoryClass ?: 0
        val largeMemoryClass = activityManager?.largeMemoryClass ?: memoryClass

        val report = buildString {
            appendLine(getString(R.string.debug_report_title))
            appendLine()
            appendLine(getString(R.string.device_info_version_format, versionName, versionCode))
            appendLine(getString(R.string.device_info_build_type_format, buildType))
            appendLine(getString(R.string.device_info_model_format, Build.MODEL ?: getString(R.string.device_info_unknown)))
            appendLine(
                getString(
                    R.string.device_info_android_version_format,
                    Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                    Build.VERSION.SDK_INT
                )
            )
            appendLine(getString(R.string.device_info_archs_format, supportedAbis))
            appendLine(getString(R.string.device_info_memory_format, memoryClass, largeMemoryClass))
            appendLine()
            appendLine(getString(R.string.backend_title) + ": " + backendUrlValue.text)
            appendLine(
                getString(R.string.ads_support_title) + ": " +
                    if (AdsPreferences.areRewardedAdsEnabled(context)) {
                        getString(R.string.debug_enabled)
                    } else {
                        getString(R.string.debug_disabled)
                    }
            )
            val diagnostics = AppDiagnostics.read(context)
            if (diagnostics.isNotBlank()) {
                appendLine()
                appendLine(getString(R.string.debug_recent_diagnostics))
                appendLine(diagnostics)
            }
        }

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.debug_share_subject))
                    putExtra(Intent.EXTRA_TEXT, report)
                },
                getString(R.string.debug_share_chooser)
            )
        )
    }

    private fun updateLaunchUpdateSwitches() {
        val context = requireContext()
        val checkOnLaunch = UpdatePreferences.isCheckOnLaunchEnabled(context)
        val showMessage = UpdatePreferences.isLaunchMessageEnabled(context)

        updateToggle(checkUpdatesOnLaunchSwitchTrack, checkUpdatesOnLaunchSwitchThumb, checkOnLaunch)
        updateToggle(showUpdateMessageSwitchTrack, showUpdateMessageSwitchThumb, showMessage)
    }

    private fun toggleCheckUpdatesOnLaunch() {
        val context = requireContext()
        val enabled = !UpdatePreferences.isCheckOnLaunchEnabled(context)
        UpdatePreferences.setCheckOnLaunchEnabled(context, enabled)
        updateLaunchUpdateSwitches()
    }

    private fun toggleShowUpdateMessageOnLaunch() {
        val context = requireContext()
        val enabled = !UpdatePreferences.isLaunchMessageEnabled(context)
        UpdatePreferences.setLaunchMessageEnabled(context, enabled)
        updateLaunchUpdateSwitches()
    }

    private fun updateUpdatesSummary() {
        val context = requireContext()
        val checkedAt = ReleaseUpdateState.getLastCheckedAt(context)
        val lastSeenTag = ReleaseUpdateState.getLastSeenTag(context)
        val lastNotifiedTag = ReleaseUpdateState.getLastNotifiedTag(context)
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
        val formattedCheck = if (checkedAt > 0L) {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(checkedAt))
        } else {
            getString(R.string.updates_never_checked)
        }
        updatesSummaryText.text = getString(R.string.updates_summary_format, versionName, formattedCheck)
        updatesDebugText.text = buildString {
            append(getString(R.string.updates_debug_last_checked_format, formattedCheck))
            append('\n')
            append(getString(R.string.updates_debug_last_seen_release_format, lastSeenTag.ifBlank { getString(R.string.updates_release_unavailable) }))
            append('\n')
            append(getString(R.string.updates_debug_last_notified_release_format, lastNotifiedTag.ifBlank { getString(R.string.updates_release_unavailable) }))
            append('\n')
            append(getString(R.string.updates_debug_notifications_format, if (NotificationPreferences.areUpdateNotificationsEnabled(context)) getString(R.string.debug_enabled) else getString(R.string.debug_disabled)))
        }
    }

    private fun refreshSettingsUi() {
        updateLanguageLabel()
        updateDownloadNetworkLabel()
        updateAppearanceSwitches()
        updateBackendUrlLabel()
        updateBatteryOptimizationLabel()
        updateAdsSupportLabel()
        updateNotificationLabel()
        updateLaunchUpdateSwitches()
        updateUpdatesSummary()
        refreshUpdateActionButton()
        updateSortLabels()
        updateAboutVersion()
    }

    private fun exportSettingsBackup() {
        pendingBackupPayload = SettingsBackupManager.exportBackup(requireContext())
        exportSettingsBackupLauncher.launch("hydrv-settings-backup.json")
    }

    private fun showBackendHealthDialog() {
        val appContext = context?.applicationContext ?: return
        val uiContext = context ?: return
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backend_health_title))
            .setMessage(getString(R.string.backend_health_loading))
            .setPositiveButton(R.string.about_dialog_close, null)
            .show()

        Thread {
            val checkedAt = AppUpdateState.getLastCheckedAt(appContext)
            val formattedCheck = if (checkedAt > 0L) {
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(checkedAt))
            } else {
                uiContext.getString(R.string.updates_never_checked)
            }
            val cacheResult = AppCatalogService.readCachedApps(appContext)
            val cacheSummary = when {
                cacheResult?.isSuccess == true -> {
                    val count = cacheResult.getOrNull()?.apps?.size ?: 0
                    uiContext.getString(R.string.backend_health_cache_ready_count, count)
                }
                else -> uiContext.getString(R.string.backend_health_cache_missing)
            }
            val isDefaultBackend = BackendPreferences.isUsingDefault(appContext)
            val summary = buildString {
                append(
                    uiContext.getString(
                        R.string.backend_health_backend_mode_format,
                        if (isDefaultBackend) {
                            uiContext.getString(R.string.backend_default_label)
                        } else {
                            uiContext.getString(R.string.backend_custom_label)
                        }
                    )
                )
                append('\n')
                append('\n')
                append(uiContext.getString(R.string.backend_health_last_checked_format, formattedCheck))
                append('\n')
                append(uiContext.getString(R.string.backend_health_last_seen_hash_format, AppUpdateState.getLastSeenHash(appContext)))
                append('\n')
                append(uiContext.getString(R.string.backend_health_last_notified_hash_format, AppUpdateState.getLastNotifiedHash(appContext)))
                append('\n')
                append(uiContext.getString(R.string.backend_health_cache_format, cacheSummary))
                append('\n')
                append(
                    uiContext.getString(
                        R.string.backend_health_notifications_format,
                        if (NotificationPreferences.areUpdateNotificationsEnabled(appContext)) {
                            uiContext.getString(R.string.debug_enabled)
                        } else {
                            uiContext.getString(R.string.debug_disabled)
                        }
                    )
                )
                append('\n')
                append(
                    uiContext.getString(
                        R.string.backend_health_ads_enabled_format,
                        if (AdsPreferences.areRewardedAdsEnabled(appContext)) {
                            uiContext.getString(R.string.debug_enabled)
                        } else {
                            uiContext.getString(R.string.debug_disabled)
                        }
                    )
                )
                append('\n')
                append(
                    uiContext.getString(
                        R.string.backend_health_ads_status_format,
                        RewardedAdManager.runtimeAvailabilityReason()
                    )
                )
                append("\n\n")
                append(uiContext.getString(R.string.backend_health_worker_status_title))
                append('\n')
                append(UpdateWorkScheduler.workerSummary(appContext))
            }
            mainHandler.post {
                if (!isAdded || view == null || !dialog.isShowing) return@post
                    dialog.setMessage(summary)
            }
        }.start()
    }

    private fun updateAboutVersion() {
        val context = requireContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "1.0.1"
        val versionCode = packageInfo.versionCodeCompat()
        aboutVersionText.text = getString(
            R.string.about_version_code_format,
            versionName,
            versionCode
        )
    }

    private fun openAboutLink(url: String) {
        val activity = activity ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            AppSnackbar.show(activity.findViewById(R.id.rootLayout), getString(R.string.about_link_placeholder))
        }
    }

    private fun showAboutPlaceholder() {
        val activity = activity ?: return
        AppSnackbar.show(activity.findViewById(R.id.rootLayout), getString(R.string.about_issue_placeholder))
    }

    private fun contactSupport(subject: String? = null, body: String? = null) {
        val activity = activity ?: return
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${getString(R.string.about_contact_email)}")
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }

        try {
            startActivity(emailIntent)
        } catch (_: ActivityNotFoundException) {
            AppSnackbar.show(activity.findViewById(R.id.rootLayout), getString(R.string.about_link_placeholder))
        }
    }

    private fun showUpdatesChangelogDialog() {
        val context = requireContext()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.updates_changelog_title)
            .setMessage(R.string.updates_changelog_loading)
            .setPositiveButton(R.string.about_dialog_close, null)
            .show()

        GitHubRepository.fetchLatestRelease { result ->
            result.onSuccess { release ->
                if (!isAdded) return@onSuccess
                val body = release.body?.trim().orEmpty()
                val message = SpannableStringBuilder().apply {
                    appendLine(release.displayLabel())
                    appendLine(release.htmlUrl)
                    appendLine()
                    append(
                        if (body.isNotBlank()) {
                            formatReleaseNotesBody(body)
                        } else {
                            getString(R.string.release_details_unavailable)
                        }
                    )
                }
                if (dialog.isShowing) {
                    dialog.setMessage(message)
                }
            }.onFailure {
                if (!isAdded) return@onFailure
                val changelog = runCatching {
                    context.resources.openRawResource(R.raw.changelog).bufferedReader().use { it.readText() }
                }.getOrNull()

                if (!changelog.isNullOrBlank()) {
                    val latestSection = extractLatestChangelogSection(changelog)
                    if (dialog.isShowing) {
                        dialog.setMessage(formatReleaseNotesBody(latestSection))
                    }
                } else if (dialog.isShowing) {
                    dialog.setMessage(getString(R.string.updates_changelog_body))
                }
            }
        }
    }

    private fun extractLatestChangelogSection(changelog: String): String {
        val lines = changelog.lineSequence().toList()
        val startIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed.startsWith("## [") && !trimmed.contains("Unreleased", ignoreCase = true)
        }

        if (startIndex == -1) return changelog

        val endIndex = ((startIndex + 1) until lines.size).firstOrNull { idx ->
            lines[idx].trim().startsWith("## [")
        } ?: lines.size

        return lines.subList(startIndex, endIndex).joinToString("\n").trim()
    }

    private fun formatReleaseNotesBody(markdown: String): CharSequence {
        val html = StringBuilder()
        var inList = false

        fun closeList() {
            if (inList) {
                html.append("</ul>")
                inList = false
            }
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> {
                    closeList()
                    html.append("<br>")
                }
                line.startsWith("### ") -> {
                    closeList()
                    html.append("<b>")
                        .append(TextUtils.htmlEncode(line.removePrefix("### ").trim()))
                        .append("</b><br>")
                }
                line.startsWith("## ") -> {
                    closeList()
                    html.append("<b>")
                        .append(TextUtils.htmlEncode(line.removePrefix("## ").trim()))
                        .append("</b><br><br>")
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    if (!inList) {
                        html.append("<ul>")
                        inList = true
                    }
                    html.append("<li>")
                        .append(TextUtils.htmlEncode(line.drop(2).trim()))
                        .append("</li>")
                }
                else -> {
                    closeList()
                    html.append(TextUtils.htmlEncode(line))
                        .append("<br>")
                }
            }
        }

        closeList()

        return HtmlCompat.fromHtml(
            html.toString(),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun runManualUpdateCheck() {
        val activity = activity ?: return
        checkForUpdatesButton.isEnabled = false
        checkForUpdatesButton.text = getString(R.string.updates_checking)

        UpdateCheckManager.runCheck(requireContext()) { result ->
            if (!isAdded) return@runCheck

            checkForUpdatesButton.isEnabled = true
            checkForUpdatesButton.text = getString(R.string.updates_check_now)

            val message = if (result == null) {
                getString(R.string.updates_check_failed)
            } else {
                ReleaseUpdateState.setLastCheckedAt(requireContext(), result.checkedAt)
                updateUpdatesSummary()
                refreshUpdateActionButton()
                if (result.hasChanges) {
                    if (!result.latestReleaseName.isNullOrBlank()) {
                        AppSnackbar.show(
                            activity.findViewById(R.id.rootLayout),
                            getString(
                                R.string.updates_check_live_with_version,
                                getString(R.string.app_name),
                                result.latestReleaseName
                            ),
                            anchorTarget = activity.findViewById(R.id.bottomNav),
                            baseBottomMarginDp = 10,
                            actionLabel = getString(R.string.updates_open_release)
                        ) {
                            val url = result.latestReleaseUrl?.trim().orEmpty()
                            if (url.isNotBlank()) {
                                openAboutLink(url)
                            }
                        }
                        return@runCheck
                    } else {
                        getString(R.string.updates_check_live_generic)
                    }
                } else {
                    getString(R.string.updates_check_up_to_date)
                }
            }

            refreshUpdateActionButton()
            AppSnackbar.show(
                activity.findViewById(R.id.rootLayout),
                message,
                anchorTarget = activity.findViewById(R.id.bottomNav),
                baseBottomMarginDp = 10
            )
        }
    }

    private fun getCurrentVersionName(context: android.content.Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.1"
        } catch (_: Exception) {
            "1.0.1"
        }
    }

    private fun refreshUpdateActionButton() {
        if (!this::checkForUpdatesButton.isInitialized || !isAdded) return
        val context = context ?: return
        val action = resolveUpdateActionState(context)

        checkForUpdatesButton.isEnabled = action.enabled
        checkForUpdatesButton.text = getString(action.labelRes)
        checkForUpdatesButton.setOnClickListener {
            when (action.state) {
                UpdateActionState.CHECK -> runManualUpdateCheck()
                UpdateActionState.DOWNLOAD -> startLatestReleaseDownload()
                UpdateActionState.INSTALL -> installLatestReleaseApk()
                UpdateActionState.DOWNLOADING -> Unit
                UpdateActionState.RESUME -> resumeLatestReleaseDownload()
            }
        }
    }

    private fun resolveUpdateActionState(context: android.content.Context): UpdateActionUi {
        val latestTag = ReleaseUpdateState.getLastSeenTag(context).trim()
        val currentVersion = getCurrentVersionName(context)
        val updateAvailable = latestTag.isNotBlank() &&
            ReleaseVersionComparator.isNewer(currentVersion, latestTag)

        if (!updateAvailable) {
            return UpdateActionUi(UpdateActionState.CHECK, R.string.updates_check_now, true)
        }

        val downloadItem = latestReleaseDownloadItem(latestTag)
        if (downloadItem == null) {
            return UpdateActionUi(UpdateActionState.DOWNLOAD, R.string.updates_download_now, true)
        }

        val file = downloadItem.filePath.takeIf { it.isNotBlank() }?.let(::File)
        val fileExists = file?.exists() == true && file.length() > 0L

        return when {
            downloadItem.status == "Downloading" -> {
                UpdateActionUi(UpdateActionState.DOWNLOADING, R.string.updates_downloading, false)
            }

            downloadItem.status == "Paused" -> {
                UpdateActionUi(UpdateActionState.RESUME, R.string.updates_resume_download, true)
            }

            downloadItem.status == "Done" && fileExists -> {
                UpdateActionUi(UpdateActionState.INSTALL, R.string.updates_install_now, true)
            }

            fileExists -> {
                UpdateActionUi(UpdateActionState.INSTALL, R.string.updates_install_now, true)
            }

            else -> {
                UpdateActionUi(UpdateActionState.DOWNLOAD, R.string.updates_download_now, true)
            }
        }
    }

    private fun latestReleaseDownloadItem(latestTag: String): DownloadItem? {
        return DownloadRepository.downloads.lastOrNull { item ->
            item.url == RuntimeConfig.githubLatestReleaseApkUrl &&
                item.versionName.trim() == latestTag
        }
    }

    private fun startLatestReleaseDownload() {
        val activity = activity ?: return
        val context = context ?: return
        val latestTag = ReleaseUpdateState.getLastSeenTag(context).trim()
        if (latestTag.isBlank()) {
            runManualUpdateCheck()
            return
        }

        val existing = latestReleaseDownloadItem(latestTag)
        if (existing != null) {
            val file = existing.filePath.takeIf { it.isNotBlank() }?.let(::File)
            val fileExists = file?.exists() == true && file.length() > 0L
            when {
                existing.status == "Downloading" -> {
                    refreshUpdateActionButton()
                    return
                }

                existing.status == "Paused" -> {
                    resumeLatestReleaseDownload()
                    return
                }

                existing.status == "Done" && fileExists -> {
                    installLatestReleaseApk()
                    return
                }

                !fileExists || existing.status == "Failed" || existing.status == "Done" -> {
                    DownloadRepository.delete(context, existing)
                }
            }
        }

        val result = DownloadRepository.startDownload(
            context,
            DownloadItem(
                name = getString(R.string.app_name),
                url = RuntimeConfig.githubLatestReleaseApkUrl,
                versionName = latestTag
            )
        )

        refreshUpdateActionButton()
        if (result != DownloadRepository.StartResult.STARTED) {
            val message = DownloadRepository.startResultMessage(context, result)
            if (!message.isNullOrBlank()) {
                AppSnackbar.show(activity.findViewById(R.id.rootLayout), message)
            }
        }
    }

    private fun resumeLatestReleaseDownload() {
        val activity = activity ?: return
        val context = context ?: return
        val latestTag = ReleaseUpdateState.getLastSeenTag(context).trim()
        val item = latestReleaseDownloadItem(latestTag) ?: return
        val result = DownloadRepository.resume(context, item)

        refreshUpdateActionButton()
        if (result != DownloadRepository.StartResult.STARTED) {
            val message = DownloadRepository.startResultMessage(context, result)
            if (!message.isNullOrBlank()) {
                AppSnackbar.show(activity.findViewById(R.id.rootLayout), message)
            }
        }
    }

    private fun installLatestReleaseApk() {
        val context = context ?: return
        val latestTag = ReleaseUpdateState.getLastSeenTag(context).trim()
        val item = latestReleaseDownloadItem(latestTag) ?: run {
            refreshUpdateActionButton()
            return
        }

        val file = item.filePath.takeIf { it.isNotBlank() }?.let(::File)
        if (file?.exists() != true) {
            refreshUpdateActionButton()
            return
        }

        InstallSessionManager.installApk(
            context,
            file.absolutePath,
            getString(R.string.app_name),
            context.packageName
        )
        refreshUpdateActionButton()
    }

    private fun updateToggle(track: FrameLayout, thumb: View, enabled: Boolean) {
        val useMaterial = AppearancePreferences.isDynamicColorEnabled(requireContext())
        track.setBackgroundResource(
            if (useMaterial) {
                if (enabled) {
                    R.drawable.settings_toggle_track_on_material
                } else {
                    R.drawable.settings_toggle_track_off_material
                }
            } else {
                if (enabled) {
                    R.drawable.settings_toggle_track_on
                } else {
                    R.drawable.settings_toggle_track_off
                }
            }
        )
        thumb.setBackgroundResource(
            if (useMaterial) {
                if (enabled) {
                    R.drawable.settings_toggle_thumb_on_material
                } else {
                    R.drawable.settings_toggle_thumb_off_material
                }
            } else {
                if (enabled) {
                    R.drawable.settings_toggle_thumb_on
                } else {
                    R.drawable.settings_toggle_thumb_off
                }
            }
        )
        thumb.animate()
            .translationX(if (enabled) 20f.dpToPx() else 0f)
            .setDuration(150)
            .start()
    }

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun attachPressAnimation(target: View) {
        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(PRESS_SCALE)
                        .scaleY(PRESS_SCALE)
                        .alpha(0.94f)
                        .setDuration(85)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(130)
                        .setInterpolator(OvershootInterpolator(0.75f))
                        .start()
                }
            }
            false
        }
    }

    private fun showAppSortDialog(
        title: String,
        currentValue: String,
        onSelected: (String) -> Unit
    ) {
        val options = arrayOf("Name A-Z", "Name Z-A", "Newest first", "Oldest first")
        val values = arrayOf(
            ListSortPreferences.HOME_SORT_NAME_ASC,
            ListSortPreferences.HOME_SORT_NAME_DESC,
            ListSortPreferences.HOME_SORT_NEWEST,
            ListSortPreferences.HOME_SORT_OLDEST
        )
        val checkedIndex = values.indexOf(currentValue).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                onSelected(values[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showDownloadSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_name_asc),
            getString(R.string.sort_name_desc),
            getString(R.string.sort_date_newest),
            getString(R.string.sort_date_oldest)
        )
        val values = arrayOf(
            ListSortPreferences.DOWNLOAD_SORT_NAME_ASC,
            ListSortPreferences.DOWNLOAD_SORT_NAME_DESC,
            ListSortPreferences.DOWNLOAD_SORT_NEWEST,
            ListSortPreferences.DOWNLOAD_SORT_OLDEST
        )
        val current = ListSortPreferences.getDownloadSort(requireContext())
        val checkedIndex = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sort_downloads_dialog_title))
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                ListSortPreferences.setDownloadSort(requireContext(), values[which])
                updateSortLabels()
                dialog.dismiss()
            }
            .show()
    }

    private fun showVersionSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_newest_first),
            getString(R.string.sort_oldest_first)
        )
        val values = arrayOf(
            ListSortPreferences.VERSION_SORT_NEWEST,
            ListSortPreferences.VERSION_SORT_OLDEST
        )
        val current = ListSortPreferences.getVersionSort(requireContext())
        val checkedIndex = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sort_versions_dialog_title))
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                ListSortPreferences.setVersionSort(requireContext(), values[which])
                updateSortLabels()
                dialog.dismiss()
            }
            .show()
    }

    private fun showDownloadNetworkDialog() {
        val options = arrayOf(
            getString(R.string.option_wifi_only),
            getString(R.string.option_wifi_or_mobile)
        )
        val values = arrayOf(
            DownloadNetworkPreferences.WIFI_ONLY,
            DownloadNetworkPreferences.WIFI_OR_MOBILE
        )
        val current = DownloadNetworkPreferences.getMode(requireContext())
        val checkedIndex = values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.download_via_title))
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                DownloadNetworkPreferences.setMode(requireContext(), values[which])
                updateDownloadNetworkLabel()
                dialog.dismiss()
            }
            .show()
    }

    private fun showBackendUrlDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = ""
            setTextColor(
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorOnBackground,
                    R.color.text
                )
            )
            setHintTextColor(
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    R.color.subtext
                )
            )
            setBackgroundResource(R.drawable.dialog_input_background)
            setPadding(28, 20, 28, 20)
        }
        val inputContainer = FrameLayout(context).apply {
            val horizontal = (20 * resources.displayMetrics.density).toInt()
            val top = (8 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, top, horizontal, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.backend_dialog_title))
            .setMessage(getString(R.string.backend_dialog_message))
            .setView(inputContainer)
            .setPositiveButton(getString(R.string.save_label), null)
            .setNeutralButton(getString(R.string.reset_label)) { dialog, _ ->
                BackendPreferences.setCatalogUrl(context, "")
                updateBackendUrlLabel()
                AppSnackbar.show(
                    requireActivity().findViewById(R.id.rootLayout),
                    getString(R.string.backend_reset_message)
                )
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.downloads_cancel), null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val rawUrl = input.text?.toString().orEmpty().trim()
            if (rawUrl.isBlank()) {
                BackendPreferences.setCatalogUrl(context, "")
                updateBackendUrlLabel()
                AppSnackbar.show(
                    requireActivity().findViewById(R.id.rootLayout),
                    getString(R.string.backend_reset_message)
                )
                dialog.dismiss()
                return@setOnClickListener
            }

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).isEnabled = false
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = false
            dialog.setMessage(getString(R.string.backend_validating_message))

            val rootView = activity?.findViewById<View>(R.id.rootLayout)
            Thread {
                val validation = AppCatalogService.validateCatalogEndpointSync(rawUrl)
                mainHandler.post {
                    if (!isAdded || view == null || !dialog.isShowing) return@post
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    dialog.getButton(DialogInterface.BUTTON_NEUTRAL).isEnabled = true
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = true

                    validation.onSuccess {
                        input.error = null
                        BackendPreferences.setCatalogUrl(context, rawUrl)
                        updateBackendUrlLabel()
                        if (rootView != null) {
                            AppSnackbar.show(
                                rootView,
                                getString(R.string.backend_saved_message)
                            )
                        }
                        dialog.dismiss()
                    }.onFailure {
                        input.error = getString(R.string.backend_invalid_message)
                        if (rootView != null) {
                            AppSnackbar.show(
                                rootView,
                                getString(R.string.backend_invalid_message)
                            )
                        }
                    }
                }
            }.start()
        }
    }

    private fun applyHeaderInsets(view: View) {
        val header = view.findViewById<View>(R.id.headerContainer) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (!header.isAttachedToWindow) return@setOnApplyWindowInsetsListener insets

            header.setPadding(
                header.paddingLeft,
                statusBar,
                header.paddingRight,
                header.paddingBottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(view)
    }
}
