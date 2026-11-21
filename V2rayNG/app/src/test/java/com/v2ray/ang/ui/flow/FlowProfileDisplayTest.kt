package com.v2ray.ang.ui.flow

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.FlowPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlowProfileDisplayTest {
    @Test
    fun `custom profiles display distinct proxy protocols`() {
        val config = """{"outbounds":[{"protocol":"freedom"},{"protocol":"vless"},{"protocol":"trojan"},{"protocol":"VLESS"}]}"""

        assertEquals("VLESS · TROJAN", customServerDetails(config).protocol)
        assertEquals("FREEDOM", customServerDetails("""{"outbounds":[{"protocol":"freedom"}]}""").protocol)
        assertEquals("", customServerDetails("""{"outbounds":[null,{"protocol":42}]}""").protocol)
    }

    @Test
    fun `profile subtitles retain provider descriptions and parse transport and security`() {
        val config = """
            {
                "outbounds": [
                    {"protocol":"vless","streamSettings":{"network":"grpc","security":"reality"}},
                    {"protocol":"vless","streamSettings":{"network":"xhttp","security":"tls"}},
                    {"protocol":"freedom"}
                ],
                "meta": {"serverDescription":"  Пусть работает боже 🙏  "}
            }
        """.trimIndent()
        val details = customServerDetails(config)
        assertEquals("Пусть работает боже 🙏", details.description)
        assertEquals("VLESS gRPC Reality · VLESS XHTTP TLS", details.protocol)
        assertEquals("VMESS WS TLS", connectionDetails("vmess", "ws", "tls"))
        assertEquals("VLESS TCP", connectionDetails("vless", "raw", "none"))
        assertNull(customServerDetails("""{"meta":{"serverDescription":" "}}""").description)
        assertNull(customServerDetails("""{"meta":{"serverDescription":42}}""").description)
    }

    @Test
    fun `regional labels preserve the European and four-star flags`() {
        assertEquals("EU", FlowPreferences.countryFromLabel("🇪🇺 Auto"))
        assertEquals("FM", FlowPreferences.countryFromLabel("🇫🇲 Auto"))
    }

    @Test
    fun `favorites use saved order and exclude the active server`() {
        val profile = ProfileItem.create(EConfigType.VLESS)
        fun favorite(id: String) = FlowServer(id, id, profile, true, null, 0L)
        val first = favorite("first")
        val second = favorite("second")
        val third = favorite("third")
        val groups = listOf(FlowGroup(null, listOf(first, second, third)))
        val snapshot = FlowProfiles(groups, "second", listOf("second", "third", "first"))
        assertEquals(listOf(third, first), snapshot.favorites)
        assertEquals(listOf("second", "third", "first"), snapshot.homePingIds)

        val switched = snapshot.copy(selected = "third", favoriteOrder = listOf("third", "first", "second"))
        assertEquals(listOf(first, second), switched.favorites)
    }

    @Test
    fun `manual divider choice takes precedence over automatic detection`() {
        val profile = ProfileItem.create(EConfigType.VLESS).apply {
            subscriptionId = "sub"
            remarks = "━━ Gameplay 👇 ━━"
        }
        assertEquals("Gameplay", FlowPreferences.dividerLabel(profile, automatic = true, override = null))
        assertNull(FlowPreferences.dividerLabel(profile, automatic = false, override = null))
        assertNull(FlowPreferences.dividerLabel(profile, automatic = true, override = false))
        assertEquals("Gameplay", FlowPreferences.dividerLabel(profile, automatic = false, override = true))
        assertEquals("Regular", FlowPreferences.dividerLabel(profile.copy(remarks = "Regular"), automatic = true, override = true))
    }
}
