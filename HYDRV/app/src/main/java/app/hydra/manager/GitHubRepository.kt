package app.hydra.manager

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale

object GitHubRepository {

    data class Release(
        @SerializedName("tag_name")
        val tagName: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("html_url")
        val htmlUrl: String,
        @SerializedName("body")
        val body: String? = "",
        @SerializedName("published_at")
        val publishedAt: String? = null
    ) {
        fun displayLabel(): String {
            val releaseName = name.trim()
            val releaseTag = tagName.trim()
            if (releaseName.isBlank()) {
                return releaseTag.ifBlank { "Latest release" }
            }
            if (releaseTag.isBlank()) {
                return releaseName
            }

            val normalizedName = releaseName.lowercase(Locale.US)
            val normalizedTag = releaseTag.lowercase(Locale.US)
            val tagWithoutPrefix = normalizedTag.removePrefix("v")

            if (
                normalizedName == normalizedTag ||
                normalizedName.contains(" $normalizedTag") ||
                (tagWithoutPrefix.isNotBlank() && normalizedName.contains(" $tagWithoutPrefix"))
            ) {
                return releaseName
            }

            return "$releaseName $releaseTag"
        }
    }

    data class Contributor(
        @SerializedName("login")
        val login: String,
        @SerializedName("avatar_url")
        val avatarUrl: String,
        @SerializedName("html_url")
        val htmlUrl: String,
        @SerializedName("contributions")
        val contributions: Int = 0
    ) {
        fun displayName(): String {
            return login.trim().ifBlank { "Contributor" }
        }
    }

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val contributorListType = object : TypeToken<List<Contributor>>() {}.type

    fun fetchLatestRelease(onResult: (Result<Release>) -> Unit): Call {
        return fetchJson(
            request = Request.Builder()
                .url(RuntimeConfig.githubLatestReleaseUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "HYDRV")
                .build(),
            parser = { body -> gson.fromJson(body, Release::class.java) },
            onResult = onResult
        )
    }

    fun fetchLatestReleaseSync(): Result<Release> {
        return fetchJsonSync(
            request = Request.Builder()
                .url(RuntimeConfig.githubLatestReleaseUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "HYDRV")
                .build(),
            parser = { body -> gson.fromJson(body, Release::class.java) }
        )
    }

    fun fetchContributors(onResult: (Result<List<Contributor>>) -> Unit): Call {
        return fetchJson(
            request = Request.Builder()
                .url(RuntimeConfig.githubContributorsUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "HYDRV")
                .build(),
            parser = { body ->
                gson.fromJson<List<Contributor>>(body, contributorListType)
                    .orEmpty()
                    .filter { it.login.isNotBlank() }
            },
            onResult = onResult
        )
    }

    fun fetchContributorsSync(): Result<List<Contributor>> {
        return fetchJsonSync(
            request = Request.Builder()
                .url(RuntimeConfig.githubContributorsUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "HYDRV")
                .build(),
            parser = { body ->
                gson.fromJson<List<Contributor>>(body, contributorListType)
                    .orEmpty()
                    .filter { it.login.isNotBlank() }
            }
        )
    }

    fun fetchChangelogSync(): Result<String> {
        return fetchTextSync(
            request = Request.Builder()
                .url(RuntimeConfig.githubChangelogUrl)
                .header("User-Agent", "HYDRV")
                .build()
        )
    }

    private fun <T> fetchJson(
        request: Request,
        parser: (String) -> T,
        onResult: (Result<T>) -> Unit
    ): Call {
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) return
                postResult(onResult, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (call.isCanceled()) {
                    response.close()
                    return
                }

                response.use {
                    if (!response.isSuccessful) {
                        postResult(onResult, Result.failure(IOException("HTTP ${response.code}")))
                        return
                    }

                    val body = response.body?.string().orEmpty()
                    val parsed = runCatching { parser(body) }
                    postResult(onResult, parsed)
                }
            }
        })
        return call
    }

    private fun <T> fetchJsonSync(
        request: Request,
        parser: (String) -> T
    ): Result<T> {
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parser(body)
            }
        }
    }

    private fun fetchTextSync(request: Request): Result<String> {
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        }
    }

    private fun <T> postResult(callback: (Result<T>) -> Unit, result: Result<T>) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(result)
        } else {
            mainHandler.post { callback(result) }
        }
    }
}
