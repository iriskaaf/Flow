package com.v2ray.ang.util

import com.google.gson.JsonObject

internal fun JsonObject.objectField(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonObject.stringField(name: String): String? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString?.trim()?.takeIf(String::isNotEmpty)

internal fun JsonObject.serverDescription(): String? {
    val value = objectField("meta")?.stringField("serverDescription")
        ?: objectField("metadata")?.stringField("serverDescription")
        ?: stringField("serverDescription")
    return value?.replace(Regex("[\\r\\n\\t]+"), " ")
}
