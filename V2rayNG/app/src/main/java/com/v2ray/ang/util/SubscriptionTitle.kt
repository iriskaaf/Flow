package com.v2ray.ang.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64

internal object SubscriptionTitle {
    private val control = Regex("[\\p{Cc}\\p{Cf}]")
    fun provider(header: String?): String? {
        val value = header?.trim()?.takeIf { it.length in 1..4096 } ?: return null
        val decoded = if (value.startsWith("base64:", ignoreCase = true)) {
            value.substringAfter(':').decodeBase64()?.utf8() ?: return null
        } else value
        return clean(decoded)
    }

    fun clean(value: String?): String? = value?.replace(control, " ")
        ?.trim()?.take(100)?.takeIf { it.isNotBlank() }

    fun isPlaceholder(name: String): Boolean = name.isBlank() || name.equals("import sub", ignoreCase = true)

    fun display(name: String, url: String): String =
        name.takeUnless(::isPlaceholder) ?: url.toHttpUrlOrNull()?.host.orEmpty()
}
