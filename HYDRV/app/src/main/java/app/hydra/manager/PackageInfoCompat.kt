package app.hydra.manager

import android.content.pm.PackageInfo
import android.os.Build

fun PackageInfo.versionCodeCompat(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        versionCode
    }
}
