package app.hydra.manager

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.SocketException
import java.util.concurrent.TimeUnit

object Downloader {

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val activeCalls = mutableMapOf<String, Call>()

    private fun keyFor(item: DownloadItem): String {
        return item.requestKey()
    }

    private fun removeActiveCallIfSame(key: String, call: Call) {
        synchronized(activeCalls) {
            if (activeCalls[key] === call) {
                activeCalls.remove(key)
            }
        }
    }

    fun cancel(item: DownloadItem) {
        val key = keyFor(item)
        synchronized(activeCalls) {
            activeCalls.remove(key)?.cancel()
        }
    }

    fun start(context: Context, item: DownloadItem): Boolean {
        return startInternal(context, item, forceFresh = false)
    }

    private fun startInternal(
        context: Context,
        item: DownloadItem,
        forceFresh: Boolean,
        retryOnExpiredToken: Boolean = true
    ): Boolean {
        val requestToken = item.requestToken
        val key = keyFor(item)
        synchronized(activeCalls) {
            activeCalls[key]?.cancel()
            activeCalls.remove(key)
        }

        val requestUrl = item.url.trim().toHttpUrlOrNull() ?: return false
        val isTokenEndpoint = requestUrl.encodedPath.contains("/token/", ignoreCase = true)

        var file = if (!forceFresh) {
            item.filePath.takeIf { it.isNotBlank() }?.let(::File)
        } else {
            null
        }
        file = if (file?.exists() == true) {
            file
        } else {
            File(
                context.getExternalFilesDir(null),
                suggestDownloadFileName(requestUrl.encodedPath, item, "download.bin")
            ).also {
                item.filePath = it.absolutePath
            }
        }
        val resumeBytes = if (!forceFresh) {
            file.takeIf { it.exists() }?.length()?.takeIf { it > 0L } ?: 0L
        } else {
            0L
        }
        AppDiagnostics.log(
            context,
            "DOWNLOAD",
            "Start network for ${item.name} ${item.versionName} token=$requestToken resumeBytes=$resumeBytes forceFresh=$forceFresh"
        )

        if (resumeBytes > 0L && item.totalBytes > 0L && resumeBytes >= item.totalBytes) {
            DownloadRepository.updateProgress(
                context,
                item,
                100,
                resumeBytes,
                resumeBytes,
                0f,
                0
            )
            DownloadRepository.markDone(context, item)
            return true
        }

        fun startFileRequest(fileUrl: String) {
            val resolvedUrl = fileUrl.trim().toHttpUrlOrNull() ?: run {
                DownloadRepository.markFailed(
                    context,
                    item,
                    context.getString(R.string.download_error_invalid_link)
                )
                return
            }

            val requestBuilder = Request.Builder().url(resolvedUrl)
            if (resumeBytes > 0L) {
                requestBuilder.header("Range", "bytes=$resumeBytes-")
            }
            val request = requestBuilder.build()
            val call = client.newCall(request)

            synchronized(activeCalls) {
                activeCalls[key] = call
            }

            call.enqueue(object : Callback {
            fun isStale(): Boolean = item.requestToken != requestToken

            fun isBenignInterruption(error: Throwable? = null): Boolean {
                return isStale() ||
                    call.isCanceled() ||
                    item.status == "Paused" ||
                    item.status == "Stopped" ||
                    (error is SocketException &&
                        error.message?.contains("socket closed", ignoreCase = true) == true)
            }

            override fun onFailure(call: Call, e: java.io.IOException) {
                removeActiveCallIfSame(key, call)

                if (isBenignInterruption(e)) {
                    AppDiagnostics.log(
                        context,
                        "DOWNLOAD",
                        "Benign interruption for ${item.name} ${item.versionName} token=$requestToken reason=${e::class.java.simpleName}: ${e.message.orEmpty()}"
                    )
                    item.speed = 0f
                    item.eta = 0
                    DownloadRepository.scheduleSave(context)
                    DownloadRepository.updateProgress(
                        context,
                        item,
                        item.progress,
                        item.downloadedBytes,
                        item.totalBytes,
                        0f,
                        0
                    )
                    return
                }

                AppDiagnostics.log(context, "DOWNLOAD", "Network failure while downloading ${item.name}", e)
                DownloadRepository.markFailed(
                    context,
                    item,
                    context.getString(R.string.download_error_network)
                )
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (isStale()) {
                        AppDiagnostics.log(
                            context,
                            "DOWNLOAD",
                            "Ignoring stale response for ${item.name} ${item.versionName} token=$requestToken"
                        )
                        response.close()
                        return
                    }
                    if (response.code == 416 && resumeBytes > 0L && !forceFresh) {
                        AppDiagnostics.log(
                            context,
                            "DOWNLOAD",
                            "Resume range rejected for ${item.name} ${item.versionName} token=$requestToken; restarting fresh"
                        )
                        response.close()
                        item.progress = 0
                        item.downloadedBytes = 0L
                        item.totalBytes = 0L
                        item.filePath = ""
                        item.requestToken = System.currentTimeMillis()
                        startInternal(context, item, forceFresh = true, retryOnExpiredToken = retryOnExpiredToken)
                        return
                    }

                    if (response.code in 401..403 && isTokenEndpoint && retryOnExpiredToken) {
                        AppDiagnostics.log(
                            context,
                            "DOWNLOAD",
                            "Token expired for ${item.name} ${item.versionName}; refreshing"
                        )
                        response.close()
                        item.requestToken = System.currentTimeMillis()
                        startInternal(context, item, forceFresh = false, retryOnExpiredToken = false)
                        return
                    }

                    if (!response.isSuccessful) {
                        AppDiagnostics.log(
                            context,
                            "DOWNLOAD",
                            "HTTP ${response.code} while downloading ${item.name}"
                        )
                        DownloadRepository.markFailed(
                            context,
                            item,
                            context.getString(R.string.download_error_http_format, response.code)
                        )
                        response.close()
                        return
                    }

                    val body = response.body ?: return
                    file = resolveDownloadFile(context, item, requestUrl, response, file, forceFresh)
                    item.filePath = file.absolutePath
                    val isPartialResponse = response.code == 206 && resumeBytes > 0L
                    val startingBytes = if (isPartialResponse) resumeBytes else 0L
                    val total = body.contentLength().takeIf { it > 0L } ?: 0L

                    if (!isPartialResponse && resumeBytes > 0L) {
                        item.downloadedBytes = 0L
                        item.progress = 0
                        file.parentFile?.mkdirs()
                        RandomAccessFile(file, "rw").use { it.setLength(0L) }
                    }

                    item.totalBytes = if (isPartialResponse && total > 0L) {
                        startingBytes + total
                    } else {
                        total
                    }
                    AppDiagnostics.log(
                        context,
                        "DOWNLOAD",
                        "Response ready for ${item.name} ${item.versionName} token=$requestToken code=${response.code} partial=$isPartialResponse total=${item.totalBytes}"
                    )

                    if (isStale() || call.isCanceled() || item.status == "Paused" || item.status == "Stopped") {
                        return
                    }

                    body.byteStream().use { input ->
                        RandomAccessFile(file, "rw").use { raf ->
                            if (startingBytes > 0L) {
                                raf.seek(startingBytes)
                            } else {
                                raf.setLength(0L)
                            }
                            val buffer = ByteArray(8192)
                            var bytes: Int
                            var lastTime = System.currentTimeMillis()
                            var lastBytes = item.downloadedBytes
                            var speedKb = 0f
                            var etaSeconds = 0L

                            if (item.status == "Paused" || item.status == "Stopped") {
                                return
                            }
                            item.status = "Downloading"

                            while (input.read(buffer).also { bytes = it } != -1) {
                                if (isStale() || call.isCanceled() || item.status == "Paused" || item.status == "Stopped") {
                                    return
                                }

                                raf.write(buffer, 0, bytes)
                                item.downloadedBytes += bytes

                                val progress = if (item.totalBytes > 0) {
                                    ((item.downloadedBytes * 100L) / item.totalBytes)
                                        .toInt()
                                        .coerceAtMost(100)
                                } else {
                                    (item.progress + 1).coerceAtMost(99)
                                }

                                val currentTime = System.currentTimeMillis()
                                val timeDiff = currentTime - lastTime

                                if (timeDiff >= 250) {
                                    val bytesDiff = item.downloadedBytes - lastBytes

                                    if (bytesDiff > 0) {
                                        val speed = bytesDiff / (timeDiff / 1000f)
                                        speedKb = speed / 1024f

                                        val remainingBytes = (item.totalBytes - item.downloadedBytes)
                                            .coerceAtLeast(0L)
                                        etaSeconds = if (speed > 0 && item.totalBytes > 0) {
                                            (remainingBytes / speed).toLong()
                                        } else {
                                            0
                                        }

                                        item.speed = speedKb
                                        item.eta = etaSeconds
                                    }

                                    lastTime = currentTime
                                    lastBytes = item.downloadedBytes
                                }

                                DownloadRepository.updateProgress(
                                    context,
                                    item,
                                    progress,
                                    item.downloadedBytes,
                                    item.totalBytes,
                                    speedKb,
                                    etaSeconds
                                )
                            }
                        }
                    }

                    if (isStale() || call.isCanceled() || item.status == "Paused" || item.status == "Stopped") {
                        return
                    }

                    DownloadRepository.updateProgress(
                        context,
                        item,
                        100,
                        item.downloadedBytes,
                        item.downloadedBytes,
                        0f,
                        0
                    )

                    DownloadRepository.markDone(context, item)
                } catch (e: Exception) {
                    if (isBenignInterruption(e)) {
                        item.speed = 0f
                        item.eta = 0
                        DownloadRepository.scheduleSave(context)
                        DownloadRepository.updateProgress(
                            context,
                            item,
                            item.progress,
                            item.downloadedBytes,
                            item.totalBytes,
                            0f,
                            0
                        )
                        return
                    }
                    AppDiagnostics.log(context, "DOWNLOAD", "Write failure while downloading ${item.name}", e)
                    DownloadRepository.markFailed(
                        context,
                        item,
                        context.getString(R.string.download_error_write_failed)
                    )
                } finally {
                    removeActiveCallIfSame(key, call)
                }
            }
            })
        }

