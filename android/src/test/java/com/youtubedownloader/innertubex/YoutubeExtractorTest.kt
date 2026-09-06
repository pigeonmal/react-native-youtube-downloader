package com.youtubedownloader.innertubex

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YoutubeExtractorTest {
    @Test
    fun recognizesBotChallengeInWrappedException() {
        val error = IllegalStateException(
            "playback failed",
            IllegalArgumentException("Sign in to confirm you're not a bot"),
        )

        assertTrue(YoutubeExtractor.isYoutubeBotChallenge(error))
    }

    @Test
    fun doesNotTreatAgeRestrictionAsBotChallenge() {
        val error = IllegalStateException("This age-restricted video cannot be watched anonymously")

        assertFalse(YoutubeExtractor.isYoutubeBotChallenge(error))
    }

    @Test
    fun recognizesWebViewAuthenticationCookies() {
        assertTrue(YoutubeExtractor.hasSupportedAuthCookie("SAPISID=value"))
        assertTrue(YoutubeExtractor.hasSupportedAuthCookie("__Secure-3PAPISID=value"))
        assertFalse(YoutubeExtractor.hasSupportedAuthCookie("SID=value"))
    }

    @Test
    fun authenticatedOnlyRejectsMissingCookieBeforeAnyRequest() {
        try {
            YoutubeExtractor.extract(
                videoId = "dQw4w9WgXcQ",
                playlistId = null,
                audioQuality = com.youtubedownloader.models.AudioQuality.AUTO,
                videoQuality = null,
                isMetered = false,
                cookie = null,
                forceVisitorData = null,
                authenticatedOnly = true,
            )
            fail("Expected authenticated extraction to require a cookie")
        } catch (error: IllegalArgumentException) {
            assertEquals("Authenticated YouTube extraction requires a cookie", error.message)
        }
    }

    @Test
    fun clientCatalogPrioritizesAnonymousFirstWithCookieFallback() {
        // Without cookie: anonymous clients only
        val anonOnly = com.youtubedownloader.innertubex.client.ClientCatalog.getClients(
            cookie = null,
            authenticatedOnly = false,
            excludedClientNames = emptySet(),
        )
        assertEquals("VISIONOS", anonOnly.first().clientName)
        assertTrue(anonOnly.any { it.clientName == "VISIONOS" })
        assertTrue(anonOnly.any { it.clientName == "ANDROID_VR" })
        assertTrue(anonOnly.any { it.clientName == "TVHTML5_SIMPLY" })
        assertTrue(anonOnly.any { it.clientName == "MWEB" })
        assertTrue(anonOnly.any { it.clientName == "WEB" })

        // With cookie: anonymous clients first, authenticated clients as fallback
        val withCookie = com.youtubedownloader.innertubex.client.ClientCatalog.getClients(
            cookie = "SAPISID=123",
            authenticatedOnly = false,
            excludedClientNames = emptySet(),
        )
        assertEquals("VISIONOS", withCookie.first().clientName)
        assertTrue(withCookie.size > anonOnly.size)
        assertTrue(withCookie.any { it.clientName == "WEB_REMIX" })

        // Authenticated only: authenticated clients only
        val authOnly = com.youtubedownloader.innertubex.client.ClientCatalog.getClients(
            cookie = "SAPISID=123",
            authenticatedOnly = true,
            excludedClientNames = emptySet(),
        )
        assertEquals("WEB_REMIX", authOnly.first().clientName)
    }

    @Test
    fun allClientsBuildValidContextAndEndpoints() {
        val allClients = listOf(
            com.youtubedownloader.innertubex.client.VisionOsClient.VISIONOS,
            com.youtubedownloader.innertubex.client.VisionOsClient.VISIONOS_0_1,
            com.youtubedownloader.innertubex.client.AndroidVrClient.ANDROID_VR_1_65_10,
            com.youtubedownloader.innertubex.client.AndroidVrClient.ANDROID_VR_1_61_48,
            com.youtubedownloader.innertubex.client.TvSimplyClient.TVHTML5_SIMPLY,
            com.youtubedownloader.innertubex.client.TvDowngradedClient.TVHTML5_DOWNGRADED,
            com.youtubedownloader.innertubex.client.WebClient.WEB,
            com.youtubedownloader.innertubex.client.WebClient.WEB_EMBEDDED_PLAYER,
            com.youtubedownloader.innertubex.client.MWebClient.MWEB,
            com.youtubedownloader.innertubex.client.WebRemixClient.WEB_REMIX,
            com.youtubedownloader.innertubex.client.TvHtml5Client.TVHTML5,
            com.youtubedownloader.innertubex.client.IosClient.IOS,
        )

        for (client in allClients) {
            val context = client.buildContext("test-visitor-data")
            assertTrue("Client ${client.friendlyName} context must contain client object", context.has("client"))
            val clientObj = context.getJSONObject("client")
            assertEquals(client.clientName, clientObj.getString("clientName"))
            assertEquals(client.clientVersion, clientObj.getString("clientVersion"))
            assertEquals("test-visitor-data", clientObj.getString("visitorData"))

            val endpoint = client.playerEndpoint()
            assertTrue("Client ${client.friendlyName} endpoint must be valid", endpoint.startsWith("https://"))
        }
    }

    @Test
    fun peekCacheReturnsNullOnCacheMiss() {
        val cached = YoutubeExtractor.peekCache(
            videoId = "dQw4w9WgXcQ",
            playlistId = null,
            audioQuality = com.youtubedownloader.models.AudioQuality.AUTO,
            videoQuality = null,
            isMetered = false,
            cookie = null,
            forceVisitorData = null,
            authenticatedOnly = false,
        )
        assertEquals(null, cached)
    }
}
