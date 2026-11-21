package com.v2ray.ang.dto

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDeviceTest {
    private val origin = "https://subscription.example/path".toHttpUrl()

    @Test
    fun hwidIsStableAndScopedToOrigin() {
        val id = SubscriptionDevice.identifier("device", origin)
        assertTrue(id.matches(Regex("[0-9a-f]{64}")))
        assertEquals(id, SubscriptionDevice.identifier("device", "https://subscription.example/other?id=2".toHttpUrl()))
        assertNotEquals(id, SubscriptionDevice.identifier("device", "https://other.example/".toHttpUrl()))
        assertNotEquals(id, SubscriptionDevice.identifier("device", "https://subscription.example:8443/".toHttpUrl()))
        assertNotEquals(id, SubscriptionDevice.identifier("second-device", origin))
    }

    @Test
    fun onlySameHttpsOriginReceivesDeviceHeaders() {
        val device = SubscriptionDevice(origin, "some-stable-hwid", "16", "Pixel")
        val headers = device.headersFor("https://subscription.example/redirect".toHttpUrl())
        assertEquals("some-stable-hwid", headers["x-hwid"])
        assertEquals("Android", headers["x-device-os"])
        assertEquals("16", headers["x-ver-os"])
        assertEquals("Pixel", headers["x-device-model"])
        assertTrue(device.headersFor("https://other.example/redirect".toHttpUrl()).isEmpty())
        assertTrue(device.headersFor("https://subscription.example:8443/redirect".toHttpUrl()).isEmpty())
        assertTrue(device.headersFor("http://subscription.example:443/redirect".toHttpUrl()).isEmpty())
    }
}