        if (isTokenEndpoint) {
            val requestBuilder = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
            val request = requestBuilder.build()
            val call = client.newCall(request)

            synchronized(activeCalls) {
                activeCalls[key] = call
            }

            call.enqueue(object : Callback {
                fun isStale(): Boolean = item.requestToken != requestToken

                fun isBenignInterruption(error: Throwable? = null): Boolean {
                    return isStale() ||
                        call.isCanceled() ||
                        item.status == "Paused" ||
                        item.status == "Stopped" ||
                        (error is SocketException &&
                            error.message?.contains("socket closed", ignoreCase = true) == true)
                }

                override fun onFailure(call: Call, e: java.io.IOException) {
                    removeActiveCallIfSame(key, call)

                    if (isBenignInterruption(e)) {
                        AppDiagnostics.log(
                            context,
                            "DOWNLOAD",
                            "Benign interruption while resolving token for ${item.name} ${item.versionName} token=$requestToken reason=${e::class.java.simpleName}: ${e.message.orEmpty()}"
                        )
                        item.speed = 0f
                        item.eta = 0
                        DownloadRepository.scheduleSave(context)
                        DownloadRepository.updateProgress(
                            context,
                            item,
                            item.progress,
                            item.downloadedBytes,
                            item.totalBytes,
                            0f,
                            0
                        )
                        return
                    }

                    AppDiagnostics.log(context, "DOWNLOAD", "Token resolution failure while downloading ${item.name}", e)
                    DownloadRepository.markFailed(
                        context,
                        item,
                        context.getString(R.string.download_error_network)
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (isStale()) {
                            response.close()
                            return
                        }

                        if (!response.isSuccessful) {
                            AppDiagnostics.log(
                                context,
                                "DOWNLOAD",
                                "HTTP ${response.code} while resolving token for ${item.name}"
                            )
                            DownloadRepository.markFailed(
                                context,
                                item,
                                context.getString(R.string.download_error_http_format, response.code)
                            )
                            response.close()
                            return
                        }

                        val tokenBody = response.body?.string().orEmpty()
                        val resolvedDownloadUrl = runCatching {
                            JSONObject(tokenBody).optString("downloadUrl").trim()
                        }.getOrDefault("")
                        response.close()

                        if (resolvedDownloadUrl.isBlank()) {
                            AppDiagnostics.log(
                                context,
                                "DOWNLOAD",
                                "Token response missing downloadUrl for ${item.name} ${item.versionName}"
                            )
                            DownloadRepository.markFailed(
                                context,
                                item,
                                context.getString(R.string.download_error_invalid_link)
                            )
                            return
                        }

                        startFileRequest(resolvedDownloadUrl)
                    } catch (e: Exception) {
                        if (isBenignInterruption(e)) {
                            item.speed = 0f
                            item.eta = 0
                            DownloadRepository.scheduleSave(context)
                            DownloadRepository.updateProgress(
                                context,
                                item,
                                item.progress,
                                item.downloadedBytes,
                                item.totalBytes,
                                0f,
                                0
                            )
                            return
                        }
                        AppDiagnostics.log(context, "DOWNLOAD", "Token parse failure while downloading ${item.name}", e)
                        DownloadRepository.markFailed(
                            context,
                            item,
                            context.getString(R.string.download_error_invalid_link)
                        )
                    } finally {
                        removeActiveCallIfSame(key, call)
                    }
                }
            })
            return true
        }

