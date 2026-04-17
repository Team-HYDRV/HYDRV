package app.hydra.manager

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppAdapter :
    ListAdapter<AppModel, AppAdapter.VH>(DIFF) {

    companion object {
        private const val CARD_PRESS_SCALE = 0.992f

        val DIFF = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel) =
                oldItem == newItem
        }
    }

    private val favoriteCache = mutableMapOf<String, Boolean>()
    private val installedVersionCache = mutableMapOf<String, Int>()
    private val addedLabelCache = mutableMapOf<Long, String>()
    private val dateFormatter by lazy { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    var onAppSelected: ((AppModel) -> Unit)? = null

    init {
        setHasStableIds(true)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.icon)
        val name: TextView = v.findViewById(R.id.name)
        val version: TextView = v.findViewById(R.id.version)
        val addedAt: TextView = v.findViewById(R.id.addedAt)
        val fav: ImageView = v.findViewById(R.id.fav)
        val badge: TextView = v.findViewById(R.id.statusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        if (AppearancePreferences.isDynamicColorEnabled(parent.context)) {
            view.setBackgroundResource(R.drawable.card_material)
        }
        attachPressAnimation(view)
        return VH(view)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).packageName.hashCode().toLong()
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = getItem(position)
        val context = holder.itemView.context
        val latest = app.latestVersion() ?: return

        holder.name.text = app.name
        holder.version.text = context.getString(R.string.version_format, latest.version_name)
        val timestampLabel = formatAddedTimestamp(context, latest.releaseTimestampMillis())
        holder.addedAt.text = timestampLabel
        holder.addedAt.visibility = if (timestampLabel.isEmpty()) View.GONE else View.VISIBLE

        val iconSize = holder.icon.layoutParams.width.takeIf { it > 0 }
            ?: (38 * context.resources.displayMetrics.density).toInt()
        bindIcon(holder.icon, app.icon, iconSize)

        val installedVersion = getInstalledVersion(context, app)
        holder.icon.contentDescription = app.name
        bindFavoriteState(holder, context, app.name)
        updateBadge(holder, installedVersion, latest.version)

        holder.fav.setOnClickListener {
            animateStar(holder.fav)
            toggleFavorite(context, app.name)
            bindFavoriteState(holder, context, app.name)
        }

        holder.itemView.setOnClickListener {
            onAppSelected?.let { callback ->
                callback(app)
                return@setOnClickListener
            }
            val activity = context as? AppCompatActivity ?: return@setOnClickListener
            val fm = activity.supportFragmentManager
            val tag = "versions"
            if (!fm.isStateSaved && fm.findFragmentByTag(tag) == null) {
                VersionSheet(app = app).show(fm, tag)
            }
        }
    }

    override fun onBindViewHolder(
        holder: VH,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val bundle = payloads[0] as Bundle
            val app = getItem(position)

            bundle.getString("version")?.let { versionName ->
                holder.version.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction {
                        holder.version.text =
                            holder.itemView.context.getString(R.string.version_format, versionName)
                        holder.version.animate().alpha(1f).setDuration(150).start()
                    }
                    .start()
            }

            if (bundle.getBoolean("favoriteChanged", false)) {
                bindFavoriteState(holder, holder.itemView.context, app.name)
            }
            if (bundle.getBoolean("runtimeStateChanged", false)) {
                val context = holder.itemView.context
                val latest = app.latestVersion()
                val installedVersion = getInstalledVersion(context, app)
                bindFavoriteState(holder, context, app.name)
                if (latest != null) {
                    updateBadge(holder, installedVersion, latest.version)
                } else {
                    holder.badge.visibility = View.GONE
                }
            }
            return
        }

        super.onBindViewHolder(holder, position, payloads)
    }

    private fun bindFavoriteState(holder: VH, context: Context, appName: String) {
        if (isFavorite(context, appName)) {
            holder.fav.setImageResource(R.drawable.ic_star)
            holder.fav.contentDescription = context.getString(R.string.favorite_toggle_remove)
            holder.fav.setColorFilter(
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorSecondary,
                    R.color.subtext
                )
            )
        } else {
            holder.fav.setImageResource(R.drawable.ic_star_outline)
            holder.fav.contentDescription = context.getString(R.string.favorite_toggle_add)
            holder.fav.setColorFilter(
                ThemeColors.color(
                    context,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    R.color.subtext
                )
            )
        }
    }

    private fun updateBadge(holder: VH, installed: Int, latest: Int) {
        when {
            installed == -1 -> holder.badge.visibility = View.GONE
            installed < latest -> {
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = holder.itemView.context.getString(R.string.badge_update)
                holder.badge.setBackgroundResource(
                    if (AppearancePreferences.isDynamicColorEnabled(holder.itemView.context)) {
                        R.drawable.badge_update_material
                    } else {
                        R.drawable.badge_update
                    }
                )
                holder.badge.setTextColor(
                    ThemeColors.color(
                        holder.itemView.context,
                        com.google.android.material.R.attr.colorOnSecondaryContainer,
                        R.color.text_on_accent_chip
                    )
                )
            }
            else -> {
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = holder.itemView.context.getString(R.string.badge_installed)
                holder.badge.setBackgroundResource(
                    if (AppearancePreferences.isDynamicColorEnabled(holder.itemView.context)) {
                        R.drawable.badge_installed_material
                    } else {
                        R.drawable.badge_installed
                    }
                )
                holder.badge.setTextColor(
                    ThemeColors.color(
                        holder.itemView.context,
                        com.google.android.material.R.attr.colorOnPrimaryContainer,
                        R.color.text_on_accent_chip
                    )
                )
            }
        }
    }

    private fun animateStar(view: View) {
        view.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(120)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(100)
            }
    }

    private fun attachPressAnimation(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    target.animate().cancel()
                    target.animate()
                        .scaleX(CARD_PRESS_SCALE)
                        .scaleY(CARD_PRESS_SCALE)
                        .setDuration(45)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    target.animate().cancel()
                    target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(70)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
            false
        }
    }

    private fun isFavorite(context: Context, name: String): Boolean {
        return favoriteCache.getOrPut(name) { AppStateCacheManager.isFavorite(context, name) }
    }

    private fun toggleFavorite(context: Context, name: String): Boolean {
        val current = AppStateCacheManager.isFavorite(context, name)
        val newValue = !current
        AppStateCacheManager.setFavorite(context, name, newValue)
        favoriteCache[name] = newValue
        return newValue
    }

    private fun getInstalledVersion(context: Context, app: AppModel): Int {
        val cacheKey = "${app.name}|${app.packageName}"
        return installedVersionCache.getOrPut(cacheKey) {
            AppStateCacheManager.hydrvInstalledVersion(context, app.packageName, app.name)
        }
    }

    fun refreshRuntimeCaches() {
        favoriteCache.clear()
        installedVersionCache.clear()
        addedLabelCache.clear()
    }

    fun refreshRuntimeState() {
        refreshRuntimeCaches()
        if (itemCount > 0) {
            val payload = Bundle().apply {
                putBoolean("runtimeStateChanged", true)
            }
            notifyItemRangeChanged(0, itemCount, payload)
        }
    }

    private fun formatAddedTimestamp(context: Context, timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return ""
        return addedLabelCache.getOrPut(timestamp) {
            context.getString(R.string.added_date_format, dateFormatter.format(Date(timestamp)))
        }
    }

    private fun bindIcon(iconView: ImageView, rawUrl: String, iconSize: Int) {
        val iconUrl = rawUrl.trim()
        val previousIconUrl = iconView.getTag(R.id.appIconUrl) as? String

        if (iconUrl.isBlank()) {
            if (previousIconUrl != iconUrl) {
                iconView.setTag(R.id.appIconUrl, iconUrl)
                Picasso.get().cancelRequest(iconView)
                iconView.setImageResource(R.drawable.ic_app_placeholder)
            }
            return
        }

        if (previousIconUrl == iconUrl) return

        iconView.setTag(R.id.appIconUrl, iconUrl)
        Picasso.get()
            .load(iconUrl)
            .placeholder(R.drawable.ic_app_placeholder)
            .error(R.drawable.ic_app_placeholder)
            .resize(iconSize, iconSize)
            .centerInside()
            .noFade()
            .into(iconView)
    }
}
