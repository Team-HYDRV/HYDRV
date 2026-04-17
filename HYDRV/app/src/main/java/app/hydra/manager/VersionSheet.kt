package app.hydra.manager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Dialog
import android.net.Uri
import android.provider.Settings
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
class VersionSheet(
    private val app: AppModel,
    private val preferOpenInstalledAction: Boolean = false,
    private val installedVersionCodeHint: Int? = null,
    private val installedVersionNameHint: String? = null
) : BottomSheetDialogFragment() {

    private enum class VersionButtonAction {
        DOWNLOAD,
        INSTALL,
        OPEN
    }

    companion object {
        private const val DONE_HOLD_MS = 260L
        private const val RESET_DELAY_MS = 1200L
        private const val PRESS_SCALE = 0.975f
        private const val SHEET_SNACKBAR_GAP_DP = 6
        private const val NAV_SNACKBAR_GAP_DP = 10
        private const val MAX_SHEET_HEIGHT_RATIO = 0.5f
        private const val MAX_SINGLE_VERSION_SHEET_HEIGHT_RATIO = 0.58f
    }

    private data class DownloadButtonViews(
        val actionRow: View,
        val container: FrameLayout,
        val track: FrameLayout,
        val fill: ImageView,
        val label: TextView,
        val icon: ImageView,
        val uninstallButton: View
    )

    private data class VersionHintViews(
        val installedHint: TextView,
        val downloadedHint: TextView,
        val actionRow: View
    )

    private val buttonViewsByKey = mutableMapOf<String, DownloadButtonViews>()
    private val completedKeys = mutableSetOf<String>()
    private val doneHandledKeys = mutableSetOf<String>()
    private val lastKnownStatuses = mutableMapOf<String, String>()
    private val visualProgressByKey = mutableMapOf<String, Int>()
    private val activeSessionKeys = mutableSetOf<String>()
    private val failedKeys = mutableSetOf<String>()
    private val resetRunnables = mutableMapOf<String, Runnable>()
    private var versionsByKey = emptyMap<String, Version>()
    private var hintViewsByKey = emptyMap<String, VersionHintViews>()
    private var currentLatestVersion: Version? = null
    private var currentInstallSnapshot: InstallIntelligence.Snapshot? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentSnackbar: Snackbar? = null
    private var isCurrentSnackbarSheetBound = false
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var versionScrollListener: RecyclerView.OnScrollListener? = null
    private var installSnapshotRunnable: Runnable? = null
    private var installEventObserverStartedAt = 0L
    private var lastInstallSnapshotRefreshAt = 0L
    private var maxSheetHeightPx = 0
    private var hasScrollableVersionContent = false
    private var versionListBasePaddingBottom = 0
    private var scrollHintAnimator: ObjectAnimator? = null
    private var currentApp: AppModel = app
    private var installedLaunchPackage: String? = null
    private lateinit var rootView: View
    private lateinit var appNameView: TextView
    private lateinit var sheetSummaryView: TextView
    private lateinit var versionList: RecyclerView
    private lateinit var scrollHintContainer: View
    private lateinit var scrollHintPill: View
    private lateinit var scrollHintText: TextView
    private lateinit var scrollHintIcon: ImageView
    private var installedVersionName: String? = null
    private var installedVersionCode: Int? = null
    private val versionAdapter = VersionAdapter()

    private val versionDiff = object : DiffUtil.ItemCallback<Version>() {
        override fun areItemsTheSame(oldItem: Version, newItem: Version): Boolean {
            return versionKey(oldItem) == versionKey(newItem)
        }

        override fun areContentsTheSame(oldItem: Version, newItem: Version): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.BottomSheetTheme).apply {
            dismissWithAnimation = true
        }
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog ?: return
        val context = requireContext()
        val sheetBackgroundRes = sheetBackgroundRes(context)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.decorView?.setBackgroundColor(Color.TRANSPARENT)

        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val screenHeight = resources.displayMetrics.heightPixels
        maxSheetHeightPx = (screenHeight * MAX_SHEET_HEIGHT_RATIO).toInt()

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isDraggable = true
        behavior.skipCollapsed = true
        behavior.isHideable = true

        bottomSheetCallback?.let(behavior::removeBottomSheetCallback)
        val callback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheetView: View, newState: Int) {
                updateSheetSnackbarPosition(bottomSheetView)
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    if (isCurrentSnackbarSheetBound) {
                        currentSnackbar?.dismiss()
                    }
                    currentSnackbar = null
                    isCurrentSnackbarSheetBound = false
                }
            }

            override fun onSlide(bottomSheetView: View, slideOffset: Float) {
                updateSheetSnackbarPosition(bottomSheetView)
            }
        }
        bottomSheetCallback = callback
        behavior.addBottomSheetCallback(callback)
        updateSheetSnackbarPosition(bottomSheet)

        bottomSheet.setBackgroundResource(sheetBackgroundRes)
        (bottomSheet.parent as? View)?.apply {
            setBackgroundColor(Color.TRANSPARENT)
            background = null
        }

        bottomSheet.post {
            if (!isAdded || view == null) return@post
            adjustSheetHeight()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.bottom_sheet_versions, container, false)
        rootView = view
        view.setBackgroundResource(sheetBackgroundRes(requireContext()))

        versionList = view.findViewById(R.id.versionList)
        scrollHintContainer = view.findViewById(R.id.scrollHintContainer)
        scrollHintPill = view.findViewById(R.id.scrollHintPill)
        scrollHintText = view.findViewById(R.id.scrollHintText)
        scrollHintIcon = view.findViewById(R.id.scrollHintIcon)
        appNameView = view.findViewById(R.id.appName)
        sheetSummaryView = view.findViewById(R.id.sheetSummary)
        versionListBasePaddingBottom = versionList.paddingBottom
        view.findViewById<View>(R.id.versionListContainer)?.setBackgroundColor(
            sheetSurfaceColor(requireContext())
        )
        val useDynamicColor = AppearancePreferences.isDynamicColorEnabled(requireContext())
        scrollHintPill.setBackgroundResource(
            if (useDynamicColor) {
                R.drawable.version_badge_latest_material
            } else {
                R.drawable.version_badge_latest_brand
            }
        )
        val scrollHintTint = if (useDynamicColor) {
            ThemeColors.color(
                requireContext(),
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                R.color.text_on_accent_chip
            )
        } else {
            ContextCompat.getColor(requireContext(), R.color.text_on_accent_chip)
        }
        scrollHintText.setTextColor(scrollHintTint)
        scrollHintIcon.imageTintList = ColorStateList.valueOf(scrollHintTint)
        if (AdsPreferences.areRewardedAdsEnabled(requireContext())) {
            RewardedAdManager.preload(requireContext())
        } else {
            RewardedAdManager.clear()
        }

        versionList.layoutManager = LinearLayoutManager(requireContext())
        versionList.adapter = versionAdapter
        versionList.itemAnimator = null
        versionScrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateScrollHint(hasScrollableVersionContent && shouldShowScrollHint(recyclerView))
            }
        }
        versionScrollListener?.let(versionList::addOnScrollListener)

        renderVersions()

        CatalogStateCenter.apps.observe(viewLifecycleOwner) { apps ->
            val updatedApp = apps.firstOrNull {
                it.packageName == currentApp.packageName ||
                    it.name.equals(currentApp.name, ignoreCase = true)
            } ?: return@observe

            if (updatedApp == currentApp) return@observe

            currentApp = updatedApp
            renderVersions()
        }

        return view
    }

    private fun sheetSurfaceColor(context: android.content.Context): Int {
        return when {
            AppearancePreferences.isPureBlackActive(context) -> Color.BLACK
            AppearancePreferences.isDynamicColorEnabled(context) -> ThemeColors.color(
                context,
                com.google.android.material.R.attr.colorSurface,
                R.color.bg
            )
            else -> ContextCompat.getColor(context, R.color.bg)
        }
    }

    private fun sheetBackgroundRes(context: android.content.Context): Int {
        return when {
            AppearancePreferences.isPureBlackActive(context) -> R.drawable.bottom_sheet_bg_pure_black
            AppearancePreferences.isDynamicColorEnabled(context) -> R.drawable.bottom_sheet_bg
            else -> R.drawable.bottom_sheet_bg_brand
        }
    }

    private fun renderVersions() {
        val ctx = requireContext()
        DownloadRepository.pruneStaleCompleted(ctx)
        refreshInstalledInfo(ctx)
        currentInstallSnapshot = initialInstallSnapshot()
        val sortMode = ListSortPreferences.getVersionSort(ctx)
        val sortedVersions = ListSortPreferences.sortVersions(sortMode, currentApp.versions)
        val displayedVersions = displayedVersions(sortedVersions)
        val downloadsSnapshot = DownloadRepository.snapshotDownloads()
        versionsByKey = displayedVersions.associateBy(::versionKey)
        currentLatestVersion = sortedVersions.firstOrNull()
        val latestVersionNumber = currentLatestVersion?.version

        appNameView.text = currentApp.name.cleanDuplicateSuffix()
        sheetSummaryView.text =
            ctx.getString(
                R.string.versions_summary_format,
                displayedVersions.size,
                ListSortPreferences.versionSortLabel(ctx, sortMode)
            )

        buttonViewsByKey.keys.toList().forEach { key ->
            cancelReset(key)
        }
        buttonViewsByKey.clear()
        hintViewsByKey = emptyMap()
        completedKeys.clear()
        doneHandledKeys.clear()
        lastKnownStatuses.clear()
        visualProgressByKey.clear()
        activeSessionKeys.clear()
        failedKeys.clear()

        if (displayedVersions.isEmpty()) {
            currentInstallSnapshot = null
            versionAdapter.submitVersions(emptyList(), latestVersionNumber)
            updateDownloadButtons(downloadsSnapshot)
            refreshVersionHints()
            versionList.post { adjustSheetHeight() }
            return
        }

        versionAdapter.submitVersions(displayedVersions, latestVersionNumber)
        updateDownloadButtons(downloadsSnapshot)
        versionList.post { adjustSheetHeight() }

        Thread {
            val snapshot = InstallIntelligence.snapshot(ctx, currentApp)
            mainHandler.post {
                if (!isAdded || view == null) return@post
                currentInstallSnapshot = snapshot
                refreshVersionHints()
                adjustSheetHeight()
            }
        }.start()
    }

    private fun applyVersionBadgePalette(badge: TextView) {
        val context = badge.context
        badge.setBackgroundResource(
            if (AppearancePreferences.isDynamicColorEnabled(context)) {
                R.drawable.version_badge_latest_material
            } else {
                R.drawable.version_badge_latest_brand
            }
        )
        badge.setTextColor(
            if (AppearancePreferences.isDynamicColorEnabled(context)) {
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorOnPrimaryContainer,
                    R.color.text_on_accent_chip
                )
            } else {
                Color.BLACK
            }
        )
    }

    private fun bindVersionCard(
        card: View,
        ctx: android.content.Context,
        version: Version,
        latestVersionNumber: Int?,
        key: String
    ) {
        card.setBackgroundResource(
            if (AppearancePreferences.isDynamicColorEnabled(ctx)) {
                R.drawable.version_sheet_card_material
            } else {
                R.drawable.version_sheet_card_brand
            }
        )

        val title = card.findViewById<TextView>(R.id.versionTitle)
        val changelog = card.findViewById<TextView>(R.id.versionChangelog)
        val badge = card.findViewById<TextView>(R.id.versionBadge)
        val sourceBadge = card.findViewById<TextView>(R.id.versionSourceBadge)
        val sourceHost = card.findViewById<TextView>(R.id.versionSourceHost)
        val installedHint = card.findViewById<TextView>(R.id.versionInstalledHint)
        val downloadedHint = card.findViewById<TextView>(R.id.versionDownloadedHint)
        val actionRow = card.findViewById<View>(R.id.versionActionRow)
        val button = card.findViewById<FrameLayout>(R.id.versionDownloadButton)
        val buttonTrack = card.findViewById<FrameLayout>(R.id.versionDownloadTrack)
        val buttonFill = card.findViewById<ImageView>(R.id.versionDownloadFill)
        val buttonLabel = card.findViewById<TextView>(R.id.versionDownloadLabel)
        val buttonIcon = card.findViewById<ImageView>(R.id.versionDownloadIcon)
        val uninstallButton = card.findViewById<View>(R.id.versionUninstallButton)
        title.text = ctx.getString(
            R.string.version_format,
            version.displayVersionName()
        )
        badge.visibility = if (version.version == latestVersionNumber) View.VISIBLE else View.GONE

        val changelogText = if (version.changelog == getString(R.string.release_details_unavailable)) {
            version.changelog
        } else {
            formatChangelogForDisplay(version.changelog)
        }
        if (changelogText.isNullOrBlank()) {
            changelog.visibility = View.GONE
        } else {
            changelog.visibility = View.VISIBLE
            changelog.text = changelogText
        }
        val sourceLabel = version.downloadSourceLabel(BackendPreferences.isUsingDefault(ctx))
        val sourceHostText = version.downloadHost()
        val unknownSourceText = getString(R.string.version_source_unknown)
        applyVersionBadgePalette(badge)
        applySourceBadgePalette(sourceBadge, BackendPreferences.isUsingDefault(ctx))
        sourceBadge.text = sourceLabel
        if (
            sourceHostText.isNullOrBlank() ||
            sourceLabel == unknownSourceText ||
            sourceHostText.equals(unknownSourceText, ignoreCase = true)
        ) {
            sourceHost.text = ""
            sourceHost.visibility = View.GONE
        } else {
            sourceHost.text = sourceHostText
            sourceHost.visibility = View.VISIBLE
        }

        val snapshot = currentInstallSnapshot
        if (snapshot != null) {
            val insight = InstallIntelligence.insight(
                ctx,
                currentApp,
                version,
                snapshot,
                currentLatestVersion
            )
            installedHint.text = insight.installedHint.orEmpty()
            installedHint.visibility = if (insight.installedHint.isNullOrBlank()) View.GONE else View.VISIBLE
            applyInstalledHintPalette(installedHint, insight)
            downloadedHint.text = insight.downloadHint.orEmpty()
            downloadedHint.visibility = if (insight.downloadHint.isNullOrBlank()) View.GONE else View.VISIBLE
        } else {
            installedHint.text = ""
            installedHint.visibility = View.GONE
            downloadedHint.text = ""
            downloadedHint.visibility = View.GONE
        }
        updateActionButtonSpacing(
            actionRow = actionRow,
            hasInstalledHint = installedHint.visibility == View.VISIBLE,
            hasDownloadedHint = downloadedHint.visibility == View.VISIBLE
        )
        hintViewsByKey = hintViewsByKey + (key to VersionHintViews(installedHint, downloadedHint, actionRow))

        buttonViewsByKey[key] = DownloadButtonViews(
            actionRow = actionRow,
            container = button,
            track = buttonTrack,
            fill = buttonFill,
            label = buttonLabel,
            icon = buttonIcon,
            uninstallButton = uninstallButton
        )
        applyVersionButtonPalette(buttonViewsByKey.getValue(key), ctx)
        attachPressAnimation(button)
        attachPressAnimation(uninstallButton)

        button.setOnClickListener {
            val activity = activity ?: return@setOnClickListener
            val item = DownloadRepository.snapshotDownloads()
                .lastOrNull { versionKey(it.versionName, it.url, it.versionCode) == key }
            when (resolveButtonAction(version, item)) {
                VersionButtonAction.OPEN -> {
                    openInstalledApp()
                }

                VersionButtonAction.INSTALL -> {
                    installDownloadedVersion(item)
                }

                VersionButtonAction.DOWNLOAD -> {
                    if (!AdsPreferences.areRewardedAdsEnabled(ctx) || RewardedAdManager.shouldBypassRewardGate()) {
                        runVersionDownload(
                            version = version,
                            key = key,
                            context = ctx,
                            rootView = rootView
                        )
                        return@setOnClickListener
                    }
                    RewardedAdManager.showThen(
                        activity = activity,
                        onRewardEarned = {
                            runVersionDownload(
                                version = version,
                                key = key,
                                context = ctx,
                                rootView = rootView
                            )
                        },
                        onAdUnavailable = {
                            runVersionDownload(
                                version = version,
                                key = key,
                                context = ctx,
                                rootView = rootView
                            )
                        },
                        onAdDismissedWithoutReward = {
                            val sheetAnchor = (dialog as? BottomSheetDialog)?.findViewById<View>(
                                com.google.android.material.R.id.design_bottom_sheet
                            ) ?: rootView
                            val snackbarHost = (sheetAnchor.parent as? View) ?: sheetAnchor
                            currentSnackbar?.dismiss()
                            currentSnackbar = AppSnackbar.show(
                                snackbarHost,
                                getString(R.string.rewarded_ad_required_message),
                                anchorTarget = sheetAnchor,
                                baseBottomMarginDp = 6
                            )
                            rootView.post {
                                if (!isAdded || view == null) return@post
                                val bottomSheetView = (dialog as? BottomSheetDialog)?.findViewById<View>(
                                    com.google.android.material.R.id.design_bottom_sheet
                                )
                                if (bottomSheetView != null) {
                                    updateSheetSnackbarPosition(bottomSheetView)
                                }
                            }
                        }
                    )
                }
            }
        }
        uninstallButton.setOnClickListener {
            launchInstalledAppUninstall()
        }

        updateDownloadButtons(DownloadRepository.snapshotDownloads())
    }

    private fun applySourceBadgePalette(badge: TextView, isOfficialBackend: Boolean) {
        val context = badge.context
        val background = if (isOfficialBackend) {
            if (AppearancePreferences.isDynamicColorEnabled(context)) {
                R.drawable.version_badge_latest_material
            } else {
                R.drawable.version_badge_latest_brand
            }
        } else {
            if (AppearancePreferences.isDynamicColorEnabled(context)) {
                R.drawable.version_badge_custom_material
            } else {
                R.drawable.version_badge_custom
            }
        }
        badge.setBackgroundResource(background)
        badge.setTextColor(
            if (AppearancePreferences.isDynamicColorEnabled(context)) {
                if (isOfficialBackend) {
                    ThemeColors.color(
                        context,
                        com.google.android.material.R.attr.colorOnPrimaryContainer,
                        R.color.text_on_accent_chip
                    )
                } else {
                    ThemeColors.color(
                        context,
                        com.google.android.material.R.attr.colorOnErrorContainer,
                        R.color.text_on_accent_chip
                    )
                }
            } else {
                if (isOfficialBackend) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
            }
        )
    }

    private fun applyInstalledHintPalette(
        hintView: TextView,
        insight: InstallIntelligence.Insight
    ) {
        val context = hintView.context
        val isWarningHint =
            insight.installedHint == context.getString(R.string.version_installed_newer_than_catalog_hint)
        val textColor = if (isWarningHint) {
            if (AppearancePreferences.isDynamicColorEnabled(context)) {
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorOnErrorContainer,
                    R.color.red
                )
            } else {
                ContextCompat.getColor(context, R.color.red)
            }
        } else {
            if (AppearancePreferences.isDynamicColorEnabled(context)) {
                ThemeColors.color(
                    context,
                    androidx.appcompat.R.attr.colorPrimary,
                    R.color.accent
                )
            } else {
                ContextCompat.getColor(context, R.color.accent)
            }
        }
        hintView.setTextColor(textColor)
    }

    private fun updateActionButtonSpacing(
        actionRow: View,
        hasInstalledHint: Boolean,
        hasDownloadedHint: Boolean
    ) {
        val params = actionRow.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val density = actionRow.resources.displayMetrics.density
        val targetTopMarginDp = when {
            hasInstalledHint && hasDownloadedHint -> 20
            hasInstalledHint || hasDownloadedHint -> 17
            else -> 14
        }
        val targetTopMarginPx = (targetTopMarginDp * density).toInt()
        if (params.topMargin == targetTopMarginPx) return
        params.topMargin = targetTopMarginPx
        actionRow.layoutParams = params
    }

    private fun clearVersionView(key: String) {
        buttonViewsByKey.remove(key)?.let { releaseLiquidDrawable(it.fill) }
        hintViewsByKey = hintViewsByKey - key
    }

    private inner class VersionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var boundKey: String? = null
        val title: TextView = itemView.findViewById(R.id.versionTitle)
        val changelog: TextView = itemView.findViewById(R.id.versionChangelog)
        val badge: TextView = itemView.findViewById(R.id.versionBadge)
        val sourceBadge: TextView = itemView.findViewById(R.id.versionSourceBadge)
        val sourceHost: TextView = itemView.findViewById(R.id.versionSourceHost)
        val installedHint: TextView = itemView.findViewById(R.id.versionInstalledHint)
        val downloadedHint: TextView = itemView.findViewById(R.id.versionDownloadedHint)
        val button: FrameLayout = itemView.findViewById(R.id.versionDownloadButton)
        val buttonTrack: FrameLayout = itemView.findViewById(R.id.versionDownloadTrack)
        val buttonFill: ImageView = itemView.findViewById(R.id.versionDownloadFill)
        val buttonLabel: TextView = itemView.findViewById(R.id.versionDownloadLabel)
        val buttonIcon: ImageView = itemView.findViewById(R.id.versionDownloadIcon)
    }

    private inner class VersionAdapter : ListAdapter<Version, VersionViewHolder>(versionDiff) {
        private var latestVersionNumber: Int? = null

        init {
            setHasStableIds(true)
        }

        fun submitVersions(versions: List<Version>, latest: Int?) {
            latestVersionNumber = latest
            submitList(versions)
        }

        override fun getItemId(position: Int): Long {
            return versionKey(getItem(position)).hashCode().toLong()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_version_sheet, parent, false)
            return VersionViewHolder(view)
        }

        override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
            val version = getItem(position)
            val key = versionKey(version)
            if (holder.boundKey != null && holder.boundKey != key) {
                clearVersionView(holder.boundKey!!)
            }
            holder.boundKey = key
            bindVersionCard(
                card = holder.itemView,
                ctx = holder.itemView.context,
                version = version,
                latestVersionNumber = latestVersionNumber,
                key = key
            )
        }

        override fun onViewRecycled(holder: VersionViewHolder) {
            holder.boundKey?.let { clearVersionView(it) }
            holder.boundKey = null
            super.onViewRecycled(holder)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        installEventObserverStartedAt = System.currentTimeMillis()
        DownloadRepository.downloadsLive.observe(viewLifecycleOwner) { downloads ->
            updateDownloadButtons(downloads)
            scheduleInstallSnapshotRefresh()
            refreshVersionHints()
        }

        InstallStatusCenter.events.observe(viewLifecycleOwner) { event ->
            if (event.token < installEventObserverStartedAt) return@observe
            val hadTrustedInstall = installedLaunchPackage != null
            if (event.refreshInstalledState) {
                scheduleInstallSnapshotRefresh()
            }
            if (hadTrustedInstall) {
                AppStateCacheManager.forceRefreshInstalledPackages(requireContext()) {
                    if (!isAdded) return@forceRefreshInstalledPackages
                    refreshInstalledInfo(requireContext())
                    val stillTrustedInstalled = AppIdentityStore.isTrustedInstalled(
                        requireContext(),
                        currentApp.packageName,
                        currentApp.name
                    )
                    if (!stillTrustedInstalled) {
                        if (preferOpenInstalledAction) {
                            requestAnimatedDismiss()
                        } else {
                            updateDownloadButtons(DownloadRepository.snapshotDownloads())
                            refreshVersionHints()
                            if (event.message.isNotBlank()) {
                                showSheetSnackbar(event.message, event.indefinite)
                            }
                        }
                        return@forceRefreshInstalledPackages
                    }
                    updateDownloadButtons(DownloadRepository.snapshotDownloads())
                    refreshVersionHints()
                    if (event.message.isNotBlank()) {
                        showSheetSnackbar(event.message, event.indefinite)
                    }
                }
                return@observe
            }
            if (event.message.isBlank()) return@observe
            showSheetSnackbar(event.message, event.indefinite)
        }
    }

    private fun requestAnimatedDismiss() {
        val bottomSheet = (dialog as? BottomSheetDialog)?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        val behavior = bottomSheet?.let { BottomSheetBehavior.from(it) }
        if (behavior != null) {
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else {
            dismiss()
        }
    }

    private fun showSheetSnackbar(message: String, indefinite: Boolean) {
        val sheetAnchor = (dialog as? BottomSheetDialog)?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: rootView
        val snackbarHost = (sheetAnchor.parent as? View) ?: sheetAnchor
        currentSnackbar?.dismiss()
        isCurrentSnackbarSheetBound = true
        currentSnackbar = AppSnackbar.show(
            snackbarHost,
            message,
            if (indefinite) Snackbar.LENGTH_INDEFINITE else Snackbar.LENGTH_LONG,
            anchorTarget = sheetAnchor,
            baseBottomMarginDp = 6
        )
        if (!indefinite) {
            currentSnackbar?.duration = 6500
        }
        rootView.post {
            if (!isAdded) return@post
            val bottomSheetView = (dialog as? BottomSheetDialog)?.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheetView != null) {
                updateSheetSnackbarPosition(bottomSheetView)
            }
        }
    }

    private fun scheduleInstallSnapshotRefresh() {
        if (!this::versionList.isInitialized) return
        val ctx = context?.applicationContext ?: return
        val now = System.currentTimeMillis()
        val elapsed = now - lastInstallSnapshotRefreshAt
        val delayMs = (220L - elapsed).coerceAtLeast(0L)

        if (delayMs == 0L) {
            installSnapshotRunnable?.let(mainHandler::removeCallbacks)
            installSnapshotRunnable = null
            runInstallSnapshotRefresh(ctx)
            return
        }

        if (installSnapshotRunnable != null) return

        val runnable = Runnable {
            installSnapshotRunnable = null
            runInstallSnapshotRefresh(ctx)
        }
        installSnapshotRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun runInstallSnapshotRefresh(ctx: android.content.Context) {
        lastInstallSnapshotRefreshAt = System.currentTimeMillis()
        Thread {
            val snapshot = InstallIntelligence.snapshot(ctx, currentApp)
            mainHandler.post {
                if (!isAdded || view == null) return@post
                val previousInstalledVersionName = installedVersionName
                val previousInstalledVersionCode = installedVersionCode
                refreshInstalledInfo(ctx)
                if (
                    preferOpenInstalledAction &&
                    (previousInstalledVersionName != installedVersionName ||
                        previousInstalledVersionCode != installedVersionCode)
                ) {
                    renderVersions()
                    return@post
                }
                currentInstallSnapshot = snapshot
                updateDownloadButtons(DownloadRepository.snapshotDownloads())
                refreshVersionHints()
                adjustSheetHeight()
            }
        }.start()
    }

    private fun displayedVersions(sortedVersions: List<Version>): List<Version> {
        if (!preferOpenInstalledAction) return sortedVersions

        val targetInstalledVersionCode = installedVersionCodeHint?.takeIf { it > 0 } ?: installedVersionCode
        val targetInstalledVersionName = installedVersionNameHint?.trim()?.takeIf { it.isNotEmpty() }
            ?: installedVersionName
        val installedOnly = sortedVersions.filter { version ->
            val versionCodeMatches = targetInstalledVersionCode != null &&
                targetInstalledVersionCode > 0 &&
                version.version == targetInstalledVersionCode
            val versionNameMatches = !targetInstalledVersionName.isNullOrBlank() &&
                version.version_name.trim() == targetInstalledVersionName
            versionCodeMatches || versionNameMatches
        }
        return installedOnly.take(1)
    }

    private fun initialInstallSnapshot(): InstallIntelligence.Snapshot? {
        val targetInstalledVersionName = installedVersionNameHint?.trim()?.takeIf { it.isNotEmpty() }
            ?: installedVersionName
        val targetInstalledVersionCode = installedVersionCodeHint?.takeIf { it > 0 }
            ?: installedVersionCode
        val downloadedByVersionKey = linkedMapOf<String, InstallIntelligence.DownloadedArchive>()

        DownloadRepository.snapshotDownloads()
            .asReversed()
            .filter {
                val looksCompleted =
                    it.status == "Done" ||
                        it.progress >= 100 ||
                        it.completedAt > 0L
                looksCompleted && it.versionName.isNotBlank()
            }
            .forEach { item ->
                val versionCode = item.versionCode.takeIf { it > 0 } ?: return@forEach
                val archiveKey = "${item.versionName.trim()}|$versionCode"
                if (downloadedByVersionKey.containsKey(archiveKey)) return@forEach
                downloadedByVersionKey[archiveKey] = InstallIntelligence.DownloadedArchive(
                    versionCode = versionCode,
                    versionName = item.versionName.trim(),
                    packageName = item.packageName.trim(),
                    signatures = emptySet()
                )
            }

        if (
            targetInstalledVersionName.isNullOrBlank() &&
            (targetInstalledVersionCode == null || targetInstalledVersionCode <= 0) &&
            downloadedByVersionKey.isEmpty()
        ) {
            return null
        }

        return InstallIntelligence.Snapshot(
            installedVersionName = targetInstalledVersionName,
            installedVersionCode = targetInstalledVersionCode,
            downloadedByVersionKey = downloadedByVersionKey,
            packageMismatchVersionKeys = emptySet(),
            signatureMismatchVersionKeys = emptySet()
        )
    }

    private fun runVersionDownload(
        version: Version,
        key: String,
        context: android.content.Context,
        rootView: View
    ) {
        val result = DownloadRepository.startDownload(
            context,
            DownloadItem(
                name = currentApp.name,
                url = version.url,
                packageName = currentApp.packageName,
                versionName = version.version_name,
                versionCode = version.version
            )
        )

        val message = if (result == DownloadRepository.StartResult.STARTED) {
            failedKeys.remove(key)
            completedKeys.remove(key)
            doneHandledKeys.remove(key)
            visualProgressByKey[key] = 0
            cancelReset(key)
            activeSessionKeys.add(key)
            lastKnownStatuses[key] = "Downloading"
            applyProgressState(buttonViewsByKey.getValue(key), 0, "0%")
            getString(
                R.string.added_to_downloads_format,
                currentApp.name.cleanDuplicateSuffix(),
                version.displayVersionName()
            )
        } else {
            if (result == DownloadRepository.StartResult.INVALID_URL) {
                failedKeys.add(key)
                visualProgressByKey.remove(key)
                activeSessionKeys.remove(key)
                completedKeys.remove(key)
                doneHandledKeys.remove(key)
                cancelReset(key)
                buttonViewsByKey[key]?.let(::applyErrorState)
            }
            DownloadRepository.startResultMessage(context, result)
                ?: DownloadNetworkPolicy.blockedMessage(context)
        }

        val sheetAnchor = (dialog as? BottomSheetDialog)?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: rootView
        val snackbarHost = (sheetAnchor.parent as? View) ?: sheetAnchor
        currentSnackbar?.dismiss()
        currentSnackbar = AppSnackbar.show(
            snackbarHost,
            message,
            anchorTarget = sheetAnchor,
            baseBottomMarginDp = 6
        )
        rootView.post {
            if (!isAdded || view == null) return@post
            val bottomSheetView = (dialog as? BottomSheetDialog)?.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheetView != null) {
                updateSheetSnackbarPosition(bottomSheetView)
            }
        }
    }

    override fun onDestroyView() {
        val sheet = (dialog as? BottomSheetDialog)?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        val behavior = sheet?.let { BottomSheetBehavior.from(it) }
        bottomSheetCallback?.let { callback ->
            behavior?.removeBottomSheetCallback(callback)
        }
        bottomSheetCallback = null
        if (isCurrentSnackbarSheetBound) {
            currentSnackbar?.dismiss()
        }
        currentSnackbar = null
        isCurrentSnackbarSheetBound = false
        installSnapshotRunnable?.let(mainHandler::removeCallbacks)
        installSnapshotRunnable = null
        lastInstallSnapshotRefreshAt = 0L
        versionScrollListener?.let { listener ->
            if (this::versionList.isInitialized) {
                versionList.removeOnScrollListener(listener)
            }
        }
        versionScrollListener = null
        stopScrollHintAnimation()
        buttonViewsByKey.values.forEach { releaseLiquidDrawable(it.fill) }
        resetRunnables.values.forEach(mainHandler::removeCallbacks)
        resetRunnables.clear()
        versionsByKey = emptyMap()
        hintViewsByKey = emptyMap()
        currentLatestVersion = null
        currentInstallSnapshot = null
        buttonViewsByKey.clear()
        completedKeys.clear()
        doneHandledKeys.clear()
        lastKnownStatuses.clear()
        visualProgressByKey.clear()
        activeSessionKeys.clear()
        failedKeys.clear()
        super.onDestroyView()
    }

    private fun updateDownloadButtons(downloads: List<DownloadItem>) {
        val relevantDownloads = downloads
            .asSequence()
            .filter { downloadPackageKey(it) == currentApp.packageName }
            .associateBy { versionKey(it.versionName, it.url, it.versionCode) }

        if (buttonViewsByKey.isEmpty()) return

        buttonViewsByKey.forEach { (key, views) ->
            val context = views.container.context
            val version = versionsByKey[key]
            val item = relevantDownloads[key]
            val visualProgress = visualProgressByKey[key] ?: 0

            when {
                item == null -> {
                    if (failedKeys.contains(key)) {
                    applyErrorState(views)
                    return@forEach
                    }
                    val lastStatus = lastKnownStatuses[key].orEmpty()
                    val shouldPreserveVisualState =
                        hasActiveVisualState(key) &&
                            (lastStatus == "Downloading" || lastStatus == "Paused" || lastStatus == "Stopped")
                    if (shouldPreserveVisualState) {
                        applyProgressState(
                            views,
                            visualProgress,
                            if (visualProgress > 0) "$visualProgress%" else "0%"
                        )
                        return@forEach
                    }
                    cancelReset(key)
                    completedKeys.remove(key)
                    doneHandledKeys.remove(key)
                    lastKnownStatuses.remove(key)
                    visualProgressByKey.remove(key)
                    activeSessionKeys.remove(key)
                    failedKeys.remove(key)
                    applyResolvedActionState(views, version, null)
                }
                item.status == "Downloading" -> {
                    if (item.progress >= 100 && downloadFileExists(item)) {
                        failedKeys.remove(key)
                        val shouldAnimateDone = hasActiveVisualState(key)
                        if (shouldAnimateDone) {
                            if (doneHandledKeys.contains(key)) {
                                applyDoneState(views, animate = false)
                                if (!resetRunnables.containsKey(key)) {
                                    scheduleReset(key)
                                }
                            } else if (completedKeys.add(key)) {
                                visualProgressByKey[key] = 100
                                views.container.isEnabled = false
                                views.icon.visibility = View.GONE
                                views.label.text = context.getString(R.string.download_progress_format, 100)
                                animateFillTo(views.fill, 100, onUpdate = { value ->
                                    views.label.text = context.getString(R.string.download_progress_format, value)
                                }, onEnd = {
                                    doneHandledKeys.add(key)
                                    applyDoneState(views)
                                    scheduleReset(key)
                                })
                                scheduleDoneFallback(key, views)
                            } else {
                                views.container.isEnabled = false
                                views.icon.visibility = View.GONE
                                views.label.text = context.getString(R.string.download_progress_format, 100)
                            }
                        } else {
                            completedKeys.remove(key)
                            doneHandledKeys.remove(key)
                            visualProgressByKey.remove(key)
                            cancelReset(key)
                            applyResolvedActionState(views, version, item)
                        }
                        activeSessionKeys.remove(key)
                        lastKnownStatuses[key] = "Done"
                        return@forEach
                    }
                    failedKeys.remove(key)
                    completedKeys.remove(key)
                    doneHandledKeys.remove(key)
                    cancelReset(key)
                    activeSessionKeys.add(key)
                    val progress = maxVisualProgress(key, item.progress)
                    applyProgressState(views, progress, if (progress > 0) "$progress%" else "0%")
                    lastKnownStatuses[key] = item.status
                }
                item.status == "Paused" -> {
                    failedKeys.remove(key)
                    completedKeys.remove(key)
                    doneHandledKeys.remove(key)
                    cancelReset(key)
                    activeSessionKeys.add(key)
                    val progress = maxVisualProgress(key, item.progress)
                    val label = if (progress > 0) {
                        getString(R.string.download_paused_percent_format, progress)
                    } else {
                        getString(R.string.download_status_paused)
                    }
                    applyProgressState(views, progress, label)
                    lastKnownStatuses[key] = item.status
                }
                item.status == "Stopped" -> {
                    failedKeys.remove(key)
                    completedKeys.remove(key)
                    doneHandledKeys.remove(key)
                    cancelReset(key)
                    activeSessionKeys.add(key)
                    val progress = maxVisualProgress(key, item.progress)
                    val label = if (progress > 0) {
                        getString(R.string.download_status_stopped)
                    } else {
                        getString(R.string.download_status_stopped)
                    }
                    applyProgressState(views, progress, label)
                    lastKnownStatuses[key] = item.status
                }
                item.status == "Done" -> {
                    if (!downloadFileExists(item)) {
                        cancelReset(key)
                        completedKeys.remove(key)
                        doneHandledKeys.remove(key)
                        visualProgressByKey.remove(key)
                        activeSessionKeys.remove(key)
                        failedKeys.remove(key)
                        lastKnownStatuses.remove(key)
                        applyResolvedActionState(views, version, item)
                        return@forEach
                    }
                    failedKeys.remove(key)
                    val shouldAnimateDone = hasActiveVisualState(key)
                    if (shouldAnimateDone) {
                        if (doneHandledKeys.contains(key)) {
                            applyDoneState(views, animate = false)
                            if (!resetRunnables.containsKey(key)) {
                                scheduleReset(key)
                            }
                        } else if (completedKeys.add(key)) {
                            visualProgressByKey[key] = 100
                            views.container.isEnabled = false
                            views.icon.visibility = View.GONE
                            views.label.text = context.getString(R.string.download_progress_format, 100)
                            animateFillTo(views.fill, 100, onUpdate = { value ->
                                views.label.text = context.getString(R.string.download_progress_format, value)
                            }, onEnd = {
                                doneHandledKeys.add(key)
                                applyDoneState(views)
                                scheduleReset(key)
                            })
                            scheduleDoneFallback(key, views)
                        } else {
                            views.container.isEnabled = false
                            views.icon.visibility = View.GONE
                            views.label.text = context.getString(R.string.download_progress_format, 100)
                        }
                    } else {
                        completedKeys.remove(key)
                        doneHandledKeys.remove(key)
                        visualProgressByKey.remove(key)
                        cancelReset(key)
                        applyResolvedActionState(views, version, item)
                    }
                    activeSessionKeys.remove(key)
                    lastKnownStatuses[key] = item.status
                }
                item.status == "Failed" -> {
                    failedKeys.add(key)
                    completedKeys.remove(key)
                    doneHandledKeys.remove(key)
                    activeSessionKeys.remove(key)
                    visualProgressByKey.remove(key)
                    cancelReset(key)
                    applyErrorState(views)
                    lastKnownStatuses[key] = item.status
                }
                else -> {
                    failedKeys.remove(key)
                    if (hasActiveVisualState(key)) {
                        applyProgressState(
                            views,
                            visualProgress,
                            if (visualProgress > 0) "$visualProgress%" else "0%"
                        )
                        lastKnownStatuses[key] = item.status
                        return@forEach
                    }
                    completedKeys.remove(key)
                    doneHandledKeys.remove(key)
                    visualProgressByKey.remove(key)
                    cancelReset(key)
                    applyResolvedActionState(views, version, item)
                    activeSessionKeys.remove(key)
                    lastKnownStatuses[key] = item.status
                }
            }
        }
    }

    private fun scheduleReset(key: String) {
        cancelReset(key)
        val runnable = Runnable {
            resetRunnables.remove(key)
            completedKeys.remove(key)
            doneHandledKeys.remove(key)
            visualProgressByKey.remove(key)
            updateDownloadButtons(DownloadRepository.snapshotDownloads())
        }
        resetRunnables[key] = runnable
        mainHandler.postDelayed(runnable, RESET_DELAY_MS)
    }

    private fun scheduleDoneFallback(key: String, views: DownloadButtonViews) {
        mainHandler.postDelayed({
            if (!isAdded || view == null) return@postDelayed
            if (doneHandledKeys.contains(key)) return@postDelayed
            if (buttonViewsByKey[key] !== views) return@postDelayed
            doneHandledKeys.add(key)
            applyDoneState(views, animate = false)
            scheduleReset(key)
        }, DONE_HOLD_MS + 720L)
    }

    private fun cancelReset(key: String) {
        resetRunnables.remove(key)?.let(mainHandler::removeCallbacks)
    }

    private fun resolveButtonAction(
        version: Version?,
        item: DownloadItem?
    ): VersionButtonAction {
        if (
            version != null &&
            installedLaunchPackage != null &&
            installedVersionName == version.version_name
        ) {
            return VersionButtonAction.OPEN
        }
        if (item != null && downloadFileExists(item)) {
            return VersionButtonAction.INSTALL
        }
        return VersionButtonAction.DOWNLOAD
    }

    private fun applyResolvedActionState(
        views: DownloadButtonViews,
        version: Version?,
        item: DownloadItem?
    ) {
        when (resolveButtonAction(version, item)) {
            VersionButtonAction.OPEN -> applyOpenState(views)
            VersionButtonAction.INSTALL -> applyInstallState(views)
            VersionButtonAction.DOWNLOAD -> applyIdleState(views)
        }
    }

    private fun applyIdleState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = true
        views.uninstallButton.visibility = View.GONE
        views.container.animate().cancel()
        views.track.animate().cancel()
        views.icon.visibility = View.GONE
        views.icon.alpha = 1f
        views.icon.scaleX = 1f
        views.icon.scaleY = 1f
        views.label.animate().cancel()
        views.label.alpha = 1f
        views.label.translationY = 0f
        views.label.text = getString(R.string.download_label)
        views.label.setTextColor(Color.BLACK)
        animateFillTo(views.fill, 0)
    }

    private fun applyInstallState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = true
        views.uninstallButton.visibility = View.GONE
        views.container.animate().cancel()
        views.track.animate().cancel()
        views.icon.visibility = View.GONE
        views.icon.alpha = 1f
        views.icon.scaleX = 1f
        views.icon.scaleY = 1f
        views.label.animate().cancel()
        views.label.alpha = 1f
        views.label.translationY = 0f
        views.label.text = getString(R.string.download_action_install)
        views.label.setTextColor(Color.BLACK)
        animateFillTo(views.fill, 0)
    }

    private fun applyOpenState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = true
        views.uninstallButton.visibility = View.VISIBLE
        views.container.animate().cancel()
        views.track.animate().cancel()
        views.icon.visibility = View.GONE
        views.icon.alpha = 1f
        views.icon.scaleX = 1f
        views.icon.scaleY = 1f
        views.label.animate().cancel()
        views.label.alpha = 1f
        views.label.translationY = 0f
        views.label.text = getString(R.string.download_action_open)
        views.label.setTextColor(Color.BLACK)
        animateFillTo(views.fill, 0)
    }

    private fun applyProgressState(views: DownloadButtonViews, progress: Int, label: String) {
        val context = views.container.context
        applyVersionButtonPalette(views, context)
        views.container.isEnabled = false
        views.uninstallButton.visibility = View.GONE
        views.track.animate().cancel()
        views.icon.visibility = View.GONE
        views.label.animate().cancel()
        views.label.alpha = 1f
        views.label.translationY = 0f
        views.label.setTextColor(Color.BLACK)
        val clamped = progress.coerceIn(0, 100)
        if (
            label.startsWith(getString(R.string.download_status_paused)) ||
            label.startsWith(getString(R.string.download_status_stopped))
        ) {
            views.label.text = label
            animateFillTo(views.fill, clamped)
        } else {
            animateFillTo(views.fill, clamped, onUpdate = { value ->
                views.label.text = context.getString(R.string.download_progress_format, value)
            })
        }
    }

    private fun applyDoneState(views: DownloadButtonViews, animate: Boolean = true) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = false
        views.uninstallButton.visibility = View.GONE
        views.label.setTextColor(Color.BLACK)
        animateFillTo(views.fill, 100)
        val doneLabel = getString(R.string.download_status_done)
        val shouldAnimate = views.icon.visibility != View.VISIBLE || views.label.text != doneLabel
        views.track.animate().cancel()
        views.label.animate().cancel()
        if (animate && shouldAnimate) {
            views.label.text = doneLabel
            views.label.alpha = 1f
            views.label.translationY = 0f

            views.icon.visibility = View.VISIBLE
            views.icon.alpha = 0f
            views.icon.scaleX = 0.72f
            views.icon.scaleY = 0.72f
            views.icon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(OvershootInterpolator(0.75f))
                .start()

            views.track.animate()
                .scaleX(1.012f)
                .scaleY(1.012f)
                .setDuration(140L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    views.track.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()
            return
        }

        views.track.scaleX = 1f
        views.track.scaleY = 1f
        views.icon.visibility = View.VISIBLE
        views.icon.alpha = 1f
        views.icon.scaleX = 1f
        views.icon.scaleY = 1f
        views.label.text = doneLabel
        views.label.alpha = 1f
        views.label.translationY = 0f
    }

    private fun applyErrorState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = true
        views.uninstallButton.visibility = View.GONE
        releaseLiquidDrawable(views.fill)
        views.fill.setImageResource(versionErrorFillDrawable(views.container.context))
        animateFillTo(views.fill, 100)
        views.track.animate().cancel()
        views.label.animate().cancel()
        views.icon.animate().cancel()
        views.icon.visibility = View.GONE
        views.label.text = getString(R.string.download_status_failed)
        views.label.setTextColor(Color.WHITE)
        views.label.alpha = 0f
        views.label.translationY = 2f
        views.label.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        views.track.scaleX = 0.985f
        views.track.scaleY = 0.985f
        views.track.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .setInterpolator(OvershootInterpolator(0.65f))
            .start()
    }

    private fun animateFillTo(
        fillView: View,
        percent: Int,
        onUpdate: ((Int) -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ) {
        val imageView = fillView as? ImageView ?: return
        val currentLevel = (imageView.getTag(R.id.versionDownloadFill) as? Int) ?: 0
        val targetLevel = percent.coerceIn(0, 100) * 100

        (imageView.getTag(R.id.versionDownloadTrack) as? ValueAnimator)?.cancel()

        if (currentLevel == targetLevel) {
            onUpdate?.invoke(targetLevel / 100)
            onEnd?.invoke()
            return
        }

        if (targetLevel != 10_000) {
            imageView.setImageLevel(targetLevel)
            imageView.setTag(R.id.versionDownloadFill, targetLevel)
            imageView.setTag(R.id.versionDownloadTrack, null)
            onUpdate?.invoke(targetLevel / 100)
            onEnd?.invoke()
            return
        }

        val delta = kotlin.math.abs(targetLevel - currentLevel) / 100
        ValueAnimator.ofInt(currentLevel, targetLevel).apply {
            duration = if (targetLevel == 10_000) {
                (290L + (delta * 9L)).coerceAtMost(640L)
            } else {
                (200L + (delta * 7L)).coerceAtMost(460L)
            }
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val level = animator.animatedValue as Int
                imageView.setImageLevel(level)
                imageView.setTag(R.id.versionDownloadFill, level)
                onUpdate?.invoke(level / 100)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    imageView.setTag(R.id.versionDownloadTrack, null)
                    if (targetLevel == 10_000) {
                        imageView.postDelayed({
                            if (!isAdded || !imageView.isAttachedToWindow) return@postDelayed
                            onEnd?.invoke()
                        }, DONE_HOLD_MS)
                    } else {
                        onEnd?.invoke()
                    }
                }
            })
            imageView.setTag(R.id.versionDownloadTrack, this)
            start()
        }
    }

    private fun applyVersionButtonPalette(views: DownloadButtonViews, context: android.content.Context) {
        val paletteState = if (AppearancePreferences.isDynamicColorEnabled(context)) 1 else 0
        views.fill.setTag(R.id.liquidProgressPaletteState, paletteState)
        val fillDrawable = liquidVersionDrawable(views.fill, context)
        views.track.setBackgroundResource(versionTrackDrawable(context))
        views.fill.setImageDrawable(fillDrawable)
        val textColor = if (AppearancePreferences.isDynamicColorEnabled(context)) {
            ThemeColors.color(
                context,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                R.color.text_on_accent_chip
            )
        } else {
            Color.BLACK
        }
        views.label.setTextColor(textColor)
        views.icon.imageTintList = ColorStateList.valueOf(textColor)
    }

    private fun liquidVersionDrawable(
        fillView: ImageView,
        context: android.content.Context
    ): LiquidWaveProgressDrawable {
        val paletteState = if (AppearancePreferences.isDynamicColorEnabled(context)) 1 else 0
        val existingDrawable = fillView.getTag(R.id.liquidProgressDrawable) as? LiquidWaveProgressDrawable
        val existingState = fillView.getTag(R.id.liquidProgressPaletteState) as? Int
        if (existingDrawable != null && existingState == paletteState) {
            return existingDrawable
        }
        existingDrawable?.dispose()
        val fillColor = if (AppearancePreferences.isDynamicColorEnabled(context)) {
            ThemeColors.color(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                R.color.accent
            )
        } else {
            ContextCompat.getColor(context, R.color.accent)
        }
        val drawable = LiquidWaveProgressDrawable(
            trackColor = Color.TRANSPARENT,
            fillColor = fillColor
        )
        fillView.setTag(R.id.liquidProgressDrawable, drawable)
        fillView.setTag(R.id.liquidProgressPaletteState, paletteState)
        return drawable
    }

    private fun versionTrackDrawable(context: android.content.Context): Int {
        return if (AppearancePreferences.isDynamicColorEnabled(context)) {
            R.drawable.version_download_button_bg
        } else {
            R.drawable.version_download_button_bg_brand
        }
    }

    private fun refreshVersionHints() {
        if (!this::versionList.isInitialized) return
        val ctx = context ?: return
        val snapshot = currentInstallSnapshot ?: return
        if (hintViewsByKey.isEmpty() || versionsByKey.isEmpty()) return

        hintViewsByKey.forEach { (key, views) ->
            val version = versionsByKey[key] ?: return@forEach
            val insight = InstallIntelligence.insight(
                ctx,
                currentApp,
                version,
                snapshot,
                currentLatestVersion
            )

            views.installedHint.text = insight.installedHint.orEmpty()
            views.installedHint.visibility = if (insight.installedHint.isNullOrBlank()) View.GONE else View.VISIBLE
            applyInstalledHintPalette(views.installedHint, insight)
            views.downloadedHint.text = insight.downloadHint.orEmpty()
            views.downloadedHint.visibility = if (insight.downloadHint.isNullOrBlank()) View.GONE else View.VISIBLE
            updateActionButtonSpacing(
                actionRow = views.actionRow,
                hasInstalledHint = views.installedHint.visibility == View.VISIBLE,
                hasDownloadedHint = views.downloadedHint.visibility == View.VISIBLE
            )
        }
        versionList.post { adjustSheetHeight() }
    }

    private fun updateScrollHint(visible: Boolean) {
        if (!this::versionList.isInitialized || !this::scrollHintContainer.isInitialized) return

        val targetVisibility = if (visible) View.VISIBLE else View.GONE
        if (scrollHintContainer.visibility != targetVisibility) {
            if (visible) {
                scrollHintContainer.alpha = 0f
                scrollHintContainer.translationY = dp(6).toFloat()
                scrollHintContainer.visibility = View.VISIBLE
                scrollHintContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                scrollHintContainer.animate().cancel()
                scrollHintContainer.visibility = View.GONE
            }
        }

        val targetPaddingBottom = versionListBasePaddingBottom + if (visible) dp(44) else 0
        if (versionList.paddingBottom != targetPaddingBottom) {
            versionList.updatePadding(bottom = targetPaddingBottom)
        }
        if (visible) {
            startScrollHintAnimation()
        } else {
            stopScrollHintAnimation()
        }
    }

    private fun shouldShowScrollHint(recyclerView: RecyclerView = versionList): Boolean {
        if (!hasScrollableVersionContent) return false
        val range = recyclerView.computeVerticalScrollRange()
        val extent = recyclerView.computeVerticalScrollExtent()
        val offset = recyclerView.computeVerticalScrollOffset()
        val hasScrollableContent = range > extent + 1
        val atBottom = offset + extent >= range - 2
        return hasScrollableContent && !atBottom
    }

    private fun startScrollHintAnimation() {
        if (!this::scrollHintIcon.isInitialized) return
        if (scrollHintAnimator?.isRunning == true || scrollHintAnimator?.isStarted == true) return

        scrollHintAnimator = ObjectAnimator.ofFloat(
            scrollHintIcon,
            View.TRANSLATION_Y,
            0f,
            dp(3).toFloat(),
            0f
        ).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopScrollHintAnimation() {
        scrollHintAnimator?.cancel()
        scrollHintAnimator = null
        if (this::scrollHintIcon.isInitialized) {
            scrollHintIcon.translationY = 0f
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun adjustSheetHeight() {
        if (!isAdded || !this::versionList.isInitialized || maxSheetHeightPx <= 0) return
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val content = bottomSheet.findViewById<View>(R.id.sheetRoot) ?: return
        val listContainer = bottomSheet.findViewById<View>(R.id.versionListContainer) ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)
        val screenHeight = resources.displayMetrics.heightPixels
        val effectiveMaxSheetHeightPx = if (versionsByKey.size <= 1) {
            (screenHeight * MAX_SINGLE_VERSION_SHEET_HEIGHT_RATIO).toInt()
        } else {
            maxSheetHeightPx
        }

        val listViewportHeight = listContainer.height.takeIf { it > 0 } ?: versionList.height
        val listContentHeight = versionList.computeVerticalScrollRange()
            .coerceAtLeast(versionList.minimumHeight)
        val baseHeight = (content.height - listViewportHeight).coerceAtLeast(0)
        val targetListHeight = if (listViewportHeight > 0) {
            val capped = (effectiveMaxSheetHeightPx - baseHeight).coerceAtLeast(0)
            minOf(listContentHeight, capped)
        } else {
            listContentHeight
        }
        hasScrollableVersionContent = listContentHeight > targetListHeight
        updateScrollHint(hasScrollableVersionContent && shouldShowScrollHint(versionList))
        listContainer.layoutParams = listContainer.layoutParams.apply {
            height = if (targetListHeight >= listContentHeight) WRAP_CONTENT else targetListHeight
        }
        val targetHeight = (baseHeight + targetListHeight)
            .coerceAtLeast(0)
            .coerceAtMost(effectiveMaxSheetHeightPx)

        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = if (targetHeight > 0) targetHeight else WRAP_CONTENT
        }
        behavior.peekHeight = targetHeight
        if (
            behavior.state != BottomSheetBehavior.STATE_EXPANDED &&
            behavior.state != BottomSheetBehavior.STATE_DRAGGING &&
            behavior.state != BottomSheetBehavior.STATE_SETTLING &&
            behavior.state != BottomSheetBehavior.STATE_HIDDEN
        ) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        bottomSheet.requestLayout()
    }

    private fun releaseLiquidDrawable(fillView: ImageView) {
        (fillView.getTag(R.id.liquidProgressDrawable) as? LiquidWaveProgressDrawable)?.dispose()
        fillView.setTag(R.id.liquidProgressDrawable, null)
        fillView.setTag(R.id.liquidProgressPaletteState, null)
    }

    private fun versionErrorFillDrawable(context: android.content.Context): Int {
        return if (AppearancePreferences.isDynamicColorEnabled(context)) {
            R.drawable.version_download_fill_error_clip
        } else {
            R.drawable.version_download_fill_error_clip
        }
    }

    private fun versionKey(version: Version): String {
        return versionKey(version.version_name, version.url, version.version)
    }

    private fun versionKey(versionName: String, url: String, versionCode: Int = 0): String {
        return "$versionName|$versionCode|$url"
    }

    private fun downloadPackageKey(item: DownloadItem): String {
        return item.backendPackageName.takeIf { it.isNotBlank() } ?: item.packageName
    }

    private fun downloadFileExists(item: DownloadItem): Boolean {
        val path = item.filePath.takeIf { it.isNotBlank() } ?: return false
        val file = java.io.File(path)
        return file.exists() && file.length() > 0L
    }

    private fun maxVisualProgress(key: String, progress: Int): Int {
        val clamped = progress.coerceIn(0, 100)
        val current = visualProgressByKey[key] ?: 0
        return maxOf(current, clamped).also { visualProgressByKey[key] = it }
    }

    private fun hasActiveVisualState(key: String): Boolean {
        return activeSessionKeys.contains(key) ||
            completedKeys.contains(key) ||
            doneHandledKeys.contains(key)
    }

    private fun updateSheetSnackbarPosition(bottomSheetView: View) {
        val snackbarView = currentSnackbar?.view ?: return
        val parentView = snackbarView.parent as? View ?: return
        if (snackbarView.height == 0 || bottomSheetView.height == 0) return

        val parentLocation = IntArray(2)
        val sheetLocation = IntArray(2)
        val snackbarLocation = IntArray(2)
        parentView.getLocationOnScreen(parentLocation)
        bottomSheetView.getLocationOnScreen(sheetLocation)
        snackbarView.getLocationOnScreen(snackbarLocation)

        val density = bottomSheetView.resources.displayMetrics.density
        val gapPx = (SHEET_SNACKBAR_GAP_DP * density).toInt()
        val desiredTopAboveSheet = sheetLocation[1] - parentLocation[1] - snackbarView.height - gapPx
        val navTopInParent = activity?.findViewById<View>(R.id.bottomNav)?.let { bottomNav ->
            val navLocation = IntArray(2)
            bottomNav.getLocationOnScreen(navLocation)
            val navGapPx = (NAV_SNACKBAR_GAP_DP * density).toInt()
            navLocation[1] - parentLocation[1] - snackbarView.height - navGapPx
        }
        val desiredTop = if (navTopInParent != null) {
            minOf(desiredTopAboveSheet, navTopInParent)
        } else {
            desiredTopAboveSheet
        }
        val currentTop = snackbarLocation[1] - parentLocation[1]
        snackbarView.translationY += (desiredTop - currentTop).toFloat()
    }

    private fun attachPressAnimation(view: View) {
        UiMotion.attachPress(
            target = view,
            scaleDown = PRESS_SCALE,
            pressedAlpha = 0.95f,
            pressedTranslationYDp = 0.9f,
            downDuration = 85L,
            upDuration = 170L,
            releaseOvershoot = 0.58f
        )
    }

    private fun copyChangelog(version: Version, changelog: String) {
        val clipboard = context?.getSystemService(ClipboardManager::class.java) ?: return
        val text = buildString {
            append(currentApp.name.cleanDuplicateSuffix())
            append(" ")
            append(version.displayVersionName())
            append('\n')
            append(changelog)
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("HYDRV release notes", text))
        AppSnackbar.show(rootView, getString(R.string.version_notes_copied))
    }

    private fun shareChangelog(version: Version, changelog: String) {
        val shareText = buildString {
            append(currentApp.name.cleanDuplicateSuffix())
            append(" ")
            append(version.displayVersionName())
            append('\n')
            append(changelog)
            val host = version.downloadHost()
            if (!host.isNullOrBlank()) {
                append("\n\n")
                append(getString(R.string.version_source_prefix, host))
            }
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "${currentApp.name.cleanDuplicateSuffix()} ${version.displayVersionName()}"
                    )
                    putExtra(Intent.EXTRA_TEXT, shareText)
                },
                getString(R.string.version_share_notes)
            )
        )
    }

    private fun refreshInstalledInfo(context: android.content.Context) {
        val trustedInstall = AppIdentityStore.findTrustedInstalledPackage(
            context,
            currentApp.packageName,
            currentApp.name
        )
        installedLaunchPackage = trustedInstall?.packageName?.takeIf { it.isNotBlank() }
        installedVersionName = trustedInstall?.versionName
        installedVersionCode = trustedInstall?.versionCode?.takeIf { it > 0 }
    }

    private fun openInstalledApp() {
        val context = context ?: return
        val packageName = installedLaunchPackage ?: return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(launchIntent)
    }

    private fun launchInstalledAppUninstall() {
        val context = context ?: return
        val packageName = installedLaunchPackage ?: return
        val candidates = linkedSetOf<String>().apply {
            add(packageName)
            InstallAliasStore.resolveForAppName(context, currentApp.name)?.let(::add)
            InstallAliasStore.resolveForPackage(context, currentApp.packageName)?.let(::add)
        }.filter { it.isNotBlank() }

        val packageManager = context.packageManager
        val uninstallTarget = candidates.firstOrNull { candidate ->
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", candidate, null))
                .resolveActivity(packageManager) != null
        } ?: candidates.firstOrNull()

        if (uninstallTarget == null) {
            AppSnackbar.show(rootView, getString(R.string.install_failed))
            return
        }

        if (!AppStateCacheManager.isInstalled(context, uninstallTarget, currentApp.name)) {
            AppStateCacheManager.forceRefreshInstalledPackages(context) {
                if (!isAdded || view == null) return@forceRefreshInstalledPackages
                refreshInstalledInfo(requireContext())
                updateDownloadButtons(DownloadRepository.snapshotDownloads())
                refreshVersionHints()
            }
            return
        }

        val uninstallUri = Uri.fromParts("package", uninstallTarget, null)
        PendingUninstallTracker.mark(currentApp.name, uninstallTarget)
        val uninstallIntent = Intent(Intent.ACTION_DELETE, uninstallUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uninstallUri)

        try {
            startActivity(uninstallIntent)
        } catch (_: Exception) {
            PendingUninstallTracker.clear()
            try {
                startActivity(fallbackIntent)
            } catch (_: Exception) {
                AppSnackbar.show(rootView, getString(R.string.install_failed))
            }
        }
    }

    private fun installDownloadedVersion(item: DownloadItem?) {
        val context = context ?: return
        val filePath = item?.filePath?.takeIf { it.isNotBlank() } ?: run {
            AppSnackbar.show(rootView, getString(R.string.install_failed_file_not_found))
            return
        }
        val file = java.io.File(filePath)
        if (!file.exists() || file.length() <= 0L) {
            AppSnackbar.show(rootView, getString(R.string.install_failed_file_not_found))
            return
        }
        InstallSessionManager.installApk(
            context = context,
            apkPath = filePath,
            appName = currentApp.name,
            backendPackage = item.backendPackageName.takeIf { it.isNotBlank() } ?: currentApp.packageName
        )
    }

}
