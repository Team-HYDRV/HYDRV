package app.hydra.manager

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.DynamicColors

class BackendManagerActivity : AppCompatActivity() {

    private data class BackendRow(
        val source: BackendSource,
        val isDefault: Boolean
    )

    private lateinit var adapter: BackendSourceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences.applyActivityTheme(this)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        LanguagePreferences.applySavedLanguage(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_backend_manager)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(findViewById(R.id.rootLayout))

        val header = findViewById<View>(R.id.headerContainer)
        val headerBasePaddingLeft = header.paddingLeft
        val headerBasePaddingTop = header.paddingTop
        val headerBasePaddingRight = header.paddingRight
        val headerBasePaddingBottom = header.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            header.setPadding(
                headerBasePaddingLeft,
                headerBasePaddingTop + statusBar,
                headerBasePaddingRight,
                headerBasePaddingBottom
            )
            insets
        }

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.backendRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BackendSourceAdapter()
        recyclerView.adapter = adapter

        findViewById<View>(R.id.addBackendRow).setOnClickListener {
            showBackendSourceFormDialog(
                title = getString(R.string.backend_add_source),
                initialName = "",
                initialUrl = ""
            ) { name, url ->
                val current = BackendPreferences.getCustomBackendSources(this).toMutableList()
                current += BackendSource(name, url, true)
                BackendPreferences.setCustomBackendSources(this, current)
                refreshBackendList()
                refreshCatalogAfterBackendChange()
            }
        }

        refreshBackendList()
    }

    override fun onResume() {
        super.onResume()
        refreshBackendList()
    }

    private fun refreshBackendList() {
        val rows = mutableListOf(
            BackendRow(
            source = BackendSource(
                name = getString(R.string.backend_default_label),
                url = RuntimeConfig.defaultCatalogUrl,
                enabled = true
            ),
            isDefault = true
            )
        )
        rows += BackendPreferences.getCustomBackendSources(this).map {
            BackendRow(source = it, isDefault = false)
        }
        adapter.submit(rows)
    }

    private fun refreshCatalogAfterBackendChange() {
        val appContext = applicationContext
        AppCatalogService.fetchApps(
            appContext,
            allowCacheFallback = true,
            bypassRemoteCache = true
        ) { result ->
            result.onSuccess { fetchResult ->
                CatalogStateCenter.update(fetchResult.apps)
                AppUpdateState.setLastSeenHash(
                    appContext,
                    CatalogFingerprint.hash(fetchResult.apps)
                )
                AppStateCacheManager.refreshFavorites(appContext)
            }
        }
    }

    private fun showBackendSourceFormDialog(
        title: String,
        initialName: String,
        initialUrl: String,
        onSave: (String, String) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = (20 * resources.displayMetrics.density).toInt()
            val vertical = (16 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
        }

        val nameField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initialName)
            hint = getString(R.string.backend_source_name_hint)
            setTextColor(
                ThemeColors.color(
                    this@BackendManagerActivity,
                    com.google.android.material.R.attr.colorOnBackground,
                    R.color.text
                )
            )
            setHintTextColor(
                ThemeColors.color(
                    this@BackendManagerActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    R.color.subtext
                )
            )
            setBackgroundResource(R.drawable.dialog_input_background)
            setPadding(28, 20, 28, 20)
        }

        val urlField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(initialUrl)
            hint = getString(R.string.backend_source_url_hint)
            setTextColor(
                ThemeColors.color(
                    this@BackendManagerActivity,
                    com.google.android.material.R.attr.colorOnBackground,
                    R.color.text
                )
            )
            setHintTextColor(
                ThemeColors.color(
                    this@BackendManagerActivity,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    R.color.subtext
                )
            )
            setBackgroundResource(R.drawable.dialog_input_background)
            setPadding(28, 20, 28, 20)
            val top = (10 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = top
            }
        }

        container.addView(nameField)
        container.addView(urlField)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(getString(R.string.save_label), null)
            .setNegativeButton(getString(R.string.downloads_cancel), null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = nameField.text?.toString().orEmpty().trim().ifBlank { "Custom backend" }
            val url = urlField.text?.toString().orEmpty().trim()
            if (url.isBlank()) {
                urlField.error = getString(R.string.backend_invalid_message)
                return@setOnClickListener
            }
            onSave(name, url)
            dialog.dismiss()
        }
    }

    private inner class BackendSourceAdapter :
        ListAdapter<BackendRow, BackendSourceAdapter.BackendSourceViewHolder>(
            object : DiffUtil.ItemCallback<BackendRow>() {
                override fun areItemsTheSame(oldItem: BackendRow, newItem: BackendRow): Boolean {
                    return oldItem.isDefault == newItem.isDefault &&
                        oldItem.source.url.equals(newItem.source.url, ignoreCase = true)
                }

                override fun areContentsTheSame(oldItem: BackendRow, newItem: BackendRow): Boolean {
                    return oldItem == newItem
                }
            }
        ) {

        fun submit(next: List<BackendRow>) {
            submitList(next)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackendSourceViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_backend_source, parent, false)
            return BackendSourceViewHolder(view)
        }

        override fun onBindViewHolder(holder: BackendSourceViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class BackendSourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val name: TextView = itemView.findViewById(R.id.backendSourceName)
            private val activeButton: Button = itemView.findViewById(R.id.backendSourceActiveButton)
            private val actionRow: LinearLayout = itemView.findViewById(R.id.backendSourceActionRow)
            private val editButton: Button = itemView.findViewById(R.id.backendSourceEditButton)
            private val removeButton: Button = itemView.findViewById(R.id.backendSourceRemoveButton)

            fun bind(item: BackendRow) {
                val activeUrl = BackendPreferences.getActiveBackendUrlValue(itemView.context)
                val isActive = if (item.isDefault) {
                    activeUrl.isBlank() ||
                        activeUrl.equals(RuntimeConfig.defaultCatalogUrl, ignoreCase = true)
                } else {
                    activeUrl.isNotBlank() &&
                        activeUrl.equals(item.source.url, ignoreCase = true)
                }

                name.text = item.source.name.ifBlank {
                    if (item.isDefault) {
                        getString(R.string.backend_default_label)
                    } else {
                        "Custom backend"
                    }
                }

                activeButton.text = if (isActive) {
                    getString(R.string.backend_active_source_short)
                } else {
                    getString(R.string.backend_set_active_source)
                }
                activeButton.setBackgroundResource(
                    if (isActive) R.drawable.button_install else R.drawable.backend_button_neutral
                )
                activeButton.setTextColor(
                    ThemeColors.color(
                        this@BackendManagerActivity,
                        if (isActive) com.google.android.material.R.attr.colorOnPrimary
                        else com.google.android.material.R.attr.colorOnSurface,
                        if (isActive) R.color.black else R.color.text
                    )
                )
                activeButton.backgroundTintList = null

                activeButton.setOnClickListener {
                    val nextActive = if (item.isDefault) {
                        RuntimeConfig.defaultCatalogUrl
                    } else {
                        item.source.url.trim()
                    }
                    if (nextActive.isBlank()) return@setOnClickListener
                    activateBackend(item, nextActive)
                }

                if (item.isDefault) {
                    actionRow.visibility = View.GONE
                    removeButton.visibility = View.GONE
                    editButton.visibility = View.GONE
                } else {
                    actionRow.visibility = View.VISIBLE
                    removeButton.visibility = View.VISIBLE
                    editButton.visibility = View.VISIBLE
                    editButton.setBackgroundResource(R.drawable.backend_button_neutral)
                    editButton.backgroundTintList = null
                    removeButton.setBackgroundResource(R.drawable.button_delete)
                    removeButton.backgroundTintList = null

                    editButton.setOnClickListener {
                        showBackendSourceFormDialog(
                            title = getString(R.string.backend_dialog_title),
                            initialName = item.source.name,
                            initialUrl = item.source.url
                        ) { updatedName, updatedUrl ->
                            val current = BackendPreferences.getCustomBackendSources(this@BackendManagerActivity)
                                .toMutableList()
                            val index = current.indexOfFirst {
                                it.url.equals(item.source.url, ignoreCase = true)
                            }
                            if (index >= 0) {
                                current[index] = BackendSource(updatedName, updatedUrl, true)
                                BackendPreferences.setCustomBackendSources(this@BackendManagerActivity, current)
                                if (isActive) {
                                    BackendPreferences.setActiveBackendUrl(this@BackendManagerActivity, updatedUrl)
                                }
                                refreshBackendList()
                                refreshCatalogAfterBackendChange()
                            }
                        }
                    }

                    removeButton.setOnClickListener {
                        val current = BackendPreferences.getCustomBackendSources(this@BackendManagerActivity)
                            .toMutableList()
                        current.removeAll { it.url.equals(item.source.url, ignoreCase = true) }
                        if (isActive) {
                            BackendPreferences.setActiveBackendUrl(
                                this@BackendManagerActivity,
                                RuntimeConfig.defaultCatalogUrl
                            )
                        }
                        BackendPreferences.setCustomBackendSources(this@BackendManagerActivity, current)
                        refreshBackendList()
                        refreshCatalogAfterBackendChange()
                    }
                }

            }
        }
    }

    private fun activateBackend(item: BackendRow, nextActive: String) {
        if (item.isDefault) {
            BackendPreferences.setActiveBackendUrl(this@BackendManagerActivity, nextActive)
            refreshBackendList()
            refreshCatalogAfterBackendChange()
            return
        }

        Thread {
            val validation = AppCatalogService.validateCatalogEndpointSync(nextActive)
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                if (validation.isSuccess) {
                    BackendPreferences.setActiveBackendUrl(this@BackendManagerActivity, nextActive)
                    refreshBackendList()
                    refreshCatalogAfterBackendChange()
                } else {
                    val reason = validation.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                    AppSnackbar.show(
                        findViewById(R.id.rootLayout),
                        if (reason != null) {
                            getString(R.string.backend_set_active_failed_detail, reason)
                        } else {
                            getString(R.string.backend_set_active_failed)
                        }
                    )
                }
            }
        }.start()
    }
}
