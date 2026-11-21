package com.v2ray.ang.ui.flow

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.FlowPreferences
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.subscriptionDivider
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.SubscriptionTitle
import com.v2ray.ang.util.objectField
import com.v2ray.ang.util.serverDescription
import com.v2ray.ang.util.stringField
import java.util.Locale

internal data class FlowServer(
    val id: String,
    val preferenceKey: String,
    val profile: ProfileItem,
    val favorite: Boolean,
    val country: String?,
    val delayMillis: Long,
    val protocol: String = connectionDetails(profile.configType.name, profile.network, profile.security),
    val sectionLabel: String? = profile.subscriptionDivider(),
    val serverDescription: String? = null,
    val name: String = displayServerName(profile),
)

private fun displayServerName(profile: ProfileItem): String =
    FlowPreferences.countryFromLabel(profile.remarks)?.let { code ->
        profile.remarks.replace(FlowPreferences.flag(code), "").trim()
    }.orEmpty().ifBlank { profile.remarks.ifBlank { profile.server.orEmpty() } }

private val nonProxyOutbounds = setOf("freedom", "blackhole", "dns", "loopback", "direct", "block")

internal data class ServerDisplayDetails(val protocol: String, val description: String? = null)

internal fun connectionDetails(protocol: String, network: String?, security: String?): String {
    val transport = when (val value = network?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "none" -> null
        "grpc" -> "gRPC"
        "raw" -> "TCP"
        "splithttp" -> "XHTTP"
        "websocket" -> "WS"
        else -> value.uppercase(Locale.ROOT)
    }
    val protection = when (val value = security?.trim()?.lowercase(Locale.ROOT)) {
        null, "", "none" -> null
        "reality" -> "Reality"
        else -> value.uppercase(Locale.ROOT)
    }
    return listOfNotNull(protocol.trim().uppercase(Locale.ROOT), transport, protection).joinToString(" ")
}

internal fun customServerDetails(raw: String?): ServerDisplayDetails {
    val configuration = raw?.let { runCatching { JsonUtil.parseString(it) }.getOrNull() }
        ?.takeIf { it.isJsonObject }?.asJsonObject
    val description = configuration?.serverDescription()
    val outbounds = configuration?.get("outbounds")?.takeIf { it.isJsonArray }?.asJsonArray
    val configured = outbounds?.mapNotNull { it.takeIf { it.isJsonObject }?.asJsonObject }
        .orEmpty().filter { it.stringField("protocol") != null }
    val routes = configured.filterNot { it.stringField("protocol")?.lowercase(Locale.ROOT) in nonProxyOutbounds }
        .ifEmpty { configured }
    val primary = routes.firstOrNull { it.stringField("tag").equals("proxy", ignoreCase = true) } ?: routes.firstOrNull()
    val stream = primary?.objectField("streamSettings")
    val protocol = primary?.stringField("protocol")?.let {
        connectionDetails(it, stream?.stringField("network"), stream?.stringField("security"))
    }.orEmpty()
    return ServerDisplayDetails(protocol, description)
}

internal data class FlowGroup(val subscription: SubscriptionCache?, val servers: List<FlowServer>) {
    val id: String get() = subscription?.guid.orEmpty()
    val title: String? = subscription?.subscription?.let { SubscriptionTitle.display(it.remarks, it.url) }
    val serversCount: Int = servers.count { it.sectionLabel == null }
}

internal data class FlowProfiles(
    val groups: List<FlowGroup> = emptyList(),
    val selected: String? = null,
    val favoriteOrder: List<String> = emptyList(),
    val automaticDividers: Boolean = true,
) {
    val allServers: List<FlowServer> = groups.flatMap { it.servers }
    val servers: List<FlowServer> = allServers.filter { it.sectionLabel == null }
    val active: FlowServer? = servers.firstOrNull { it.id == selected }
    val favorites: List<FlowServer> = run {
        val order = favoriteOrder.withIndex().associate { (position, key) -> key to position }
        servers.filter { it.favorite && it.id != selected }
            .sortedBy { order[it.preferenceKey] ?: Int.MAX_VALUE }
    }
    val homePingIds: List<String> get() = listOfNotNull(active?.id) + favorites.map(FlowServer::id)

    fun updateFavorite(key: String, order: List<String>): FlowProfiles {
        val marked = key in order
        return copy(groups = groups.map { group ->
            if (group.servers.none { it.preferenceKey == key }) group else group.copy(servers = group.servers.map { server ->
                if (server.preferenceKey == key) server.copy(favorite = marked) else server
            })
        }, favoriteOrder = order)
    }

    fun refreshDelays(ids: Set<String>): FlowProfiles {
        val updated = groups.map { group ->
            val servers = group.servers.map serverMap@{ server ->
                if (server.id !in ids) return@serverMap server
                val delay = MmkvManager.decodeServerAffiliationInfo(server.id)?.testDelayMillis ?: 0L
                if (delay == server.delayMillis) server else server.copy(delayMillis = delay)
            }
            if (servers.indices.all { servers[it] === group.servers[it] }) group else group.copy(servers = servers)
        }
        return if (groups.indices.all { updated[it] === groups[it] }) this else copy(groups = updated)
    }

    companion object {
        fun read(): FlowProfiles {
            val storedOrder = FlowPreferences.storedFavoriteOrder()
            val favorites = storedOrder?.toHashSet() ?: FlowPreferences.legacyFavorites()
            val automaticDividers = FlowPreferences.automaticDividers()
            val overrides = FlowPreferences.dividerOverrides()
            val knownGroups = MmkvManager.decodeSubscriptions().filter { it.subscription.url.isNotBlank() }
            val servers = MmkvManager.decodeAllServerList().mapNotNull { id ->
                val profile = MmkvManager.decodeServerConfig(id) ?: return@mapNotNull null
                val key = FlowPreferences.profileKey(id, profile)
                val details = if (profile.configType == EConfigType.CUSTOM) {
                    customServerDetails(MmkvManager.decodeServerRaw(id))
                } else ServerDisplayDetails(connectionDetails(profile.configType.name, profile.network, profile.security))
                FlowServer(
                    id, key, profile, key in favorites,
                    FlowPreferences.countryOverride(key) ?: FlowPreferences.countryFromLabel(profile.remarks),
                    MmkvManager.decodeServerAffiliationInfo(id)?.testDelayMillis ?: 0L,
                    protocol = details.protocol,
                    sectionLabel = FlowPreferences.dividerLabel(profile, automaticDividers, overrides[key]),
                    serverDescription = details.description ?: profile.serverDescription,
                )
            }.groupBy { it.profile.subscriptionId }
            val subscriptionIds = knownGroups.map { it.guid }.toSet()
            val local = servers.filterKeys { it !in subscriptionIds }.values.flatten()
            val grouped = knownGroups.map { FlowGroup(it, servers[it.guid].orEmpty()) }
            val groups = grouped + listOfNotNull(FlowGroup(null, local).takeIf { local.isNotEmpty() })
            val favoriteOrder = storedOrder ?: (groups.flatMap { group ->
                group.servers.filter(FlowServer::favorite).map(FlowServer::preferenceKey)
            } + favorites.sorted()).distinct()
            return FlowProfiles(groups,
                MmkvManager.getSelectServer(), favoriteOrder, automaticDividers)
        }
    }
}
