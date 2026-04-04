package app.hydra.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object DownloadNetworkPolicy {

    fun canDownloadNow(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val mode = DownloadNetworkPreferences.getMode(context)
        val onWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val onCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        if (!online) return false

        return when (mode) {
            DownloadNetworkPreferences.WIFI_ONLY -> onWifi
            else -> onWifi || onCellular || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }

    fun blockedMessage(context: Context): String {
        return when (DownloadNetworkPreferences.getMode(context)) {
            DownloadNetworkPreferences.WIFI_ONLY ->
                context.getString(R.string.downloads_wifi_only_blocked)
            else ->
                context.getString(R.string.no_internet_connection)
        }
    }
}
