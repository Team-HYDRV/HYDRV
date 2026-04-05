package app.hydra.manager

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.tabs.TabLayout
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import okhttp3.Call as OkHttpCall

class HomeFragment : Fragment() {

    companion object {
        private const val MIN_SHIMMER_DURATION_MS = 180L
        private const val SEARCH_DEBOUNCE_MS = 180L
        private const val INITIAL_RENDER_ITEM_COUNT = 6
        private var cachedApps: List<AppModel> = emptyList()
        private var cachedHash: Int = 0
    }

    private var appList: List<AppModel> = cachedApps
    private var lastHash: Int = cachedHash
    private var currentTab = 0

    private lateinit var recyclerView: RecyclerView
    private lateinit var shimmer: ShimmerFrameLayout
    private lateinit var search: EditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var lastUpdated: TextView
    private lateinit var emptyView: TextView
    private lateinit var filterScroll: HorizontalScrollView
    private lateinit var filterChipContainer: LinearLayout

    private lateinit var adapter: AppAdapter
    private var pendingLastUpdatedBanner = false
    private var startupRenderSignaled = false
    private var startupFullListSubmitted = false
    private val handler = Handler(Looper.getMainLooper())
    private var appsCall: OkHttpCall? = null
    private var shimmerShownAt = 0L
    private var selectedTag: String? = null
    private var recentOnly = false
    private val searchRunnable = Runnable { renderCurrentTab(animate = true) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(view)

        applyHeaderInsets(view)

        recyclerView = view.findViewById(R.id.recyclerView)
        shimmer = view.findViewById(R.id.shimmer)
        search = view.findViewById(R.id.search)
        if (AppearancePreferences.isDynamicColorEnabled(requireContext())) {
            search.setBackgroundResource(R.drawable.card_material)
        }
        val tabs = view.findViewById<TabLayout>(R.id.tabs)
        emptyView = view.findViewById(R.id.emptyView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        lastUpdated = view.findViewById(R.id.lastUpdated)
        filterScroll = view.findViewById(R.id.filterScroll)
        filterChipContainer = view.findViewById(R.id.filterChipContainer)

        tabs.tabRippleColor = null
        tabs.removeAllTabs()
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_all)))
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_favorites)))
        tabs.addTab(tabs.newTab().setText(getString(R.string.tab_installed)))

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = AppAdapter()
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null
        recyclerView.setHasFixedSize(true)

        if (appList.isEmpty()) {
            preloadCachedApps()
        }

        if (appList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.GONE
            shimmer.visibility = View.VISIBLE
            shimmer.startShimmer()
        } else {
            shimmer.visibility = View.GONE
            renderStartupSnapshot()
        }

        if (appList.isEmpty()) {
            loadApps(false)
        }

        swipeRefresh.setOnRefreshListener {
            loadApps(true)
        }

        CatalogStateCenter.apps.observe(viewLifecycleOwner) { apps ->
            val context = context ?: return@observe
            if (apps.isEmpty()) return@observe

            val newHash = CatalogFingerprint.hash(apps)
            if (newHash == lastHash && cachedApps.isNotEmpty()) return@observe

            lastHash = newHash
            val sortedApps = ListSortPreferences.sortApps(
                ListSortPreferences.getHomeSort(context),
                apps
            )
            appList = sortedApps
            cachedApps = sortedApps
            cachedHash = newHash
            adapter.refreshRuntimeState()
            refreshFilterChips()
            renderCurrentTab(animate = false)
        }

        InstallStatusCenter.events.observe(viewLifecycleOwner) { event ->
            if (!event.refreshInstalledState || !isAdded) return@observe
            AppStateCacheManager.forceRefreshInstalledPackages(requireContext()) {
                if (!isAdded || view == null) return@forceRefreshInstalledPackages
                adapter.refreshRuntimeState()
                renderCurrentTab(animate = false)
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacks(searchRunnable)
                handler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                renderCurrentTab(animate = true)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        return view
    }

    private fun preloadCachedApps() {
        val context = context ?: return
        val cachedResult = AppCatalogService.readCachedApps(context)?.getOrNull() ?: return
        CatalogStateCenter.update(cachedResult.apps)
        val sortedCachedApps = ListSortPreferences.sortApps(
            ListSortPreferences.getHomeSort(context),
            cachedResult.apps
        )
        val cachedAppsHash = CatalogFingerprint.hash(cachedResult.apps)

        appList = sortedCachedApps
        cachedApps = sortedCachedApps
        lastHash = cachedAppsHash
        cachedHash = cachedAppsHash
    }

    private fun renderStartupSnapshot() {
        recyclerView.visibility = View.VISIBLE
        refreshFilterChips()
        val initialList = appList.take(INITIAL_RENDER_ITEM_COUNT)
        adapter.submitList(initialList) {
            updateEmptyState(initialList)
            signalHomeFirstRenderIfNeeded()

            if (!startupFullListSubmitted && appList.size > initialList.size) {
                startupFullListSubmitted = true
                recyclerView.post {
                    if (!isAdded || view == null) return@post
                    adapter.submitList(appList) {
                        updateEmptyState(appList)
                    }
                }
            }
        }
    }

    private fun signalHomeFirstRenderIfNeeded() {
        if (startupRenderSignaled) return
        startupRenderSignaled = true
        (activity as? MainActivity)?.onHomeFirstRenderComplete()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            refreshInstalledStateForVisibleHome()
            view?.post {
                if (isAdded && !isHidden) {
                    adapter.refreshRuntimeCaches()
                    renderCurrentTab()
                    signalHomeFirstRenderIfNeeded()
                }
            }
        }
        showPendingLastUpdatedIfNeeded()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && ::adapter.isInitialized) {
            refreshInstalledStateForVisibleHome()
            view?.post {
                if (isAdded && !isHidden) {
                    adapter.refreshRuntimeCaches()
                    renderCurrentTab()
                }
            }
            showPendingLastUpdatedIfNeeded()
        } else {
            hideLastUpdated(immediate = true)
        }
    }

    override fun onDestroyView() {
        appsCall?.cancel()
        appsCall = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }

    override fun onPause() {
        if (isAdded) {
            hideLastUpdated(immediate = true)
        }
        super.onPause()
    }

    private fun refreshInstalledStateForVisibleHome() {
        if (!isAdded || isHidden || !::adapter.isInitialized) return
        AppStateCacheManager.forceRefreshInstalledPackages(requireContext()) {
            if (!isAdded || isHidden || view == null) return@forceRefreshInstalledPackages
            adapter.refreshRuntimeState()
            renderCurrentTab(animate = false)
        }
    }

    private fun applyHeaderInsets(view: View) {
        val header = view.findViewById<View>(R.id.headerContainer)

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top

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

    private fun loadApps(isManualRefresh: Boolean, isSilentRefresh: Boolean = false) {
        if (!isSilentRefresh && (isManualRefresh || appList.isEmpty())) {
            shimmer.visibility = View.VISIBLE
            shimmer.startShimmer()
            shimmerShownAt = SystemClock.elapsedRealtime()
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.GONE
            lastUpdated.visibility = View.GONE
        }

        appsCall?.cancel()
        appsCall = AppCatalogService.fetchApps(requireContext(), allowCacheFallback = true) { result ->
            if (!isAdded || view == null || appsCall?.isCanceled() == true) {
                return@fetchApps
            }

            val completeUiUpdateOnError = {
                updateEmptyState(emptyList())
                swipeRefresh.isRefreshing = false
            }

            result.onSuccess { fetchResult ->
                val context = context ?: return@onSuccess
                val newList = fetchResult.apps
                val newHash = CatalogFingerprint.hash(newList)
                val previousHash = lastHash
                val hasChanged = newHash != lastHash

                if (hasChanged) {
                    CatalogStateCenter.update(newList)
                    lastHash = newHash
                    appList = ListSortPreferences.sortApps(
                        ListSortPreferences.getHomeSort(context),
                        newList
                    )
                    cachedApps = appList
                    cachedHash = newHash

                    if (adapter.currentList != appList) {
                        adapter.submitList(appList)
                    }
                } else {
                    CatalogStateCenter.update(newList)
                }

                AppUpdateState.setLastSeenHash(context, newHash)
                if (hasChanged && !fetchResult.fromCache) {
                    if (!isManualRefresh && previousHash != 0) {
                        AppNotificationHelper.showBackendUpdateNotification(context)
                    }
                    AppUpdateState.setLastNotifiedHash(context, newHash)
                }

                val completeUiUpdate = {
                    AppStateCacheManager.refreshFavorites(context)
                    AppStateCacheManager.warmInstalledPackages(context, onComplete = {
                        if (isAdded) {
                            adapter.refreshRuntimeState()
                            renderCurrentTab()
                        }
                    })
                    adapter.refreshRuntimeState()
                    renderCurrentTab()

                    if (isManualRefresh || (hasChanged && !fetchResult.fromCache)) {
                        if (!isHidden) {
                            showLastUpdatedBanner()
                        } else {
                            pendingLastUpdatedBanner = true
                        }
                    }

                    swipeRefresh.isRefreshing = false
                }

                if (isSilentRefresh) {
                    completeUiUpdate()
                } else {
                    finishLoading(completeUiUpdate)
                }
            }.onFailure { error ->
                if (isSilentRefresh) {
                    completeUiUpdateOnError()
                } else {
                    finishLoading(completeUiUpdateOnError)
                    if (isCatalogParseError(error)) {
                        showCatalogJsonError()
                    }
                }
            }
        }
    }

    private fun isCatalogParseError(error: Throwable): Boolean {
        return error is JsonParseException ||
            error is MalformedJsonException ||
            error.cause is JsonParseException ||
            error.cause is MalformedJsonException
    }

    private fun showCatalogJsonError() {
        context?.let {
            AppDiagnostics.log(it, "CATALOG", "Catalog JSON could not be parsed")
        }
        val host = activity?.findViewById<View>(R.id.rootLayout) ?: view ?: return
        AppSnackbar.show(host, getString(R.string.home_error_invalid_catalog))
    }

    private val lastUpdatedHideRunnable = Runnable {
        hideLastUpdated(immediate = false)
    }

    private fun finishLoading(onComplete: () -> Unit) {
        val elapsed = SystemClock.elapsedRealtime() - shimmerShownAt
        val remaining = (MIN_SHIMMER_DURATION_MS - elapsed).coerceAtLeast(0L)

        handler.postDelayed({
            shimmer.stopShimmer()
            shimmer.visibility = View.GONE
            shimmerShownAt = 0L
            onComplete()
        }, remaining)
    }

    private fun showPendingLastUpdatedIfNeeded() {
        if (!pendingLastUpdatedBanner || isHidden) return
        showLastUpdatedBanner()
        pendingLastUpdatedBanner = false
    }

    private fun showLastUpdatedBanner() {
        if (!::lastUpdated.isInitialized) return
        lastUpdated.animate().cancel()
        lastUpdated.alpha = 1f
        lastUpdated.text = getString(R.string.home_updated_just_now)
        lastUpdated.visibility = View.VISIBLE
        handler.removeCallbacks(lastUpdatedHideRunnable)
        handler.postDelayed(lastUpdatedHideRunnable, 2000)
    }

    private fun hideLastUpdated(immediate: Boolean) {
        if (!::lastUpdated.isInitialized) return

        lastUpdated.animate().cancel()
        if (immediate) {
            lastUpdated.visibility = View.GONE
            lastUpdated.alpha = 1f
            return
        }

        lastUpdated.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                lastUpdated.visibility = View.GONE
                lastUpdated.alpha = 1f
            }
    }

    private fun updateEmptyState(list: List<AppModel>) {
        if (list.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE

            emptyView.text = when {
                search.text.toString().isNotEmpty() -> getString(R.string.home_empty_no_results)
                currentTab == 1 -> getString(R.string.home_empty_no_favorites)
                currentTab == 2 -> getString(R.string.home_empty_no_installed)
                else -> getString(R.string.home_empty_no_apps)
            }
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun renderCurrentTab(animate: Boolean = false) {
        val context = context ?: return
        if (!::adapter.isInitialized || !::search.isInitialized) return

        val query = search.text?.toString().orEmpty()
        val baseApps = cachedApps
        appList = ListSortPreferences.sortApps(
            ListSortPreferences.getHomeSort(context),
            baseApps
        )

        val baseList = when (currentTab) {
            1 -> ListSortPreferences.sortApps(
                ListSortPreferences.getFavoritesSort(context),
                baseApps.filter { AppStateCacheManager.isFavorite(context, it.name) }
            )

            2 -> ListSortPreferences.sortApps(
                ListSortPreferences.getInstalledSort(context),
                baseApps.filter { AppStateCacheManager.isInstalled(context, it.packageName, it.name) }
            )

            else -> appList
        }

        val filteredList = if (query.isBlank()) {
            baseList
        } else {
            baseList.filter { app ->
                app.name.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true) ||
                    app.normalizedTags().any { it.contains(query, ignoreCase = true) } ||
                    app.versions.any {
                        it.version_name.contains(query, ignoreCase = true) ||
                            it.changelog.orEmpty().contains(query, ignoreCase = true)
                    }
            }
        }
            .filter { app ->
                (!recentOnly || isRecentApp(app)) &&
                    (selectedTag == null || app.normalizedTags().any {
                        it.equals(selectedTag, ignoreCase = true)
                    })
            }

        adapter.setPreferOpenInstalledAction(currentTab == 2)

        if (animate) {
            animateFilteredContentSwap(filteredList)
        } else {
            adapter.submitList(filteredList)
            updateEmptyState(filteredList)
        }
    }

    private fun animateFilteredContentSwap(filteredList: List<AppModel>) {
        val currentTarget = if (recyclerView.visibility == View.VISIBLE) recyclerView else emptyView
        currentTarget.animate().cancel()

        currentTarget.animate()
            .alpha(0f)
            .translationY(6f)
            .setDuration(90)
            .withEndAction {
                adapter.submitList(filteredList) {
                    updateEmptyState(filteredList)
                    animateContentSwap()
                }
            }
            .start()
    }

    private fun animateContentSwap() {
        val target = if (recyclerView.visibility == View.VISIBLE) recyclerView else emptyView
        target.animate().cancel()
        target.alpha = 0f
        target.translationY = 10f
        target.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180)
            .start()
    }

    private fun refreshFilterChips() {
        if (!::filterChipContainer.isInitialized) return
        val context = context ?: return
        val tags = cachedApps.flatMap { it.normalizedTags() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
            .take(8)

        filterChipContainer.removeAllViews()
        val hasAnyFilters = tags.isNotEmpty() || recentOnly || selectedTag != null
        filterScroll.visibility = if (tags.isEmpty() && !hasAnyFilters) View.GONE else View.VISIBLE

        addFilterChip(
            context = context,
            label = getString(R.string.home_filter_recent),
            selected = recentOnly
        ) {
            recentOnly = !recentOnly
            refreshFilterChips()
            renderCurrentTab(animate = true)
        }

        tags.forEach { tag ->
            addFilterChip(
                context = context,
                label = tag,
                selected = selectedTag.equals(tag, ignoreCase = true)
            ) {
                selectedTag = if (selectedTag.equals(tag, ignoreCase = true)) null else tag
                refreshFilterChips()
                renderCurrentTab(animate = true)
            }
        }
    }

    private fun addFilterChip(
        context: android.content.Context,
        label: String,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val chip = TextView(context).apply {
            text = label
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(
                (14 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (14 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            background = ContextCompat.getDrawable(
                context,
                if (selected) R.drawable.home_filter_chip_selected else R.drawable.home_filter_chip
            )
            setTextColor(
                ThemeColors.color(
                    context,
                    if (selected) com.google.android.material.R.attr.colorOnPrimary
                    else com.google.android.material.R.attr.colorOnBackground,
                    if (selected) android.R.color.black else R.color.text
                )
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (8 * resources.displayMetrics.density).toInt()
            layoutParams = params
            setOnClickListener { onClick() }
        }
        filterChipContainer.addView(chip)
    }

    private fun isRecentApp(app: AppModel): Boolean {
        val latestTimestamp = app.latestVersion()?.releaseTimestampMillis() ?: return false
        val ageMs = System.currentTimeMillis() - latestTimestamp
        return ageMs in 0..(30L * 24L * 60L * 60L * 1000L)
    }
}
