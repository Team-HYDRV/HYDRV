package app.hydra.manager

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class DownloadFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: DownloadAdapter
    private lateinit var emptyText: TextView
    private lateinit var titleDownloads: TextView
    private lateinit var defaultHeaderRow: View
    private lateinit var downloadCount: TextView
    private lateinit var buttonSelectMode: TextView
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView
    private lateinit var buttonSelectAll: TextView
    private lateinit var buttonDeleteSelected: TextView
    private lateinit var buttonCancelSelection: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_download, container, false)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(view)

        applyHeaderInsets(view)

        recycler = view.findViewById(R.id.recyclerDownloads)
        emptyText = view.findViewById(R.id.emptyText)
        titleDownloads = view.findViewById(R.id.titleDownloads)
        defaultHeaderRow = view.findViewById(R.id.defaultHeaderRow)
        downloadCount = view.findViewById(R.id.downloadCount)
        buttonSelectMode = view.findViewById(R.id.buttonSelectMode)
        selectionBar = view.findViewById(R.id.selectionBar)
        selectionCount = view.findViewById(R.id.selectionCount)
        buttonSelectAll = view.findViewById(R.id.buttonSelectAll)
        buttonDeleteSelected = view.findViewById(R.id.buttonDeleteSelected)
        buttonCancelSelection = view.findViewById(R.id.buttonCancelSelection)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.itemAnimator = null

        adapter = DownloadAdapter(mutableListOf())
        recycler.adapter = adapter
        adapter.onSelectionChanged = { selectedCount, totalCount, allSelected ->
            updateSelectionUi(selectedCount, totalCount, allSelected)
        }
        adapter.onUninstallRequested = { packageName, appName ->
            launchUninstall(packageName, appName)
        }

        buttonSelectMode.setOnClickListener {
            adapter.setSelectionMode(true)
        }
        buttonCancelSelection.setOnClickListener {
            adapter.setSelectionMode(false)
        }
        buttonSelectAll.setOnClickListener {
            adapter.toggleSelectAll()
        }
        buttonDeleteSelected.setOnClickListener {
            val selectedItems = adapter.selectedItems()
            DownloadRepository.deleteMany(requireContext(), selectedItems)
            adapter.setSelectionMode(false)
        }

        DownloadRepository.downloadsLive.observe(viewLifecycleOwner) { list ->
            val sortedList = ListSortPreferences.sortDownloads(
                ListSortPreferences.getDownloadSort(requireContext()),
                list
            )

            adapter.updateList(sortedList.toMutableList())

            if (sortedList.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                recycler.visibility = View.GONE
                adapter.setSelectionMode(false)
            } else {
                emptyText.visibility = View.GONE
                recycler.visibility = View.VISIBLE
            }
        }

        InstallStatusCenter.events.observe(viewLifecycleOwner) { event ->
            if (!event.refreshInstalledState || !isAdded) return@observe
            AppStateCacheManager.forceRefreshInstalledPackages(requireContext()) {
                if (!isAdded || view == null) return@forceRefreshInstalledPackages
                refreshDownloads()
                adapter.refreshRuntimeState()
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledStateAndUi()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        if (!hidden && ::adapter.isInitialized) {
            refreshInstalledStateAndUi()
        }
    }

    private fun refreshDownloads() {
        if (!::adapter.isInitialized) return

        recycler.stopScroll()
        val sortedList = ListSortPreferences.sortDownloads(
            ListSortPreferences.getDownloadSort(requireContext()),
            DownloadRepository.downloads
        )
        adapter.updateList(sortedList.toMutableList())
        emptyText.visibility = if (sortedList.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (sortedList.isEmpty()) View.GONE else View.VISIBLE
        if (sortedList.isEmpty()) {
            adapter.setSelectionMode(false)
        }
    }

    private fun updateSelectionUi(selectedCount: Int, totalCount: Int, allSelected: Boolean) {
        val inSelectionMode = adapter.isSelectionMode()
        defaultHeaderRow.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        selectionBar.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        titleDownloads.text = if (inSelectionMode) {
            getString(R.string.downloads_select_title)
        } else {
            getString(R.string.downloads_title)
        }
        downloadCount.text = resources.getQuantityString(
            R.plurals.download_items_count,
            totalCount,
            totalCount
        )
        selectionCount.text = resources.getQuantityString(
            R.plurals.download_selected_count,
            selectedCount,
            selectedCount
        )
        buttonSelectAll.text = if (allSelected && totalCount > 0) {
            getString(R.string.downloads_clear_all)
        } else {
            getString(R.string.downloads_select_all)
        }
        buttonDeleteSelected.alpha = if (selectedCount > 0) 1f else 0.45f
        buttonDeleteSelected.isEnabled = selectedCount > 0
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

    private fun launchUninstall(packageName: String, appName: String) {
        val context = context ?: return
        val candidates = linkedSetOf<String>().apply {
            add(packageName)
            InstallAliasStore.resolveForAppName(context, appName)?.let(::add)
            InstallAliasStore.resolveForPackage(context, packageName)?.let(::add)
        }.filter { it.isNotBlank() }

        val packageManager = context.packageManager
        val uninstallTarget = candidates.firstOrNull { candidate ->
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", candidate, null))
                .resolveActivity(packageManager) != null
        } ?: candidates.firstOrNull()

        if (uninstallTarget == null) {
            AppSnackbar.show(requireView(), getString(R.string.install_failed))
            return
        }

        if (!AppStateCacheManager.isInstalled(context, uninstallTarget, appName)) {
            AppStateCacheManager.forceRefreshInstalledPackages(context) {
                if (!isAdded || view == null) return@forceRefreshInstalledPackages
                refreshDownloads()
                adapter.refreshRuntimeState()
            }
            return
        }

        val uninstallUri = Uri.fromParts("package", uninstallTarget, null)
        AppDiagnostics.log(requireContext(), "UNINSTALL", "Trying uninstall for $appName package=$uninstallTarget candidates=$candidates")
        PendingUninstallTracker.mark(appName, uninstallTarget)
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
                AppSnackbar.show(requireView(), getString(R.string.install_failed))
            }
        }
    }

    private fun refreshInstalledStateAndUi() {
        if (!::adapter.isInitialized || !isAdded) return
        AppStateCacheManager.forceRefreshInstalledPackages(requireContext()) {
            if (!isAdded || view == null) return@forceRefreshInstalledPackages
            refreshDownloads()
            adapter.refreshRuntimeState()
        }
    }
}
