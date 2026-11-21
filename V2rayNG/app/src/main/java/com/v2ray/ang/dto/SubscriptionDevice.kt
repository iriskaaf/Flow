package com.v2ray.ang.dto

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.handler.FlowPreferences
import com.v2ray.ang.handler.MmkvManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.util.UUID

data class SubscriptionDevice internal constructor(
    private val origin: HttpUrl,
    private val hwid: String,
    private val version: String,
    private val model: String,
) {
    internal fun headersFor(url: HttpUrl): Map<String, String> {
        if (!origin.isHttps || !url.isHttps || origin.host != url.host || origin.port != url.port) return emptyMap()
        return mapOf(
            "x-hwid" to hwid,
            "x-device-os" to "Android",
            "x-ver-os" to version,
            "x-device-model" to model,
            "x-app-version" to BuildConfig.VERSION_NAME,
        )
    }

    companion object {
        private const val INSTALL_SEED = "flow_hwid_install_seed"

        fun forRequest(context: Context, url: String): SubscriptionDevice? {
            if (!MmkvManager.decodeSettingsBool(FlowPreferences.SUBSCRIPTION_HWID, true)) return null
            val origin = url.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
            val deviceId = if (Build.VERSION.SDK_INT >= 26) {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } else null
            val seed = deviceId?.takeIf { it.isNotBlank() } ?: installationSeed()
            return SubscriptionDevice(
                origin = origin,
                hwid = identifier(seed, origin),
                version = Build.VERSION.RELEASE.asHeaderValue(),
                model = Build.MODEL.asHeaderValue(),
            )
        }

        internal fun identifier(seed: String, origin: HttpUrl): String {
            val input = "Flow/subscription/v1\u0000$seed\u0000${origin.host}\u0000${origin.port}"
            return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        @Synchronized
        private fun installationSeed(): String = MmkvManager.decodeSettingsString(INSTALL_SEED)
            ?: UUID.randomUUID().toString().also { MmkvManager.encodeSettings(INSTALL_SEED, it) }

        private fun String.asHeaderValue(): String = filter { it in ' '..'~' }.trim().take(128).ifBlank { "Unknown" }
    }
}
