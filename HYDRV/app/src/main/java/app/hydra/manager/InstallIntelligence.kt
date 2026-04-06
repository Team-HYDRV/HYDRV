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
        val installedVersionOrder: Int?,
        val downloadedByVersionName: Map<String, DownloadedArchive>,
        val packageMismatchVersions: Set<String>,
        val signatureMismatchVersions: Set<String>
    )

    data class DownloadedArchive(
        val versionName: String,
        val packageName: String,
        val signatures: Set<String>
    )

    fun snapshot(context: Context, app: AppModel): Snapshot {
        val packageManager = context.packageManager
        val installedInfo = installedPackageInfo(packageManager, app.packageName)
        val installedVersionName = installedInfo?.versionName?.trim()?.takeIf { it.isNotEmpty() }
        val installedVersionOrder = app.versions.firstOrNull {
            it.version_name.equals(installedVersionName, ignoreCase = true)
        }?.version
        val installedSignatures = installedInfo?.let(::signaturesFor).orEmpty()

        val downloadedByVersionName = linkedMapOf<String, DownloadedArchive>()
        val packageMismatchVersions = linkedSetOf<String>()
        val signatureMismatchVersions = linkedSetOf<String>()

        DownloadRepository.downloads
            .asReversed()
            .filter { it.status == "Done" && it.versionName.isNotBlank() && it.filePath.isNotBlank() }
            .forEach { item ->
                val file = File(item.filePath)
                if (!file.exists() || file.length() <= 0L) return@forEach

                val archiveInfo = archivePackageInfo(packageManager, file) ?: return@forEach
                val archiveVersionName = archiveInfo.versionName?.trim().orEmpty()
                if (archiveVersionName.isBlank()) return@forEach
                if (downloadedByVersionName.containsKey(archiveVersionName)) return@forEach

                val archivePackageName = archiveInfo.packageName.orEmpty()
                val archiveSignatures = signaturesFor(archiveInfo)
                downloadedByVersionName[archiveVersionName] = DownloadedArchive(
                    versionName = archiveVersionName,
                    packageName = archivePackageName,
                    signatures = archiveSignatures
                )

                if (archivePackageName.isNotBlank() && archivePackageName != app.packageName) {
                    packageMismatchVersions.add(archiveVersionName)
                } else if (
                    installedSignatures.isNotEmpty() &&
                    archiveSignatures.isNotEmpty() &&
                    installedSignatures != archiveSignatures
                ) {
                    signatureMismatchVersions.add(archiveVersionName)
                }
            }

        return Snapshot(
            installedVersionName = installedVersionName,
            installedVersionOrder = installedVersionOrder,
            downloadedByVersionName = downloadedByVersionName,
            packageMismatchVersions = packageMismatchVersions,
            signatureMismatchVersions = signatureMismatchVersions
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

        val installedHint = when {
            snapshot.installedVersionName.isNullOrBlank() -> null
            version.version_name.equals(snapshot.installedVersionName, ignoreCase = true) ->
                context.getString(R.string.version_installed_hint)
            snapshot.installedVersionOrder != null && version.version > snapshot.installedVersionOrder ->
                context.getString(R.string.version_new_since_installed)
            snapshot.installedVersionOrder == null && version.version == latestVersionNumber ->
                context.getString(R.string.version_installed_unknown_catalog_hint)
            snapshot.installedVersionOrder != null &&
                version.version == latestVersionNumber &&
                snapshot.installedVersionOrder > version.version ->
                context.getString(R.string.version_installed_newer_than_catalog_hint)
            else -> null
        }

        val downloadHint = when {
            snapshot.downloadedByVersionName.containsKey(version.version_name) ->
                context.getString(R.string.version_downloaded_hint)
            else -> null
        }

        val warningHint = when {
            snapshot.packageMismatchVersions.contains(version.version_name) ->
                context.getString(R.string.version_package_mismatch_hint)
            snapshot.signatureMismatchVersions.contains(version.version_name) ->
                context.getString(R.string.version_signature_mismatch_hint)
            snapshot.installedVersionOrder != null && version.version < snapshot.installedVersionOrder ->
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
}
