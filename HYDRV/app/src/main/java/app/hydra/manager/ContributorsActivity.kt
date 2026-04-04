package app.hydra.manager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors

class ContributorsActivity : AppCompatActivity() {

    private data class Contributor(
        val name: String,
        val role: String,
        val summary: String
    )

    private val contributors = listOf(
        Contributor(
            name = "HYDRV Core",
            role = "Project direction",
            summary = "Shapes the app experience, release goals, and overall product direction."
        ),
        Contributor(
            name = "UI and UX",
            role = "Design and interface polish",
            summary = "Refines layouts, interaction details, and the visual consistency across screens."
        ),
        Contributor(
            name = "Package and install flow",
            role = "Downloads and installs",
            summary = "Keeps download handling, package installs, and update behavior moving reliably."
        ),
        Contributor(
            name = "Translations and QA",
            role = "Localization and testing",
            summary = "Helps expand language support and catch rough edges before release."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        LanguagePreferences.applySavedLanguage(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_contributors)
        AppearancePreferences.applyPureBlackBackgroundIfNeeded(findViewById(R.id.rootLayout))

        val header = findViewById<View>(R.id.headerContainer)
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

        val recyclerView = findViewById<RecyclerView>(R.id.contributorsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ContributorsAdapter(contributors)
    }

    private class ContributorsAdapter(
        private val contributors: List<Contributor>
    ) : RecyclerView.Adapter<ContributorsAdapter.ContributorViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContributorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contributor_credit, parent, false)
            return ContributorViewHolder(view)
        }

        override fun onBindViewHolder(holder: ContributorViewHolder, position: Int) {
            holder.bind(contributors[position])
        }

        override fun getItemCount(): Int = contributors.size

        class ContributorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val name: TextView = itemView.findViewById(R.id.contributorName)
            private val role: TextView = itemView.findViewById(R.id.contributorRole)
            private val summary: TextView = itemView.findViewById(R.id.contributorSummary)

            fun bind(item: Contributor) {
                name.text = item.name
                role.text = item.role
                summary.text = item.summary
            }
        }
    }
}
