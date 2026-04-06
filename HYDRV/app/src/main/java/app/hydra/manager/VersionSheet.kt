package app.hydra.manager

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
class VersionSheet(
    private val app: AppModel,
    private val preferOpenInstalledAction: Boolean = false
) : BottomSheetDialogFragment() {

    companion object {
        private const val DONE_HOLD_MS = 260L
        private const val RESET_DELAY_MS = 1200L
        private const val PRESS_SCALE = 0.975f
        private const val SHEET_SNACKBAR_GAP_DP = 6
        private const val NAV_SNACKBAR_GAP_DP = 10
    }

    private data class DownloadButtonViews(
        val container: FrameLayout,
        val track: FrameLayout,
        val fill: ImageView,
        val label: TextView,
        val icon: ImageView
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentSnackbar: Snackbar? = null
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var scrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null
    private var currentApp: AppModel = app
    private var installedLaunchPackage: String? = null
    private lateinit var rootView: View
    private lateinit var appNameView: TextView
    private lateinit var sheetSummaryView: TextView
    private lateinit var containerLayout: LinearLayout
    private lateinit var scrollView: NestedScrollView
    private lateinit var fadeView: View
    private var installedVersionName: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), R.style.BottomSheetTheme).apply {
            dismissWithAnimation = true
        }
    }

    override fun onStart() {
        super.onStart()

        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.decorView?.setBackgroundColor(Color.TRANSPARENT)

        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        val screenHeight = resources.displayMetrics.heightPixels
        val maxSheetHeight = (screenHeight * 0.62f).toInt()

        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isDraggable = true
        behavior.skipCollapsed = true
        behavior.isHideable = true

        bottomSheetCallback?.let(behavior::removeBottomSheetCallback)
        val callback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheetView: View, newState: Int) {
                updateSheetSnackbarPosition(bottomSheetView)
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    currentSnackbar?.dismiss()
                    currentSnackbar = null
                }
            }

            override fun onSlide(bottomSheetView: View, slideOffset: Float) {
                updateSheetSnackbarPosition(bottomSheetView)
            }
        }
        bottomSheetCallback = callback
        behavior.addBottomSheetCallback(callback)
        updateSheetSnackbarPosition(bottomSheet)

        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        bottomSheet.background = null
        (bottomSheet.parent as? View)?.apply {
            setBackgroundColor(Color.TRANSPARENT)
            background = null
        }

        bottomSheet.post {
            if (!isAdded || view == null) return@post
            val content = bottomSheet.findViewById<View>(R.id.sheetRoot) ?: return@post
            val targetHeight = minOf(content.height, maxSheetHeight)
            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = if (targetHeight > 0) targetHeight else WRAP_CONTENT
            }
            behavior.peekHeight = targetHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.bottom_sheet_versions, container, false)
        rootView = view
        if (AppearancePreferences.isPureBlackActive(requireContext())) {
            view.setBackgroundResource(R.drawable.bottom_sheet_bg_pure_black)
        } else if (AppearancePreferences.isDynamicColorEnabled(requireContext())) {
            view.setBackgroundResource(R.drawable.bottom_sheet_bg)
        } else {
            view.setBackgroundResource(R.drawable.bottom_sheet_bg_brand)
        }

        scrollView = view.findViewById(R.id.scrollView)
        fadeView = view.findViewById(R.id.scrollFade)
        appNameView = view.findViewById(R.id.appName)
        sheetSummaryView = view.findViewById(R.id.sheetSummary)
        containerLayout = view.findViewById(R.id.container)
        RewardedAdManager.preload(requireContext())

        scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
            updateScrollFade()
        }
        scrollView.viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)

        renderVersions(inflater)

        CatalogStateCenter.apps.observe(viewLifecycleOwner) { apps ->
            val updatedApp = apps.firstOrNull {
                it.packageName == currentApp.packageName ||
                    it.name.equals(currentApp.name, ignoreCase = true)
            } ?: return@observe

            if (updatedApp == currentApp) return@observe

            currentApp = updatedApp
            renderVersions(inflater)
        }

        scrollView.post {
            if (!isAdded || view == null) return@post
            updateScrollFade()
        }

        return view
    }

    private fun renderVersions(inflater: LayoutInflater) {
        if (!this::containerLayout.isInitialized) return

        val ctx = requireContext()
        refreshInstalledInfo(ctx)
        val previousScrollY = if (this::scrollView.isInitialized) scrollView.scrollY else 0
        val sortMode = ListSortPreferences.getVersionSort(ctx)
        val sortedVersions = ListSortPreferences.sortVersions(sortMode, currentApp.versions)
        versionsByKey = sortedVersions.associateBy(::versionKey)
        val latestVersion = sortedVersions.firstOrNull()
        val latestVersionNumber = latestVersion?.version
        val installSnapshot = InstallIntelligence.snapshot(ctx, currentApp)

        appNameView.text = currentApp.name
        sheetSummaryView.text =
            ctx.getString(
                R.string.versions_summary_format,
                sortedVersions.size,
                ListSortPreferences.versionSortLabel(ctx, sortMode)
            )

        buttonViewsByKey.keys.toList().forEach { key ->
            cancelReset(key)
        }
        buttonViewsByKey.clear()
        completedKeys.clear()
        doneHandledKeys.clear()
        lastKnownStatuses.clear()
        visualProgressByKey.clear()
        activeSessionKeys.clear()
        failedKeys.clear()
        containerLayout.removeAllViews()

        sortedVersions.forEach { version ->
            val card = inflater.inflate(
                R.layout.item_version_sheet,
                containerLayout,
                false
            )
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
            val button = card.findViewById<FrameLayout>(R.id.versionDownloadButton)
            val buttonTrack = card.findViewById<FrameLayout>(R.id.versionDownloadTrack)
            val buttonFill = card.findViewById<ImageView>(R.id.versionDownloadFill)
            val buttonLabel = card.findViewById<TextView>(R.id.versionDownloadLabel)
            val buttonIcon = card.findViewById<ImageView>(R.id.versionDownloadIcon)
            val key = versionKey(version)

            title.text = ctx.getString(R.string.version_format, version.version_name)
            badge.visibility = if (version.version == latestVersionNumber) View.VISIBLE else View.GONE

            val changelogText = sanitizeChangelog(version.changelog)
            if (changelogText.isNullOrBlank()) {
                changelog.visibility = View.GONE
            } else {
                changelog.visibility = View.VISIBLE
                changelog.text = formatChangelogText(changelogText)
            }
            val sourceLabel = version.downloadSourceLabel()
            val sourceHostText = version.downloadHost()
            val unknownSourceText = getString(R.string.version_source_unknown)
            applyVersionBadgePalette(badge)
            applyVersionBadgePalette(sourceBadge)
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
            val insight = InstallIntelligence.insight(
                ctx,
                currentApp,
                version,
                installSnapshot,
                latestVersion
            )
            installedHint.text = insight.installedHint.orEmpty()
            installedHint.visibility = if (insight.installedHint.isNullOrBlank()) View.GONE else View.VISIBLE
            downloadedHint.text = insight.downloadHint.orEmpty()
            downloadedHint.visibility = if (insight.downloadHint.isNullOrBlank()) View.GONE else View.VISIBLE

            buttonViewsByKey[key] = DownloadButtonViews(
                container = button,
                track = buttonTrack,
                fill = buttonFill,
                label = buttonLabel,
                icon = buttonIcon
            )
            applyVersionButtonPalette(buttonViewsByKey.getValue(key), ctx)
            applyIdleState(buttonViewsByKey.getValue(key))
            attachPressAnimation(button)

            button.setOnClickListener {
                if (
                    preferOpenInstalledAction &&
                    installedLaunchPackage != null &&
                    installedVersionName == version.version_name
                ) {
                    openInstalledApp()
                    return@setOnClickListener
                }
                val activity = activity ?: return@setOnClickListener
                if (!AdsPreferences.areRewardedAdsEnabled(ctx)) {
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

            containerLayout.addView(card)
        }

        updateDownloadButtons(DownloadRepository.downloads)
        if (this::scrollView.isInitialized) {
            scrollView.post {
                val content = scrollView.getChildAt(0)
                val maxScroll = ((content?.height ?: 0) - scrollView.height).coerceAtLeast(0)
                scrollView.scrollTo(0, previousScrollY.coerceAtMost(maxScroll))
                updateScrollFade()
            }
        }
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        DownloadRepository.downloadsLive.observe(viewLifecycleOwner) { downloads ->
            updateDownloadButtons(downloads)
        }
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
                versionName = version.version_name
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
            getString(R.string.added_to_downloads_format, currentApp.name, version.version_name)
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
        currentSnackbar?.dismiss()
        currentSnackbar = null
        scrollChangedListener?.let { listener ->
            scrollView.viewTreeObserver.takeIf { it.isAlive }?.removeOnScrollChangedListener(listener)
        }
        scrollChangedListener = null
        buttonViewsByKey.values.forEach { releaseLiquidDrawable(it.fill) }
        resetRunnables.values.forEach(mainHandler::removeCallbacks)
        resetRunnables.clear()
        versionsByKey = emptyMap()
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
            .associateBy { versionKey(it.versionName, it.url) }

        val candidateKeys = linkedSetOf<String>().apply {
            addAll(relevantDownloads.keys)
            addAll(activeSessionKeys)
            addAll(visualProgressByKey.keys)
            addAll(completedKeys)
            addAll(doneHandledKeys)
            addAll(failedKeys)
            addAll(lastKnownStatuses.keys)
        }

        if (candidateKeys.isEmpty()) return

        candidateKeys.forEach { key ->
            val views = buttonViewsByKey[key] ?: return@forEach
            val version = versionsByKey[key]
            if (
                preferOpenInstalledAction &&
                version != null &&
                installedLaunchPackage != null &&
                installedVersionName == version.version_name
            ) {
                applyOpenState(views)
                return@forEach
            }
            val item = relevantDownloads[key]
            val visualProgress = visualProgressByKey[key] ?: 0

            when {
                item == null -> {
                    if (failedKeys.contains(key)) {
                    applyErrorState(views)
                    return@forEach
                    }
                    if (hasActiveVisualState(key)) {
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
                    applyIdleState(views)
                }
                item.status == "Downloading" -> {
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
                item.status == "Done" -> {
                    failedKeys.remove(key)
                    val shouldAnimateDone = hasActiveVisualState(key)
                    if (shouldAnimateDone) {
                        if (doneHandledKeys.contains(key)) {
                            applyDoneState(views)
                        } else if (completedKeys.add(key)) {
                            visualProgressByKey[key] = 100
                            views.container.isEnabled = false
                            views.icon.visibility = View.GONE
                            views.label.text = "100%"
                            animateFillTo(views.fill, 100, onUpdate = { value ->
                                views.label.text = "$value%"
                            }, onEnd = {
                                doneHandledKeys.add(key)
                                applyDoneState(views)
                                scheduleReset(key, views)
                            })
                        } else {
                            views.container.isEnabled = false
                            views.icon.visibility = View.GONE
                            views.label.text = "100%"
                        }
                    } else {
                        completedKeys.remove(key)
                        doneHandledKeys.remove(key)
                        visualProgressByKey.remove(key)
                        cancelReset(key)
                        applyIdleState(views)
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
                    applyIdleState(views)
                    activeSessionKeys.remove(key)
                    lastKnownStatuses[key] = item.status
                }
            }
        }
    }

    private fun scheduleReset(key: String, views: DownloadButtonViews) {
        cancelReset(key)
        val runnable = Runnable {
            completedKeys.remove(key)
            doneHandledKeys.remove(key)
            visualProgressByKey.remove(key)
            applyIdleState(views)
        }
        resetRunnables[key] = runnable
        mainHandler.postDelayed(runnable, RESET_DELAY_MS)
    }

    private fun cancelReset(key: String) {
        resetRunnables.remove(key)?.let(mainHandler::removeCallbacks)
    }

    private fun applyIdleState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = true
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

    private fun applyOpenState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = true
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
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = false
        views.track.animate().cancel()
        views.icon.visibility = View.GONE
        views.label.animate().cancel()
        views.label.alpha = 1f
        views.label.translationY = 0f
        views.label.setTextColor(Color.BLACK)
        val clamped = progress.coerceIn(0, 100)
        if (label.startsWith(getString(R.string.download_status_paused))) {
            views.label.text = label
            animateFillTo(views.fill, clamped)
        } else {
            animateFillTo(views.fill, clamped, onUpdate = { value ->
                views.label.text = "$value%"
            })
        }
    }

    private fun applyDoneState(views: DownloadButtonViews) {
        applyVersionButtonPalette(views, views.container.context)
        views.container.isEnabled = false
        views.label.setTextColor(Color.BLACK)
        animateFillTo(views.fill, 100)
        val doneLabel = getString(R.string.download_status_done)
        val shouldAnimate = views.icon.visibility != View.VISIBLE || views.label.text != doneLabel
        views.track.animate().cancel()
        views.label.animate().cancel()
        if (shouldAnimate) {
            views.label.text = doneLabel
            views.label.alpha = 0f
            views.label.translationY = 2f
            views.label.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()

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
        val fillDrawable = liquidVersionDrawable(views.fill, context)
        views.track.setBackgroundResource(versionTrackDrawable(context))
        views.fill.setImageDrawable(fillDrawable)
        views.label.setTextColor(
            ThemeColors.color(
                context,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                R.color.text_on_accent_chip
            )
        )
        views.icon.imageTintList = ColorStateList.valueOf(
            ThemeColors.color(
                context,
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                R.color.text_on_accent_chip
            )
        )
    }

    private fun liquidVersionDrawable(
        fillView: ImageView,
        context: android.content.Context
    ): LiquidWaveProgressDrawable {
        (fillView.getTag(R.id.liquidProgressDrawable) as? LiquidWaveProgressDrawable)?.let { return it }
        val drawable = LiquidWaveProgressDrawable(
            trackColor = Color.TRANSPARENT,
            fillColor = ThemeColors.color(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                R.color.accent
            )
        )
        fillView.setTag(R.id.liquidProgressDrawable, drawable)
        return drawable
    }

    private fun versionTrackDrawable(context: android.content.Context): Int {
        return if (AppearancePreferences.isDynamicColorEnabled(context)) {
            R.drawable.version_download_button_bg
        } else {
            R.drawable.version_download_button_bg_brand
        }
    }

    private fun versionFillDrawable(context: android.content.Context): Int {
        return if (AppearancePreferences.isDynamicColorEnabled(context)) {
            R.drawable.version_download_fill_clip
        } else {
            R.drawable.version_download_fill_brand_clip
        }
    }

    private fun releaseLiquidDrawable(fillView: ImageView) {
        (fillView.getTag(R.id.liquidProgressDrawable) as? LiquidWaveProgressDrawable)?.dispose()
        fillView.setTag(R.id.liquidProgressDrawable, null)
    }

    private fun versionErrorFillDrawable(context: android.content.Context): Int {
        return if (AppearancePreferences.isDynamicColorEnabled(context)) {
            R.drawable.version_download_fill_error_clip
        } else {
            R.drawable.version_download_fill_error_clip
        }
    }

    private fun versionKey(version: Version): String {
        return versionKey(version.version_name, version.url)
    }

    private fun versionKey(versionName: String, url: String): String {
        return "$versionName|$url"
    }

    private fun downloadPackageKey(item: DownloadItem): String {
        return item.backendPackageName.takeIf { it.isNotBlank() } ?: item.packageName
    }

    private fun maxVisualProgress(key: String, progress: Int): Int {
        val clamped = progress.coerceIn(0, 100)
        val current = visualProgressByKey[key] ?: 0
        return maxOf(current, clamped).also { visualProgressByKey[key] = it }
    }

    private fun hasActiveVisualState(key: String): Boolean {
        return activeSessionKeys.contains(key) || (visualProgressByKey[key] ?: 0) > 0
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
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(PRESS_SCALE)
                        .scaleY(PRESS_SCALE)
                        .alpha(0.96f)
                        .setDuration(90L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(120L)
                        .setInterpolator(OvershootInterpolator(0.7f))
                        .start()
                }
            }
            false
        }
    }

    private fun copyChangelog(version: Version, changelog: String) {
        val clipboard = context?.getSystemService(ClipboardManager::class.java) ?: return
        val text = buildString {
            append(currentApp.name)
            append(" ")
            append(version.version_name)
            append('\n')
            append(changelog)
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("HYDRV release notes", text))
        AppSnackbar.show(rootView, getString(R.string.version_notes_copied))
    }

    private fun shareChangelog(version: Version, changelog: String) {
        val shareText = buildString {
            append(currentApp.name)
            append(" ")
            append(version.version_name)
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
                    putExtra(Intent.EXTRA_SUBJECT, "${currentApp.name} ${version.version_name}")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                },
                getString(R.string.version_share_notes)
            )
        )
    }

    private fun formatChangelogText(raw: String): String {
        if (raw == getString(R.string.release_details_unavailable)) return raw
        val parts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size >= 3) {
            parts.joinToString(separator = "\n") { "\u2022 $it" }
        } else {
            raw
        }
    }

    private fun sanitizeChangelog(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return null
        if (text.equals("Disabled Advertisements", ignoreCase = true)) return null
        return text
    }

    private fun updateScrollFade() {
        if (!this::scrollView.isInitialized || !this::fadeView.isInitialized) return
        val viewChild = scrollView.getChildAt(0)
        val diff = if (viewChild == null) {
            0
        } else {
            viewChild.bottom - (scrollView.height + scrollView.scrollY)
        }
        fadeView.visibility = if (diff <= 0) View.GONE else View.VISIBLE
    }

    private fun refreshInstalledInfo(context: android.content.Context) {
        val resolvedPackage = InstallAliasStore.resolveForAppName(context, currentApp.name)
            ?: InstallAliasStore.resolveForPackage(context, currentApp.packageName)
            ?: currentApp.packageName
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(resolvedPackage, 0)
        }.getOrNull()
        installedLaunchPackage = packageInfo?.packageName?.takeIf { it.isNotBlank() }
        installedVersionName = packageInfo?.versionName?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun openInstalledApp() {
        val context = context ?: return
        val packageName = installedLaunchPackage ?: return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        startActivity(launchIntent)
    }

}
