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
import android.os.SystemClock
import android.content.res.Configuration
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import android.content.res.ColorStateList
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.WeakHashMap

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
        private const val SHEET_SNACKBAR_GAP_DP = 14
        private const val NAV_SNACKBAR_GAP_DP = 10
        private const val MAX_SHEET_HEIGHT_RATIO = 0.5f
        private const val MAX_SINGLE_VERSION_SHEET_HEIGHT_RATIO = 0.58f
        private const val INSTALL_CIRCLE_SIZE_DP = 46
        private const val INSTALL_PROGRESS_SIZE_DP = 24
        private const val UNINSTALL_PROGRESS_SIZE_DP = 24
        private const val INSTALL_PROGRESS_THICKNESS_DP = 3
        private const val UNINSTALL_PROGRESS_THICKNESS_DP = 3
        private const val INSTALL_PROGRESS_CAP = 92
        private const val BUTTON_MORPH_TO_CIRCLE_MS = 255L
        private const val BUTTON_MORPH_FROM_CIRCLE_MS = 300L
        private const val INSTALL_OPEN_REVEAL_DELAY_MS = 620L
        private const val INSTALL_OPEN_REVEAL_MOVE_MS = 520L
        private const val INSTALL_OPEN_REVEAL_EXPAND_MS = 860L
        private const val CONTENT_REVEAL_MS = 190L
        private const val TRACK_SETTLE_MS = 210L
        private const val PROGRESS_REVEAL_MS = 145L
        private const val INSTALL_SUCCESS_SETTLE_DELAY_MS = 980L
        private const val UNINSTALL_SUCCESS_SETTLE_DELAY_MS = 760L
        private const val POST_SYSTEM_ANIMATION_DELAY_MS = 260L
        private const val UNINSTALL_RESULT_CHECK_DELAY_MS = 80L
        private const val UNINSTALL_RESULT_RETRY_DELAY_MS = 160L
        private const val MAX_UNINSTALL_STILL_INSTALLED_RETRIES = 5
        private const val UNINSTALL_CONFIRMATION_GRACE_MS = 900L
        private const val RECENT_SYSTEM_RETURN_WINDOW_MS = 1500L
        private const val SYSTEM_RETURN_ANIMATION_DELAY_MS = 160L
        private const val HINT_LAYOUT_TRANSITION_MS = 165L
        private const val SHEET_HEIGHT_ANIMATION_MS = 190L
        @Volatile
        private var presentationBlocked = false
        private val singleVersionListHeightCachePx = mutableMapOf<String, Int>()

        fun isPresentationBlocked(): Boolean = presentationBlocked

        private fun markPresentationBlocked(blocked: Boolean) {
            presentationBlocked = blocked
        }

        private fun cacheSingleVersionListHeight(cacheKey: String, heightPx: Int) {
            if (cacheKey.isNotBlank() && heightPx > 0) {
                singleVersionListHeightCachePx[cacheKey] = heightPx
            }
        }

        private fun cachedSingleVersionListHeight(cacheKey: String): Int? {
            if (cacheKey.isBlank()) return null
            return singleVersionListHeightCachePx[cacheKey]
        }

        private fun stateLabel(state: Int): String {
            return when (state) {
                BottomSheetBehavior.STATE_EXPANDED -> "expanded"
                BottomSheetBehavior.STATE_COLLAPSED -> "collapsed"
                BottomSheetBehavior.STATE_HIDDEN -> "hidden"
                BottomSheetBehavior.STATE_DRAGGING -> "dragging"
                BottomSheetBehavior.STATE_SETTLING -> "settling"
                BottomSheetBehavior.STATE_HALF_EXPANDED -> "half_expanded"
                else -> state.toString()
            }
        }

        fun present(
            fragmentManager: FragmentManager,
            context: android.content.Context,
            app: AppModel,
            tag: String,
            preferOpenInstalledAction: Boolean = false,
            installedVersionCodeHint: Int? = null,
            installedVersionNameHint: String? = null
        ): Boolean {
            fragmentManager.executePendingTransactions()
            val existingFragment = fragmentManager.findFragmentByTag(tag)
            if (existingFragment != null && !fragmentManager.isStateSaved) {
                val dialogFragment = existingFragment as? BottomSheetDialogFragment
                val isActivelyShowing =
                    existingFragment.isAdded &&
                        !existingFragment.isRemoving &&
                        (dialogFragment?.dialog?.isShowing != false)
                if (!isActivelyShowing) {
                    runCatching {
                        fragmentManager.beginTransaction()
                            .remove(existingFragment)
                            .commitNowAllowingStateLoss()
                    }
                }
            }
            val currentFragment = fragmentManager.findFragmentByTag(tag)
            AppDiagnostics.trace(
                context,
                "UI",
                "version_sheet_show_requested",
                "${app.name} | blocked=$presentationBlocked | stateSaved=${fragmentManager.isStateSaved} | existing=${currentFragment != null}"
            )
            if (
                presentationBlocked ||
                fragmentManager.isStateSaved ||
                currentFragment != null
            ) {
                AppDiagnostics.trace(
                    context,
                    "UI",
                    "version_sheet_show_skipped",
                    "${app.name} | blocked=$presentationBlocked | stateSaved=${fragmentManager.isStateSaved} | existing=${currentFragment != null}"
                )
                return false
            }

            val sheet = VersionSheet(
                app = app,
                preferOpenInstalledAction = preferOpenInstalledAction,
                installedVersionCodeHint = installedVersionCodeHint,
                installedVersionNameHint = installedVersionNameHint
            )

            return runCatching {
                markPresentationBlocked(true)
                fragmentManager.beginTransaction()
                    .add(sheet, tag)
                    .commit()
                fragmentManager.executePendingTransactions()
                AppDiagnostics.trace(
                    context,
                    "UI",
                    "version_sheet_show_committed",
                    "${app.name} | added=${sheet.isAdded}"
                )
                true
            }.getOrElse { error ->
                markPresentationBlocked(false)
                AppDiagnostics.trace(
                    context,
                    "UI",
                    "version_sheet_show_failed",
                    "${app.name} | ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
                )
                false
            }
        }
    }

    private data class DownloadButtonViews(
        val actionRow: View,
        val container: FrameLayout,
        val track: FrameLayout,
        val fill: ImageView,
        val content: View,
        val label: TextView,
        val icon: ImageView,
        val installProgress: ComposeView,
        val uninstallButton: FrameLayout,
        val uninstallLabel: TextView,
        val uninstallProgress: ComposeView
    )

    private enum class InstallVisualState {
        WAITING_CONFIRMATION,
        INSTALLING,
        FINALIZING
    }

    private enum class UninstallVisualState {
        WAITING_RESULT
    }

    private data class VersionHintViews(
        val cardRoot: ViewGroup,
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
    private val pendingRewardLaunchKeys = mutableSetOf<String>()
    private val installVisualStateByKey = mutableMapOf<String, InstallVisualState>()
    private val installVisualProgressByKey = mutableMapOf<String, Int>()
    private val installProgressAnimators = mutableMapOf<String, ValueAnimator>()
    private val restoredInstallVisualKeys = mutableSetOf<String>()
    private val morphSurfaceAnimators = WeakHashMap<View, ValueAnimator>()
    private val installVisualCompletingKeys = mutableSetOf<String>()
    private val installTransitioningKeys = mutableSetOf<String>()
    private val uninstallVisualStateByKey = mutableMapOf<String, UninstallVisualState>()
    private val uninstallTransitioningKeys = mutableSetOf<String>()
    private val resolvedUninstallKeys = mutableSetOf<String>()
    private val resetRunnables = mutableMapOf<String, Runnable>()
    private val hintLayoutSettleRunnables = mutableMapOf<String, Runnable>()
    private val lastVersionSizingTracePayloads = mutableMapOf<String, String>()
    private val lastUninstallTracePayloads = mutableMapOf<String, String>()
    private var versionsByKey = emptyMap<String, Version>()
    private var hintViewsByKey = emptyMap<String, VersionHintViews>()
    private var currentLatestVersion: Version? = null
    private var currentInstallSnapshot: InstallIntelligence.Snapshot? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentSnackbar: Snackbar? = null
    private var isCurrentSnackbarSheetBound = false
    private var pendingSheetSnackbarMessage: String? = null
    private var pendingSheetSnackbarIndefinite = false
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var enforceExpandedRunnable: Runnable? = null
    private var versionScrollListener: RecyclerView.OnScrollListener? = null
    private var installSnapshotRunnable: Runnable? = null
    private var deferredUiRefreshRunnable: Runnable? = null
    private var sheetHeightAdjustRunnable: Runnable? = null
    private var sheetHeightSettleRunnable: Runnable? = null
    private var zeroContentHeightRetryRunnable: Runnable? = null
    private var zeroContentHeightRetriesRemaining = 0
    private var installEventObserverStartedAt = 0L
    private var lastInstallSnapshotRefreshAt = 0L
    private var suppressUiRefreshUntil = 0L
    private var maxSheetHeightPx = 0
    private var hasScrollableVersionContent = false
    private var versionListBasePaddingBottom = 0
    private var pendingInitialMultiItemClamp = false
    private var initialSheetClampListener: ViewTreeObserver.OnPreDrawListener? = null
    private var scrollHintAnimator: ObjectAnimator? = null
    private var sheetHeightAnimator: ValueAnimator? = null
    private val standardInterpolator = PathInterpolator(0.22f, 0f, 0.08f, 1f)
    private val enterInterpolator = PathInterpolator(0.12f, 0f, 0f, 1f)
    private val exitInterpolator = PathInterpolator(0.4f, 0f, 0.8f, 0.2f)
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
    private var pendingInstallKey: String? = null
    private var awaitingInstallConfirmationReturn = false
    private var pendingUninstallKey: String? = null
    private var awaitingUninstallResult = false
    private var pendingUninstallStillInstalledRetries = 0
    private var pendingUninstallStartedAtMs = 0L
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
        markPresentationBlocked(true)
        context?.let {
            AppDiagnostics.trace(it, "UI", "version_sheet_on_create_dialog", currentApp.name)
        }
        return BottomSheetDialog(requireContext(), R.style.BottomSheetTheme).apply {
            dismissWithAnimation = true
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        context?.let {
            AppDiagnostics.trace(it, "UI", "version_sheet_on_dismiss", currentApp.name)
        }
        markPresentationBlocked(false)
        super.onDismiss(dialog)
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        context?.let {
            AppDiagnostics.trace(it, "UI", "version_sheet_on_cancel", currentApp.name)
        }
        super.onCancel(dialog)
    }

    override fun dismiss() {
        context?.let {
            AppDiagnostics.trace(it, "UI", "version_sheet_dismiss_called", currentApp.name)
        }
        super.dismiss()
    }

    override fun dismissAllowingStateLoss() {
        context?.let {
            AppDiagnostics.trace(
                it,
                "UI",
                "version_sheet_dismiss_state_loss_called",
                currentApp.name
            )
        }
        super.dismissAllowingStateLoss()
    }

    override fun onStart() {
        super.onStart()
        context?.let {
            AppDiagnostics.trace(it, "UI", "version_sheet_on_start", currentApp.name)
        }

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
        val outsideTouchView = dialog.findViewById<View>(com.google.android.material.R.id.touch_outside)
        behavior.isDraggable = true
        behavior.skipCollapsed = true
        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        AppDiagnostics.trace(
            context,
            "UI",
            "version_sheet_behavior_init",
            "${currentApp.name} | cancelable=$isCancelable"
        )
        traceSheetPresentationSnapshot("version_sheet_snapshot_init", bottomSheet, behavior)
        outsideTouchView?.setOnClickListener {
            AppDiagnostics.trace(
                context,
                "UI",
                "version_sheet_outside_dismiss",
                currentApp.name
            )
            dismiss()
        }
        applyInitialMultiItemSheetBounds(bottomSheet, behavior)

        bottomSheetCallback?.let(behavior::removeBottomSheetCallback)
        val callback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheetView: View, newState: Int) {
                AppDiagnostics.trace(
                    requireContext(),
                    "UI",
                    "version_sheet_state_changed",
                    "${currentApp.name} | state=${stateLabel(newState)}"
                )
                traceSheetPresentationSnapshot(
                    "version_sheet_snapshot_state_changed",
                    bottomSheetView,
                    behavior
                )
                updateSheetSnackbarPosition(bottomSheetView)
                if (versionsByKey.size <= 1 && newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetView.post {
                        if (!isAdded || view == null) return@post
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                    return
                }
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

        requestInitialSheetClamp(bottomSheet)
        bottomSheet.post {
            if (!isAdded || view == null) return@post
            adjustSheetHeight()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            traceSheetPresentationSnapshot("version_sheet_snapshot_post_start", bottomSheet, behavior)
            scheduleExpandedEnforcement(bottomSheet, behavior)
        }
    }

    override fun onResume() {
        super.onResume()
        if (awaitingInstallConfirmationReturn) {
            val installKey = pendingInstallKey ?: return
            if (installVisualStateByKey[installKey] != InstallVisualState.WAITING_CONFIRMATION) return
            awaitingInstallConfirmationReturn = false
            rootView.postDelayed({
                if (!isAdded || view == null) return@postDelayed
                if (installVisualStateByKey[installKey] != InstallVisualState.WAITING_CONFIRMATION) return@postDelayed
                startInstallingVisualState(installKey)
            }, 220L)
            return
        }

        if (awaitingUninstallResult) {
            val uninstallKey = pendingUninstallKey ?: return
            rootView.postDelayed({
                if (!isAdded || view == null) return@postDelayed
                if (!awaitingUninstallResult) return@postDelayed
                schedulePendingUninstallRefresh(uninstallKey, UNINSTALL_RESULT_CHECK_DELAY_MS)
            }, 40L)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        context?.let {
            AppDiagnostics.trace(it, "UI", "version_sheet_on_create_view", currentApp.name)
        }

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
        pendingInitialMultiItemClamp = displayedVersions.size > 1
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
        lastVersionSizingTracePayloads.clear()
        lastUninstallTracePayloads.clear()
        completedKeys.clear()
        doneHandledKeys.clear()
        lastKnownStatuses.clear()
        visualProgressByKey.clear()
        activeSessionKeys.clear()
        failedKeys.clear()
        pendingRewardLaunchKeys.clear()
        installVisualStateByKey.clear()
        installVisualProgressByKey.clear()
        restoredInstallVisualKeys.clear()
        installVisualCompletingKeys.clear()
        uninstallVisualStateByKey.clear()
        uninstallTransitioningKeys.clear()
        resolvedUninstallKeys.clear()
        installProgressAnimators.values.forEach(ValueAnimator::cancel)
        installProgressAnimators.clear()
        sheetHeightAnimator?.cancel()
        sheetHeightAnimator = null
        pendingInstallKey = null
        awaitingInstallConfirmationReturn = false
        pendingUninstallKey = null
        awaitingUninstallResult = false
        pendingUninstallStillInstalledRetries = 0
        pendingUninstallStartedAtMs = 0L

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
        val buttonContent = card.findViewById<View>(R.id.versionDownloadContent)
        val buttonLabel = card.findViewById<TextView>(R.id.versionDownloadLabel)
        val buttonIcon = card.findViewById<ImageView>(R.id.versionDownloadIcon)
        val installProgress = card.findViewById<ComposeView>(R.id.versionInstallProgress)
        val uninstallButton = card.findViewById<FrameLayout>(R.id.versionUninstallButton)
        val uninstallLabel = card.findViewById<TextView>(R.id.versionUninstallLabel)
        val uninstallProgress = card.findViewById<ComposeView>(R.id.versionUninstallProgress)
        installProgress.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )
        uninstallProgress.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )
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
        hintViewsByKey = hintViewsByKey + (
            key to VersionHintViews(
                cardRoot = card as ViewGroup,
                installedHint = installedHint,
                downloadedHint = downloadedHint,
                actionRow = actionRow
            )
        )

        buttonViewsByKey[key] = DownloadButtonViews(
            actionRow = actionRow,
            container = button,
            track = buttonTrack,
            fill = buttonFill,
            content = buttonContent,
            label = buttonLabel,
            icon = buttonIcon,
            installProgress = installProgress,
            uninstallButton = uninstallButton,
            uninstallLabel = uninstallLabel,
            uninstallProgress = uninstallProgress
        )
        applyVersionButtonPalette(buttonViewsByKey.getValue(key), ctx)
        attachPressAnimation(button)
        attachPressAnimation(uninstallButton)
        uninstallButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    traceUninstallInteraction(
                        key = key,
                        views = buttonViewsByKey[key] ?: return@setOnTouchListener false,
                        eventName = "uninstall_touch",
                        extra = "action=${motionActionLabel(event.actionMasked)}"
                    )
                }
            }
            false
        }

        button.setOnClickListener {
            val activity = activity ?: return@setOnClickListener
            val item = DownloadRepository.snapshotDownloads()
                .lastOrNull { versionKey(it.versionName, it.url, it.versionCode) == key }
            val action = resolveButtonAction(version, item)
            if (isPrimaryActionLocked(key, action)) {
                traceVersionButtonAction(
                    key = key,
                    action = when (action) {
                        VersionButtonAction.OPEN -> "open"
                        VersionButtonAction.INSTALL -> "install"
                        VersionButtonAction.DOWNLOAD -> "download"
                    },
                    button = "primary",
                    item = item,
                    version = version,
                    result = "ignored_locked"
                )
                return@setOnClickListener
            }
            when (action) {
                VersionButtonAction.OPEN -> traceVersionButtonAction(
                    key = key,
                    action = "open",
                    button = "primary",
                    item = item,
                    version = version
                )

                VersionButtonAction.INSTALL -> traceVersionButtonAction(
                    key = key,
                    action = "install",
                    button = "primary",
                    item = item,
                    version = version
                )

                VersionButtonAction.DOWNLOAD -> Unit
            }
            when (action) {
                VersionButtonAction.OPEN -> {
                    openInstalledApp()
                }

                VersionButtonAction.INSTALL -> {
                    installDownloadedVersion(key, item)
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
                    pendingRewardLaunchKeys.add(key)
                    RewardedAdManager.showThen(
                        activity = activity,
                        onRewardEarned = {
                            pendingRewardLaunchKeys.remove(key)
                            runVersionDownload(
                                version = version,
                                key = key,
                                context = ctx,
                                rootView = rootView
                            )
                        },
                        onAdUnavailable = {
                            pendingRewardLaunchKeys.remove(key)
                            runVersionDownload(
                                version = version,
                                key = key,
                                context = ctx,
                                rootView = rootView
                            )
                        },
                        onAdDismissedWithoutReward = {
                            pendingRewardLaunchKeys.remove(key)
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
            if (isUninstallActionLocked(key)) {
                traceVersionButtonAction(
                    key = key,
                    action = "uninstall",
                    button = "secondary",
                    item = DownloadRepository.snapshotDownloads()
                        .lastOrNull { versionKey(it.versionName, it.url, it.versionCode) == key },
                    version = version,
                    result = "ignored_locked"
                )
                return@setOnClickListener
            }
            traceVersionButtonAction(
                key = key,
                action = "uninstall",
                button = "secondary",
                item = DownloadRepository.snapshotDownloads()
                    .lastOrNull { versionKey(it.versionName, it.url, it.versionCode) == key },
                version = version
            )
            traceUninstallInteraction(
                key = key,
                views = buttonViewsByKey[key] ?: return@setOnClickListener,
                eventName = "uninstall_click"
            )
            launchInstalledAppUninstall(key)
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
        hintLayoutSettleRunnables.remove(key)?.let(mainHandler::removeCallbacks)
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
            submitList(versions) {
                if (!isAdded || view == null || !this@VersionSheet::versionList.isInitialized) {
                    return@submitList
                }
                AppDiagnostics.trace(
                    requireContext(),
                    "UI",
                    "version_sheet_list_committed",
                    "${currentApp.name} | count=${versions.size}"
                )
                requestSheetHeightAdjust()
                requestSheetHeightAdjust(32L)
            }
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
            if (position == 0) {
                attachPrimaryVersionLayoutProbe(holder.itemView, key)
            }
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
            if (shouldDeferAutomaticUiRefresh()) {
                scheduleDeferredUiRefresh()
                return@observe
            }
            updateDownloadButtons(downloads)
            scheduleInstallSnapshotRefresh()
            refreshVersionHints()
        }

        InstallStatusCenter.events.observe(viewLifecycleOwner) { event ->
            if (event.token < installEventObserverStartedAt) return@observe
            handleInstallVisualEvent(event)
            val shouldShowSnackbar =
                event.installStage == null ||
                    event.installStage == InstallStatusCenter.InstallStage.SUCCESS ||
                    event.installStage == InstallStatusCenter.InstallStage.FAILURE
            val hadTrustedInstall = installedLaunchPackage != null
            val stateRefreshDelayMs = stateRefreshDelayFor(event)
            val installSuccessTransitionActive =
                event.installStage == InstallStatusCenter.InstallStage.SUCCESS &&
                    (installVisualCompletingKeys.isNotEmpty() || installTransitioningKeys.isNotEmpty())
            if (event.refreshInstalledState) {
                if (stateRefreshDelayMs > 0L) {
                    mainHandler.postDelayed({
                        if (!isAdded) return@postDelayed
                        scheduleInstallSnapshotRefresh()
                    }, stateRefreshDelayMs)
                } else {
                    scheduleInstallSnapshotRefresh()
                }
            }
            if (installSuccessTransitionActive) {
                if (shouldShowSnackbar && event.message.isNotBlank()) {
                    showSheetSnackbar(event.message, event.indefinite)
                }
                return@observe
            }
            if (hadTrustedInstall) {
                val refreshBlock = {
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
                                if (!runPostUninstallTransitionIfNeeded(event)) {
                                    updateDownloadButtons(DownloadRepository.snapshotDownloads())
                                }
                                refreshVersionHints()
                                if (shouldShowSnackbar && event.message.isNotBlank()) {
                                    showSheetSnackbar(event.message, event.indefinite)
                                }
                            }
                            return@forceRefreshInstalledPackages
                        }
                        if (
                            awaitingUninstallResult &&
                            pendingUninstallKey != null &&
                            !PendingUninstallTracker.matches(currentApp.name, currentApp.packageName)
                        ) {
                            resolvePendingUninstallResult(pendingUninstallKey!!, stillInstalled = true)
                            if (shouldShowSnackbar && event.message.isNotBlank()) {
                                showSheetSnackbar(event.message, event.indefinite)
                            }
                            return@forceRefreshInstalledPackages
                        }
                        updateDownloadButtons(DownloadRepository.snapshotDownloads())
                        refreshVersionHints()
                        if (shouldShowSnackbar && event.message.isNotBlank()) {
                            showSheetSnackbar(event.message, event.indefinite)
                        }
                    }
                }
                if (stateRefreshDelayMs > 0L) {
                    mainHandler.postDelayed({
                        if (!isAdded) return@postDelayed
                        refreshBlock()
                    }, stateRefreshDelayMs)
                } else {
                    refreshBlock()
                }
                return@observe
            }
            if (!shouldShowSnackbar || event.message.isBlank()) return@observe
            showSheetSnackbar(event.message, event.indefinite)
        }
    }

    private fun requestAnimatedDismiss() {
        dismiss()
    }

    private fun showSheetSnackbar(message: String, indefinite: Boolean) {
        if (shouldDeferAutomaticUiRefresh()) {
            pendingSheetSnackbarMessage = message
            pendingSheetSnackbarIndefinite = indefinite
            scheduleDeferredUiRefresh()
            return
        }
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
                if (shouldDeferAutomaticUiRefresh()) {
                    scheduleDeferredUiRefresh()
                    return@post
                }
                updateDownloadButtons(DownloadRepository.snapshotDownloads())
                refreshVersionHints()
                requestSheetHeightAdjust()
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
        markPresentationBlocked(false)
        clearInitialSheetClampListener()
        enforceExpandedRunnable?.let(mainHandler::removeCallbacks)
        enforceExpandedRunnable = null
        zeroContentHeightRetryRunnable?.let(mainHandler::removeCallbacks)
        zeroContentHeightRetryRunnable = null
        val sheet = (dialog as? BottomSheetDialog)?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        val behavior = sheet?.let { BottomSheetBehavior.from(it) }
        (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.touch_outside)
            ?.setOnClickListener(null)
        bottomSheetCallback?.let { callback ->
            behavior?.removeBottomSheetCallback(callback)
        }
        bottomSheetCallback = null
        if (isCurrentSnackbarSheetBound) {
            currentSnackbar?.dismiss()
        }
        currentSnackbar = null
        isCurrentSnackbarSheetBound = false
        pendingSheetSnackbarMessage = null
        pendingSheetSnackbarIndefinite = false
        installSnapshotRunnable?.let(mainHandler::removeCallbacks)
        installSnapshotRunnable = null
        deferredUiRefreshRunnable?.let(mainHandler::removeCallbacks)
        deferredUiRefreshRunnable = null
        sheetHeightAdjustRunnable?.let(mainHandler::removeCallbacks)
        sheetHeightAdjustRunnable = null
        sheetHeightSettleRunnable?.let(mainHandler::removeCallbacks)
        sheetHeightSettleRunnable = null
        hintLayoutSettleRunnables.values.forEach(mainHandler::removeCallbacks)
        hintLayoutSettleRunnables.clear()
        sheetHeightAnimator?.cancel()
        sheetHeightAnimator = null
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
        pendingRewardLaunchKeys.clear()
        installVisualStateByKey.clear()
        installVisualProgressByKey.clear()
        restoredInstallVisualKeys.clear()
        installVisualCompletingKeys.clear()
        installTransitioningKeys.clear()
        uninstallVisualStateByKey.clear()
        uninstallTransitioningKeys.clear()
        resolvedUninstallKeys.clear()
        installProgressAnimators.values.forEach(ValueAnimator::cancel)
        installProgressAnimators.clear()
        pendingInstallKey = null
        awaitingInstallConfirmationReturn = false
        pendingUninstallKey = null
        awaitingUninstallResult = false
        pendingUninstallStillInstalledRetries = 0
        pendingUninstallStartedAtMs = 0L
        zeroContentHeightRetriesRemaining = 0
        super.onDestroyView()
    }

    private fun scheduleExpandedEnforcement(
        bottomSheet: View,
        behavior: BottomSheetBehavior<View>,
        attemptsRemaining: Int = 4
    ) {
        if (attemptsRemaining <= 0) return
        enforceExpandedRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            enforceExpandedRunnable = null
            if (!isAdded || view == null) return@Runnable
            val targetHeight = bottomSheet.layoutParams?.height?.takeIf { it > 0 } ?: return@Runnable
            val parentHeight = (bottomSheet.parent as? View)?.height ?: 0
            val visibleHeight = bottomSheet.height.takeIf { it > 0 } ?: 0
            val looksPeeked =
                visibleHeight in 1 until targetHeight ||
                    (parentHeight > 0 && bottomSheet.top > parentHeight / 2)
            traceSheetPresentationSnapshot(
                "version_sheet_snapshot_enforce_check",
                bottomSheet,
                behavior,
                "visibleH=$visibleHeight targetH=$targetHeight parentH=$parentHeight top=${bottomSheet.top} attempts=$attemptsRemaining looksPeeked=$looksPeeked"
            )

            if (looksPeeked || behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                AppDiagnostics.trace(
                    requireContext(),
                    "UI",
                    "version_sheet_expand_enforced",
                    "${currentApp.name} | visibleH=$visibleHeight targetH=$targetHeight top=${bottomSheet.top} state=${stateLabel(behavior.state)} attempts=$attemptsRemaining"
                )
                bottomSheet.requestLayout()
                behavior.peekHeight = targetHeight
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                traceSheetPresentationSnapshot(
                    "version_sheet_snapshot_enforced",
                    bottomSheet,
                    behavior
                )
            }

            if (attemptsRemaining > 1) {
                scheduleExpandedEnforcement(bottomSheet, behavior, attemptsRemaining - 1)
            }
        }
        enforceExpandedRunnable = runnable
        mainHandler.postDelayed(runnable, 48L)
    }

    private fun shouldDeferAutomaticUiRefresh(): Boolean {
        return System.currentTimeMillis() < suppressUiRefreshUntil ||
            awaitingUninstallResult ||
            installTransitioningKeys.isNotEmpty() ||
            uninstallTransitioningKeys.isNotEmpty() ||
            installVisualCompletingKeys.isNotEmpty()
    }

    private fun beginUiSettleWindow(durationMs: Long) {
        val targetUntil = System.currentTimeMillis() + durationMs
        if (targetUntil > suppressUiRefreshUntil) {
            suppressUiRefreshUntil = targetUntil
        }
        context?.let {
            AppDiagnostics.trace(
                it,
                "UI",
                "sheet_settle_window",
                "${currentApp.name} | duration=${durationMs}ms"
            )
        }
    }

    private fun scheduleDeferredUiRefresh() {
        val delayMs = (suppressUiRefreshUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        deferredUiRefreshRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            deferredUiRefreshRunnable = null
            if (!isAdded || view == null) return@Runnable
            if (shouldDeferAutomaticUiRefresh()) {
                scheduleDeferredUiRefresh()
                return@Runnable
            }
            updateDownloadButtons(DownloadRepository.snapshotDownloads())
            scheduleInstallSnapshotRefresh()
            refreshVersionHints()
            flushPendingSheetSnackbar()
        }
        deferredUiRefreshRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun flushPendingSheetSnackbar() {
        val message = pendingSheetSnackbarMessage ?: return
        pendingSheetSnackbarMessage = null
        val indefinite = pendingSheetSnackbarIndefinite
        pendingSheetSnackbarIndefinite = false
        showSheetSnackbar(message, indefinite)
    }

    private fun stateRefreshDelayFor(event: InstallStatusCenter.Event): Long {
        val eventAppName = event.appName ?: return 0L
        if (!eventAppName.equals(currentApp.name, ignoreCase = true)) return 0L
        return when {
            event.installStage == InstallStatusCenter.InstallStage.SUCCESS -> INSTALL_SUCCESS_SETTLE_DELAY_MS
            awaitingUninstallResult && event.refreshInstalledState -> UNINSTALL_SUCCESS_SETTLE_DELAY_MS
            else -> 0L
        }
    }

    private fun updateDownloadButtons(downloads: List<DownloadItem>) {
        val relevantDownloads = downloads
            .asSequence()
            .filter { downloadPackageKey(it) == currentApp.packageName }
            .associateBy { versionKey(it.versionName, it.url, it.versionCode) }

        if (buttonViewsByKey.isEmpty()) return
        restoreActiveInstallVisualState()
        restorePendingUninstallVisualState()

        buttonViewsByKey.forEach { (key, views) ->
            val context = views.container.context
            val version = versionsByKey[key]
            val item = relevantDownloads[key]
            val visualProgress = visualProgressByKey[key] ?: 0
            val installVisualState = installVisualStateByKey[key]
            val uninstallVisualState = uninstallVisualStateByKey[key]
            val resolvedAction = resolveButtonAction(version, item)

            if (resolvedAction == VersionButtonAction.OPEN) {
                val successAnimationOwnsKey =
                    installVisualCompletingKeys.contains(key) ||
                        installTransitioningKeys.contains(key)
                val hadStaleInstallVisualState =
                    installVisualState != null && !successAnimationOwnsKey
                if (hadStaleInstallVisualState) {
                    clearInstallVisualState(key)
                }
                if (uninstallVisualState == null && hadStaleInstallVisualState) {
                    applyOpenState(views, animateUninstall = false)
                    lastKnownStatuses[key] = item?.status.orEmpty()
                    return@forEach
                }
            }

            if (key in installTransitioningKeys) {
                lastKnownStatuses[key] = item?.status.orEmpty()
                return@forEach
            }

            if (key in uninstallTransitioningKeys) {
                lastKnownStatuses[key] = item?.status.orEmpty()
                return@forEach
            }

            if (uninstallVisualState != null) {
                applyUninstallVisualState(views, uninstallVisualState)
                lastKnownStatuses[key] = item?.status.orEmpty()
                return@forEach
            }

            if (installVisualState != null) {
                if (
                    resolvedAction == VersionButtonAction.OPEN &&
                    !installVisualCompletingKeys.contains(key) &&
                    !installTransitioningKeys.contains(key)
                ) {
                    clearInstallVisualState(key)
                    applyOpenState(views, animateUninstall = false)
                } else {
                    applyInstallVisualState(
                        views = views,
                        state = installVisualState,
                        progress = installVisualProgressByKey[key] ?: 0
                    )
                }
                lastKnownStatuses[key] = item?.status.orEmpty()
                return@forEach
            }

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
            VersionButtonAction.OPEN -> applyOpenState(views, animateUninstall = false)
            VersionButtonAction.INSTALL -> applyInstallState(views)
            VersionButtonAction.DOWNLOAD -> applyIdleState(views)
        }
    }

    private fun restoreActiveInstallVisualState() {
        if (installVisualStateByKey.isNotEmpty()) return
        val activeInstall = InstallStatusCenter.activeStateForApp(currentApp.name) ?: return
        val key = resolveActiveInstallKey(activeInstall) ?: return
        if (!buttonViewsByKey.containsKey(key) || !versionsByKey.containsKey(key)) return
        when (activeInstall.stage) {
            InstallStatusCenter.InstallStage.WAITING_CONFIRMATION -> {
                installVisualStateByKey[key] = InstallVisualState.WAITING_CONFIRMATION
                installVisualProgressByKey.remove(key)
            }
            InstallStatusCenter.InstallStage.PREPARING -> {
                if (activeInstall.confirmationRequested) {
                    installVisualStateByKey[key] = InstallVisualState.INSTALLING
                    installVisualProgressByKey[key] = activeInstall.progress.coerceIn(0, 100)
                } else {
                    installVisualStateByKey[key] = InstallVisualState.WAITING_CONFIRMATION
                    installVisualProgressByKey.remove(key)
                }
            }
            InstallStatusCenter.InstallStage.SUCCESS -> {
                installVisualStateByKey[key] = InstallVisualState.INSTALLING
                installVisualProgressByKey[key] = activeInstall.progress.coerceIn(0, 100)
            }
            InstallStatusCenter.InstallStage.FAILURE -> return
        }
        restoredInstallVisualKeys.add(key)
        pendingInstallKey = key
        awaitingInstallConfirmationReturn = activeInstall.stage == InstallStatusCenter.InstallStage.WAITING_CONFIRMATION
        context?.let {
            AppDiagnostics.trace(
                it,
                "UI",
                "version_sheet_install_visual_restored",
                "${currentApp.name} | key=$key | stage=${activeInstall.stage}"
            )
        }
    }

    private fun handleInstallVisualEvent(event: InstallStatusCenter.Event) {
        val eventAppName = event.appName ?: return
        if (!eventAppName.equals(currentApp.name, ignoreCase = true)) return

        when (event.installStage) {
            InstallStatusCenter.InstallStage.PREPARING -> {
                val activeInstall = InstallStatusCenter.activeStateForApp(currentApp.name)
                val installKey = pendingInstallKey
                    ?: activeInstall?.let(::resolveActiveInstallKey)
                    ?: return
                pendingInstallKey = installKey
                if (activeInstall?.confirmationRequested == true) {
                    installVisualStateByKey[installKey] = InstallVisualState.INSTALLING
                    installVisualProgressByKey[installKey] = event.progress?.coerceIn(0, 100) ?: 0
                    updateDownloadButtons(DownloadRepository.snapshotDownloads())
                } else if (installVisualStateByKey[installKey] != InstallVisualState.WAITING_CONFIRMATION) {
                    installVisualStateByKey[installKey] = InstallVisualState.WAITING_CONFIRMATION
                    installVisualProgressByKey.remove(installKey)
                    updateDownloadButtons(DownloadRepository.snapshotDownloads())
                }
            }

            InstallStatusCenter.InstallStage.WAITING_CONFIRMATION -> {
                val installKey = pendingInstallKey
                    ?: InstallStatusCenter.activeStateForApp(currentApp.name)?.let(::resolveActiveInstallKey)
                    ?: return
                pendingInstallKey = installKey
                awaitingInstallConfirmationReturn = true
                installVisualStateByKey[installKey] = InstallVisualState.WAITING_CONFIRMATION
                installVisualProgressByKey.remove(installKey)
                updateDownloadButtons(DownloadRepository.snapshotDownloads())
            }

            InstallStatusCenter.InstallStage.SUCCESS -> {
                awaitingInstallConfirmationReturn = false
                val installKey = pendingInstallKey ?: installVisualStateByKey.keys.firstOrNull() ?: return
                completeInstallingVisualState(installKey)
            }

            InstallStatusCenter.InstallStage.FAILURE -> {
                awaitingInstallConfirmationReturn = false
                val installKey = pendingInstallKey ?: installVisualStateByKey.keys.firstOrNull()
                pendingInstallKey = null
                if (installKey != null) {
                    val wasWaitingConfirmation =
                        installVisualStateByKey[installKey] == InstallVisualState.WAITING_CONFIRMATION
                    clearInstallVisualState(installKey)
                    if (wasWaitingConfirmation) {
                        installTransitioningKeys.add(installKey)
                        val recentSystemReason = SystemOperationReturnGate.recentReason(RECENT_SYSTEM_RETURN_WINDOW_MS)
                        if (recentSystemReason != null) {
                            context?.let {
                                AppDiagnostics.trace(
                                    it,
                                    "ANIM",
                                    "install_cancel_reverse_delayed",
                                    recentSystemReason
                                )
                            }
                        }
                        val animationDelayMs = if (recentSystemReason != null) {
                            SYSTEM_RETURN_ANIMATION_DELAY_MS
                        } else {
                            0L
                        }
                        mainHandler.postDelayed({
                            if (!isAdded || view == null) return@postDelayed
                            buttonViewsByKey[installKey]?.let(::animateCancelledInstallReturn)
                        }, animationDelayMs)
                        mainHandler.postDelayed({
                            installTransitioningKeys.remove(installKey)
                            if (!isAdded || view == null) return@postDelayed
                            buttonViewsByKey[installKey]?.let(::applyInstallState)
                            updateDownloadButtons(DownloadRepository.snapshotDownloads())
                        }, animationDelayMs + BUTTON_MORPH_FROM_CIRCLE_MS + 80L)
                    } else {
                        updateDownloadButtons(DownloadRepository.snapshotDownloads())
                    }
                }
            }

            else -> Unit
        }
    }

    private fun runPostUninstallTransitionIfNeeded(event: InstallStatusCenter.Event): Boolean {
        if (event.appName?.equals(currentApp.name, ignoreCase = true) != true) return false
        val uninstallKey = pendingUninstallKey ?: resolveInstalledOrOpenVersionKey() ?: return false
        pendingUninstallKey = uninstallKey
        resolvePendingUninstallResult(uninstallKey, stillInstalled = false)
        return true
    }

    private fun restorePendingUninstallVisualState() {
        if (awaitingUninstallResult || uninstallVisualStateByKey.isNotEmpty()) return
        if (!PendingUninstallTracker.matches(currentApp.name, currentApp.packageName)) return
        val uninstallKey = resolveInstalledOrOpenVersionKey() ?: return
        pendingUninstallKey = uninstallKey
        awaitingUninstallResult = true
        pendingUninstallStillInstalledRetries = 0
        pendingUninstallStartedAtMs = SystemClock.uptimeMillis()
        uninstallVisualStateByKey[uninstallKey] = UninstallVisualState.WAITING_RESULT
        context?.let {
            AppDiagnostics.trace(
                it,
                "UI",
                "version_sheet_uninstall_visual_restored",
                "${currentApp.name} | key=$uninstallKey"
            )
        }
    }

    private fun schedulePendingUninstallRefresh(uninstallKey: String, delayMs: Long) {
        rootView.postDelayed({
            if (!isAdded || view == null) return@postDelayed
            if (!awaitingUninstallResult) return@postDelayed
            AppStateCacheManager.forceRefreshInstalledPackages(requireContext()) {
                if (!isAdded || view == null) return@forceRefreshInstalledPackages
                refreshInstalledInfo(requireContext())
                val stillInstalled = AppIdentityStore.isTrustedInstalled(
                    requireContext(),
                    currentApp.packageName,
                    currentApp.name
                )
                val elapsedSinceUninstallStart = if (pendingUninstallStartedAtMs > 0L) {
                    SystemClock.uptimeMillis() - pendingUninstallStartedAtMs
                } else {
                    Long.MAX_VALUE
                }
                val shouldKeepWaiting = stillInstalled &&
                    pendingUninstallStillInstalledRetries < MAX_UNINSTALL_STILL_INSTALLED_RETRIES &&
                    elapsedSinceUninstallStart < UNINSTALL_CONFIRMATION_GRACE_MS
                if (shouldKeepWaiting) {
                    pendingUninstallStillInstalledRetries += 1
                    context?.let {
                        AppDiagnostics.trace(
                            it,
                            "UI",
                            "uninstall_result_retry",
                            "${currentApp.name} | key=$uninstallKey | retry=$pendingUninstallStillInstalledRetries | elapsedMs=$elapsedSinceUninstallStart"
                        )
                    }
                    schedulePendingUninstallRefresh(uninstallKey, UNINSTALL_RESULT_RETRY_DELAY_MS)
                    return@forceRefreshInstalledPackages
                }
                resolvePendingUninstallResult(uninstallKey, stillInstalled)
            }
        }, delayMs)
    }

    private fun resolvePendingUninstallResult(uninstallKey: String, stillInstalled: Boolean) {
        if (!resolvedUninstallKeys.add(uninstallKey)) return
        awaitingUninstallResult = false
        pendingUninstallStillInstalledRetries = 0
        pendingUninstallStartedAtMs = 0L
        if (stillInstalled) {
            context?.let { PendingUninstallTracker.clearIfStillInstalled(it) }
        }
        clearUninstallVisualState(uninstallKey)
        context?.let {
            AppDiagnostics.trace(
                it,
                "UI",
                "uninstall_result_resolved",
                "${currentApp.name} | key=$uninstallKey | stillInstalled=$stillInstalled"
            )
        }
        if (stillInstalled) {
            uninstallTransitioningKeys.add(uninstallKey)
            val recentSystemReason = SystemOperationReturnGate.recentReason(RECENT_SYSTEM_RETURN_WINDOW_MS)
            if (recentSystemReason != null) {
                context?.let {
                    AppDiagnostics.trace(
                        it,
                        "ANIM",
                        "uninstall_cancel_reverse_delayed",
                        recentSystemReason
                    )
                }
            }
            val animationDelayMs = if (recentSystemReason != null) {
                SYSTEM_RETURN_ANIMATION_DELAY_MS
            } else {
                0L
            }
            mainHandler.postDelayed({
                if (!isAdded || view == null) return@postDelayed
                buttonViewsByKey[uninstallKey]?.let(::animateCancelledUninstallReturn)
            }, animationDelayMs)
            mainHandler.postDelayed({
                uninstallTransitioningKeys.remove(uninstallKey)
                if (!isAdded || view == null) return@postDelayed
                updateDownloadButtons(DownloadRepository.snapshotDownloads())
            }, animationDelayMs + BUTTON_MORPH_FROM_CIRCLE_MS + 80L)
            return
        }

        beginUiSettleWindow(UNINSTALL_SUCCESS_SETTLE_DELAY_MS)
        if (preferOpenInstalledAction) {
            mainHandler.postDelayed({
                if (!isAdded || view == null) return@postDelayed
                requestAnimatedDismiss()
            }, POST_SYSTEM_ANIMATION_DELAY_MS)
            return
        }

        uninstallTransitioningKeys.add(uninstallKey)
        mainHandler.postDelayed({
            if (!isAdded || view == null) return@postDelayed
            buttonViewsByKey[uninstallKey]?.let(::animateOpenToInstallState)
        }, POST_SYSTEM_ANIMATION_DELAY_MS)
        mainHandler.postDelayed({
            uninstallTransitioningKeys.remove(uninstallKey)
            if (!isAdded || view == null) return@postDelayed
            updateDownloadButtons(DownloadRepository.snapshotDownloads())
            refreshVersionHints()
        }, UNINSTALL_SUCCESS_SETTLE_DELAY_MS + POST_SYSTEM_ANIMATION_DELAY_MS)
    }

    private fun animateOpenToInstallState(views: DownloadButtonViews) {
        context?.let {
            AppDiagnostics.trace(it, "ANIM", "open_to_install_start", currentApp.name)
        }
        val installExpandedWidth = resolveInstallExpandedWidth(views)
        views.uninstallButton.animate().cancel()
        cancelMorphSurface(views.uninstallButton)
        views.uninstallButton.alpha = 0f
        views.uninstallButton.scaleX = 0.92f
        views.uninstallButton.scaleY = 0.92f
        views.uninstallButton.visibility = View.GONE

        applyVersionButtonPalette(views, views.container.context)
        views.installProgress.animate().cancel()
        views.installProgress.visibility = View.GONE
        applyInstallProgressPalette(views.installProgress, views.container.context, indeterminate = true)
        views.content.visibility = View.VISIBLE
        views.content.animate().cancel()
        views.content.alpha = 0.9f
        views.content.scaleX = 0.988f
        views.content.scaleY = 0.988f
        views.content.translationY = 1.5f
        views.container.isEnabled = true
        views.track.animate().cancel()
        views.track.scaleX = 1f
        views.track.scaleY = 1f
        views.content.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(CONTENT_REVEAL_MS)
            .setInterpolator(enterInterpolator)
            .withLayer()
            .start()
        views.label.text = getString(R.string.download_action_install)
        views.icon.visibility = View.GONE
        views.fill.alpha = 1f
        animateFillTo(views.fill, 0)
        morphInstallTrack(
            views,
            toCircle = false,
            animate = true,
            animateExpansion = true,
            targetExpandedWidth = installExpandedWidth,
            expandFromStart = true
        )
    }

    private fun animateCancelledUninstallReturn(views: DownloadButtonViews) {
        context?.let {
            AppDiagnostics.trace(it, "ANIM", "uninstall_cancel_reverse_start", currentApp.name)
        }
        applyVersionButtonPalette(views, views.container.context)
        resetInstallProgressUi(
            views,
            targetExpandedWidth = resolvePrimaryExpandedWidth(views)
        )
        views.container.isEnabled = true
        views.label.text = getString(R.string.download_action_open)
        views.icon.visibility = View.GONE
        views.fill.alpha = 1f
        animateFillTo(views.fill, 0)

        views.uninstallButton.visibility = View.VISIBLE
        views.uninstallButton.isEnabled = true
        views.uninstallButton.animate().cancel()
        cancelMorphSurface(views.uninstallButton)
        views.uninstallLabel.animate().cancel()
        views.uninstallProgress.animate().cancel()
        views.uninstallProgress.visibility = View.GONE
        applyUninstallProgressPalette(views.uninstallProgress, views.uninstallButton.context, indeterminate = true)
        views.uninstallButton.alpha = 1f
        views.uninstallButton.scaleX = 1f
        views.uninstallButton.scaleY = 1f
        morphUninstallButton(views, toCircle = false, animate = true)
        views.uninstallLabel.visibility = View.VISIBLE
        views.uninstallLabel.alpha = 0f
        views.uninstallLabel.scaleX = 0.94f
        views.uninstallLabel.scaleY = 0.94f
        views.uninstallLabel.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(72L)
            .setDuration(CONTENT_REVEAL_MS)
            .setInterpolator(enterInterpolator)
            .withLayer()
            .start()
        traceUninstallInteraction(
            key = findButtonKey(views),
            views = views,
            eventName = "uninstall_button_state",
            extra = "reason=cancel_reverse_applied"
        )
    }

    private fun animateCancelledInstallReturn(views: DownloadButtonViews) {
        context?.let {
            AppDiagnostics.trace(it, "ANIM", "install_cancel_reverse_start", currentApp.name)
        }
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = true
        views.icon.visibility = View.GONE
        views.fill.alpha = 1f
        animateFillTo(views.fill, 0)
        resetInstallProgressUi(
            views,
            animateContent = true,
            animateReverseMorph = true,
            targetExpandedWidth = resolveInstallExpandedWidth(views)
        )
        views.label.text = getString(R.string.download_action_install)
    }

    private fun clearUninstallVisualState(key: String) {
        uninstallVisualStateByKey.remove(key)
        if (pendingUninstallKey == key) {
            pendingUninstallKey = null
        }
    }

    private fun startInstallingVisualState(key: String) {
        restoredInstallVisualKeys.remove(key)
        installVisualStateByKey[key] = InstallVisualState.INSTALLING
        animateInstallProgressTo(
            key = key,
            targetProgress = 68,
            durationMs = 1400L,
            interpolator = LinearInterpolator()
        ) {
            if (installVisualStateByKey[key] == InstallVisualState.INSTALLING) {
                animateInstallProgressTo(
                    key = key,
                    targetProgress = INSTALL_PROGRESS_CAP,
                    durationMs = 1800L,
                    interpolator = LinearInterpolator()
                ) {
                    if (installVisualStateByKey[key] == InstallVisualState.INSTALLING) {
                        installVisualStateByKey[key] = InstallVisualState.FINALIZING
                        updateDownloadButtons(DownloadRepository.snapshotDownloads())
                    }
                }
            }
        }
        updateDownloadButtons(DownloadRepository.snapshotDownloads())
    }

    private fun completeInstallingVisualState(key: String) {
        restoredInstallVisualKeys.remove(key)
        beginUiSettleWindow(INSTALL_SUCCESS_SETTLE_DELAY_MS)
        context?.let {
            AppDiagnostics.trace(
                it,
                "UI",
                "install_success_animation_start",
                "${currentApp.name} | key=$key"
            )
        }
        installVisualStateByKey[key] = InstallVisualState.INSTALLING
        installVisualCompletingKeys.add(key)
        animateInstallProgressTo(
            key = key,
            targetProgress = 100,
            durationMs = 420L,
            interpolator = DecelerateInterpolator()
        ) {
            installVisualCompletingKeys.remove(key)
            clearInstallVisualState(key)
            if (!isAdded || view == null) return@animateInstallProgressTo
            installTransitioningKeys.add(key)
            rootView.postDelayed({
                if (!isAdded || view == null) return@postDelayed
                buttonViewsByKey[key]?.let(::animateInstallSuccessToOpenState)
            }, INSTALL_OPEN_REVEAL_DELAY_MS)
            rootView.postDelayed({
                installTransitioningKeys.remove(key)
                if (!isAdded || view == null) return@postDelayed
                updateDownloadButtons(DownloadRepository.snapshotDownloads())
            }, INSTALL_OPEN_REVEAL_DELAY_MS + INSTALL_OPEN_REVEAL_EXPAND_MS + 140L)
        }
        updateDownloadButtons(DownloadRepository.snapshotDownloads())
    }

    private fun animateInstallSuccessToOpenState(views: DownloadButtonViews) {
        context?.let {
            AppDiagnostics.trace(it, "ANIM", "install_success_open_reveal_start", currentApp.name)
        }
        applyVersionButtonPalette(views, views.container.context)
        applyUninstallProgressPalette(views.uninstallProgress, views.uninstallButton.context, indeterminate = true)
        views.container.isEnabled = true
        views.uninstallButton.animate().cancel()
        views.uninstallLabel.animate().cancel()
        views.uninstallProgress.animate().cancel()
        ensureInstallTrackCircle(views)
        val trackParams = views.track.layoutParams as? FrameLayout.LayoutParams ?: return
        val currentWidth = views.track.width.takeIf { it > 0 } ?: dp(INSTALL_CIRCLE_SIZE_DP)
        trackParams.width = currentWidth
        trackParams.gravity = Gravity.CENTER
        views.track.layoutParams = trackParams
        views.track.translationX = 0f
        views.track.scaleX = 1f
        views.track.scaleY = 1f
        views.content.animate().cancel()
        views.content.visibility = View.INVISIBLE
        views.content.alpha = 0f
        views.content.scaleX = 1f
        views.content.scaleY = 1f
        views.content.translationY = 0f
        views.label.text = getString(R.string.download_action_open)
        views.label.alpha = 1f
        views.label.translationY = 0f
        views.icon.visibility = View.GONE
        views.fill.alpha = 1f
        animateFillTo(views.fill, 0)
        views.installProgress.visibility = View.VISIBLE
        views.installProgress.alpha = 1f
        views.installProgress.scaleX = 1f
        views.installProgress.scaleY = 1f
        views.installProgress.rotation = 0f
        applyInstallProgressPalette(views.installProgress, views.container.context, indeterminate = true)

        views.uninstallButton.visibility = View.VISIBLE
        ensureUninstallButtonExpanded(views)
        views.uninstallButton.isEnabled = true
        views.uninstallButton.alpha = 0f
        views.uninstallButton.scaleX = 0.94f
        views.uninstallButton.scaleY = 0.94f
        views.uninstallButton.translationX = 0f
        views.uninstallLabel.visibility = View.VISIBLE
        views.uninstallLabel.alpha = 0f
        views.uninstallLabel.scaleX = 0.94f
        views.uninstallLabel.scaleY = 0.94f
        views.uninstallProgress.visibility = View.GONE

        val targetExpandedWidth = resolvePrimaryExpandedWidth(views)
        val shiftDistance = ((targetExpandedWidth - currentWidth) / 2f).coerceAtLeast(0f)
        views.track.animate()
            .translationX(-shiftDistance)
            .setDuration(INSTALL_OPEN_REVEAL_MOVE_MS)
            .setInterpolator(standardInterpolator)
            .withLayer()
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    views.track.animate().setListener(null)
                    val leftAnchoredParams = views.track.layoutParams as? FrameLayout.LayoutParams ?: return
                    leftAnchoredParams.width = currentWidth
                    leftAnchoredParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    views.track.layoutParams = leftAnchoredParams
                    views.track.translationX = 0f
                    hideCircularIndicator(views.installProgress)

                    ValueAnimator.ofInt(currentWidth, targetExpandedWidth).apply {
                        duration = INSTALL_OPEN_REVEAL_EXPAND_MS
                        interpolator = enterInterpolator
                        addUpdateListener { animator ->
                            val width = animator.animatedValue as Int
                            val expandingParams = views.track.layoutParams as FrameLayout.LayoutParams
                            expandingParams.width = width
                            expandingParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            views.track.layoutParams = expandingParams
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                views.content.visibility = View.VISIBLE
                                views.content.animate().cancel()
                                views.content.alpha = 0f
                                views.content.scaleX = 0.985f
                                views.content.scaleY = 0.985f
                                views.content.animate()
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(260L)
                                    .setStartDelay(120L)
                                    .setInterpolator(enterInterpolator)
                                    .withLayer()
                                    .start()
                                views.uninstallButton.animate()
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(240L)
                                    .setStartDelay(220L)
                                    .setInterpolator(DecelerateInterpolator())
                                    .withLayer()
                                    .start()
                                views.uninstallLabel.animate()
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(180L)
                                    .setStartDelay(300L)
                                    .setInterpolator(DecelerateInterpolator())
                                    .withLayer()
                                    .start()
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                val expandedParams = views.track.layoutParams as FrameLayout.LayoutParams
                                expandedParams.width = MATCH_PARENT
                                expandedParams.gravity = Gravity.FILL_HORIZONTAL
                                views.track.layoutParams = expandedParams
                                views.track.translationX = 0f
                                traceUninstallInteraction(
                                    key = findButtonKey(views),
                                    views = views,
                                    eventName = "uninstall_button_state",
                                    extra = "reason=install_success_open_reveal"
                                )
                            }
                        })
                        start()
                    }
                }
            })
            .start()
    }

    private fun clearInstallVisualState(key: String) {
        installVisualStateByKey.remove(key)
        installVisualProgressByKey.remove(key)
        restoredInstallVisualKeys.remove(key)
        installVisualCompletingKeys.remove(key)
        installProgressAnimators.remove(key)?.cancel()
        if (pendingInstallKey == key) {
            pendingInstallKey = null
        }
    }

    private fun animateInstallProgressTo(
        key: String,
        targetProgress: Int,
        durationMs: Long,
        interpolator: android.animation.TimeInterpolator,
        onEnd: (() -> Unit)? = null
    ) {
        val target = targetProgress.coerceIn(0, 100)
        val current = installVisualProgressByKey[key] ?: 0
        installProgressAnimators.remove(key)?.cancel()

        if (current == target) {
            onEnd?.invoke()
            return
        }

        ValueAnimator.ofInt(current, target).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Int
                installVisualProgressByKey[key] = progress
                buttonViewsByKey[key]?.let { views ->
                    if (installVisualStateByKey[key] == InstallVisualState.INSTALLING) {
                        applyInstallVisualState(views, InstallVisualState.INSTALLING, progress)
                    }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    installProgressAnimators.remove(key)
                    onEnd?.invoke()
                }

                override fun onAnimationCancel(animation: Animator) {
                    installProgressAnimators.remove(key)
                }
            })
            installProgressAnimators[key] = this
            start()
        }
    }

    private fun applyInstallVisualState(
        views: DownloadButtonViews,
        state: InstallVisualState,
        progress: Int
    ) {
        val context = views.container.context
        val key = findButtonKey(views)
        val restoreImmediately = key != null && restoredInstallVisualKeys.remove(key)
        val wasShowingProgress = views.installProgress.visibility == View.VISIBLE
        applyVersionButtonPalette(views, context)
        views.track.setBackgroundResource(versionProgressTrackDrawable(context))
        views.container.isEnabled = false
        views.uninstallButton.visibility = View.GONE
        views.container.animate().cancel()
        views.track.animate().cancel()
        views.label.animate().cancel()
        views.icon.animate().cancel()
        views.fill.animate().cancel()
        views.content.alpha = 0f
        views.content.scaleX = 1f
        views.content.scaleY = 1f
        views.content.translationY = 0f
        views.content.visibility = View.INVISIBLE
        views.fill.alpha = 0f
        views.icon.visibility = View.GONE
        if (!wasShowingProgress && !restoreImmediately) {
            showCircularIndicator(views.installProgress)
        } else {
            views.installProgress.visibility = View.VISIBLE
            views.installProgress.alpha = 1f
            views.installProgress.scaleX = 1f
            views.installProgress.scaleY = 1f
            views.installProgress.rotation = 0f
        }
        when (state) {
            InstallVisualState.WAITING_CONFIRMATION ->
                applyInstallProgressPalette(views.installProgress, context, indeterminate = true)

            InstallVisualState.INSTALLING ->
                applyInstallProgressPalette(
                    views.installProgress,
                    context,
                    progress = progress.coerceIn(0, 100),
                    indeterminate = false
                )

            InstallVisualState.FINALIZING ->
                applyInstallProgressPalette(views.installProgress, context, indeterminate = true)
        }
        if (!wasShowingProgress) {
            if (restoreImmediately) {
                ensureInstallTrackCircle(views)
            } else {
                morphInstallTrack(views, toCircle = true, animate = true)
            }
        } else {
            ensureInstallTrackCircle(views)
        }
    }

    private fun applyUninstallVisualState(
        views: DownloadButtonViews,
        state: UninstallVisualState
    ) {
        val wasShowingProgress = views.uninstallProgress.visibility == View.VISIBLE
        applyUninstallProgressPalette(views.uninstallProgress, views.uninstallButton.context, indeterminate = true)
        views.uninstallButton.visibility = View.VISIBLE
        views.uninstallButton.isEnabled = false
        views.uninstallButton.animate().cancel()
        cancelMorphSurface(views.uninstallButton)
        views.uninstallLabel.animate().cancel()
        views.uninstallProgress.animate().cancel()
        views.uninstallLabel.visibility = View.INVISIBLE
        if (!wasShowingProgress) {
            showCircularIndicator(views.uninstallProgress)
        } else {
            views.uninstallProgress.visibility = View.VISIBLE
            views.uninstallProgress.alpha = 1f
            views.uninstallProgress.scaleX = 1f
            views.uninstallProgress.scaleY = 1f
            views.uninstallProgress.rotation = 0f
        }
        when (state) {
            UninstallVisualState.WAITING_RESULT ->
                applyUninstallProgressPalette(views.uninstallProgress, views.uninstallButton.context, indeterminate = true)
        }
        morphUninstallButton(views, toCircle = true, animate = !wasShowingProgress)
        traceUninstallInteraction(
            key = findButtonKey(views),
            views = views,
            eventName = "uninstall_button_state",
            extra = "reason=waiting_result"
        )
    }

    private fun resetInstallProgressUi(
        views: DownloadButtonViews,
        animateContent: Boolean = false,
        animateReverseMorph: Boolean = false,
        targetExpandedWidth: Int? = null,
        expandDurationOverrideMs: Long? = null,
        expandFromStart: Boolean = false
    ) {
        val wasShowingProgress = views.installProgress.visibility == View.VISIBLE
        views.installProgress.animate().cancel()
        hideCircularIndicator(views.installProgress)
        applyInstallProgressPalette(views.installProgress, views.container.context, indeterminate = true)
        views.content.visibility = View.VISIBLE
        if (animateContent && wasShowingProgress) {
            traceMorph(
                "install_content_reveal_start",
                "reverse=$animateReverseMorph | ${currentApp.name}"
            )
            views.content.animate().cancel()
            views.content.alpha = 0f
            views.content.scaleX = 0.982f
            views.content.scaleY = 0.982f
            views.content.translationY = 2f
            views.content.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(CONTENT_REVEAL_MS)
                .setStartDelay(if (animateReverseMorph) 56L else 40L)
                .setInterpolator(enterInterpolator)
                .withLayer()
                .start()
        } else {
            views.content.animate().cancel()
            views.content.alpha = 1f
            views.content.scaleX = 1f
            views.content.scaleY = 1f
            views.content.translationY = 0f
        }
        views.fill.alpha = 1f
        morphInstallTrack(
            views,
            toCircle = false,
            animate = wasShowingProgress,
            animateExpansion = animateReverseMorph,
            targetExpandedWidth = targetExpandedWidth,
            expandDurationOverrideMs = expandDurationOverrideMs,
            expandFromStart = expandFromStart
        )
        if (animateContent && wasShowingProgress) {
            views.track.scaleX = 0.992f
            views.track.scaleY = 0.992f
            views.track.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(if (animateReverseMorph) BUTTON_MORPH_FROM_CIRCLE_MS else TRACK_SETTLE_MS)
                .setInterpolator(standardInterpolator)
                .withLayer()
                .start()
        } else {
            views.track.scaleX = 1f
            views.track.scaleY = 1f
        }
    }

    private fun resetUninstallProgressUi(
        views: DownloadButtonViews,
        force: Boolean = false
    ) {
        if (!force && isUninstallTransitionActive(views)) {
            traceUninstallInteraction(
                key = findButtonKey(views),
                views = views,
                eventName = "uninstall_button_state",
                extra = "reason=reset_progress_skipped_transition"
            )
            return
        }
        val wasShowingProgress = views.uninstallProgress.visibility == View.VISIBLE
        views.uninstallButton.animate().cancel()
        cancelMorphSurface(views.uninstallButton)
        views.uninstallLabel.animate().cancel()
        views.uninstallProgress.animate().cancel()
        views.uninstallButton.isEnabled = true
        views.uninstallButton.alpha = 1f
        views.uninstallButton.scaleX = 1f
        views.uninstallButton.scaleY = 1f
        views.uninstallLabel.visibility = View.VISIBLE
        views.uninstallLabel.alpha = 1f
        views.uninstallLabel.scaleX = 1f
        views.uninstallLabel.scaleY = 1f
        hideCircularIndicator(views.uninstallProgress)
        applyUninstallProgressPalette(views.uninstallProgress, views.uninstallButton.context, indeterminate = true)
        morphUninstallButton(views, toCircle = false, animate = wasShowingProgress)
        traceUninstallInteraction(
            key = findButtonKey(views),
            views = views,
            eventName = "uninstall_button_state",
            extra = "reason=reset_progress"
        )
    }

    private fun isUninstallTransitionActive(views: DownloadButtonViews): Boolean {
        val key = findButtonKey(views) ?: return false
        return key in uninstallTransitioningKeys
    }

    private fun isOpenStateStable(views: DownloadButtonViews): Boolean {
        val trackParams = views.track.layoutParams as? FrameLayout.LayoutParams ?: return false
        val uninstallParams = views.uninstallButton.layoutParams as? LinearLayout.LayoutParams ?: return false
        val isOpenLabel = views.label.text == getString(R.string.download_action_open)
        val isTrackExpanded = trackParams.gravity == Gravity.FILL_HORIZONTAL
        val isUninstallExpanded = uninstallParams.width == 0 && uninstallParams.weight > 0f
        return views.installProgress.visibility != View.VISIBLE &&
            views.content.visibility == View.VISIBLE &&
            views.icon.visibility != View.VISIBLE &&
            views.uninstallButton.visibility == View.VISIBLE &&
            views.uninstallProgress.visibility != View.VISIBLE &&
            views.uninstallLabel.visibility == View.VISIBLE &&
            isOpenLabel &&
            isTrackExpanded &&
            isUninstallExpanded
    }

    private fun isInstallStateStable(views: DownloadButtonViews): Boolean {
        val trackParams = views.track.layoutParams as? FrameLayout.LayoutParams ?: return false
        val isInstallLabel = views.label.text == getString(R.string.download_action_install)
        val isTrackExpanded = trackParams.gravity == Gravity.FILL_HORIZONTAL
        return views.installProgress.visibility != View.VISIBLE &&
            views.content.visibility == View.VISIBLE &&
            views.icon.visibility != View.VISIBLE &&
            views.uninstallButton.visibility != View.VISIBLE &&
            isInstallLabel &&
            isTrackExpanded
    }

    private fun morphInstallTrack(
        views: DownloadButtonViews,
        toCircle: Boolean,
        animate: Boolean,
        animateExpansion: Boolean = false,
        targetExpandedWidth: Int? = null,
        expandDurationOverrideMs: Long? = null,
        expandFromStart: Boolean = false
    ) {
        val params = views.track.layoutParams as? FrameLayout.LayoutParams ?: return
        val targetWidth = if (toCircle) {
            dp(INSTALL_CIRCLE_SIZE_DP)
        } else {
            targetExpandedWidth ?: views.container.width.takeIf { it > 0 }
        } ?: return
        val currentWidth = when {
            params.width > 0 -> params.width
            views.track.width > 0 -> views.track.width
            views.container.width > 0 -> views.container.width
            else -> return
        }
        val targetGravity = if (toCircle) Gravity.CENTER else Gravity.FILL_HORIZONTAL
        traceMorph(
            "install_track_morph_start",
            "toCircle=$toCircle | animate=$animate | expand=$animateExpansion | from=$currentWidth | to=$targetWidth | ${currentApp.name}"
        )
        if (currentWidth == targetWidth && params.gravity == targetGravity) {
            if (!toCircle) {
                params.width = MATCH_PARENT
                params.gravity = Gravity.FILL_HORIZONTAL
                views.track.layoutParams = params
            }
            traceMorph(
                "install_track_morph_skipped",
                "toCircle=$toCircle | width=$currentWidth | ${currentApp.name}"
            )
            return
        }

        if (!animate) {
            if (toCircle) {
                params.width = targetWidth
                params.gravity = Gravity.CENTER
            } else {
                params.width = MATCH_PARENT
                params.gravity = Gravity.FILL_HORIZONTAL
            }
            views.track.layoutParams = params
            traceMorph(
                "install_track_morph_applied",
                "toCircle=$toCircle | target=$targetWidth | ${currentApp.name}"
            )
            return
        }

        animateMorphSurface(views.track, toCircle, "install_track")

        if (!toCircle) {
            params.gravity = Gravity.CENTER
            views.track.layoutParams = params
            if (animateExpansion && expandFromStart) {
                params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                params.width = currentWidth
                views.track.layoutParams = params
            }
            ValueAnimator.ofInt(currentWidth, targetWidth).apply {
                duration = if (animateExpansion) {
                    expandDurationOverrideMs ?: BUTTON_MORPH_FROM_CIRCLE_MS
                } else {
                    250L
                }
                interpolator = enterInterpolator
                addUpdateListener { animator ->
                    val width = animator.animatedValue as Int
                    val layoutParams = views.track.layoutParams as FrameLayout.LayoutParams
                    layoutParams.width = width
                    layoutParams.gravity =
                        if (animateExpansion && expandFromStart) {
                            Gravity.START or Gravity.CENTER_VERTICAL
                        } else {
                            Gravity.CENTER
                        }
                    views.track.layoutParams = layoutParams
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        val layoutParams = views.track.layoutParams as FrameLayout.LayoutParams
                        layoutParams.width = MATCH_PARENT
                        layoutParams.gravity = Gravity.FILL_HORIZONTAL
                        views.track.layoutParams = layoutParams
                        traceMorph(
                            "install_track_morph_end",
                            "toCircle=false | target=$targetWidth | ${currentApp.name}"
                        )
                    }
                })
                start()
            }
            return
        }

        ValueAnimator.ofInt(currentWidth, targetWidth).apply {
            duration = BUTTON_MORPH_TO_CIRCLE_MS
            interpolator = exitInterpolator
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                val layoutParams = views.track.layoutParams as FrameLayout.LayoutParams
                layoutParams.width = width
                layoutParams.gravity = Gravity.CENTER
                views.track.layoutParams = layoutParams
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val layoutParams = views.track.layoutParams as FrameLayout.LayoutParams
                    if (toCircle) {
                        layoutParams.width = targetWidth
                        layoutParams.gravity = Gravity.CENTER
                    } else {
                        layoutParams.width = MATCH_PARENT
                        layoutParams.gravity = Gravity.FILL_HORIZONTAL
                    }
                    views.track.layoutParams = layoutParams
                    traceMorph(
                        "install_track_morph_end",
                        "toCircle=$toCircle | target=$targetWidth | ${currentApp.name}"
                    )
                }
            })
            start()
        }
    }

    private fun ensureInstallTrackCircle(views: DownloadButtonViews) {
        val params = views.track.layoutParams as? FrameLayout.LayoutParams ?: return
        val circleWidth = dp(INSTALL_CIRCLE_SIZE_DP)
        if (params.width == circleWidth && params.gravity == Gravity.CENTER) return
        params.width = circleWidth
        params.gravity = Gravity.CENTER
        views.track.layoutParams = params
    }

    private fun ensureUninstallButtonExpanded(views: DownloadButtonViews) {
        val params = views.uninstallButton.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.width == 0 && params.weight == 1f) return
        params.width = 0
        params.weight = 1f
        views.uninstallButton.layoutParams = params
    }

    private fun morphUninstallButton(views: DownloadButtonViews, toCircle: Boolean, animate: Boolean) {
        val params = views.uninstallButton.layoutParams as? LinearLayout.LayoutParams ?: return
        val targetWidth = if (toCircle) dp(INSTALL_CIRCLE_SIZE_DP) else 0
        val targetWeight = if (toCircle) 0f else 1f
        val currentWidth = when {
            params.width > 0 -> params.width
            views.uninstallButton.width > 0 -> views.uninstallButton.width
            else -> 0
        }
        traceMorph(
            "uninstall_button_morph_start",
            "toCircle=$toCircle | animate=$animate | from=$currentWidth | ${currentApp.name}"
        )

        if (!animate) {
            params.width = targetWidth
            params.weight = targetWeight
            views.uninstallButton.layoutParams = params
            traceMorph(
                "uninstall_button_morph_applied",
                "toCircle=$toCircle | targetWidth=$targetWidth | targetWeight=$targetWeight | ${currentApp.name}"
            )
            return
        }

        animateMorphSurface(views.uninstallButton, toCircle, "uninstall_button")

        if (!toCircle) {
            val expandedWidth = resolveUninstallExpandedWidth(views)
            params.weight = 0f
            views.uninstallButton.layoutParams = params
            ValueAnimator.ofInt(currentWidth, expandedWidth).apply {
                duration = BUTTON_MORPH_FROM_CIRCLE_MS
                interpolator = enterInterpolator
                addUpdateListener { animator ->
                    val width = animator.animatedValue as Int
                    val layoutParams = views.uninstallButton.layoutParams as LinearLayout.LayoutParams
                    layoutParams.width = width
                    layoutParams.weight = 0f
                    views.uninstallButton.layoutParams = layoutParams
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        val layoutParams = views.uninstallButton.layoutParams as LinearLayout.LayoutParams
                        layoutParams.width = 0
                        layoutParams.weight = 1f
                        views.uninstallButton.layoutParams = layoutParams
                        traceMorph(
                            "uninstall_button_morph_end",
                            "toCircle=false | expanded=$expandedWidth | ${currentApp.name}"
                        )
                    }
                })
                start()
            }
            return
        }

        params.weight = 0f
        views.uninstallButton.layoutParams = params

        ValueAnimator.ofInt(currentWidth, targetWidth).apply {
            duration = BUTTON_MORPH_TO_CIRCLE_MS
            interpolator = exitInterpolator
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                val layoutParams = views.uninstallButton.layoutParams as LinearLayout.LayoutParams
                layoutParams.width = width
                layoutParams.weight = 0f
                views.uninstallButton.layoutParams = layoutParams
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val layoutParams = views.uninstallButton.layoutParams as LinearLayout.LayoutParams
                    layoutParams.width = targetWidth
                    layoutParams.weight = 0f
                    views.uninstallButton.layoutParams = layoutParams
                    traceMorph(
                        "uninstall_button_morph_end",
                        "toCircle=true | target=$targetWidth | ${currentApp.name}"
                    )
                }
            })
            start()
        }
    }

    private fun resolveUninstallExpandedWidth(views: DownloadButtonViews): Int {
        val row = views.actionRow as? ViewGroup ?: return views.uninstallButton.width.coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP))
        val uninstallParams = views.uninstallButton.layoutParams as? LinearLayout.LayoutParams
        val rowContentWidth = row.width - row.paddingLeft - row.paddingRight
        val spacingWidth =
            (uninstallParams?.leftMargin ?: 0) +
                (uninstallParams?.rightMargin ?: 0)
        val sharedWidth = (rowContentWidth - spacingWidth).coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP) * 2)
        return (sharedWidth / 2)
            .coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP))
            .coerceAtLeast(views.uninstallButton.width)
    }

    private fun resolvePrimaryExpandedWidth(views: DownloadButtonViews): Int {
        val row = views.actionRow as? ViewGroup ?: return views.track.width.coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP))
        val uninstallParams = views.uninstallButton.layoutParams as? LinearLayout.LayoutParams
        val rowContentWidth = row.width - row.paddingLeft - row.paddingRight
        val spacingWidth =
            (uninstallParams?.leftMargin ?: 0) +
                (uninstallParams?.rightMargin ?: 0)
        val sharedWidth = (rowContentWidth - spacingWidth).coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP) * 2)
        return (sharedWidth / 2)
            .coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP))
    }

    private fun resolveInstallExpandedWidth(views: DownloadButtonViews): Int {
        val row = views.actionRow as? ViewGroup ?: return views.track.width.coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP))
        val containerParams = views.container.layoutParams as? LinearLayout.LayoutParams
        val rowContentWidth = row.width - row.paddingLeft - row.paddingRight
        val horizontalMargins =
            (containerParams?.leftMargin ?: 0) +
                (containerParams?.rightMargin ?: 0)
        return (rowContentWidth - horizontalMargins)
            .coerceAtLeast(dp(INSTALL_CIRCLE_SIZE_DP))
            .coerceAtLeast(views.track.width)
    }

    private fun animateMorphSurface(view: View, toCircle: Boolean, traceLabel: String) {
        view.animate().cancel()
        morphSurfaceAnimators.remove(view)?.cancel()
        val duration = if (toCircle) BUTTON_MORPH_TO_CIRCLE_MS else BUTTON_MORPH_FROM_CIRCLE_MS
        val interpolator = if (toCircle) {
            exitInterpolator
        } else {
            enterInterpolator
        }
        traceMorph(
            "morph_surface_start",
            "target=$traceLabel | toCircle=$toCircle | duration=$duration | ${currentApp.name}"
        )
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val wave = kotlin.math.sin(fraction * Math.PI).toFloat()
                if (toCircle) {
                    view.scaleX = 1f - (wave * 0.008f)
                    view.scaleY = 1f - (wave * 0.013f)
                } else {
                    view.scaleX = 1f + (wave * 0.009f)
                    view.scaleY = 1f + (wave * 0.007f)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    morphSurfaceAnimators.remove(view)
                    view.scaleX = 1f
                    view.scaleY = 1f
                    traceMorph(
                        "morph_surface_end",
                        "target=$traceLabel | toCircle=$toCircle | ${currentApp.name}"
                    )
                }

                override fun onAnimationCancel(animation: Animator) {
                    morphSurfaceAnimators.remove(view)
                    view.scaleX = 1f
                    view.scaleY = 1f
                    traceMorph(
                        "morph_surface_cancel",
                        "target=$traceLabel | toCircle=$toCircle | ${currentApp.name}"
                    )
                }
            })
            morphSurfaceAnimators[view] = this
            start()
        }
    }

    private fun cancelMorphSurface(view: View) {
        morphSurfaceAnimators.remove(view)?.cancel()
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun traceMorph(stage: String, detail: String) {
        context?.let {
            AppDiagnostics.trace(it, "ANIM", stage, detail)
        }
    }

    private fun applyInstallProgressPalette(
        indicator: ComposeView,
        context: android.content.Context,
        progress: Int? = null,
        indeterminate: Boolean = true
    ) {
        val indicatorColor = installButtonContentColor(context)
        val baseTrackColor = installButtonSurfaceColor(context)
        styleCircularIndicator(
            indicator = indicator,
            indicatorColor = indicatorColor,
            trackColor = ColorUtils.setAlphaComponent(baseTrackColor, 255),
            sizeDp = INSTALL_PROGRESS_SIZE_DP,
            thicknessDp = INSTALL_PROGRESS_THICKNESS_DP,
            progress = progress,
            indeterminate = indeterminate
        )
    }

    private fun applyUninstallProgressPalette(
        indicator: ComposeView,
        context: android.content.Context,
        progress: Int? = null,
        indeterminate: Boolean = true
    ) {
        styleCircularIndicator(
            indicator = indicator,
            indicatorColor = ContextCompat.getColor(context, R.color.white),
            trackColor = ColorUtils.setAlphaComponent(
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorErrorContainer,
                    R.color.red
                ),
                (255 * 0.18f).toInt()
            ),
            sizeDp = UNINSTALL_PROGRESS_SIZE_DP,
            thicknessDp = UNINSTALL_PROGRESS_THICKNESS_DP,
            progress = progress,
            indeterminate = indeterminate
        )
    }

    private fun styleCircularIndicator(
        indicator: ComposeView,
        indicatorColor: Int,
        trackColor: Int,
        sizeDp: Int,
        thicknessDp: Int,
        progress: Int? = null,
        indeterminate: Boolean = true
    ) {
        val strokeWidthDp = thicknessDp.dp
        val normalizedProgress = if (indeterminate) null else (progress?.coerceIn(0, 100) ?: 0) / 100f
        val renderState = "$indicatorColor|$trackColor|$sizeDp|$thicknessDp|$normalizedProgress|$indeterminate"
        if (indicator.getTag(R.id.versionSheetSpinnerRenderState) != renderState) {
            indicator.setTag(R.id.versionSheetSpinnerRenderState, renderState)
            indicator.setContent {
                VersionSheetCircularIndicator(
                    progress = normalizedProgress,
                    indicatorColorArgb = indicatorColor,
                    trackColorArgb = trackColor,
                    strokeWidth = strokeWidthDp
                )
            }
        }
        val layoutParams = indicator.layoutParams
        val targetSize = dp(sizeDp)
        if (layoutParams.width != targetSize || layoutParams.height != targetSize) {
            layoutParams.width = targetSize
            layoutParams.height = targetSize
            indicator.layoutParams = layoutParams
        }
    }

    private fun showCircularIndicator(indicator: ComposeView) {
        indicator.animate().cancel()
        indicator.visibility = View.VISIBLE
        indicator.alpha = 0f
        indicator.scaleX = 0.9f
        indicator.scaleY = 0.9f
        indicator.rotation = 0f
        indicator.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(PROGRESS_REVEAL_MS)
            .setInterpolator(enterInterpolator)
            .withLayer()
            .start()
    }

    private fun hideCircularIndicator(indicator: ComposeView) {
        if (indicator.visibility != View.VISIBLE) {
            indicator.alpha = 1f
            indicator.scaleX = 1f
            indicator.scaleY = 1f
            indicator.rotation = 0f
            indicator.visibility = View.GONE
            return
        }
        indicator.animate().cancel()
        indicator.animate()
            .alpha(0f)
            .scaleX(0.93f)
            .scaleY(0.93f)
            .rotation(0f)
            .setDuration(90L)
            .setInterpolator(exitInterpolator)
            .withLayer()
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    indicator.visibility = View.GONE
                    indicator.alpha = 1f
                    indicator.scaleX = 1f
                    indicator.scaleY = 1f
                    indicator.rotation = 0f
                    indicator.animate().setListener(null)
                }
            })
            .start()
    }

    private fun applyIdleState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        resetInstallProgressUi(views)
        resetUninstallProgressUi(views)
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
        animateFillTo(views.fill, 0)
    }

    private fun applyInstallState(views: DownloadButtonViews) {
        if (isInstallStateStable(views)) {
            return
        }
        applyVersionButtonPalette(views, views.container.context)
        resetInstallProgressUi(views)
        resetUninstallProgressUi(views)
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
        animateFillTo(views.fill, 0)
        traceUninstallInteraction(
            key = findButtonKey(views),
            views = views,
            eventName = "uninstall_button_state",
            extra = "reason=apply_install_state"
        )
    }

    private fun applyOpenState(views: DownloadButtonViews, animateUninstall: Boolean = false) {
        if (animateUninstall) {
            context?.let {
                AppDiagnostics.trace(it, "ANIM", "open_reveal_start", currentApp.name)
            }
        } else if (isOpenStateStable(views)) {
            return
        }
        val preserveUninstallTransition = !animateUninstall && isUninstallTransitionActive(views)
        applyVersionButtonPalette(views, views.container.context)
        val targetExpandedWidth = if (animateUninstall) {
            resolvePrimaryExpandedWidth(views)
        } else {
            null
        }
        resetInstallProgressUi(
            views,
            animateContent = animateUninstall,
            targetExpandedWidth = targetExpandedWidth,
            expandDurationOverrideMs = if (animateUninstall) INSTALL_OPEN_REVEAL_EXPAND_MS else null,
            expandFromStart = animateUninstall
        )
        views.container.isEnabled = true
        applyUninstallProgressPalette(views.uninstallProgress, views.uninstallButton.context)
        if (animateUninstall && views.uninstallButton.visibility != View.VISIBLE) {
            views.uninstallButton.animate().cancel()
            views.uninstallLabel.animate().cancel()
            views.uninstallProgress.animate().cancel()
            ensureUninstallButtonExpanded(views)
            views.uninstallButton.isEnabled = true
            views.uninstallLabel.alpha = 1f
            views.uninstallLabel.scaleX = 1f
            views.uninstallLabel.scaleY = 1f
            views.uninstallLabel.visibility = View.VISIBLE
            views.uninstallProgress.visibility = View.GONE
            views.uninstallButton.alpha = 0f
            views.uninstallButton.scaleX = 0.94f
            views.uninstallButton.scaleY = 0.94f
            views.uninstallButton.visibility = View.VISIBLE
            views.uninstallLabel.alpha = 0f
            views.uninstallLabel.scaleX = 0.94f
            views.uninstallLabel.scaleY = 0.94f
            views.container.translationX = 0f
            views.uninstallButton.translationX = 0f
            views.track.scaleX = 0.988f
            views.track.scaleY = 0.988f
            views.track.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
            views.uninstallButton.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .setStartDelay(80L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
            views.uninstallLabel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(160L)
                .setStartDelay(130L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
        } else {
            if (!preserveUninstallTransition) {
                resetUninstallProgressUi(views)
                views.uninstallButton.visibility = View.VISIBLE
                views.container.translationX = 0f
                views.uninstallButton.translationX = 0f
            }
        }
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
        animateFillTo(views.fill, 0)
        traceUninstallInteraction(
            key = findButtonKey(views),
            views = views,
            eventName = "uninstall_button_state",
            extra = when {
                animateUninstall -> "reason=apply_open_state_animated"
                preserveUninstallTransition -> "reason=apply_open_state_preserved_transition"
                else -> "reason=apply_open_state"
            }
        )
    }

    private fun applyProgressState(views: DownloadButtonViews, progress: Int, label: String) {
        val context = views.container.context
        applyVersionButtonPalette(views, context)
        resetInstallProgressUi(views)
        resetUninstallProgressUi(views)
        views.container.isEnabled = false
        views.uninstallButton.visibility = View.GONE
        views.track.animate().cancel()
        views.icon.visibility = View.GONE
        views.label.animate().cancel()
        views.label.alpha = 1f
        views.label.translationY = 0f
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
        resetInstallProgressUi(views)
        resetUninstallProgressUi(views)
        views.container.isEnabled = false
        views.uninstallButton.visibility = View.GONE
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
        resetInstallProgressUi(views)
        resetUninstallProgressUi(views)
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

        val delta = kotlin.math.abs(targetLevel - currentLevel) / 100
        ValueAnimator.ofInt(currentLevel, targetLevel).apply {
            duration = if (targetLevel == 10_000) {
                (290L + (delta * 9L)).coerceAtMost(640L)
            } else {
                (260L + (delta * 10L)).coerceAtMost(460L)
            }
            interpolator = if (targetLevel == 10_000) {
                DecelerateInterpolator()
            } else {
                LinearInterpolator()
            }
            var lastReportedPercent = currentLevel / 100
            addUpdateListener { animator ->
                val level = animator.animatedValue as Int
                imageView.setImageLevel(level)
                imageView.setTag(R.id.versionDownloadFill, level)
                val roundedPercent = ((level + 50) / 100).coerceIn(0, 100)
                if (roundedPercent != lastReportedPercent) {
                    lastReportedPercent = roundedPercent
                    onUpdate?.invoke(roundedPercent)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    imageView.setTag(R.id.versionDownloadTrack, null)
                    onUpdate?.invoke(targetLevel / 100)
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
        val textColor = installButtonContentColor(context)
        views.label.setTextColor(textColor)
        views.icon.imageTintList = ColorStateList.valueOf(textColor)
    }

    private fun installButtonSurfaceColor(context: android.content.Context): Int {
        return if (AppearancePreferences.isDynamicColorEnabled(context)) {
            ThemeColors.color(
                context,
                com.google.android.material.R.attr.colorPrimaryContainer,
                R.color.about_pill
            )
        } else {
            ContextCompat.getColor(context, R.color.about_pill)
        }
    }

    private fun installButtonContentColor(context: android.content.Context): Int {
        val isNightMode =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return if (AppearancePreferences.isDynamicColorEnabled(context) && isNightMode) {
            ContextCompat.getColor(context, R.color.text_on_dark_chip)
        } else if (AppearancePreferences.isDynamicColorEnabled(context)) {
            ThemeColors.color(
                context,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                R.color.text_on_accent_chip
            )
        } else {
            Color.BLACK
        }
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
        val isNightMode =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val fillColor = if (AppearancePreferences.isDynamicColorEnabled(context) && !isNightMode) {
            ColorUtils.blendARGB(
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorPrimaryContainer,
                    R.color.permission_button_surface
                ),
                ThemeColors.color(
                    context,
                    androidx.appcompat.R.attr.colorPrimary,
                    R.color.accent
                ),
                0.38f
            )
        } else if (AppearancePreferences.isDynamicColorEnabled(context)) {
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

    private fun versionProgressTrackDrawable(context: android.content.Context): Int {
        return versionTrackDrawable(context)
    }

    private fun refreshVersionHints() {
        if (!this::versionList.isInitialized) return
        if (shouldDeferAutomaticUiRefresh()) {
            scheduleDeferredUiRefresh()
            return
        }
        val ctx = context ?: return
        val snapshot = currentInstallSnapshot ?: return
        if (hintViewsByKey.isEmpty() || versionsByKey.isEmpty()) return

        var layoutChanged = false
        hintViewsByKey.forEach { (key, views) ->
            val version = versionsByKey[key] ?: return@forEach
            val insight = InstallIntelligence.insight(
                ctx,
                currentApp,
                version,
                snapshot,
                currentLatestVersion
            )

            val nextInstalledText = insight.installedHint.orEmpty()
            val nextInstalledVisibility = if (insight.installedHint.isNullOrBlank()) View.GONE else View.VISIBLE
            val installedChanged =
                views.installedHint.text.toString() != nextInstalledText ||
                    views.installedHint.visibility != nextInstalledVisibility
            views.installedHint.text = nextInstalledText
            applyInstalledHintPalette(views.installedHint, insight)
            val nextDownloadedText = insight.downloadHint.orEmpty()
            val nextDownloadedVisibility = if (insight.downloadHint.isNullOrBlank()) View.GONE else View.VISIBLE
            val downloadedChanged =
                views.downloadedHint.text.toString() != nextDownloadedText ||
                    views.downloadedHint.visibility != nextDownloadedVisibility
            val spacingChanged = actionRowTopMarginPx(
                actionRow = views.actionRow,
                hasInstalledHint = nextInstalledVisibility == View.VISIBLE,
                hasDownloadedHint = nextDownloadedVisibility == View.VISIBLE
            ) != currentActionRowTopMarginPx(views.actionRow)

            val cardLayoutChanged = installedChanged || downloadedChanged || spacingChanged
            if (cardLayoutChanged) {
                layoutChanged = true
                traceVersionSizing(
                    eventName = "version_hint_layout_change",
                    key = key,
                    views = views,
                    extra = buildString {
                        append("installedChanged=")
                        append(installedChanged)
                        append(" downloadedChanged=")
                        append(downloadedChanged)
                        append(" spacingChanged=")
                        append(spacingChanged)
                        append(" nextInstalledVis=")
                        append(visibilityLabel(nextInstalledVisibility))
                        append(" nextDownloadedVis=")
                        append(visibilityLabel(nextDownloadedVisibility))
                    }
                )
                beginHintLayoutTransition(views.cardRoot)
                requestHintLayoutSettle(key, views)
            }

            views.installedHint.visibility = nextInstalledVisibility
            views.downloadedHint.text = nextDownloadedText
            views.downloadedHint.visibility = nextDownloadedVisibility
            updateActionButtonSpacing(
                actionRow = views.actionRow,
                hasInstalledHint = nextInstalledVisibility == View.VISIBLE,
                hasDownloadedHint = nextDownloadedVisibility == View.VISIBLE
            )
        }
        if (layoutChanged) {
            requestSheetHeightAdjust()
            requestSheetHeightSettle()
        }
    }

    private fun beginHintLayoutTransition(cardRoot: ViewGroup) {
        TransitionManager.beginDelayedTransition(
            cardRoot,
            AutoTransition().apply {
                duration = HINT_LAYOUT_TRANSITION_MS
                interpolator = standardInterpolator
            }
        )
    }

    private fun requestSheetHeightAdjust(extraDelayMs: Long = 0L) {
        if (!this::versionList.isInitialized) return
        val delayMs = if (shouldDeferAutomaticUiRefresh()) {
            (suppressUiRefreshUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            0L
        } + extraDelayMs
        sheetHeightAdjustRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            sheetHeightAdjustRunnable = null
            if (!isAdded || view == null) return@Runnable
            if (shouldDeferAutomaticUiRefresh()) {
                requestSheetHeightAdjust()
                return@Runnable
            }
            adjustSheetHeight()
        }
        sheetHeightAdjustRunnable = runnable
        if (delayMs == 0L) {
            versionList.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    private fun requestSheetHeightSettle() {
        if (!this::versionList.isInitialized) return
        val delayMs = if (shouldDeferAutomaticUiRefresh()) {
            (suppressUiRefreshUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        } else {
            HINT_LAYOUT_TRANSITION_MS
        }
        sheetHeightSettleRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            sheetHeightSettleRunnable = null
            if (!isAdded || view == null) return@Runnable
            if (shouldDeferAutomaticUiRefresh()) {
                requestSheetHeightSettle()
                return@Runnable
            }
            versionList.requestLayout()
            versionList.post {
                if (!isAdded || view == null) return@post
                adjustSheetHeight()
                hintViewsByKey.entries.firstOrNull()?.let { (key, views) ->
                    traceVersionSizing(
                        eventName = "sheet_height_settle",
                        key = key,
                        views = views
                    )
                }
            }
        }
        sheetHeightSettleRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun attachPrimaryVersionLayoutProbe(card: View, key: String) {
        val immediateHeight = card.height.takeIf { it > 0 } ?: card.measuredHeight
        if (immediateHeight > 0) {
            AppDiagnostics.trace(
                requireContext(),
                "UI",
                "version_sheet_primary_card_layout",
                "${currentApp.name} | key=$key | h=$immediateHeight | measuredH=${card.measuredHeight} | immediate=true"
            )
            requestSheetHeightAdjust()
            return
        }
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                val height = v.height.takeIf { it > 0 } ?: v.measuredHeight
                if (height <= 0) return
                v.removeOnLayoutChangeListener(this)
                AppDiagnostics.trace(
                    requireContext(),
                    "UI",
                    "version_sheet_primary_card_layout",
                    "${currentApp.name} | key=$key | h=$height | measuredH=${v.measuredHeight} | immediate=false"
                )
                requestSheetHeightAdjust()
            }
        }
        card.addOnLayoutChangeListener(listener)
    }

    private fun requestHintLayoutSettle(key: String, views: VersionHintViews) {
        hintLayoutSettleRunnables.remove(key)?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            hintLayoutSettleRunnables.remove(key)
            if (!isAdded || view == null) return@Runnable
            if (shouldDeferAutomaticUiRefresh()) {
                requestHintLayoutSettle(key, views)
                return@Runnable
            }
            TransitionManager.endTransitions(views.cardRoot)
            views.cardRoot.layoutParams = views.cardRoot.layoutParams.apply {
                height = WRAP_CONTENT
            }
            views.cardRoot.forceLayout()
            views.cardRoot.requestLayout()
            (views.cardRoot.parent as? View)?.requestLayout()
            versionList.requestLayout()
            versionList.post {
                if (!isAdded || view == null) return@post
                TransitionManager.endTransitions(views.cardRoot)
                views.cardRoot.layoutParams = views.cardRoot.layoutParams.apply {
                    height = WRAP_CONTENT
                }
                views.cardRoot.forceLayout()
                views.cardRoot.requestLayout()
                (views.cardRoot.parent as? View)?.requestLayout()
                traceVersionSizing(
                    eventName = "version_card_layout_settle",
                    key = key,
                    views = views
                )
                adjustSheetHeight()
            }
        }
        hintLayoutSettleRunnables[key] = runnable
        mainHandler.postDelayed(runnable, HINT_LAYOUT_TRANSITION_MS + 24L)
    }

    private fun currentActionRowTopMarginPx(actionRow: View): Int {
        val params = actionRow.layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
        return params.topMargin
    }

    private fun traceVersionSizing(
        eventName: String,
        key: String,
        views: VersionHintViews,
        extra: String? = null
    ) {
        val context = context ?: return
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val listContainer = dialog?.findViewById<View>(R.id.versionListContainer)
        val actionParams = views.actionRow.layoutParams as? ViewGroup.MarginLayoutParams
        val cardParams = views.cardRoot.layoutParams as? ViewGroup.MarginLayoutParams
        val installedParams = views.installedHint.layoutParams as? ViewGroup.MarginLayoutParams
        val downloadedParams = views.downloadedHint.layoutParams as? ViewGroup.MarginLayoutParams
        val payload = buildString {
            append(currentApp.name)
            append(" | key=")
            append(key)
            if (!extra.isNullOrBlank()) {
                append(" | ")
                append(extra)
            }
            append(" | cardH=")
            append(views.cardRoot.height)
            append(" cardMeasuredH=")
            append(views.cardRoot.measuredHeight)
            append(" cardBottomMargin=")
            append(cardParams?.bottomMargin ?: "na")
            append(" installedVis=")
            append(visibilityLabel(views.installedHint.visibility))
            append(" installedH=")
            append(views.installedHint.height)
            append(" installedMeasuredH=")
            append(views.installedHint.measuredHeight)
            append(" installedTopMargin=")
            append(installedParams?.topMargin ?: "na")
            append(" downloadedVis=")
            append(visibilityLabel(views.downloadedHint.visibility))
            append(" downloadedH=")
            append(views.downloadedHint.height)
            append(" downloadedMeasuredH=")
            append(views.downloadedHint.measuredHeight)
            append(" downloadedTopMargin=")
            append(downloadedParams?.topMargin ?: "na")
            append(" actionTopMargin=")
            append(actionParams?.topMargin ?: "na")
            append(" actionH=")
            append(views.actionRow.height)
            append(" listH=")
            append(if (this@VersionSheet::versionList.isInitialized) versionList.height else -1)
            append(" listMeasuredH=")
            append(if (this@VersionSheet::versionList.isInitialized) versionList.measuredHeight else -1)
            append(" listRange=")
            append(if (this@VersionSheet::versionList.isInitialized) versionList.computeVerticalScrollRange() else -1)
            append(" listContainerH=")
            append(listContainer?.height ?: -1)
            append(" sheetH=")
            append(bottomSheet?.height ?: -1)
            append(" sheetLpH=")
            append(bottomSheet?.layoutParams?.height ?: "na")
        }
        val traceKey = "$eventName|$key"
        if (lastVersionSizingTracePayloads[traceKey] == payload) return
        lastVersionSizingTracePayloads[traceKey] = payload
        AppDiagnostics.trace(context, "UI", eventName, payload)
    }

    private fun traceSheetPresentationSnapshot(
        eventName: String,
        bottomSheet: View,
        behavior: BottomSheetBehavior<View>,
        extra: String? = null
    ) {
        val context = context ?: return
        val parent = bottomSheet.parent as? View
        val payload = buildString {
            append(currentApp.name)
            if (!extra.isNullOrBlank()) {
                append(" | ")
                append(extra)
            }
            append(" | state=")
            append(stateLabel(behavior.state))
            append(" top=")
            append(bottomSheet.top)
            append(" bottom=")
            append(bottomSheet.bottom)
            append(" height=")
            append(bottomSheet.height)
            append(" measuredH=")
            append(bottomSheet.measuredHeight)
            append(" y=")
            append(bottomSheet.y)
            append(" translationY=")
            append(bottomSheet.translationY)
            append(" alpha=")
            append(String.format("%.2f", bottomSheet.alpha))
            append(" shown=")
            append(bottomSheet.isShown)
            append(" attached=")
            append(bottomSheet.isAttachedToWindow)
            append(" lpH=")
            append(bottomSheet.layoutParams?.height ?: "na")
            append(" peekH=")
            append(behavior.peekHeight)
            append(" hideable=")
            append(behavior.isHideable)
            append(" draggable=")
            append(behavior.isDraggable)
            append(" parentH=")
            append(parent?.height ?: -1)
            append(" parentMeasuredH=")
            append(parent?.measuredHeight ?: -1)
            append(" rootH=")
            append(view?.height ?: -1)
        }
        AppDiagnostics.trace(context, "UI", eventName, payload)
    }

    private fun actionRowTopMarginPx(
        actionRow: View,
        hasInstalledHint: Boolean,
        hasDownloadedHint: Boolean
    ): Int {
        val density = actionRow.resources.displayMetrics.density
        val targetTopMarginDp = when {
            hasInstalledHint && hasDownloadedHint -> 20
            hasInstalledHint || hasDownloadedHint -> 17
            else -> 14
        }
        return (targetTopMarginDp * density).toInt()
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
                    .setDuration(170L)
                    .setInterpolator(enterInterpolator)
                    .start()
            } else {
                scrollHintContainer.animate()
                    .alpha(0f)
                    .translationY(dp(4).toFloat())
                    .setDuration(130L)
                    .setInterpolator(exitInterpolator)
                    .withEndAction {
                        scrollHintContainer.visibility = View.GONE
                        scrollHintContainer.alpha = 1f
                        scrollHintContainer.translationY = 0f
                    }
                    .start()
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

    private fun requestInitialSheetClamp(bottomSheet: View) {
        clearInitialSheetClampListener()
        if (!pendingInitialMultiItemClamp) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (!isAdded || view == null) {
                clearInitialSheetClampListener()
                return@OnPreDrawListener true
            }
            if (!pendingInitialMultiItemClamp || versionsByKey.size <= 1 || maxSheetHeightPx <= 0) {
                pendingInitialMultiItemClamp = false
                clearInitialSheetClampListener()
                return@OnPreDrawListener true
            }

            adjustSheetHeight()
            val targetHeight = bottomSheet.layoutParams?.height?.takeIf { it > 0 } ?: 0
            val currentHeight = bottomSheet.height.takeIf { it > 0 } ?: 0
            val needsClampPass = targetHeight > 0 && (currentHeight <= 0 || currentHeight != targetHeight)
            if (needsClampPass) {
                return@OnPreDrawListener false
            }

            pendingInitialMultiItemClamp = false
            clearInitialSheetClampListener()
            true
        }
        initialSheetClampListener = listener
        bottomSheet.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun applyInitialMultiItemSheetBounds(
        bottomSheet: View,
        behavior: BottomSheetBehavior<View>
    ) {
        if (!pendingInitialMultiItemClamp || versionsByKey.size <= 1 || maxSheetHeightPx <= 0) return
        val initialHeight = maxSheetHeightPx
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = initialHeight
        }
        behavior.peekHeight = initialHeight
        bottomSheet.requestLayout()
    }

    private fun clearInitialSheetClampListener() {
        val listener = initialSheetClampListener ?: return
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val observer = bottomSheet?.viewTreeObserver
        if (observer != null && observer.isAlive) {
            observer.removeOnPreDrawListener(listener)
        }
        initialSheetClampListener = null
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

        val currentListContainerHeight = listContainer.height.takeIf { it > 0 }
            ?: (listContainer.layoutParams?.height?.takeIf { it > 0 })
            ?: versionList.height
        val currentSheetHeight = bottomSheet.height.takeIf { it > 0 }
            ?: (bottomSheet.layoutParams?.height?.takeIf { it > 0 })
            ?: 0
        val scrollRangeContentHeight = versionList.computeVerticalScrollRange()
        val childMeasuredContentHeight = visibleVersionListContentHeight()
        val measuredListContentHeight = maxOf(
            scrollRangeContentHeight,
            childMeasuredContentHeight,
            versionList.minimumHeight
        )
        val measuredSingleFallbackHeight = if (
            measuredListContentHeight <= 0 &&
                versionsByKey.size == 1
        ) {
            measureSingleVersionFallbackHeight(bottomSheet)
        } else {
            0
        }
        val cachedSingleVersionHeight = if (versionsByKey.size == 1) {
            cachedSingleVersionListHeight(singleVersionCacheKey())
        } else {
            null
        }
        val listContentHeight = if (
            measuredListContentHeight <= 0 &&
                measuredSingleFallbackHeight > 0
        ) {
            AppDiagnostics.trace(
                requireContext(),
                "UI",
                "version_sheet_single_measure_fallback",
                "${currentApp.name} | listH=$measuredSingleFallbackHeight"
            )
            measuredSingleFallbackHeight
        } else if (
            measuredListContentHeight <= 0 &&
                versionsByKey.size == 1 &&
                cachedSingleVersionHeight != null
        ) {
            AppDiagnostics.trace(
                requireContext(),
                "UI",
                "version_sheet_cached_single_height_used",
                "${currentApp.name} | listH=$cachedSingleVersionHeight"
            )
            cachedSingleVersionHeight
        } else {
            measuredListContentHeight
        }
        if (measuredListContentHeight == 0 && versionAdapter.itemCount > 0 && zeroContentHeightRetriesRemaining < 6) {
            zeroContentHeightRetriesRemaining += 1
            scheduleZeroContentHeightRetry()
        } else if (measuredListContentHeight > 0) {
            zeroContentHeightRetriesRemaining = 0
            zeroContentHeightRetryRunnable?.let(mainHandler::removeCallbacks)
            zeroContentHeightRetryRunnable = null
        }
        val stableBaseHeight = stableSheetBaseHeight(content, listContainer)
        val cappedListHeight = (effectiveMaxSheetHeightPx - stableBaseHeight).coerceAtLeast(0)
        val targetListHeight = minOf(listContentHeight, cappedListHeight)
        if (versionsByKey.size == 1 && targetListHeight > 0) {
            cacheSingleVersionListHeight(singleVersionCacheKey(), targetListHeight)
        }
        val baseHeight = stableBaseHeight
        hasScrollableVersionContent = listContentHeight > targetListHeight
        listContainer.layoutParams = listContainer.layoutParams.apply {
            height = if (targetListHeight >= listContentHeight) WRAP_CONTENT else targetListHeight
        }
        listContainer.requestLayout()
        updateScrollHint(hasScrollableVersionContent && shouldShowScrollHint(versionList))
        val targetHeight = (baseHeight + targetListHeight)
            .coerceAtLeast(0)
            .coerceAtMost(effectiveMaxSheetHeightPx)
        val hasOpeningOvershoot =
            versionsByKey.size > 1 &&
                currentListContainerHeight > targetListHeight &&
                currentSheetHeight > targetHeight
        hintViewsByKey.entries.firstOrNull()?.let { (key, views) ->
            traceVersionSizing(
                eventName = "sheet_height_adjust",
                key = key,
                views = views,
                extra = "baseH=$baseHeight targetListH=$targetListHeight listContentH=$listContentHeight targetSheetH=$targetHeight viewportH=$currentListContainerHeight"
            )
        }

        val appliedTargetHeight = if (targetHeight > 0) targetHeight else WRAP_CONTENT
        animateSheetHeight(
            bottomSheet = bottomSheet,
            behavior = behavior,
            targetHeight = appliedTargetHeight,
            animate = !hasOpeningOvershoot
        )
        if (
            behavior.state != BottomSheetBehavior.STATE_EXPANDED &&
            behavior.state != BottomSheetBehavior.STATE_DRAGGING &&
            behavior.state != BottomSheetBehavior.STATE_SETTLING &&
            behavior.state != BottomSheetBehavior.STATE_HIDDEN
        ) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        bottomSheet.requestLayout()
        traceSheetPresentationSnapshot(
            "version_sheet_snapshot_height_adjust",
            bottomSheet,
            behavior,
            "targetH=$appliedTargetHeight effectiveMaxH=$effectiveMaxSheetHeightPx targetListH=$targetListHeight listContentH=$listContentHeight scrollRangeH=$scrollRangeContentHeight childContentH=$childMeasuredContentHeight"
        )
    }

    private fun visibleVersionListContentHeight(): Int {
        if (!this::versionList.isInitialized) return 0
        if (versionList.childCount == 0) return 0
        var contentHeight = versionList.paddingTop + versionList.paddingBottom
        for (index in 0 until versionList.childCount) {
            val child = versionList.getChildAt(index) ?: continue
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
            val childHeight = child.height.takeIf { it > 0 } ?: child.measuredHeight
            if (childHeight <= 0) continue
            contentHeight += childHeight
            contentHeight += lp?.topMargin ?: 0
            contentHeight += lp?.bottomMargin ?: 0
        }
        return contentHeight
    }

    private fun measureSingleVersionFallbackHeight(bottomSheet: View): Int {
        val version = versionsByKey.values.firstOrNull() ?: return 0
        val availableWidth = (
            versionList.width.takeIf { it > 0 }
                ?: bottomSheet.width.takeIf { it > 0 }
                ?: rootView.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels
            ) - versionList.paddingLeft - versionList.paddingRight
        if (availableWidth <= 0) return 0

        val itemView = layoutInflater.inflate(R.layout.item_version_sheet, versionList, false)
        val key = "${versionKey(version)}#measure"
        bindVersionCard(
            card = itemView,
            ctx = itemView.context,
            version = version,
            latestVersionNumber = currentLatestVersion?.version,
            key = key
        )

        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            availableWidth.coerceAtLeast(1),
            View.MeasureSpec.EXACTLY
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        itemView.measure(widthSpec, heightSpec)
        clearVersionView(key)
        val measuredHeight = itemView.measuredHeight
        if (measuredHeight <= 0) return 0
        return measuredHeight + versionList.paddingTop + versionList.paddingBottom
    }

    private fun singleVersionCacheKey(): String {
        return currentApp.packageName.ifBlank { currentApp.name.lowercase() }
    }

    private fun scheduleZeroContentHeightRetry() {
        zeroContentHeightRetryRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            zeroContentHeightRetryRunnable = null
            if (!isAdded || view == null) return@Runnable
            AppDiagnostics.trace(
                requireContext(),
                "UI",
                "version_sheet_zero_content_retry",
                "${currentApp.name} | remaining=$zeroContentHeightRetriesRemaining | items=${versionAdapter.itemCount}"
            )
            adjustSheetHeight()
        }
        zeroContentHeightRetryRunnable = runnable
        mainHandler.postDelayed(runnable, 48L)
    }

    private fun stableSheetBaseHeight(content: View, listContainer: View): Int {
        if (content !is ViewGroup) {
            val listHeight = listContainer.height.takeIf { it > 0 } ?: 0
            return (content.height - listHeight).coerceAtLeast(0)
        }
        var baseHeight = content.paddingTop + content.paddingBottom
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child === listContainer || child.visibility == View.GONE) continue
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
            baseHeight += child.height
            baseHeight += lp?.topMargin ?: 0
            baseHeight += lp?.bottomMargin ?: 0
        }
        return baseHeight.coerceAtLeast(0)
    }

    private fun animateSheetHeight(
        bottomSheet: View,
        behavior: BottomSheetBehavior<View>,
        targetHeight: Int,
        animate: Boolean = true
    ) {
        val currentHeight = bottomSheet.height.takeIf { it > 0 }
            ?: (bottomSheet.layoutParams?.height ?: 0)
        if (!animate || currentHeight <= 0 || targetHeight <= 0 || currentHeight == targetHeight) {
            sheetHeightAnimator?.cancel()
            sheetHeightAnimator = null
            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = targetHeight
            }
            behavior.peekHeight = if (targetHeight == WRAP_CONTENT) 0 else targetHeight
            updateSheetSnackbarPosition(bottomSheet)
            return
        }

        sheetHeightAnimator?.cancel()
        sheetHeightAnimator = ValueAnimator.ofInt(currentHeight, targetHeight).apply {
            val delta = kotlin.math.abs(targetHeight - currentHeight)
            duration = (SHEET_HEIGHT_ANIMATION_MS + (delta / 6L)).coerceAtMost(290L)
            interpolator = if (targetHeight < currentHeight) {
                standardInterpolator
            } else {
                enterInterpolator
            }
            addUpdateListener { animator ->
                val animatedHeight = animator.animatedValue as Int
                bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                    height = animatedHeight
                }
                behavior.peekHeight = animatedHeight
                bottomSheet.requestLayout()
                updateSheetSnackbarPosition(bottomSheet)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    sheetHeightAnimator = null
                }

                override fun onAnimationEnd(animation: Animator) {
                    bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                        height = targetHeight
                    }
                    behavior.peekHeight = targetHeight
                    bottomSheet.requestLayout()
                    updateSheetSnackbarPosition(bottomSheet)
                    sheetHeightAnimator = null
                }
            })
            start()
        }
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

    private fun resolveInstalledOrOpenVersionKey(): String? {
        val relevantDownloads = DownloadRepository.snapshotDownloads()
            .asSequence()
            .filter { downloadPackageKey(it) == currentApp.packageName }
            .associateBy { versionKey(it.versionName, it.url, it.versionCode) }

        val openEntry = versionsByKey.entries.firstOrNull { (key, version) ->
            resolveButtonAction(version, relevantDownloads[key]) == VersionButtonAction.OPEN
        }
        if (openEntry != null) {
            return openEntry.key
        }

        val installedName = installedVersionName?.trim()?.takeIf { it.isNotBlank() }
        if (installedName != null) {
            val installedMatch = versionsByKey.entries.firstOrNull { (_, version) ->
                version.version_name.equals(installedName, ignoreCase = true)
            }
            if (installedMatch != null) {
                return installedMatch.key
            }
        }

        return null
    }

    private fun resolveActiveInstallKey(activeInstall: InstallStatusCenter.ActiveInstallState): String? {
        val exactKey = activeInstall.versionKey
        if (buttonViewsByKey.containsKey(exactKey) && versionsByKey.containsKey(exactKey)) {
            return exactKey
        }

        val normalizedApkPath = activeInstall.apkPath?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedApkPath != null) {
            val matchingDownload = DownloadRepository.snapshotDownloads()
                .lastOrNull { item ->
                    downloadPackageKey(item) == currentApp.packageName &&
                        item.filePath.equals(normalizedApkPath, ignoreCase = true)
                }
            if (matchingDownload != null) {
                val resolvedKey = versionKey(
                    matchingDownload.versionName,
                    matchingDownload.url,
                    matchingDownload.versionCode
                )
                if (buttonViewsByKey.containsKey(resolvedKey) && versionsByKey.containsKey(resolvedKey)) {
                    return resolvedKey
                }
            }
        }

        val fallbackDownload = DownloadRepository.snapshotDownloads()
            .asReversed()
            .firstOrNull { item ->
                downloadPackageKey(item) == currentApp.packageName &&
                    downloadFileExists(item)
            }
            ?: return null
        val fallbackKey = versionKey(
            fallbackDownload.versionName,
            fallbackDownload.url,
            fallbackDownload.versionCode
        )
        return fallbackKey.takeIf { buttonViewsByKey.containsKey(it) && versionsByKey.containsKey(it) }
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

    private fun isPrimaryActionLocked(key: String, action: VersionButtonAction): Boolean {
        if (installTransitioningKeys.contains(key) || uninstallTransitioningKeys.contains(key)) {
            return true
        }
        return when (action) {
            VersionButtonAction.OPEN -> false
            VersionButtonAction.INSTALL ->
                pendingInstallKey == key ||
                    installVisualStateByKey.containsKey(key) ||
                    installVisualCompletingKeys.contains(key)
            VersionButtonAction.DOWNLOAD ->
                hasActiveVisualState(key) ||
                    pendingRewardLaunchKeys.contains(key)
        }
    }

    private fun isUninstallActionLocked(key: String): Boolean {
        return (awaitingUninstallResult && pendingUninstallKey == key) ||
            uninstallVisualStateByKey.containsKey(key) ||
            uninstallTransitioningKeys.contains(key) ||
            installTransitioningKeys.contains(key)
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
        }.coerceAtLeast(gapPx)
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

    private fun findButtonKey(views: DownloadButtonViews): String {
        return buttonViewsByKey.entries.firstOrNull { it.value === views }?.key ?: "unknown"
    }

    private fun traceUninstallInteraction(
        key: String,
        views: DownloadButtonViews,
        eventName: String,
        extra: String? = null
    ) {
        val context = context ?: return
        val uninstallParams = views.uninstallButton.layoutParams as? LinearLayout.LayoutParams
        val trackParams = views.track.layoutParams as? FrameLayout.LayoutParams
        val payload = buildString {
            append(currentApp.name)
            append(" | key=")
            append(key)
            if (!extra.isNullOrBlank()) {
                append(" | ")
                append(extra)
            }
            append(" | buttonVis=")
            append(visibilityLabel(views.uninstallButton.visibility))
            append(" enabled=")
            append(views.uninstallButton.isEnabled)
            append(" clickable=")
            append(views.uninstallButton.isClickable)
            append(" shown=")
            append(views.uninstallButton.isShown)
            append(" hasClick=")
            append(views.uninstallButton.hasOnClickListeners())
            append(" alpha=")
            append(String.format("%.2f", views.uninstallButton.alpha))
            append(" pressed=")
            append(views.uninstallButton.isPressed)
            append(" width=")
            append(views.uninstallButton.width)
            append(" lpWidth=")
            append(uninstallParams?.width ?: "na")
            append(" lpWeight=")
            append(uninstallParams?.weight ?: "na")
            append(" labelVis=")
            append(visibilityLabel(views.uninstallLabel.visibility))
            append(" progressVis=")
            append(visibilityLabel(views.uninstallProgress.visibility))
            append(" trackWidth=")
            append(views.track.width)
            append(" trackGravity=")
            append(trackParams?.gravity ?: "na")
            append(" containerEnabled=")
            append(views.container.isEnabled)
            append(" awaitingUninstall=")
            append(awaitingUninstallResult)
            append(" pendingKey=")
            append(pendingUninstallKey ?: "null")
        }
        val traceKey = "$eventName|$key"
        if (lastUninstallTracePayloads[traceKey] == payload) return
        lastUninstallTracePayloads[traceKey] = payload
        AppDiagnostics.trace(context, "UI", eventName, payload)
    }

    private fun traceVersionButtonAction(
        key: String,
        action: String,
        button: String,
        version: Version? = null,
        item: DownloadItem? = null,
        result: String? = null
    ) {
        val context = context ?: return
        val payload = buildString {
            append(currentApp.name)
            append(" | key=")
            append(key)
            append(" | button=")
            append(button)
            append(" | action=")
            append(action)
            if (!result.isNullOrBlank()) {
                append(" | result=")
                append(result)
            }
            if (version != null) {
                append(" | versionName=")
                append(version.version_name)
                append(" | versionCode=")
                append(version.version)
            }
            if (item != null) {
                append(" | downloadStatus=")
                append(item.status)
                append(" | downloadedInstalled=")
                append(item.installed)
            }
            append(" | trustedInstalled=")
            append(installedLaunchPackage != null)
        }
        AppDiagnostics.trace(context, "UI", "version_button_action", payload)
    }

    private fun motionActionLabel(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "down"
            MotionEvent.ACTION_UP -> "up"
            MotionEvent.ACTION_CANCEL -> "cancel"
            else -> action.toString()
        }
    }

    private fun visibilityLabel(visibility: Int): String {
        return when (visibility) {
            View.VISIBLE -> "visible"
            View.INVISIBLE -> "invisible"
            View.GONE -> "gone"
            else -> visibility.toString()
        }
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

    private fun launchInstalledAppUninstall(key: String) {
        val context = context ?: return
        if (awaitingUninstallResult && pendingUninstallKey == key) {
            context.let {
                AppDiagnostics.trace(it, "UI", "uninstall_click_ignored_pending", "${currentApp.name} | key=$key")
            }
            return
        }
        refreshInstalledInfo(context)
        val candidates = linkedSetOf<String>().apply {
            installedLaunchPackage?.let(::add)
            InstallAliasStore.resolveForAppName(context, currentApp.name)?.let(::add)
            InstallAliasStore.resolveForPackage(context, currentApp.packageName)?.let(::add)
            currentApp.packageName.takeIf { it.isNotBlank() }?.let(::add)
        }.filter { it.isNotBlank() }

        if (candidates.isEmpty()) {
            context.let {
                AppDiagnostics.trace(
                    it,
                    "UI",
                    "uninstall_launch_blocked",
                    "${currentApp.name} | key=$key | source=no_candidates"
                )
            }
            AppStateCacheManager.forceRefreshInstalledPackages(context) {
                if (!isAdded || view == null) return@forceRefreshInstalledPackages
                refreshInstalledInfo(requireContext())
                context?.let {
                    AppDiagnostics.trace(
                        it,
                        "UI",
                        "uninstall_click_retry_launch",
                        "${currentApp.name} | key=$key | source=no_candidates"
                    )
                }
                launchInstalledAppUninstall(key)
            }
            return
        }

        val installedCandidates = candidates.filter {
            AppStateCacheManager.isInstalled(context, it, currentApp.name)
        }
        val candidatePool = if (installedCandidates.isNotEmpty()) installedCandidates else candidates

        val packageManager = context.packageManager
        val uninstallTarget = candidatePool.firstOrNull { candidate ->
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", candidate, null))
                .resolveActivity(packageManager) != null
        } ?: candidatePool.firstOrNull()

        if (uninstallTarget == null) {
            context.let {
                AppDiagnostics.trace(
                    it,
                    "UI",
                    "uninstall_launch_blocked",
                    "${currentApp.name} | key=$key | source=no_resolved_target | candidates=$candidatePool"
                )
            }
            AppSnackbar.show(rootView, getString(R.string.install_failed))
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
            context.let {
                AppDiagnostics.trace(
                    it,
                    "UI",
                    "uninstall_launch_start",
                    "${currentApp.name} | key=$key | package=$uninstallTarget"
                )
            }
            resolvedUninstallKeys.remove(key)
            pendingUninstallKey = key
            awaitingUninstallResult = true
            pendingUninstallStillInstalledRetries = 0
            pendingUninstallStartedAtMs = SystemClock.uptimeMillis()
            uninstallVisualStateByKey[key] = UninstallVisualState.WAITING_RESULT
            updateDownloadButtons(DownloadRepository.snapshotDownloads())
            SystemOperationReturnGate.mark("version_sheet_uninstall_prompt")
            startActivity(uninstallIntent)
        } catch (_: Exception) {
            resolvedUninstallKeys.remove(key)
            awaitingUninstallResult = false
            pendingUninstallStillInstalledRetries = 0
            pendingUninstallStartedAtMs = 0L
            clearUninstallVisualState(key)
            updateDownloadButtons(DownloadRepository.snapshotDownloads())
            PendingUninstallTracker.clear()
            try {
                startActivity(fallbackIntent)
            } catch (_: Exception) {
                AppSnackbar.show(rootView, getString(R.string.install_failed))
            }
        }
    }

    private fun installDownloadedVersion(key: String, item: DownloadItem?) {
        val context = context ?: return
        if (
            pendingInstallKey == key ||
            installVisualStateByKey.containsKey(key) ||
            installTransitioningKeys.contains(key)
        ) {
            return
        }
        val filePath = item?.filePath?.takeIf { it.isNotBlank() } ?: run {
            AppSnackbar.show(rootView, getString(R.string.install_failed_file_not_found))
            return
        }
        val file = java.io.File(filePath)
        if (!file.exists() || file.length() <= 0L) {
            AppSnackbar.show(rootView, getString(R.string.install_failed_file_not_found))
            return
        }
        clearInstallVisualState(key)
        pendingInstallKey = key
        awaitingInstallConfirmationReturn = false
        installVisualStateByKey[key] = InstallVisualState.WAITING_CONFIRMATION
        installVisualProgressByKey.remove(key)
        updateDownloadButtons(DownloadRepository.snapshotDownloads())
        InstallStatusCenter.markActive(
            appName = currentApp.name,
            versionKey = key,
            stage = InstallStatusCenter.InstallStage.PREPARING,
            apkPath = filePath
        )
        InstallSessionManager.installApk(
            context = context,
            apkPath = filePath,
            appName = currentApp.name,
            backendPackage = item.backendPackageName.takeIf { it.isNotBlank() } ?: currentApp.packageName
        )
    }

}
