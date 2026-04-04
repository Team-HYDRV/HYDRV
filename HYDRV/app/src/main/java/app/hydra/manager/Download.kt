package app.hydra.manager

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast

fun download(context: Context, url: String, name: String) {

    if (!url.startsWith("http")) return

    val item = DownloadItem(name = name, url = url)

    val result = DownloadRepository.startDownload(context, item)
    if (result != DownloadRepository.StartResult.STARTED) {
        Toast.makeText(
            context,
            DownloadRepository.startResultMessage(context, result)
                ?: DownloadNetworkPolicy.blockedMessage(context),
            Toast.LENGTH_SHORT
        ).show()
    }
}
