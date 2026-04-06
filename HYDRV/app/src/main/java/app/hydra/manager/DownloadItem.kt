package app.hydra.manager

data class DownloadItem(
    val name: String,
    val url: String,
    var progress: Int = 0,
    var status: String = "Downloading",
    var createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long = 0L,

    var downloadedBytes: Long = 0,
    var totalBytes: Long = 0,
    var filePath: String = "",

    var speed: Float = 0f,
    var eta: Long = 0,

    var backendPackageName: String = "",
    var packageName: String = "",
    val versionName: String = "",
    var versionCode: Int = 0,
    var errorMessage: String = "",
    var installed: Boolean = false,
    var isSelfUpdate: Boolean = false,

    var isAnimatedDone: Boolean = false,
    var lastStatus: String = "",
    var doneHandled: Boolean = false,
    var requestToken: Long = 0L
)

fun DownloadItem.requestKey(): String {
    return buildString {
        append(name.trim())
        append('|')
        append(versionName.trim())
        append('|')
        append(versionCode.takeIf { it > 0 } ?: 0)
        append('|')
        append(url.trim())
    }
}
