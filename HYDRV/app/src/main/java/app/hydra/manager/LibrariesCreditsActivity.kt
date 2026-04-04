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

class LibrariesCreditsActivity : AppCompatActivity() {

    private data class LibraryCredit(
        val title: String,
        val author: String,
        val version: String,
        val license: String
    )

    private val libraries = listOf(
        LibraryCredit("OkHttp", "Square", "4.12.0", "Apache License 2.0"),
        LibraryCredit("Retrofit", "Square", "2.9.0", "Apache License 2.0"),
        LibraryCredit("Gson Converter", "Square", "2.9.0", "Apache License 2.0"),
        LibraryCredit("Picasso", "Square", "2.8", "Apache License 2.0"),
        LibraryCredit("WorkManager", "AndroidX", "2.9.1", "Apache License 2.0"),
        LibraryCredit("RecyclerView", "AndroidX", "1.3.2", "Apache License 2.0"),
        LibraryCredit("SwipeRefreshLayout", "AndroidX", "1.1.0", "Apache License 2.0"),
        LibraryCredit("Core KTX", "AndroidX", "1.16.0", "Apache License 2.0"),
        LibraryCredit("AppCompat", "AndroidX", "1.7.0", "Apache License 2.0"),
        LibraryCredit("Material Components", "Google", "1.12.0", "Apache License 2.0"),
        LibraryCredit("Facebook Shimmer", "Meta", "0.5.0", "BSD License"),
        LibraryCredit("Google Mobile Ads", "Google", "25.1.0", "SDK Service Terms")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences.applyActivityTheme(this)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        LanguagePreferences.applySavedLanguage(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_libraries_credits)
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

        val recyclerView = findViewById<RecyclerView>(R.id.librariesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = LibraryCreditsAdapter(libraries)
    }

    private class LibraryCreditsAdapter(
        private val libraries: List<LibraryCredit>
    ) : RecyclerView.Adapter<LibraryCreditsAdapter.LibraryCreditViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryCreditViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_library_credit, parent, false)
            return LibraryCreditViewHolder(view)
        }

        override fun onBindViewHolder(holder: LibraryCreditViewHolder, position: Int) {
            holder.bind(libraries[position])
        }

        override fun getItemCount(): Int = libraries.size

        class LibraryCreditViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.libraryTitle)
            private val author: TextView = itemView.findViewById(R.id.libraryAuthor)
            private val version: TextView = itemView.findViewById(R.id.libraryVersion)
            private val license: TextView = itemView.findViewById(R.id.libraryLicense)

            fun bind(item: LibraryCredit) {
                title.text = item.title
                author.text = item.author
                version.text = item.version
                license.text = item.license
            }
        }
    }
}
