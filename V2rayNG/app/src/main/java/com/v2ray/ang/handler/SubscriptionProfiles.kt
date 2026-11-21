package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import java.util.Locale

private const val dividerArrow = "👇"
private val dividerRules = Regex("""[\p{Pd}─━═_]{3,}""")
private val dividerTrim = Regex("""^[\s\p{Pd}─━═_]+|[\s\p{Pd}─━═_]+$""")
private val whitespace = Regex("""\s+""")

internal fun ProfileItem.subscriptionDivider(): String? {
    if (subscriptionId.isBlank()) return null
    val label = remarks.trim()
    if (dividerRules.matches(label)) return ""
    if (dividerArrow !in label) return null
    return label.replace(dividerArrow, "").replace("\uFE0F", "").replace(dividerTrim, "")
}

internal fun matchSubscriptionProfiles(
    previous: Map<String, ProfileItem>,
    current: Map<String, ProfileItem>,
    includeDividers: Boolean = false,
): Map<String, String> {
    val old = if (includeDividers) previous.toMutableMap() else previous.filterValues { it.subscriptionDivider() == null }.toMutableMap()
    val new = if (includeDividers) current.toMutableMap() else current.filterValues { it.subscriptionDivider() == null }.toMutableMap()
    val matches = mutableMapOf<String, String>()

    fun matchBy(identity: (String, ProfileItem) -> String?) {
        val before = old.entries.mapNotNull { (id, profile) -> identity(id, profile)?.let { it to id } }
            .groupBy({ it.first }, { it.second })
        val after = new.entries.mapNotNull { (id, profile) -> identity(id, profile)?.let { it to id } }
            .groupBy({ it.first }, { it.second })
        for ((identityKey, oldIds) in before) {
            val oldId = oldIds.singleOrNull() ?: continue
            val newId = after[identityKey]?.singleOrNull() ?: continue
            matches[oldId] = newId
            old.remove(oldId)
            new.remove(newId)
        }
    }

    matchBy(FlowPreferences::profileKey)
    matchBy { _, profile ->
        profile.remarks.trim().replace(whitespace, " ").lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }
    matchBy { _, profile ->
        val host = profile.server?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        host?.let { listOf(profile.configType.name, it, profile.serverPort.orEmpty(), profile.password.orEmpty()).joinToString("\u0000") }
    }
    return matches
}
