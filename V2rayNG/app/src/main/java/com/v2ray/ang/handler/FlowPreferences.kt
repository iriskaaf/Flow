package com.v2ray.ang.handler

import android.annotation.SuppressLint
import android.content.Context
import com.v2ray.ang.dto.entities.ProfileItem
import java.security.MessageDigest
import java.util.Locale

object FlowPreferences {
    const val SYSTEM_COLORS = "flow_system_colors"
    const val CONNECT_ON_LAUNCH = "flow_connect_on_launch"
    const val LOOK_UP_EXIT_COUNTRY = "flow_look_up_exit_country"
    const val SUBSCRIPTION_HWID = "flow_subscription_hwid"
    const val WELCOME_COMPLETED = "flow_welcome_completed"
    const val DYNAMIC_ISLAND = "flow_dynamic_island"
    const val AUTOMATIC_DIVIDERS = "flow_automatic_dividers"
    const val SERVER_LIST_VIEW = "flow_server_list_view"
    private const val FAVORITES = "flow_favorite_profiles"
    private const val FAVORITE_ORDER = "flow_favorite_order"
    private const val DIVIDER_OVERRIDES = "flow_divider_overrides"
    private const val COUNTRY_PREFIX = "flow_country_"
    private const val REGIONAL_A = 0x1F1E6
    private const val HEX = "0123456789abcdef"
    private val countries = Locale.getISOCountries().toSet()
    private val additionalFlags = setOf("EU", "UN", "XK")

    fun profileKey(guid: String, profile: ProfileItem): String {
        if (profile.subscriptionId.isBlank()) return guid
        // Subscription updates replace GUIDs. Use the provider's label and endpoint to retain metadata.
        val identity = listOf(
            profile.subscriptionId, profile.configType.name,
            profile.remarks.trim(), profile.server.orEmpty(), profile.serverPort.orEmpty()
        ).joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        return buildString(hash.size * 2) {
            for (byte in hash) {
                append(HEX[(byte.toInt() and 255) ushr 4])
                append(HEX[byte.toInt() and 15])
            }
        }
    }

    private fun String.profileKeys() = lineSequence().filter(String::isNotBlank).toList()

    fun storedFavoriteOrder(): List<String>? = MmkvManager.decodeSettingsString(FAVORITE_ORDER)?.profileKeys()

    fun legacyFavorites(): Set<String> = MmkvManager.decodeSettingsStringSet(FAVORITES).orEmpty()

    private fun updateFavorites(initial: List<String> = legacyFavorites().sorted(), change: (List<String>) -> List<String>): List<String> =
        MmkvManager.updateSettingsString(FAVORITE_ORDER) { encoded ->
            change(encoded?.profileKeys() ?: initial).joinToString("\n")
        }.profileKeys()

    fun toggleFavorite(key: String, order: List<String>): List<String> = updateFavorites(order) {
        if (key in it) it - key else it + key
    }

    fun moveFavoriteToEnd(key: String, order: List<String>): List<String> = updateFavorites(order) {
        if (key in it) it - key + key else it
    }

    internal fun migrateProfilePreferences(keys: Map<String, String>) {
        if (keys.isEmpty() || keys.all { (old, new) -> old == new }) return
        updateFavorites { order -> order.map { keys[it] ?: it }.distinct() }
        updateDividers { overrides ->
            overrides.entries.associate { (key, value) -> (keys[key] ?: key) to value }
        }
    }

    fun automaticDividers(): Boolean = MmkvManager.decodeSettingsBool(AUTOMATIC_DIVIDERS, true)

    private fun String?.dividerEntries(): Map<String, Boolean> = orEmpty().lineSequence()
        .filter { '=' in it }.associate { it.substringBefore('=') to (it.substringAfter('=') == "1") }

    fun dividerOverrides(): Map<String, Boolean> = MmkvManager.decodeSettingsString(DIVIDER_OVERRIDES).dividerEntries()

    private fun updateDividers(change: (Map<String, Boolean>) -> Map<String, Boolean>) {
        MmkvManager.updateSettingsString(DIVIDER_OVERRIDES) { encoded ->
            change(encoded.dividerEntries()).entries.joinToString("\n") { (key, divider) ->
                "$key=${if (divider) 1 else 0}"
            }
        }
    }

    fun setDivider(key: String, divider: Boolean) = updateDividers { it + (key to divider) }

    internal fun dividerLabel(profile: ProfileItem, automatic: Boolean, override: Boolean?): String? = when (override) {
        true -> profile.subscriptionDivider() ?: profile.remarks.trim()
        false -> null
        null -> if (automatic) profile.subscriptionDivider() else null
    }

    internal fun allowInitialSelection(profile: ProfileItem): Boolean =
        !automaticDividers() || profile.subscriptionDivider() == null

    fun countryOverride(key: String): String? =
        normalizeCountry(MmkvManager.decodeSettingsString(COUNTRY_PREFIX + key))

    fun setCountry(key: String, country: String?) {
        MmkvManager.encodeSettings(COUNTRY_PREFIX + key, country.orEmpty())
    }

    fun normalizeCountry(value: String?): String? =
        value?.trim()?.uppercase(Locale.ROOT)?.takeIf(countries::contains)

    fun countryFromLabel(label: String): String? {
        val points = label.codePoints().toArray()
        for (index in 0 until points.lastIndex) {
            val first = points[index] - REGIONAL_A
            val second = points[index + 1] - REGIONAL_A
            if (first in 0..25 && second in 0..25) {
                val code = "${'A' + first}${'A' + second}"
                if (code in countries || code in additionalFlags) return code
            }
        }
        return null
    }

    fun flag(country: String): String = buildString {
        country.forEach { appendCodePoint(REGIONAL_A + (it - 'A')) }
    }

    @SuppressLint("DiscouragedApi")
    fun flagResource(context: Context, country: String?): Int? =
        (normalizeCountry(country) ?: country?.takeIf(additionalFlags::contains))?.let {
        context.resources.getIdentifier("flag_iso_${it.lowercase(Locale.ROOT)}", "drawable", context.packageName)
            .takeIf { id -> id != 0 }
    }
}
