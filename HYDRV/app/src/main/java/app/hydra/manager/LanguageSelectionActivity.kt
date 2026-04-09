package app.hydra.manager

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors

class LanguageSelectionActivity : AppCompatActivity() {

    private data class LanguageItem(
        val code: String,
        val titleRes: Int,
        val nativeNameRes: Int
    )

    private val languageItems = listOf(
        LanguageItem(
            LanguagePreferences.SYSTEM,
            R.string.language_system_default,
            R.string.language_native_system_default
        ),
        LanguageItem(
            LanguagePreferences.ARABIC,
            R.string.language_arabic_region,
            R.string.language_native_arabic_region
        ),
        LanguageItem(
            LanguagePreferences.CHINESE_SIMPLIFIED,
            R.string.language_chinese_simplified_region,
            R.string.language_native_chinese_simplified_region
        ),
        LanguageItem(
            LanguagePreferences.CZECH,
            R.string.language_czech_region,
            R.string.language_native_czech_region
        ),
        LanguageItem(
            LanguagePreferences.DUTCH,
            R.string.language_dutch_region,
            R.string.language_native_dutch_region
        ),
        LanguageItem(
            LanguagePreferences.ENGLISH,
            R.string.language_english_region,
            R.string.language_native_english_region
        ),
        LanguageItem(
            LanguagePreferences.FILIPINO,
            R.string.language_filipino_region,
            R.string.language_native_filipino_region
        ),
        LanguageItem(
            LanguagePreferences.FRENCH,
            R.string.language_french_region,
            R.string.language_native_french_region
        ),
        LanguageItem(
            LanguagePreferences.GERMAN,
            R.string.language_german_region,
            R.string.language_native_german_region
        ),
        LanguageItem(
            LanguagePreferences.HINDI,
            R.string.language_hindi_region,
            R.string.language_native_hindi_region
        ),
        LanguageItem(
            LanguagePreferences.INDONESIAN,
            R.string.language_indonesian_region,
            R.string.language_native_indonesian_region
        ),
        LanguageItem(
            LanguagePreferences.ITALIAN,
            R.string.language_italian_region,
            R.string.language_native_italian_region
        ),
        LanguageItem(
            LanguagePreferences.JAPANESE,
            R.string.language_japanese_region,
            R.string.language_native_japanese_region
        ),
        LanguageItem(
            LanguagePreferences.KOREAN,
            R.string.language_korean_region,
            R.string.language_native_korean_region
        ),
        LanguageItem(
            LanguagePreferences.MALAY,
            R.string.language_malay_region,
            R.string.language_native_malay_region
        ),
        LanguageItem(
            LanguagePreferences.POLISH,
            R.string.language_polish_region,
            R.string.language_native_polish_region
        ),
        LanguageItem(
            LanguagePreferences.PORTUGUESE_BRAZIL,
            R.string.language_portuguese_brazil_region,
            R.string.language_native_portuguese_brazil_region
        ),
        LanguageItem(
            LanguagePreferences.ROMANIAN,
            R.string.language_romanian_region,
            R.string.language_native_romanian_region
        ),
        LanguageItem(
            LanguagePreferences.RUSSIAN,
            R.string.language_russian_region,
            R.string.language_native_russian_region
        ),
        LanguageItem(
            LanguagePreferences.SPANISH,
            R.string.language_spanish_region,
            R.string.language_native_spanish_region
        ),
        LanguageItem(
            LanguagePreferences.THAI,
            R.string.language_thai_region,
            R.string.language_native_thai_region
        ),
        LanguageItem(
            LanguagePreferences.TURKISH,
            R.string.language_turkish_region,
            R.string.language_native_turkish_region
        ),
        LanguageItem(
            LanguagePreferences.UKRAINIAN,
            R.string.language_ukrainian_region,
            R.string.language_native_ukrainian_region
        ),
        LanguageItem(
            LanguagePreferences.VIETNAMESE,
            R.string.language_vietnamese_region,
            R.string.language_native_vietnamese_region
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearancePreferences.applyActivityTheme(this)
        if (AppearancePreferences.isDynamicColorEnabled(this)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        LanguagePreferences.applySavedLanguage(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_language_selection)
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

        val recyclerView = findViewById<RecyclerView>(R.id.languageRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = LanguageAdapter(
            languageItems = languageItems,
            selectedCode = LanguagePreferences.getLanguage(this)
        ) { item ->
            LanguagePreferences.setLanguage(this, item.code)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private class LanguageAdapter(
        private val languageItems: List<LanguageItem>,
        private val selectedCode: String,
        private val onSelected: (LanguageItem) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language_option, parent, false)
            return LanguageViewHolder(view, onSelected)
        }

        override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
            holder.bind(languageItems[position], languageItems[position].code == selectedCode)
        }

        override fun getItemCount(): Int = languageItems.size

        class LanguageViewHolder(
            itemView: View,
            private val onSelected: (LanguageItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.languageTitle)
            private val subtitle: TextView = itemView.findViewById(R.id.languageSubtitle)
            private val radio: RadioButton = itemView.findViewById(R.id.languageRadio)

            fun bind(item: LanguageItem, isSelected: Boolean) {
                title.setText(item.titleRes)
                subtitle.setText(item.nativeNameRes)
                radio.isChecked = isSelected

                itemView.setOnClickListener { onSelected(item) }
                radio.setOnClickListener { onSelected(item) }
            }
        }
    }
}
