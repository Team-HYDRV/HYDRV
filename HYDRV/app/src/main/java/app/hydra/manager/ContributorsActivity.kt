package app.hydra.manager

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.squareup.picasso.Picasso

class ContributorsActivity : AppCompatActivity() {

    private data class ContributorCredit(
        val name: String,
        val role: String,
        val summary: String,
        val avatarUrl: String,
        val profileUrl: String
    )

    private data class ContributorGroup(
        val summary: String,
        val members: List<ContributorCredit>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences.applyActivityTheme(this)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        LanguagePreferences.applySavedLanguage(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_contributors)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(findViewById(R.id.rootLayout))

        val header = findViewById<View>(R.id.headerContainer)
        val statusText = findViewById<TextView>(R.id.contributorsStatusText)
        val recyclerView = findViewById<RecyclerView>(R.id.contributorsRecyclerView)
        val adapter = ContributorsAdapter { profileUrl ->
            openProfile(profileUrl)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = null

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            header.setPadding(
                header.paddingLeft,
                statusBar + header.paddingTop,
                header.paddingRight,
                header.paddingBottom
            )
            insets
        }

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }

        statusText.text = getString(R.string.contributors_loading)
        adapter.submitList(emptyList())
        loadGitHubContributors(statusText, adapter)
    }

    private fun loadGitHubContributors(
        statusText: TextView,
        adapter: ContributorsAdapter
    ) {
        GitHubRepository.fetchContributors { result ->
            result.onSuccess { contributors ->
                val credits = contributors
                    .filterNot { it.login.endsWith("[bot]", ignoreCase = true) }
                    .sortedByDescending { it.contributions }
                    .map { contributor ->
                        ContributorCredit(
                            name = contributor.displayName(),
                            role = resources.getQuantityString(
                                R.plurals.contributor_role_contributions,
                                contributor.contributions,
                                contributor.contributions
                            ),
                            summary = contributor.htmlUrl,
                            avatarUrl = contributor.avatarUrl,
                            profileUrl = contributor.htmlUrl
                        )
                    }
                val groups = buildContributorGroups(credits)

                statusText.text = if (credits.isNotEmpty()) {
                    getString(R.string.contributors_loaded, credits.size)
                } else {
                    getString(R.string.contributors_failed)
                }
                adapter.submitList(groups)
            }.onFailure {
                statusText.text = getString(R.string.contributors_failed)
                adapter.submitList(emptyList())
            }
        }
    }

    private fun buildContributorGroups(contributors: List<ContributorCredit>): List<ContributorGroup> {
        val filtered = contributors.filter { it.name.isNotBlank() }
        if (filtered.isEmpty()) return emptyList()

        val chunks = filtered.chunked(6)
        return chunks.mapIndexed { index, chunk ->
            ContributorGroup(
                summary = getString(R.string.contributors_group_summary),
                members = chunk
            )
        }
    }

    private fun openProfile(profileUrl: String) {
        if (profileUrl.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
        } catch (_: ActivityNotFoundException) {
            AppSnackbar.show(findViewById(R.id.rootLayout), getString(R.string.about_link_placeholder))
        }
    }

    private class ContributorsAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<ContributorsAdapter.ContributorViewHolder>() {

        private val items = mutableListOf<ContributorGroup>()

        fun submitList(newItems: List<ContributorGroup>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContributorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contributor_credit, parent, false)
            if (AppearancePreferences.isDynamicColorEnabled(parent.context)) {
                view.setBackgroundResource(R.drawable.card_material)
            }
            return ContributorViewHolder(view, onClick)
        }

        override fun onBindViewHolder(holder: ContributorViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class ContributorViewHolder(
            itemView: View,
            private val onClick: (String) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val groupSummary: TextView = itemView.findViewById(R.id.groupSummary)
            private val avatarsContainer: LinearLayout = itemView.findViewById(R.id.avatarsContainer)

            fun bind(item: ContributorGroup) {
                groupSummary.text = item.summary
                avatarsContainer.removeAllViews()
                val context = itemView.context
                val rows = item.members.chunked(4)
                rows.forEachIndexed { rowIndex, rowMembers ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                    rowMembers.forEach { member ->
                        row.addView(createAvatarView(context, member))
                    }
                    if (rowIndex > 0) {
                        val params = row.layoutParams as? ViewGroup.MarginLayoutParams
                        if (params != null) {
                            params.topMargin = dpToPx(context, 12)
                            row.layoutParams = params
                        } else {
                            row.layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = dpToPx(context, 12)
                            }
                        }
                    } else {
                        row.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    avatarsContainer.addView(row)
                }
            }

            private fun createAvatarView(context: android.content.Context, item: ContributorCredit): View {
                val size = dpToPx(context, 52)
                val marginEnd = dpToPx(context, 10)
                val frame = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        rightMargin = marginEnd
                    }
                    background = context.getDrawable(R.drawable.about_logo_ring)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick(item.profileUrl) }
                    contentDescription = item.name
                    addView(ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply {
                            setMargins(dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 6), dpToPx(context, 6))
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        if (item.avatarUrl.isNotBlank()) {
                            Picasso.get()
                                .load(item.avatarUrl)
                                .placeholder(R.drawable.ic_app_placeholder)
                                .error(R.drawable.ic_app_placeholder)
                                .fit()
                                .centerCrop()
                                .into(this)
                        } else {
                            setImageResource(R.drawable.ic_app_placeholder)
                        }
                    })
                }
                return frame
            }

            private fun dpToPx(context: android.content.Context, value: Int): Int {
                return (value * context.resources.displayMetrics.density).toInt()
            }
        }
    }
}
