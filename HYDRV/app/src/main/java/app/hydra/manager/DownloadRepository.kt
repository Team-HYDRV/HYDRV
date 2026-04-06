package app.hydra.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DownloadRepository {

    private const val SAVE_DEBOUNCE_MS = 350L
    private const val PROGRESS_NOTIFY_INTERVAL_MS = 120L

    enum class StartResult {
        STARTED,
        BLOCKED_BY_NETWORK,
        INVALID_URL,
        ALREADY_EXISTS
    }

    val downloads = mutableListOf<DownloadItem>()

    private val _downloadsLive = MutableLiveData<List<DownloadItem>>()
    val downloadsLive: LiveData<List<DownloadItem>> = _downloadsLive
    private val mainHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var progressNotifyRunnable: Runnable? = null
    private var lastProgressNotifyAt = 0L
    private val startLock = Any()

    private fun notifyChange() {
        val snapshot = downloads.toList()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _downloadsLive.value = snapshot
        } else {
            mainHandler.post {
                _downloadsLive.value = snapshot
            }
        }
    }

    private fun notifyChangeDebounced() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { notifyChangeDebounced() }
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastProgressNotifyAt
        if (elapsed >= PROGRESS_NOTIFY_INTERVAL_MS) {
            progressNotifyRunnable?.let(mainHandler::removeCallbacks)
            progressNotifyRunnable = null
            lastProgressNotifyAt = now
            notifyChange()
            return
        }

        val remaining = (PROGRESS_NOTIFY_INTERVAL_MS - elapsed).coerceAtLeast(0L)
        if (progressNotifyRunnable == null) {
            val runnable = Runnable {
                progressNotifyRunnable = null
                lastProgressNotifyAt = System.currentTimeMillis()
                notifyChange()
            }
            progressNotifyRunnable = runnable
            mainHandler.postDelayed(runnable, remaining)
        }
    }

    private fun isApkValid(context: Context, item: DownloadItem): Boolean {
        val file = File(item.filePath)

        if (!file.exists()) return false
        if (file.length() == 0L) return false

        if (!file.name.endsWith(".apk", ignoreCase = true)) {
            return true
        }

        return try {
            val info = context.packageManager
                .getPackageArchiveInfo(file.absolutePath, 0)
            info != null
        } catch (e: Exception) {
            false
        }
    }

    private fun archivePackageName(context: Context, filePath: String): String? {
        return try {
            context.packageManager
                .getPackageArchiveInfo(filePath, 0)
                ?.packageName
                ?.trim()
                ?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun startDownload(context: Context, item: DownloadItem): StartResult {
        synchronized(startLock) {
            if (!DownloadNetworkPolicy.canDownloadNow(context)) {
                AppDiagnostics.log(context, "DOWNLOAD", "Blocked by network policy for ${item.name} ${item.versionName}")
                return StartResult.BLOCKED_BY_NETWORK
            }

            val existing = downloads.lastOrNull { itemKey(it) == itemKey(item) }
            if (existing != null) {
                AppDiagnostics.log(context, "DOWNLOAD", "Skipped duplicate start for ${item.name} ${item.versionName}")
                return StartResult.ALREADY_EXISTS
            }

            val now = System.currentTimeMillis()
            val newItem = item.copy(
                createdAt = now,
                completedAt = 0L,
                errorMessage = "",
                installed = false,
                doneHandled = false,
                isAnimatedDone = false,
                lastStatus = "",
                requestToken = now
            )
            if (newItem.backendPackageName.isBlank()) {
                newItem.backendPackageName = newItem.packageName
            }

            downloads.add(newItem)
            AppDiagnostics.log(
                context,
                "DOWNLOAD",
                "Start queued for ${newItem.name} ${newItem.versionName} token=${newItem.requestToken}"
            )
            notifyChange()

            if (!Downloader.start(context, newItem)) {
                downloads.remove(newItem)
                notifyChange()
                return StartResult.INVALID_URL
            }

            scheduleSave(context)
            return StartResult.STARTED
        }
    }

    fun startResultMessage(context: Context, result: StartResult): String? {
        return when (result) {
            StartResult.BLOCKED_BY_NETWORK -> DownloadNetworkPolicy.blockedMessage(context)
            StartResult.INVALID_URL -> context.getString(R.string.download_error_invalid_link)
            StartResult.ALREADY_EXISTS -> context.getString(R.string.download_already_exists)
            StartResult.STARTED -> null
        }
    }

    private fun itemKey(item: DownloadItem): String = item.requestKey()

    fun pause(context: Context, item: DownloadItem) {
        synchronized(startLock) {
            item.requestToken = System.currentTimeMillis()
            item.status = "Paused"
            item.speed = 0f
            item.eta = 0
            AppDiagnostics.log(
                context,
                "DOWNLOAD",
                "Pause requested for ${item.name} ${item.versionName} token=${item.requestToken} progress=${item.progress}"
            )
            Downloader.cancel(item)
            scheduleSave(context)
            notifyChange()
        }
    }

    fun resume(context: Context, item: DownloadItem): StartResult {
        synchronized(startLock) {
            if (!DownloadNetworkPolicy.canDownloadNow(context)) {
                item.status = "Paused"
                item.speed = 0f
                item.eta = 0
                AppDiagnostics.log(
                    context,
                    "DOWNLOAD",
                    "Resume blocked by network policy for ${item.name} ${item.versionName}"
                )
                scheduleSave(context)
                notifyChange()
                return StartResult.BLOCKED_BY_NETWORK
            }

            val file = item.filePath.takeIf { it.isNotBlank() }?.let(::File)
            val canResumeExistingFile = file?.exists() == true && file.length() > 0L

            if (!canResumeExistingFile) {
                file?.takeIf { it.exists() }?.delete()
                item.progress = 0
                item.downloadedBytes = 0L
                item.totalBytes = 0L
                item.filePath = ""
            } else {
                val existingBytes = file.length()
                item.downloadedBytes = existingBytes
                if (item.totalBytes > 0L) {
                    item.progress = ((existingBytes * 100L) / item.totalBytes)
                        .toInt()
                        .coerceIn(0, 99)
                }
            }

            val resumeToken = System.currentTimeMillis()
            item.requestToken = resumeToken
            item.status = "Downloading"
            item.createdAt = resumeToken
            item.completedAt = 0L
            item.speed = 0f
            item.eta = 0
            item.doneHandled = false
            item.isAnimatedDone = false
            item.errorMessage = ""
            item.lastStatus = ""
            AppDiagnostics.log(
                context,
                "DOWNLOAD",
                "Resume requested for ${item.name} ${item.versionName} token=$resumeToken bytes=${item.downloadedBytes}/${item.totalBytes} progress=${item.progress}"
            )

            scheduleSave(context)
            notifyChange()
            if (!Downloader.start(context, item)) {
                item.status = "Failed"
                item.errorMessage = context.getString(R.string.download_error_invalid_link)
                item.speed = 0f
                item.eta = 0
                AppDiagnostics.log(
                    context,
                    "DOWNLOAD",
                    "Resume failed to start for ${item.name} ${item.versionName} token=$resumeToken"
                )
                scheduleSave(context)
                notifyChange()
                return StartResult.INVALID_URL
            }
            return StartResult.STARTED
        }
    }

    fun delete(context: Context, item: DownloadItem) {
        synchronized(startLock) {
            item.requestToken = System.currentTimeMillis()
            Downloader.cancel(item)
            val file = File(item.filePath)
            if (file.exists()) file.delete()

            downloads.remove(item)
            scheduleSave(context)
            notifyChange()
        }
    }

    fun deleteMany(context: Context, items: Collection<DownloadItem>) {
        if (items.isEmpty()) return

        synchronized(startLock) {
            val targets = items.map(::itemKey).toSet()
            val iterator = downloads.iterator()
            var changed = false

            while (iterator.hasNext()) {
                val item = iterator.next()
                if (!targets.contains(itemKey(item))) continue

                item.requestToken = System.currentTimeMillis()
                Downloader.cancel(item)
                val file = File(item.filePath)
                if (file.exists()) file.delete()
                iterator.remove()
                changed = true
            }

            if (!changed) return

            scheduleSave(context)
            notifyChange()
        }
    }

    fun clearCompleted(context: Context) {
        deleteMany(context, downloads.filter { it.status == "Done" })
    }

    fun updateProgress(
        context: Context,
        item: DownloadItem,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speedKb: Float,
        etaSeconds: Long
    ) {
        synchronized(startLock) {
            if (item.status == "Paused") return

            val previousProgress = item.progress
            val previousSpeed = item.speed
            val previousEta = item.eta

            item.progress = progress
            item.downloadedBytes = downloadedBytes
            item.totalBytes = totalBytes
            item.speed = speedKb
            item.eta = etaSeconds

            scheduleSave(context)
            if (
                previousProgress != progress ||
                previousSpeed != speedKb ||
                previousEta != etaSeconds
            ) {
                notifyChangeDebounced()
            }
        }
    }

    fun markDone(context: Context, item: DownloadItem) {
        synchronized(startLock) {
            item.status = "Done"
            item.progress = 100
            item.errorMessage = ""
            if (item.completedAt == 0L) {
                item.completedAt = System.currentTimeMillis()
            }
            item.speed = 0f
            item.eta = 0
            AppDiagnostics.log(
                context,
                "DOWNLOAD",
                "Marked done for ${item.name} ${item.versionName} token=${item.requestToken}"
            )

            if (!isApkValid(context, item)) {
                delete(context, item)
                return
            }

            val backendPackage = item.packageName
            if (item.filePath.endsWith(".apk", ignoreCase = true)) {
                archivePackageName(context, item.filePath)?.let { actualPackage ->
                    item.packageName = actualPackage
                    InstallAliasStore.saveAlias(context, item.name, backendPackage, actualPackage)
                }
            }

            scheduleSave(context)
            notifyChange()
        }
    }

    fun handleInstallSuccess(context: Context, appName: String, apkPath: String, backendPackage: String) {
        val actualPackage = archivePackageName(context, apkPath) ?: return

        downloads.forEach { item ->
            if (item.filePath == apkPath || (item.name == appName && item.status == "Done")) {
                item.packageName = actualPackage
                item.installed = true
            }
        }

        InstallAliasStore.saveAlias(context, appName, backendPackage, actualPackage)
        scheduleSave(context)
        notifyChange()
    }

    fun markFailed(context: Context, item: DownloadItem, errorMessage: String) {
        synchronized(startLock) {
            item.status = "Failed"
            item.speed = 0f
            item.eta = 0
            item.errorMessage = errorMessage
            AppDiagnostics.log(
                context,
                "DOWNLOAD",
                "Marked failed for ${item.name} ${item.versionName} token=${item.requestToken} reason=$errorMessage"
            )
            scheduleSave(context)
            notifyChange()
        }
    }

    fun scheduleSave(context: Context) {
        saveRunnable?.let(mainHandler::removeCallbacks)
        val appContext = context.applicationContext
        val runnable = Runnable {
            persistNow(appContext)
            saveRunnable = null
        }
        saveRunnable = runnable
        mainHandler.postDelayed(runnable, SAVE_DEBOUNCE_MS)
    }

    fun save(context: Context) {
        saveRunnable?.let(mainHandler::removeCallbacks)
        saveRunnable = null
        persistNow(context.applicationContext)
    }

    private fun persistNow(context: Context) {
        val array = JSONArray()

        downloads.forEach {
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("url", it.url)
            obj.put("progress", it.progress)
            obj.put("status", it.status)
            obj.put("createdAt", it.createdAt)
            obj.put("completedAt", it.completedAt)
            obj.put("downloadedBytes", it.downloadedBytes)
            obj.put("totalBytes", it.totalBytes)
            obj.put("filePath", it.filePath)
            obj.put("packageName", it.packageName)
            obj.put("versionName", it.versionName)
            obj.put("errorMessage", it.errorMessage)
            obj.put("installed", it.installed)
            obj.put("speed", it.speed)
            obj.put("eta", it.eta)
            obj.put("requestToken", it.requestToken)
            obj.put("backendPackageName", it.backendPackageName)
            array.put(obj)
        }

        DownloadStore.write(context, array.toString())
    }

    fun load(context: Context) {
        val data = DownloadStore.read(context) ?: return

        val loadedItems = mutableListOf<DownloadItem>()

        try {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                val item = DownloadItem(
                    name = obj.getString("name"),
                    url = obj.getString("url"),
                    progress = obj.optInt("progress", 0),
                    status = obj.optString("status", "Paused"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    completedAt = obj.optLong("completedAt", 0L),
                    downloadedBytes = obj.optLong("downloadedBytes", 0L),
                    totalBytes = obj.optLong("totalBytes", 0L),
                    filePath = obj.optString("filePath", ""),
                    speed = obj.optDouble("speed", 0.0).toFloat(),
                    eta = obj.optLong("eta", 0L),
                    packageName = obj.optString("packageName", ""),
                    versionName = obj.optString("versionName", ""),
                    errorMessage = obj.optString("errorMessage", ""),
                    installed = obj.optBoolean("installed", false),
                    requestToken = obj.optLong("requestToken", 0L),
                    backendPackageName = obj.optString("backendPackageName", obj.optString("packageName", ""))
                )

                if (item.backendPackageName.isBlank()) {
                    item.backendPackageName = item.packageName
                }

                if (item.progress in 1..99 && item.status == "Downloading") {
                    if (DownloadNetworkPolicy.canDownloadNow(context)) {
                        Downloader.start(context, item)
                    } else {
                        item.status = "Paused"
                        item.speed = 0f
                        item.eta = 0
                    }
                }

                if (item.progress == 100) {
                    item.status = "Done"
                    if (item.completedAt == 0L) {
                        item.completedAt = item.createdAt
                    }
                }

                loadedItems.add(item)
            }
        } catch (_: Exception) {
            DownloadStore.clear(context)
            downloads.clear()
            notifyChange()
            return
        }

        val existingByKey = downloads.associateBy(::itemKey).toMutableMap()
        val merged = mutableListOf<DownloadItem>()

        loadedItems.forEach { loaded ->
            val key = itemKey(loaded)
            val existing = existingByKey.remove(key)
            if (existing == null) {
                merged.add(loaded)
                return@forEach
            }

            merged.add(mergeLoadedItem(existing, loaded))
        }

        merged.addAll(existingByKey.values)

        downloads.clear()
        downloads.addAll(merged)
        notifyChange()
    }

    private fun mergeLoadedItem(existing: DownloadItem, loaded: DownloadItem): DownloadItem {
        return existing.copy(
            progress = maxOf(existing.progress, loaded.progress),
            status = pickBetterStatus(existing.status, loaded.status),
            createdAt = minOf(existing.createdAt, loaded.createdAt),
            completedAt = maxOf(existing.completedAt, loaded.completedAt),
            downloadedBytes = maxOf(existing.downloadedBytes, loaded.downloadedBytes),
            totalBytes = maxOf(existing.totalBytes, loaded.totalBytes),
            filePath = if (existing.filePath.isNotBlank()) existing.filePath else loaded.filePath,
            speed = maxOf(existing.speed, loaded.speed),
            eta = minOfPositive(existing.eta, loaded.eta),
            packageName = if (existing.packageName.isNotBlank()) existing.packageName else loaded.packageName,
            errorMessage = if (existing.errorMessage.isNotBlank()) existing.errorMessage else loaded.errorMessage,
            installed = existing.installed || loaded.installed,
            isAnimatedDone = existing.isAnimatedDone || loaded.isAnimatedDone,
            lastStatus = if (existing.lastStatus.isNotBlank()) existing.lastStatus else loaded.lastStatus,
            doneHandled = existing.doneHandled || loaded.doneHandled,
            requestToken = maxOf(existing.requestToken, loaded.requestToken)
        )
    }

    private fun pickBetterStatus(a: String, b: String): String {
        return when {
            a == "Downloading" || b == "Downloading" -> "Downloading"
            a == "Paused" || b == "Paused" -> "Paused"
            a == "Failed" || b == "Failed" -> "Failed"
            a == "Done" || b == "Done" -> "Done"
            else -> if (a.isNotBlank()) a else b
        }
    }

    private fun minOfPositive(a: Long, b: Long): Long {
        return when {
            a > 0L && b > 0L -> minOf(a, b)
            a > 0L -> a
            b > 0L -> b
            else -> 0L
        }
    }
}
