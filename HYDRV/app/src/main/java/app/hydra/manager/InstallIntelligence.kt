package app.hydra.manager

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object InstallIntelligence {

    data class Insight(
        val installedHint: String? = null,
        val downloadHint: String? = null,
        val warningHint: String? = null
    )

    data class Snapshot(
        val installedVersionName: String?,
        val installedVersionCode: Int?,
        val downloadedByVersionKey: Map<String, DownloadedArchive>,
        val packageMismatchVersionKeys: Set<String>,
        val signatureMismatchVersionKeys: Set<String>
    )

    data class DownloadedArchive(
        val versionCode: Int,
        val versionName: String,
        val packageName: String,
        val signatures: Set<String>
    )

    fun snapshot(context: Context, app: AppModel): Snapshot {
        val packageManager = context.packageManager
        val installedInfo = installedPackageInfo(packageManager, app.packageName)
        val installedVersionName = installedInfo?.versionName?.trim()?.takeIf { it.isNotEmpty() }
        val installedVersionCode = installedInfo?.versionCodeCompat()?.takeIf { it > 0 }
        val installedSignatures = installedInfo?.let(::signaturesFor).orEmpty()

        val downloadedByVersionKey = linkedMapOf<String, DownloadedArchive>()
        val packageMismatchVersionKeys = linkedSetOf<String>()
        val signatureMismatchVersionKeys = linkedSetOf<String>()

        DownloadRepository.downloads
            .asReversed()
            .filter {
                val looksCompleted =
                    it.status == "Done" ||
                        it.progress >= 100 ||
                        it.completedAt > 0L
                looksCompleted && it.versionName.isNotBlank() && it.filePath.isNotBlank()
            }
            .forEach { item ->
                val file = File(item.filePath)
                if (!file.exists() || file.length() <= 0L) return@forEach

                val archiveInfo = archivePackageInfo(packageManager, file) ?: return@forEach
                val archiveVersionName = archiveInfo.versionName?.trim().orEmpty()
                val archiveVersionCode = item.versionCode.takeIf { it > 0 }
                    ?: archiveInfo.versionCodeCompat().takeIf { it > 0 }
                    ?: return@forEach
                if (archiveVersionName.isBlank()) return@forEach
                val archiveKey = versionKey(archiveVersionName, archiveVersionCode)
                if (downloadedByVersionKey.containsKey(archiveKey)) return@forEach

                val archivePackageName = archiveInfo.packageName.orEmpty()
                val archiveSignatures = signaturesFor(archiveInfo)
                downloadedByVersionKey[archiveKey] = DownloadedArchive(
                    versionCode = archiveVersionCode,
                    versionName = archiveVersionName,
                    packageName = archivePackageName,
                    signatures = archiveSignatures
                )

                if (archivePackageName.isNotBlank() && archivePackageName != app.packageName) {
                    packageMismatchVersionKeys.add(archiveKey)
                } else if (
                    installedSignatures.isNotEmpty() &&
                    archiveSignatures.isNotEmpty() &&
                    installedSignatures != archiveSignatures
                ) {
                    signatureMismatchVersionKeys.add(archiveKey)
                }
            }

        return Snapshot(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            downloadedByVersionKey = downloadedByVersionKey,
            packageMismatchVersionKeys = packageMismatchVersionKeys,
            signatureMismatchVersionKeys = signatureMismatchVersionKeys
        )
    }

    fun insight(
        context: Context,
        app: AppModel,
        version: Version,
        snapshot: Snapshot,
        latestVersion: Version? = null
    ): Insight {
        val latestVersionNumber = latestVersion?.version ?: app.latestVersion()?.version
        val versionKey = versionKey(version.version_name, version.version)

        val installedHint = when {
            snapshot.installedVersionCode == null -> null
            version.version == snapshot.installedVersionCode ->
                context.getString(R.string.version_installed_hint)
            version.version > snapshot.installedVersionCode ->
                context.getString(R.string.version_new_since_installed)
            version.version == latestVersionNumber &&
                snapshot.installedVersionCode > version.version ->
                context.getString(R.string.version_installed_newer_than_catalog_hint)
            else -> null
        }

        val downloadHint = when {
            snapshot.downloadedByVersionKey.containsKey(versionKey) ->
                context.getString(R.string.version_downloaded_hint)
            else -> null
        }

        val warningHint = when {
            snapshot.packageMismatchVersionKeys.contains(versionKey) ->
                context.getString(R.string.version_package_mismatch_hint)
            snapshot.signatureMismatchVersionKeys.contains(versionKey) ->
                context.getString(R.string.version_signature_mismatch_hint)
            snapshot.installedVersionCode != null && version.version < snapshot.installedVersionCode ->
                context.getString(R.string.version_downgrade_hint)
            else -> null
        }

        return Insight(
            installedHint = installedHint,
            downloadHint = downloadHint,
            warningHint = warningHint
        )
    }

    private fun installedPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo? {
        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, signingFlags())
        }.getOrNull()
    }

    private fun archivePackageInfo(packageManager: PackageManager, file: File): PackageInfo? {
        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags())
        }.getOrNull()
    }

    private fun signingFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    }

    private fun signaturesFor(packageInfo: PackageInfo): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.orEmpty().map { digest(it.toByteArray()) }.toSet()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures.orEmpty().map { digest(it.toByteArray()) }.toSet()
        }
    }

    private fun digest(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun versionKey(versionName: String, versionCode: Int): String {
        return "${versionName.trim()}|${versionCode.takeIf { it > 0 } ?: 0}"
    }
}
