package com.v2ray.ang.ui.flow

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.matchSubscriptionProfiles
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionProfilesTest {
    private fun profile(name: String, host: String) =
        ProfileItem(configType = EConfigType.VLESS, subscriptionId = "fixture", remarks = name, server = host, serverPort = "443")

    @Test
    fun `matches a refreshed server by provider name when its endpoint changes`() {
        val previous = mapOf("old" to profile("🇳🇱 Streaming", "old.example"))
        val current = mapOf("new" to profile("🇳🇱 Streaming", "new.example"))
        assertEquals(mapOf("old" to "new"), matchSubscriptionProfiles(previous, current))
    }

    @Test
    fun `keeps exact profiles separate and does not guess ambiguous matches`() {
        val previous = mapOf(
            "certain" to profile("West", "west.example"),
            "old-one" to profile("Auto", "old-one.example"),
            "old-two" to profile("Auto", "old-two.example"),
        )
        val current = mapOf(
            "new-two" to profile("Auto", "new-two.example"),
            "new-west" to profile("West", "west.example"),
            "new-one" to profile("Auto", "new-one.example"),
        )
        assertEquals(mapOf("certain" to "new-west"), matchSubscriptionProfiles(previous, current))
    }

    @Test
    fun `separators use a full row and never enter the home ping queue`() {
        fun server(id: String, name: String, favorite: Boolean) =
            FlowServer(id, id, profile(name, "$id.example"), favorite, null, 0L)

        val section = server("section", "━━ Gaming 👇 ━━", true)
        val entries = listOf(server("active", "Active", true), section, server("favorite", "Favorite", true),
            server("other", "Other", false))
        assertEquals("Gaming", section.sectionLabel)
        assertEquals(listOf(listOf("active"), listOf("section"), listOf("favorite", "other")),
            entries.sectionRows().map { it.map(FlowServer::id) })
        val snapshot = FlowProfiles(listOf(FlowGroup(null, entries)), selected = "active")
        assertEquals(listOf("favorite"), snapshot.favorites.map(FlowServer::id))
        assertEquals(listOf("active", "favorite"), snapshot.homePingIds)
    }
}
