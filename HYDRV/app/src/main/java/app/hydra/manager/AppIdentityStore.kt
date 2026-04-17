package app.hydra.manager

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object AppIdentityStore {

    private const val PREFS = "app_identity_store"
    private const val KEY_APP_PACKAGES = "app_packages"
    private const val KEY_PACKAGE_PACKAGES = "package_packages"
    private const val KEY_APP_SIGNERS = "app_signers"
    private const val KEY_PACKAGE_SIGNERS = "package_signers"

    data class TrustedInstall(
        val packageName: String,
        val versionCode: Int,
        val versionName: String?
    )

    fun recordInstall(
        context: Context,
        appName: String,
        backendPackage: String,
        packageInfo: PackageInfo
    ) {
        val actualPackage = packageInfo.packageName?.trim().orEmpty()
        if (actualPackage.isBlank()) return
        val signatures = signaturesFor(packageInfo)
        if (signatures.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_APP_PACKAGES, mergeValues(prefs.getString(KEY_APP_PACKAGES, "{}"), appName, setOf(actualPackage)))
            putString(KEY_PACKAGE_PACKAGES, mergeValues(prefs.getString(KEY_PACKAGE_PACKAGES, "{}"), backendPackage, setOf(actualPackage)))
            putString(KEY_APP_SIGNERS, mergeValues(prefs.getString(KEY_APP_SIGNERS, "{}"), appName, signatures))
            putString(KEY_PACKAGE_SIGNERS, mergeValues(prefs.getString(KEY_PACKAGE_SIGNERS, "{}"), backendPackage, signatures))
        }
    }

    fun findTrustedInstalledPackage(context: Context, backendPackage: String, appName: String): TrustedInstall? {
        val trustedPackages = resolveTrustedPackages(context, backendPackage, appName)
        val trustedSignatures = resolveTrustedSignatures(context, backendPackage, appName)
        if (trustedPackages.isEmpty() || trustedSignatures.isEmpty()) return null

        val packageManager = context.packageManager
        return trustedPackages.asSequence()
            .mapNotNull { packageName ->
                installedPackageInfo(packageManager, packageName)
                    ?.takeIf { signaturesFor(it).intersect(trustedSignatures).isNotEmpty() }
            }
            .maxByOrNull { it.versionCodeCompat() }
            ?.let { info ->
                TrustedInstall(
                    packageName = info.packageName,
                    versionCode = info.versionCodeCompat(),
                    versionName = info.versionName?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
    }

    fun isTrustedInstalled(context: Context, backendPackage: String, appName: String): Boolean {
        return findTrustedInstalledPackage(context, backendPackage, appName) != null
    }

    private fun resolveTrustedPackages(context: Context, backendPackage: String, appName: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return buildSet {
            readValues(prefs.getString(KEY_APP_PACKAGES, "{}"), appName).forEach(::add)
            readValues(prefs.getString(KEY_PACKAGE_PACKAGES, "{}"), backendPackage).forEach(::add)
            InstallAliasStore.resolveForAppName(context, appName)?.let(::add)
            InstallAliasStore.resolveForPackage(context, backendPackage)?.let(::add)
            backendPackage.trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun resolveTrustedSignatures(context: Context, backendPackage: String, appName: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return buildSet {
            readValues(prefs.getString(KEY_APP_SIGNERS, "{}"), appName).forEach(::add)
            readValues(prefs.getString(KEY_PACKAGE_SIGNERS, "{}"), backendPackage).forEach(::add)
        }
    }

    private fun mergeValues(rawJson: String?, key: String, values: Set<String>): String {
        if (key.isBlank() || values.isEmpty()) return rawJson.orEmpty().ifBlank { "{}" }
        val json = runCatching { JSONObject(rawJson.orEmpty().ifBlank { "{}" }) }.getOrDefault(JSONObject())
        val merged = linkedSetOf<String>()
        readValuesFromJson(json, key).forEach(merged::add)
        values.map(String::trim).filter(String::isNotBlank).forEach(merged::add)
        val array = JSONArray()
        merged.forEach(array::put)
        json.put(key.trim(), array)
        return json.toString()
    }

    private fun readValues(rawJson: String?, key: String): Set<String> {
        if (key.isBlank()) return emptySet()
        val json = runCatching { JSONObject(rawJson.orEmpty().ifBlank { "{}" }) }.getOrNull() ?: return emptySet()
        return readValuesFromJson(json, key)
    }

    private fun readValuesFromJson(json: JSONObject, key: String): Set<String> {
        val trimmedKey = key.trim()
        if (trimmedKey.isBlank()) return emptySet()
        val entry = json.opt(trimmedKey) ?: return emptySet()
        return when (entry) {
            is JSONArray -> buildSet {
                for (index in 0 until entry.length()) {
                    entry.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }

            else -> setOf(entry.toString().trim()).filter(String::isNotBlank).toSet()
        }
    }

    private fun installedPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo? {
        return runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, signingFlags())
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
