package com.v2ray.ang.handler

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.util.JsonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class FlowUpdate(val version: String, val url: String, val size: Long, val sha256: String)

internal object FlowUpdates {
    const val PACKAGE = "app.flowvpn.client"
    private const val MAX_APK_SIZE = 200L * 1024 * 1024
    private const val RELEASE_DOWNLOAD = "https://github.com/iriskaaf/Flow/releases/download/"
    private val versionPattern = Regex("v?([0-9]+)\\.([0-9]+)\\.([0-9]+)")
    private val digestPattern = Regex("sha256:([a-fA-F0-9]{64})")
    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun check(): FlowUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${AppConfig.APP_API_URL}/latest")
            .header("Accept", "application/vnd.github+json").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            check(response.isSuccessful) { "Release API returned ${response.code}" }
            val release = requireNotNull(JsonUtil.fromJson(response.peekBody(524_288).string(), GitHubRelease::class.java))
            available(release, BuildConfig.VERSION_NAME)
        }
    }

    internal fun available(release: GitHubRelease, current: String): FlowUpdate? {
        if (release.draft || release.prerelease) return null
        val latest = versionParts(release.tagName)
        val installed = versionParts(current)
        if (compareValuesBy(latest, installed, { it.first }, { it.second }, { it.third }) <= 0) return null
        val asset = requireNotNull(release.assets.singleOrNull { it.name == "Flow.apk" }) { "Release is missing Flow.apk" }
        val url = "$RELEASE_DOWNLOAD${release.tagName}/Flow.apk"
        require(asset.browserDownloadUrl == url && asset.size in 1..MAX_APK_SIZE)
        val digest = requireNotNull(asset.digest?.let(digestPattern::matchEntire)) { "Release is missing the APK SHA-256" }
        return FlowUpdate(release.tagName.removePrefix("v"), url, asset.size, digest.groupValues[1].lowercase())
    }

    private fun versionParts(version: String): Triple<Int, Int, Int> {
        val match = requireNotNull(versionPattern.matchEntire(version)) { "Invalid release version" }
        return Triple(match.groupValues[1].toInt(), match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }

    suspend fun download(context: Context, release: FlowUpdate, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "flow-updates").apply {
            check(isDirectory || mkdirs()) { "Could not create the update directory" }
        }
        val apk = File.createTempFile("Flow-", ".apk", directory)
        var verified = false
        try {
            val request = Request.Builder().url(release.url).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "APK download returned ${response.code}" }
                val body = requireNotNull(response.body)
                val sha256 = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                var lastPercent = -1
                body.byteStream().use { source ->
                    apk.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            bytes += count
                            check(bytes <= release.size) { "APK is larger than the release asset" }
                            sha256.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            val percent = (bytes * 100 / release.size).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main.immediate) { onProgress(percent) }
                            }
                        }
                    }
                }
                val digest = sha256.digest().joinToString("") { "%02x".format(it) }
                check(bytes == release.size && digest == release.sha256) { "APK SHA-256 or size does not match the release" }
            }
            verifyPackage(context, apk, release.version)
            verified = true
            apk
        } finally {
            if (!verified) apk.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(context: Context, apk: File, expectedVersion: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, flags)
        val downloaded = requireNotNull(manager.getPackageArchiveInfo(apk.absolutePath, flags)) { "Invalid APK" }
        check(downloaded.packageName == context.packageName &&
            downloaded.versionName == expectedVersion &&
            PackageInfoCompat.getLongVersionCode(downloaded) > PackageInfoCompat.getLongVersionCode(installed)
        ) { "APK package or version does not match the update" }
        val currentSigners = signers(installed)
        check(currentSigners.isNotEmpty() && signers(downloaded) == currentSigners) { "APK signer does not match Flow" }
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<Signature> =
        if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty().toSet()
        else info.signatures.orEmpty().toSet()
}
