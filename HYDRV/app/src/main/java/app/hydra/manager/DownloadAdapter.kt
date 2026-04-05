package app.hydra.manager

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.webkit.MimeTypeMap

class DownloadAdapter(
    private val list: MutableList<DownloadItem>
) : RecyclerView.Adapter<DownloadAdapter.VH>() {

    companion object {
        private const val DONE_HOLD_MS = 260L
        private const val DONE_SETTLE_MS = 1200L
        private const val PRESS_SCALE = 0.965f
        private const val PAYLOAD_RUNTIME_STATE_CHANGED = "runtime_state_changed"
        private const val CONTROL_COOLDOWN_MS = 650L
    }

    private val smoothedSpeeds = mutableMapOf<String, Float>()
    private val selectedKeys = linkedSetOf<String>()
    private val controlCooldownUntil = mutableMapOf<String, Long>()
    private val pauseLockedProgress = mutableMapOf<String, Int>()
    private val pauseLockedUntil = mutableMapOf<String, Long>()
    private var selectionMode = false
    var onSelectionChanged: ((selectedCount: Int, totalCount: Int, allSelected: Boolean) -> Unit)? = null
    var onUninstallRequested: ((packageName: String, appName: String) -> Unit)? = null

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cardRoot: View = v.findViewById(R.id.cardRoot)
        val name: TextView = v.findViewById(R.id.name)
        val selectCheck: CheckBox = v.findViewById(R.id.selectCheck)
        val downloadedTime: TextView = v.findViewById(R.id.downloadedTime)
        val progress: ProgressBar = v.findViewById(R.id.progress)
        val percent: TextView = v.findViewById(R.id.percent)
        val status: TextView = v.findViewById(R.id.status)
        val speed: TextView = v.findViewById(R.id.speed)
        val eta: TextView = v.findViewById(R.id.eta)
        val actionGroup: View = v.findViewById(R.id.actionGroup)
        val action: Button = v.findViewById(R.id.action)
        val delete: Button = v.findViewById(R.id.delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        if (AppearancePreferences.isDynamicColorEnabled(parent.context)) {
            view.setBackgroundResource(R.drawable.card_material)
        }
        return VH(view)
    }

    override fun getItemCount() = list.size

    fun updateList(newList: MutableList<DownloadItem>) {
        val oldList = list.map { it.copy() }
        val snapshotList = newList.map { it.copy() }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size

            override fun getNewListSize() = snapshotList.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return progressKey(oldList[oldItemPosition]) == progressKey(snapshotList[newItemPosition])
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = snapshotList[newItemPosition]
                return oldItem.progress == newItem.progress &&
                    oldItem.status == newItem.status &&
                    oldItem.errorMessage == newItem.errorMessage &&
                    oldItem.speed == newItem.speed &&
                    oldItem.eta == newItem.eta &&
                    oldItem.completedAt == newItem.completedAt &&
                    oldItem.createdAt == newItem.createdAt &&
                    oldItem.filePath == newItem.filePath &&
                    oldItem.doneHandled == newItem.doneHandled
            }
        })
        list.clear()
        list.addAll(snapshotList)
        diff.dispatchUpdatesTo(this)
        syncSelectionWithList()
    }

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled && (!enabled || selectedKeys.isEmpty())) {
            dispatchSelectionChanged()
            return
        }
        selectionMode = enabled
        if (!enabled) {
            selectedKeys.clear()
        }
        notifyDataSetChanged()
        dispatchSelectionChanged()
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun toggleSelectAll() {
        val previousSelection = selectedKeys.toSet()
        if (selectedKeys.size == list.size) {
            selectedKeys.clear()
        } else {
            selectedKeys.clear()
            selectedKeys.addAll(list.map(::progressKey))
        }
        notifySelectionChanges(previousSelection, selectedKeys)
        dispatchSelectionChanged()
    }

    fun selectedItems(): List<DownloadItem> {
        return list.filter { selectedKeys.contains(progressKey(it)) }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = list[position]
        val context = holder.itemView.context
        val itemKey = progressKey(item)
        val isSelected = selectedKeys.contains(itemKey)
        val controlsCoolingDown = isControlCoolingDown(itemKey)
        val pauseLocked = isPauseLocked(itemKey, item)

        holder.progress.animate().cancel()
        holder.percent.animate().cancel()
        holder.speed.animate().cancel()
        holder.eta.animate().cancel()
        holder.status.animate().cancel()

        holder.progress.apply {
            clearAnimation()
            visibility = if (item.status == "Done" && item.doneHandled) View.GONE else View.VISIBLE
            alpha = 1f
            scaleY = 1f
        }
        holder.percent.apply {
            clearAnimation()
            visibility = if (item.status == "Done" && item.doneHandled) View.GONE else View.VISIBLE
            alpha = 1f
            translationY = 0f
        }
        holder.speed.apply {
            clearAnimation()
            visibility = if (item.status == "Done" && item.doneHandled) View.GONE else View.VISIBLE
            alpha = 1f
        }
        holder.eta.apply {
            clearAnimation()
            visibility = if (item.status == "Done" && item.doneHandled) View.GONE else View.VISIBLE
            alpha = 1f
        }
        holder.status.apply {
            clearAnimation()
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }

        holder.name.text = if (item.versionName.isNotEmpty()) {
            "${item.name} - ${item.versionName}"
        } else {
            item.name
        }
        holder.selectCheck.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.selectCheck.isChecked = isSelected
        holder.actionGroup.visibility = if (selectionMode) View.GONE else View.VISIBLE
        holder.cardRoot.alpha = if (selectionMode && isSelected) 0.82f else 1f
        holder.downloadedTime.text = formatDownloadTime(context, item)
        holder.action.isEnabled = !controlsCoolingDown && !pauseLocked
        holder.delete.isEnabled = !controlsCoolingDown
        holder.action.alpha = when {
            controlsCoolingDown -> 0.72f
            pauseLocked -> 0.62f
            else -> 1f
        }
        holder.delete.alpha = if (controlsCoolingDown) 0.72f else 1f

        val previousKey = holder.progress.getTag(R.id.name) as? String
        if (previousKey != itemKey) {
            (holder.progress.getTag(R.id.progress) as? ValueAnimator)?.cancel()
            holder.progress.progress = item.progress
            holder.progress.setTag(R.id.progress, null)
        } else {
            animateProgress(holder.progress, item.progress)
        }
        holder.progress.setTag(R.id.name, itemKey)
        holder.percent.text = "${item.progress}%"

        val speedText = formatSpeed(item)
        val etaText = formatETA(item.eta)

        holder.speed.text = context.getString(R.string.download_speed_format, speedText)
        holder.eta.text = if (etaText == "--") "" else context.getString(R.string.download_eta_format, etaText)

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelection(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (selectionMode) {
                false
            } else {
                selectionMode = true
                selectedKeys.add(itemKey)
                notifyDataSetChanged()
                dispatchSelectionChanged()
                true
            }
        }

        val isInstalled = isAppInstalled(context, item.packageName, item.name)

        fun animatePress(view: View) {
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate()
                            .scaleX(PRESS_SCALE)
                            .scaleY(PRESS_SCALE)
                            .alpha(0.94f)
                            .setDuration(85)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(110)
                            .setInterpolator(OvershootInterpolator(0.7f))
                            .start()
                    }
                }
                false
            }
        }

        animatePress(holder.action)
        animatePress(holder.delete)
        animatePress(holder.cardRoot)

        holder.delete.setOnClickListener {
            val target = repositoryItem(item) ?: item
            DownloadRepository.delete(context, target)

            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                list.removeAt(pos)
                notifyItemRemoved(pos)
            }
        }

        when (item.status) {

            "Done" -> {
                smoothedSpeeds.remove(itemKey)
                holder.status.text = context.getString(R.string.download_status_done)
                holder.status.setTextColor(
                    ThemeColors.color(
                        context,
                        androidx.appcompat.R.attr.colorPrimary,
                        R.color.accent
                    )
                )
                val isSettledDone = item.completedAt > 0L &&
                    System.currentTimeMillis() - item.completedAt >= DONE_SETTLE_MS

                if (isSettledDone && !item.doneHandled) {
                    item.doneHandled = true
                    repositoryItem(item)?.doneHandled = true
                }

                if (!item.doneHandled) {
                    holder.progress.visibility = View.VISIBLE
                    holder.percent.visibility = View.VISIBLE
                    holder.speed.visibility = View.VISIBLE
                    holder.eta.visibility = View.VISIBLE
                    holder.percent.setTextColor(
                        ThemeColors.color(
                            context,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            R.color.subtext
                        )
                    )
                    animateProgress(
                        holder.progress,
                        100,
                        onUpdate = { value -> holder.percent.text = "$value%" }
                    ) {
                        holder.percent.text = "100%"
                        holder.progress.postDelayed({
                            if (holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                animateDone(holder)
                                item.doneHandled = true
                                repositoryItem(item)?.doneHandled = true
                            }
                        }, DONE_HOLD_MS)
                    }
                } else {
                    holder.progress.visibility = View.GONE
                    holder.percent.visibility = View.GONE
                    holder.speed.visibility = View.GONE
                    holder.eta.visibility = View.GONE
                }

                val isApkDownload = isApkDownload(item)

                if (isInstalled && isApkDownload) {
                    holder.action.text = context.getString(R.string.download_action_open)
                    holder.delete.visibility = View.VISIBLE
                    holder.delete.text = context.getString(R.string.downloads_uninstall)

                    holder.action.setOnClickListener {
                        if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                        openApp(context, item.packageName, item.name)
                    }
                    holder.delete.setOnClickListener {
                        if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                        onUninstallRequested?.invoke(item.packageName, item.name)
                    }
                } else if (isApkDownload) {
                    holder.action.text = context.getString(R.string.download_action_install)
                    holder.delete.visibility = View.VISIBLE
                    holder.delete.text = context.getString(R.string.downloads_delete)

                    holder.action.setOnClickListener {
                        if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                        installApk(context, item.filePath, item.name, item.packageName)
                    }
                    holder.delete.setOnClickListener {
                        if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                        val target = repositoryItem(item) ?: item
                        DownloadRepository.delete(context, target)

                        val pos = holder.bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            list.removeAt(pos)
                            notifyItemRemoved(pos)
                        }
                    }
                } else {
                    holder.action.text = context.getString(R.string.download_action_open)
                    holder.delete.visibility = View.VISIBLE
                    holder.delete.text = context.getString(R.string.downloads_delete)

                    holder.action.setOnClickListener {
                        if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                        openDownloadedFile(context, item.filePath)
                    }
                    holder.delete.setOnClickListener {
                        if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                        val target = repositoryItem(item) ?: item
                        DownloadRepository.delete(context, target)

                        val pos = holder.bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            list.removeAt(pos)
                            notifyItemRemoved(pos)
                        }
                    }
                }
            }

            "Paused" -> {
                holder.status.text = context.getString(R.string.download_status_paused)
                holder.status.setTextColor(
                    ThemeColors.color(
                        context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        R.color.accent
                    )
                )

                holder.action.text = context.getString(R.string.download_action_resume)
                holder.delete.visibility = View.VISIBLE
                holder.delete.text = context.getString(R.string.downloads_delete)

                holder.action.setOnClickListener {
                    if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                    val target = repositoryItem(item) ?: item
                    armPauseLockIfNeeded(itemKey, target)
                    val result = DownloadRepository.resume(context, target)
                    if (result != DownloadRepository.StartResult.STARTED) {
                        pauseLockedProgress.remove(itemKey)
                        pauseLockedUntil.remove(itemKey)
                        Toast.makeText(
                            context,
                            DownloadRepository.startResultMessage(context, result)
                                ?: DownloadNetworkPolicy.blockedMessage(context),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            "Failed" -> {
                smoothedSpeeds.remove(itemKey)
                holder.status.text = context.getString(R.string.download_status_failed)
                holder.status.setTextColor(
                    ThemeColors.color(
                        context,
                        com.google.android.material.R.attr.colorOnError,
                        R.color.red
                    )
                )
                holder.downloadedTime.text = item.errorMessage.ifBlank {
                    context.getString(R.string.download_error_invalid_link)
                }
                holder.progress.visibility = View.GONE
                holder.percent.visibility = View.GONE
                holder.speed.visibility = View.GONE
                holder.eta.visibility = View.GONE

                holder.action.text = context.getString(R.string.download_action_retry)
                holder.delete.visibility = View.VISIBLE
                holder.delete.text = context.getString(R.string.downloads_delete)

                holder.action.setOnClickListener {
                    if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                    val target = repositoryItem(item) ?: item
                    armPauseLockIfNeeded(itemKey, target)
                    val result = DownloadRepository.resume(context, target)
                    if (result != DownloadRepository.StartResult.STARTED) {
                        pauseLockedProgress.remove(itemKey)
                        pauseLockedUntil.remove(itemKey)
                        Toast.makeText(
                            context,
                            DownloadRepository.startResultMessage(context, result)
                                ?: DownloadNetworkPolicy.blockedMessage(context),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            else -> {
                holder.status.text = context.getString(R.string.download_status_downloading)
                holder.status.setTextColor(
                    ThemeColors.color(
                        context,
                        androidx.appcompat.R.attr.colorPrimary,
                        R.color.accent
                    )
                )
                holder.percent.setTextColor(
                    ThemeColors.color(
                        context,
                        com.google.android.material.R.attr.colorOnSurfaceVariant,
                        R.color.subtext
                    )
                )

                holder.action.text = context.getString(R.string.download_action_pause)
                holder.delete.visibility = View.GONE

                holder.action.setOnClickListener {
                    if (!tryBeginControlAction(holder, itemKey)) return@setOnClickListener
                    val target = repositoryItem(item) ?: item
                    DownloadRepository.pause(context, target)
                    notifyItemChanged(position)
                }
            }
        }

        repositoryItem(item)?.lastStatus = item.status
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        (holder.progress.getTag(R.id.progress) as? ValueAnimator)?.cancel()
        holder.progress.setTag(R.id.progress, null)
        holder.progress.animate().cancel()
        holder.percent.animate().cancel()
        holder.speed.animate().cancel()
        holder.eta.animate().cancel()
        holder.status.animate().cancel()
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.any { it == PAYLOAD_RUNTIME_STATE_CHANGED }) {
            onBindViewHolder(holder, position)
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun animateDone(holder: VH) {
        holder.progress.animate()
            .alpha(0f)
            .scaleY(0.55f)
            .scaleX(0.985f)
            .setDuration(240)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                holder.progress.visibility = View.GONE
                holder.progress.alpha = 1f
                holder.progress.scaleY = 1f
                holder.progress.scaleX = 1f
            }
            .start()
        holder.percent.animate()
            .alpha(0f)
            .translationY(-3f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                holder.percent.visibility = View.GONE
                holder.percent.alpha = 1f
                holder.percent.translationY = 0f
            }
            .start()
        holder.speed.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                holder.speed.visibility = View.GONE
                holder.speed.alpha = 1f
            }
            .start()
        holder.eta.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                holder.eta.visibility = View.GONE
                holder.eta.alpha = 1f
            }
            .start()

        holder.status.apply {
            scaleX = 0.92f
            scaleY = 0.92f
            alpha = 0f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(0.65f))
                .start()
        }
    }

    private fun animateProgress(
        progressBar: ProgressBar,
        to: Int,
        onUpdate: ((Int) -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ) {
        (progressBar.getTag(R.id.progress) as? ValueAnimator)?.cancel()

        if (to <= 0 || to < progressBar.progress || kotlin.math.abs(progressBar.progress - to) <= 1) {
            progressBar.progress = to
            progressBar.setTag(R.id.progress, null)
            onUpdate?.invoke(to)
            onEnd?.invoke()
            return
        }

        val from = progressBar.progress
        val delta = (to - from).coerceAtLeast(1)
        val animator = ValueAnimator.ofInt(progressBar.progress, to)
        animator.duration = (280L + (delta * 9L)).coerceAtMost(620L)
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            val value = it.animatedValue as Int
            progressBar.progress = value
            onUpdate?.invoke(value)
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (progressBar.getTag(R.id.progress) === animation) {
                    progressBar.setTag(R.id.progress, null)
                }
                onEnd?.invoke()
            }
        })
        progressBar.setTag(R.id.progress, animator)
        animator.start()
    }

    private fun installApk(context: Context, path: String, appName: String, backendPackage: String) {
        val file = File(path)

        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            InstallSessionManager.installApk(context, path, appName, backendPackage)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.install_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppInstalled(context: Context, packageName: String, appName: String): Boolean {
        return AppStateCacheManager.isInstalled(context, packageName, appName)
    }

    private fun openApp(context: Context, packageName: String, appName: String) {
        val resolvedPackage = InstallAliasStore.resolveForAppName(context, appName)
            ?: InstallAliasStore.resolveForPackage(context, packageName)
            ?: packageName
        val intent = context.packageManager.getLaunchIntentForPackage(resolvedPackage)
        intent?.let { context.startActivity(it) }
    }

    private fun openDownloadedFile(context: Context, path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val mimeType = guessMimeType(file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.download_action_open)))
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMimeType(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "txt" -> "text/plain"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase(Locale.US))
                ?: "application/octet-stream"
        }
    }

    private fun isApkDownload(item: DownloadItem): Boolean {
        return item.filePath.endsWith(".apk", ignoreCase = true) ||
            item.url.isApkUrl()
    }

    private fun formatSpeed(item: DownloadItem): String {
        val key = progressKey(item)
        val previous = smoothedSpeeds[key] ?: 0f
        val smooth = if (previous == 0f) item.speed else (previous * 0.7f + item.speed * 0.3f)
        smoothedSpeeds[key] = smooth

        return when {
            smooth <= 0 -> "0 KB/s"
            smooth < 1024 -> "${smooth.toInt()} KB/s"
            else -> String.format("%.1f MB/s", smooth / 1024f)
        }
    }

    private fun progressKey(item: DownloadItem): String {
        return item.requestKey()
    }

    private fun isControlCoolingDown(itemKey: String): Boolean {
        return (controlCooldownUntil[itemKey] ?: 0L) > System.currentTimeMillis()
    }

    private fun isPauseLocked(itemKey: String, item: DownloadItem): Boolean {
        val lockedProgress = pauseLockedProgress[itemKey] ?: return false
        val lockedUntil = pauseLockedUntil[itemKey] ?: 0L
        val now = System.currentTimeMillis()

        if (item.status != "Downloading" ||
            now >= lockedUntil ||
            item.progress >= 100 ||
            item.progress > lockedProgress
        ) {
            pauseLockedProgress.remove(itemKey)
            pauseLockedUntil.remove(itemKey)
            return false
        }
        return true
    }

    private fun armPauseLockIfNeeded(itemKey: String, item: DownloadItem) {
        if (item.status == "Downloading") {
            pauseLockedProgress[itemKey] = item.progress
            pauseLockedUntil[itemKey] = System.currentTimeMillis() + 2000L
        } else {
            pauseLockedProgress.remove(itemKey)
            pauseLockedUntil.remove(itemKey)
        }
    }

    private fun tryBeginControlAction(holder: VH, itemKey: String): Boolean {
        val now = System.currentTimeMillis()
        val until = controlCooldownUntil[itemKey] ?: 0L
        if (until > now) return false

        controlCooldownUntil[itemKey] = now + CONTROL_COOLDOWN_MS
        holder.action.isEnabled = false
        holder.delete.isEnabled = false
        holder.action.alpha = 0.72f
        holder.delete.alpha = 0.72f
        holder.itemView.postDelayed({
            val bindingPos = holder.bindingAdapterPosition
            if (bindingPos != RecyclerView.NO_POSITION) {
                notifyItemChanged(bindingPos, PAYLOAD_RUNTIME_STATE_CHANGED)
            }
        }, CONTROL_COOLDOWN_MS)
        return true
    }

    private fun repositoryItem(item: DownloadItem): DownloadItem? {
        val key = progressKey(item)
        return DownloadRepository.downloads.lastOrNull { progressKey(it) == key }
    }

    private fun toggleSelection(item: DownloadItem) {
        val key = progressKey(item)
        val index = list.indexOfFirst { progressKey(it) == key }
        if (!selectedKeys.add(key)) {
            selectedKeys.remove(key)
        }
        if (index != -1) {
            notifyItemChanged(index)
        }
        dispatchSelectionChanged()
    }

    private fun notifySelectionChanges(
        previousSelection: Set<String>,
        newSelection: Set<String>
    ) {
        if (previousSelection == newSelection) return

        val changedKeys = (previousSelection + newSelection)
            .filter { previousSelection.contains(it) != newSelection.contains(it) }
            .toSet()

        if (changedKeys.size > (list.size / 2)) {
            notifyDataSetChanged()
            return
        }

        list.forEachIndexed { index, item ->
            if (changedKeys.contains(progressKey(item))) {
                notifyItemChanged(index)
            }
        }
    }

    private fun syncSelectionWithList() {
        val keysInList = list.map(::progressKey).toSet()
        selectedKeys.retainAll(keysInList)
        if (selectionMode && list.isEmpty()) {
            selectionMode = false
        }
        dispatchSelectionChanged()
    }

    fun refreshRuntimeState() {
        if (itemCount == 0) return
        notifyItemRangeChanged(0, itemCount, PAYLOAD_RUNTIME_STATE_CHANGED)
    }

    private fun dispatchSelectionChanged() {
        val totalCount = list.size
        val selectedCount = selectedKeys.size
        onSelectionChanged?.invoke(
            selectedCount,
            totalCount,
            totalCount > 0 && selectedCount == totalCount
        )
    }

    private fun formatETA(seconds: Long): String {
        return when {
            seconds <= 0 -> "--"
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m"
            else -> "${seconds / 3600}h"
        }
    }

    private fun formatDownloadTime(context: Context, item: DownloadItem): String {
        val timestamp = item.completedAt.takeIf { it > 0L } ?: item.createdAt
        if (timestamp <= 0L) return ""

        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val formattedDate = formatter.format(Date(timestamp))
        return if (item.completedAt > 0L) {
            context.getString(R.string.downloaded_date_format, formattedDate)
        } else {
            context.getString(R.string.started_date_format, formattedDate)
        }
    }
}
