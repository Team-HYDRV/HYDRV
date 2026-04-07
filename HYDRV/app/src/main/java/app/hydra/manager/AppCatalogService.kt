package app.hydra.manager

import android.os.Handler
import android.os.Looper
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AppCatalogService {

    data class FetchResult(
        val apps: List<AppModel>,
        val fromCache: Boolean
    )

    private class CatalogFetchSession {
        @Volatile var started: Boolean = false
        @Volatile var cancelled: Boolean = false
        @Volatile var currentCall: Call? = null

        val call: Call = object : Call {
            override fun request(): Request {
                return currentCall?.request()
                    ?: Request.Builder().url(RuntimeConfig.defaultCatalogUrl).build()
            }

            override fun execute(): Response {
                throw UnsupportedOperationException("Use fetchApps callback")
            }

            override fun enqueue(responseCallback: Callback) {
                throw UnsupportedOperationException("Use fetchApps callback")
            }

            override fun cancel() {
                cancelled = true
                currentCall?.cancel()
            }

            override fun isExecuted(): Boolean {
                return started
            }

            override fun isCanceled(): Boolean {
                return cancelled || currentCall?.isCanceled() == true
            }

            override fun clone(): Call {
                return this
            }

            override fun timeout(): okio.Timeout {
                return currentCall?.timeout() ?: okio.Timeout.NONE
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val appListType = object : TypeToken<List<AppModel>>() {}.type
    private val mainHandler = Handler(Looper.getMainLooper())
    private data class RequestSpec(val call: Call, val url: String)

    fun requestApps(context: Context, bypassRemoteCache: Boolean = false): Call {
        val baseUrl = BackendPreferences.getCatalogUrl(context)
        return buildRequest(baseUrl, bypassRemoteCache).call
    }

    private fun buildRequest(
        baseUrl: String,
        bypassRemoteCache: Boolean
    ): RequestSpec {
        val requestUrl = if (bypassRemoteCache) {
            baseUrl.toHttpUrlOrNull()
                ?.newBuilder()
                ?.setQueryParameter("_hydrv_ts", System.currentTimeMillis().toString())
                ?.build()
                ?.toString()
                ?: baseUrl
        } else {
            baseUrl
        }

        return RequestSpec(
            client.newCall(
            Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/json")
                .header("Cache-Control", if (bypassRemoteCache) "no-cache" else "max-age=0")
                .header("Pragma", if (bypassRemoteCache) "no-cache" else "no-cache")
                .build()
            ),
            baseUrl
        )
    }

    fun fetchApps(
        context: Context,
        allowCacheFallback: Boolean = true,
        bypassRemoteCache: Boolean = false,
        onResult: (Result<FetchResult>) -> Unit
    ): Call {
        val appContext = context.applicationContext
        val candidates = BackendPreferences.getCatalogUrlCandidates(appContext)
        val firstRequest = buildRequest(candidates.first(), bypassRemoteCache)
        val call = firstRequest.call

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                tryNextCandidateAsync(
                    context = appContext,
                    candidates = candidates.drop(1),
                    allowCacheFallback = allowCacheFallback,
                    bypassRemoteCache = bypassRemoteCache,
                    originalError = e,
                    onResult = onResult
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) {
                    response.close()
                    return
                }

                response.use {
                    if (!response.isSuccessful) {
                        tryNextCandidateAsync(
                            context = appContext,
                            candidates = candidates.drop(1),
                            allowCacheFallback = allowCacheFallback,
                            bypassRemoteCache = bypassRemoteCache,
                            originalError = IOException("HTTP ${response.code}"),
                            onResult = onResult
                        )
                        return
                    }

                    val parsed = parseResponseToCacheFile(
                        context = appContext,
                        cacheUrl = firstRequest.url,
                        response = response
                    )
                    if (parsed.isSuccess) {
                        postResult(onResult, Result.success(FetchResult(parsed.getOrThrow(), fromCache = false)))
                        return
                    }

                    tryNextCandidateAsync(
                        context = appContext,
                        candidates = candidates.drop(1),
                        allowCacheFallback = allowCacheFallback,
                        bypassRemoteCache = bypassRemoteCache,
                        originalError = parsed.exceptionOrNull().asException(),
                        onResult = onResult
                    )
                }
            }
        })

        return call
    }

    fun fetchAppsSync(
        context: Context,
        allowCacheFallback: Boolean = true,
        bypassRemoteCache: Boolean = false
    ): Result<FetchResult> {
        val appContext = context.applicationContext
        val candidates = BackendPreferences.getCatalogUrlCandidates(appContext)
        var lastError: Exception? = null

        candidates.forEach { candidate ->
            val request = buildRequest(candidate, bypassRemoteCache)
            val result = runCatching {
                request.call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val parsed = parseResponseToCacheFile(
                        context = appContext,
                        cacheUrl = candidate,
                        response = response
                    ).getOrThrow()
                    Result.success(FetchResult(parsed, fromCache = false))
                }
            }.getOrElse {
                lastError = it.asException()
                null
            }

            if (result != null) return result
        }

        val fallback = if (allowCacheFallback) {
            candidates.firstNotNullOfOrNull { readCachedResult(appContext, it) }
        } else {
            null
        }
        return fallback ?: Result.failure(lastError ?: IOException("Catalog request failed"))
    }

    fun validateCatalogEndpointSync(url: String): Result<Unit> {
        val requestUrl = url.trim().toHttpUrlOrNull()
            ?: return Result.failure(IOException("Invalid backend URL"))

        return runCatching {
            client.newCall(
                Request.Builder()
                    .url(requestUrl)
                    .header("Accept", "application/json")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                parseResponseToCacheFile(
                    context = null,
                    cacheUrl = null,
                    response = response
                ).getOrThrow()
            }
        }.map { Unit }
    }

    fun readCachedApps(context: Context): Result<FetchResult>? {
        val appContext = context.applicationContext
        return BackendPreferences.getCatalogUrlCandidates(appContext)
            .firstNotNullOfOrNull { readCachedResult(appContext, it) }
            ?: readAnyCachedResult(appContext)
    }

    private fun parse(raw: String): Result<List<AppModel>> {
        return raw.reader().use { parse(it) }
    }

    private fun parse(reader: java.io.Reader): Result<List<AppModel>> {
        return try {
            val apps = sanitizeParsedApps(parseRawApps(reader))
            val validation = CatalogValidation.validate(apps)
            if (validation.isSuccess) {
                Result.success(validation.getOrThrow())
            } else {
                Result.failure(validation.exceptionOrNull() ?: IOException("Catalog validation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRawApps(reader: java.io.Reader): List<AppModel> {
        val jsonReader = JsonReader(reader).apply { isLenient = true }
        val candidates = listOf("apps", "catalog", "catalogue", "items", "data")

        return when (jsonReader.peek()) {
            JsonToken.BEGIN_ARRAY -> runCatching {
                gson.fromJson<List<AppModel>>(jsonReader, appListType)
            }.getOrDefault(emptyList())

            JsonToken.BEGIN_OBJECT -> {
                jsonReader.beginObject()
                while (jsonReader.hasNext()) {
                    val name = jsonReader.nextName()
                    if (name in candidates && jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
                        val parsed = runCatching {
                            gson.fromJson<List<AppModel>>(jsonReader, appListType)
                        }.getOrDefault(emptyList())
                        if (parsed.isNotEmpty()) {
                            if (jsonReader.peek() == JsonToken.END_OBJECT) {
                                jsonReader.endObject()
                            }
                            return parsed
                        }
                    } else {
                        jsonReader.skipValue()
                    }
                }
                jsonReader.endObject()
                emptyList()
            }

            else -> emptyList()
        }
    }

    private fun sanitizeParsedApps(apps: List<AppModel>): List<AppModel> {
        return apps.mapNotNull { app ->
            val safeName = text(app.name)
            if (safeName.isEmpty()) return@mapNotNull null

            val safePackage = text(app.packageName)
            val safeIcon = text(app.icon)
            val safeTags = strings(app.tags)
                val safeVersions = app.versions
                .mapNotNull { version ->
                    val safeVersionName = text(version.version_name).ifBlank {
                        "v${version.version}"
                    }
                    val safeUrl = text(version.url)
                    val safeChangelog = text(version.changelog)

                    if (safeUrl.isEmpty() || version.version <= 0) return@mapNotNull null

                    version.copy(
                        version_name = safeVersionName,
                        url = safeUrl,
                        changelog = safeChangelog
                    )
                }

            app.copy(
                name = safeName,
                packageName = safePackage,
                icon = safeIcon,
                tags = safeTags,
                versions = safeVersions
            )
        }
    }

    private fun text(value: Any?): String {
        return (value as? String).orEmpty().trim()
    }

    private fun strings(value: Any?): List<String> {
        return (value as? List<*>)?.mapNotNull {
            (it as? String)?.trim()?.takeIf(String::isNotEmpty)
        }.orEmpty()
    }

    private fun readCachedResult(context: Context, url: String): Result<FetchResult>? {
        val cached = CatalogCacheStore.file(context, url) ?: return null
        val parsed = runCatching {
            cached.inputStream().reader(Charsets.UTF_8).use { reader -> parse(reader).getOrThrow() }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(it) }
        )
        return if (parsed.isSuccess) {
            Result.success(FetchResult(parsed.getOrThrow(), fromCache = true))
        } else {
            null
        }
    }

    private fun readAnyCachedResult(context: Context): Result<FetchResult>? {
        val cacheDir = context.cacheDir ?: return null
        val files = cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith("catalog_") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        return files.firstNotNullOfOrNull { file ->
            val parsed = runCatching {
                file.inputStream().reader(Charsets.UTF_8).use { reader -> parse(reader).getOrThrow() }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(it) }
            )
            if (parsed.isSuccess) {
                Result.success(FetchResult(parsed.getOrThrow(), fromCache = true))
            } else {
                null
            }
        }
    }

    private fun postResult(
        callback: (Result<FetchResult>) -> Unit,
        result: Result<FetchResult>
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(result)
        } else {
            mainHandler.post { callback(result) }
        }
    }

    private fun tryNextCandidateAsync(
        context: Context,
        candidates: List<String>,
        allowCacheFallback: Boolean,
        bypassRemoteCache: Boolean,
        originalError: Exception,
        onResult: (Result<FetchResult>) -> Unit
    ) {
        if (candidates.isEmpty()) {
            val fallback = if (allowCacheFallback) {
                BackendPreferences.getCatalogUrlCandidates(context)
                    .firstNotNullOfOrNull { readCachedResult(context, it) }
            } else {
                null
            }
            postResult(onResult, fallback ?: Result.failure(originalError))
            return
        }

        val next = buildRequest(candidates.first(), bypassRemoteCache)
        next.call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                tryNextCandidateAsync(
                    context = context,
                    candidates = candidates.drop(1),
                    allowCacheFallback = allowCacheFallback,
                    bypassRemoteCache = bypassRemoteCache,
                    originalError = e,
                    onResult = onResult
                )
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) {
                    response.close()
                    return
                }

                response.use {
                    if (!response.isSuccessful) {
                        tryNextCandidateAsync(
                            context = context,
                            candidates = candidates.drop(1),
                            allowCacheFallback = allowCacheFallback,
                            bypassRemoteCache = bypassRemoteCache,
                            originalError = IOException("HTTP ${response.code}"),
                            onResult = onResult
                        )
                        return
                    }

                    val parsed = parseResponseToCacheFile(
                        context = context,
                        cacheUrl = next.url,
                        response = response
                    )
                    if (parsed.isSuccess) {
                        postResult(onResult, Result.success(FetchResult(parsed.getOrThrow(), fromCache = false)))
                    } else {
                        tryNextCandidateAsync(
                            context = context,
                            candidates = candidates.drop(1),
                            allowCacheFallback = allowCacheFallback,
                            bypassRemoteCache = bypassRemoteCache,
                            originalError = parsed.exceptionOrNull().asException(),
                            onResult = onResult
                        )
                    }
                }
            }
        })
    }

    private fun fetchSourceSync(
        context: Context,
        source: BackendSource,
        allowCacheFallback: Boolean,
        bypassRemoteCache: Boolean,
        session: CatalogFetchSession? = null
    ): Result<FetchResult> {
        val request = buildRequest(source.url, bypassRemoteCache)
        val call = request.call
        session?.currentCall = call

        return try {
            if (session?.cancelled == true) {
                Result.failure(IOException("Catalog request canceled"))
            } else {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val fallback = if (allowCacheFallback) {
                            readCachedResult(context, source.url)
                        } else {
                            null
                        }
                        fallback ?: Result.failure(IOException("HTTP ${response.code}"))
                    } else {
                        val parsed = parseResponseToCacheFile(
                            context = context,
                            cacheUrl = source.url,
                            response = response
                        )
                        if (parsed.isSuccess) {
                            Result.success(
                                FetchResult(
                                    apps = parsed.getOrThrow().map {
                                        it.withCatalogSource(source.name, source.url)
                                    },
                                    fromCache = false
                                )
                            )
                        } else {
                            val fallback = if (allowCacheFallback) {
                                readCachedResult(context, source.url)
                            } else {
                                null
                            }
                            fallback ?: Result.failure(parsed.exceptionOrNull().asException())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val fallback = if (allowCacheFallback) {
                readCachedResult(context, source.url)
            } else {
                null
            }
            fallback ?: Result.failure(e.asException())
        } finally {
            if (session?.currentCall === call) {
                session.currentCall = null
            }
        }
    }

    private fun Throwable?.asException(): Exception {
        return this as? Exception ?: IOException(this?.message ?: "Catalog request failed")
    }

    private fun parseResponseToCacheFile(
        context: Context?,
        cacheUrl: String?,
        response: Response
    ): Result<List<AppModel>> {
        val body = response.body ?: return Result.failure(IOException("Empty response"))
        val tempFile = if (context?.cacheDir != null) {
            File.createTempFile("catalog_", ".json", context.cacheDir)
        } else {
            File.createTempFile("catalog_", ".json")
        }

        return runCatching {
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val parsed = tempFile.inputStream().reader(Charsets.UTF_8).use { reader ->
                parse(reader)
            }

            if (parsed.isSuccess && context != null && cacheUrl != null) {
                CatalogCacheStore.write(context, cacheUrl, tempFile)
            }

            parsed
        }.getOrElse { error ->
            Result.failure(error.asException())
        }.also {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}

private object CatalogCacheStore {
    private fun fileFor(context: Context, url: String): File {
        val key = sha256Hex(url).take(24)
        return File(context.cacheDir, "catalog_$key.json")
    }

    fun write(context: Context, url: String, source: File) {
        runCatching {
            val target = fileFor(context, url)
            if (target.exists()) {
                target.delete()
            }
            source.copyTo(target, overwrite = true)
        }
    }

    fun file(context: Context, url: String): File? {
        return fileFor(context, url).takeIf { it.exists() }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                append(byte.toUByte().toString(16).padStart(2, '0'))
            }
        }
    }
}
