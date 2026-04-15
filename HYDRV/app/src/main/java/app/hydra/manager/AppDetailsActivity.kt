package app.hydra.manager

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import com.google.android.material.color.DynamicColors
import com.squareup.picasso.Picasso

class AppDetailsActivity : AppCompatActivity() {

    private val activeDownloadObservers = mutableMapOf<String, Observer<List<DownloadItem>>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences.applyActivityTheme(this)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        LanguagePreferences.applySavedLanguage(this)
        setContentView(R.layout.activity_app_details)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(findViewById(android.R.id.content))

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val contentRoot = findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
        val basePaddingTop = contentRoot.paddingTop
        val basePaddingBottom = contentRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = basePaddingTop + bars.top,
                bottom = basePaddingBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(contentRoot)

        val app = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("app", AppModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("app") as? AppModel
        } ?: run {
            finish()
            return
        }

        val icon = findViewById<ImageView>(R.id.icon)
        val name = findViewById<TextView>(R.id.name)
        val latest = findViewById<TextView>(R.id.latest)
        val install = findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.install)
        val container = findViewById<LinearLayout>(R.id.versionsContainer)

        Picasso.get()
            .load(app.icon)
            .placeholder(R.drawable.ic_app_placeholder)
            .error(R.drawable.ic_app_placeholder)
            .fit()
            .centerInside()
            .noFade()
            .into(icon)
        icon.contentDescription = app.name

        name.text = app.name
        val latestVersion = app.latestVersion()
        latest.text = if (latestVersion != null) {
            getString(
                R.string.app_details_latest_version,
                latestVersion.displayVersionName()
            )
        } else {
            getString(R.string.app_details_latest_unavailable)
        }
        if (AdsPreferences.areRewardedAdsEnabled(this)) {
            RewardedAdManager.preload(this)
        } else {
            RewardedAdManager.clear()
        }

        install.isEnabled = latestVersion != null
        install.alpha = if (latestVersion != null) 1f else 0.55f
        install.setOnClickListener {
            val version = latestVersion ?: return@setOnClickListener
            maybeStartDownloadWithAds(
                btn = install,
                url = version.url,
                name = app.name,
                packageName = app.packageName,
                versionName = version.displayVersionName(),
                versionCode = version.version
            )
        }

        container.removeAllViews()

        if (app.versions.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.app_details_missing_versions)
                setTextColor(
                    ThemeColors.color(
                        this@AppDetailsActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        R.color.subtext
                    )
                )
            }
            container.addView(empty)
            return
        }

        for (v in app.sortedVersionsNewestFirst()) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.setPadding(0, 20, 0, 20)

            val title = TextView(this)
            title.text = getString(
                R.string.app_details_version_title,
                v.displayVersionName()
            )
            title.setTextColor(
                ThemeColors.color(
                    this,
                    com.google.android.material.R.attr.colorOnBackground,
                    R.color.text
                )
            )

            val formattedChangelog = formatChangelogForDisplay(v.changelog)
            val changelog = TextView(this).apply {
                text = formattedChangelog.orEmpty()
                setTextColor(
                    ThemeColors.color(
                        this@AppDetailsActivity,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        R.color.subtext
                    )
                )
            }

            val btn = androidx.appcompat.widget.AppCompatButton(this)
            btn.text = getString(R.string.app_details_download)
            btn.setBackgroundResource(R.drawable.button_install)
            btn.setTextColor(
                ThemeColors.color(
                    this,
                    com.google.android.material.R.attr.colorOnPrimary,
                    R.color.text_on_accent_chip
                )
            )
            btn.backgroundTintList = null

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 16
            btn.layoutParams = params

            btn.setOnClickListener {
                maybeStartDownloadWithAds(
                    btn = btn,
                    url = v.url,
                    name = app.name,
                    packageName = app.packageName,
                    versionName = v.displayVersionName(),
                    versionCode = v.version
                )
            }

            item.addView(title)
            if (!formattedChangelog.isNullOrBlank()) {
                item.addView(changelog)
            }
            item.addView(btn)

            container.addView(item)
        }
    }

    override fun onDestroy() {
        activeDownloadObservers.values.forEach { observer ->
            DownloadRepository.downloadsLive.removeObserver(observer)
        }
        activeDownloadObservers.clear()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun maybeStartDownloadWithAds(
        btn: androidx.appcompat.widget.AppCompatButton,
        url: String,
        name: String,
        packageName: String,
        versionName: String,
        versionCode: Int
    ) {
        if (!AdsPreferences.areRewardedAdsEnabled(this) || RewardedAdManager.shouldBypassRewardGate()) {
            startDownload(btn, url, name, packageName, versionName, versionCode)
            return
        }

        RewardedAdManager.showThen(
            activity = this,
            onRewardEarned = {
                startDownload(btn, url, name, packageName, versionName, versionCode)
            },
            onAdUnavailable = {
                startDownload(btn, url, name, packageName, versionName, versionCode)
            },
            onAdDismissedWithoutReward = {
                AppSnackbar.show(
                    findViewById(android.R.id.content),
                    getString(R.string.rewarded_ad_required_message)
                )
            }
        )
    }

    private fun startDownload(
        btn: androidx.appcompat.widget.AppCompatButton,
        url: String,
        name: String,
        packageName: String,
        versionName: String,
        versionCode: Int
    ) {
        btn.isEnabled = false
        btn.text = "0%"

        fun resetButton() {
            btn.text = getString(R.string.app_details_download)
            btn.isEnabled = true
            btn.setTextColor(
                ThemeColors.color(
                    this@AppDetailsActivity,
                    com.google.android.material.R.attr.colorOnPrimary,
                    R.color.text_on_accent_chip
                )
            )
            btn.setBackgroundResource(R.drawable.button_install)
        }

        fun showDone() {
            btn.text = getString(R.string.app_details_done)
            btn.setTextColor(
                ThemeColors.color(
                    this@AppDetailsActivity,
                    com.google.android.material.R.attr.colorOnPrimary,
                    R.color.text_on_accent_chip
                )
            )
            btn.setBackgroundResource(R.drawable.button_done)

            btn.postDelayed({
                resetButton()
            }, 1000)
        }

        val item = DownloadItem(
            name = name,
            url = url,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode
        )

        val result = DownloadRepository.startDownload(this, item)
        if (result != DownloadRepository.StartResult.STARTED) {
            resetButton()
            AppSnackbar.show(
                findViewById(android.R.id.content),
                DownloadRepository.startResultMessage(this, result)
                    ?: DownloadNetworkPolicy.blockedMessage(this)
            )
            return
        }

        val downloadKey = item.requestKey()
        activeDownloadObservers.remove(downloadKey)?.let {
            DownloadRepository.downloadsLive.removeObserver(it)
        }

        val observer = Observer<List<DownloadItem>> { downloads ->
            val match = downloads.lastOrNull {
                it.requestKey() == downloadKey
            }
            if (match == null) {
                activeDownloadObservers.remove(downloadKey)?.let {
                    DownloadRepository.downloadsLive.removeObserver(it)
                }
                resetButton()
                return@Observer
            }

            when {
                match.status == "Done" || match.progress >= 100 -> {
                    activeDownloadObservers.remove(downloadKey)?.let {
                        DownloadRepository.downloadsLive.removeObserver(it)
                    }
                    showDone()
                }
                match.status == "Failed" -> {
                    activeDownloadObservers.remove(downloadKey)?.let {
                        DownloadRepository.downloadsLive.removeObserver(it)
                    }
                    resetButton()
                }
                else -> {
                    btn.text = "${match.progress}%"
                }
            }
        }

        activeDownloadObservers[downloadKey] = observer
        DownloadRepository.downloadsLive.observe(this, observer)
    }
}
