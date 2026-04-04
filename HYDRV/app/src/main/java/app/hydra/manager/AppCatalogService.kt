package app.hydra.manager

import android.os.Handler
import android.os.Looper
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.JsonParser
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

                    val body = response.body?.string().orEmpty()
                    val parsed = parse(body)
                    if (parsed.isSuccess) {
                        CatalogCacheStore.write(appContext, firstRequest.url, body)
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
                    val body = response.body?.string().orEmpty()
                    val parsed = parse(body).getOrThrow()
                    CatalogCacheStore.write(appContext, candidate, body)
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

    fun readCachedApps(context: Context): Result<FetchResult>? {
        val appContext = context.applicationContext
        return BackendPreferences.getCatalogUrlCandidates(appContext)
            .firstNotNullOfOrNull { readCachedResult(appContext, it) }
    }

    private fun parse(raw: String): Result<List<AppModel>> {
        return try {
            val apps = sanitizeParsedApps(parseRawApps(raw))
            val validation = CatalogValidation.validate(apps)
            if (validation.isSuccess) {
                Result.success(validation.getOrThrow())
            } else {
                Result.success(
                    apps.filter { it.versions.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRawApps(raw: String): List<AppModel> {
        val directList = runCatching {
            gson.fromJson<List<AppModel>>(raw, appListType)
        }.getOrNull().orEmpty()
        if (directList.isNotEmpty()) return directList

        val root = JsonParser().parse(raw)
        if (!root.isJsonObject) return emptyList()
        val obj = root.asJsonObject
        val candidates = listOf("apps", "catalog", "catalogue", "items", "data")
        for (key in candidates) {
            val array = obj.getAsJsonArray(key) ?: continue
            val parsed = runCatching {
                gson.fromJson<List<AppModel>>(array, appListType)
            }.getOrNull().orEmpty()
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
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
                    val safeVersionName = text(version.version_name)
                    val safeUrl = text(version.url)
                    val safeChangelog = text(version.changelog)

                    if (safeVersionName.isEmpty() || safeUrl.isEmpty()) return@mapNotNull null

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
        val cached = CatalogCacheStore.read(context, url) ?: return null
        val parsed = parse(cached)
        return if (parsed.isSuccess) {
            Result.success(FetchResult(parsed.getOrThrow(), fromCache = true))
        } else {
            null
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

                    val body = response.body?.string().orEmpty()
                    val parsed = parse(body)
                    if (parsed.isSuccess) {
                        CatalogCacheStore.write(context, next.url, body)
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

    private fun Throwable?.asException(): Exception {
        return this as? Exception ?: IOException(this?.message ?: "Catalog request failed")
    }
}

private object CatalogCacheStore {
    private fun fileFor(context: Context, url: String): File {
        val key = sha256Hex(url).take(24)
        return File(context.cacheDir, "catalog_$key.json")
    }

    fun write(context: Context, url: String, raw: String) {
        runCatching {
            fileFor(context, url).writeText(raw)
        }
    }

    fun read(context: Context, url: String): String? {
        return runCatching { fileFor(context, url).takeIf { it.exists() }?.readText() }.getOrNull()
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