        startFileRequest(requestUrl.toString())

        return true
    }

    private fun resolveDownloadFile(
        context: Context,
        item: DownloadItem,
        requestUrl: okhttp3.HttpUrl,
        response: Response,
        currentFile: File?,
        forceFresh: Boolean
    ): File {
        val suggestedName = suggestDownloadFileName(
            requestUrl.encodedPath,
            item,
            response.header("Content-Disposition")
                ?.let(::extractFilenameFromContentDisposition)
                ?: response.header("Content-Type")
                ?.let(::fileExtensionFromContentType)
                ?.let { ext -> "download$ext" }
                ?: "download.bin"
        )

        val current = currentFile?.takeIf { it.exists() && !forceFresh }
        if (current != null) {
            val currentNameMatches = current.name.equals(suggestedName, ignoreCase = true)
            if (currentNameMatches) return current

            val renamed = File(current.parentFile, suggestedName)
            if (current.absolutePath != renamed.absolutePath) {
                runCatching {
                    current.parentFile?.mkdirs()
                    if (current.renameTo(renamed)) {
                        return renamed
                    }
                }
            }
            return current
        }

        return File(context.getExternalFilesDir(null), suggestedName)
    }

    private fun suggestDownloadFileName(encodedPath: String, item: DownloadItem, fallbackName: String): String {
        val pathName = encodedPath
            .substringAfterLast('/', "")
            .takeIf { it.isNotBlank() && it != "token" && it != "download" }

        val baseName = pathName
            ?: item.name.trim().ifBlank { fallbackName }

        val sanitized = sanitizeFileName(baseName)
        val versionTagged = if (item.versionCode > 0) {
            val suffix = "-${item.versionCode}"
            if (sanitized.contains('.')) {
                val extension = sanitized.substringAfterLast('.', "")
                val stem = sanitized.substringBeforeLast('.')
                if (stem.endsWith(suffix)) sanitized else "$stem$suffix.$extension"
            } else {
                if (sanitized.endsWith(suffix)) sanitized else "$sanitized$suffix"
            }
        } else {
            sanitized
        }
        return when {
            versionTagged.endsWith(".apk", ignoreCase = true) -> versionTagged
            item.packageName.isNotBlank() -> "$versionTagged.apk"
            versionTagged.contains('.') -> versionTagged
            else -> versionTagged
        }
    }

    private fun sanitizeFileName(value: String): String {
        val clean = value.trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "download.bin" }

        return clean
    }

    private fun extractFilenameFromContentDisposition(disposition: String): String? {
        val direct = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrDefault(it) }

        if (!direct.isNullOrBlank()) return sanitizeFileName(direct)

        val simple = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(disposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return simple?.let(::sanitizeFileName)
    }

    private fun fileExtensionFromContentType(contentType: String): String? {
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return when (normalized) {
            "application/vnd.android.package-archive" -> ".apk"
            "application/zip", "application/x-zip-compressed" -> ".zip"
            "application/json" -> ".json"
            "text/plain" -> ".txt"
            "text/csv" -> ".csv"
            "application/xml", "text/xml" -> ".xml"
            "text/html" -> ".html"
            "application/pdf" -> ".pdf"
            else -> null
        }
    }

}
