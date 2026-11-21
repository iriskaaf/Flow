package com.v2ray.ang.handler

import com.v2ray.ang.dto.GitHubRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FlowUpdatesTest {
    private val sha256 = "b".repeat(64)
    private val asset = GitHubRelease.Asset(
        "Flow.apk",
        "https://github.com/iriskaaf/Flow/releases/download/v1.10.0/Flow.apk",
        85_000_000L,
        "sha256:$sha256",
    )
    private val release = GitHubRelease("v1.10.0", "", listOf(asset))

    @Test
    fun onlyOffersNewerStableVersions() {
        assertEquals(FlowUpdate("1.10.0", asset.browserDownloadUrl, asset.size, sha256),
            FlowUpdates.available(release, "1.9.9"))
        assertNull(FlowUpdates.available(release, "1.10.0"))
        assertNull(FlowUpdates.available(release, "1.11.0"))
        assertNull(FlowUpdates.available(release.copy(draft = true), "1.9.9"))
        assertNull(FlowUpdates.available(release.copy(prerelease = true), "1.9.9"))
    }

    @Test
    fun requiresAHashedApkFromTheFlowRelease() {
        assertThrows(IllegalArgumentException::class.java) {
            FlowUpdates.available(release.copy(assets = listOf(asset.copy(digest = null))), "1.9.9")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlowUpdates.available(release.copy(assets = listOf(asset.copy(browserDownloadUrl = "https://example.org/Flow.apk"))),
                "1.9.9")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlowUpdates.available(release.copy(assets = listOf(asset.copy(size = Long.MAX_VALUE))), "1.9.9")
        }
    }
}
