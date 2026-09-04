package com.youtubedownloader.extractors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PipePipeExtractorTest {
    @Test
    fun recognizesBotChallengeInWrappedException() {
        val error = IllegalStateException(
            "playback failed",
            IllegalArgumentException("Sign in to confirm you're not a bot"),
        )

        assertTrue(PipePipeExtractor.isYoutubeBotChallenge(error))
    }

    @Test
    fun doesNotTreatAgeRestrictionAsBotChallenge() {
        val error = IllegalStateException("This age-restricted video cannot be watched anonymously")

        assertFalse(PipePipeExtractor.isYoutubeBotChallenge(error))
    }

    @Test
    fun recognizesWebViewAuthenticationCookies() {
        assertTrue(PipePipeExtractor.hasSupportedAuthCookie("SAPISID=value"))
        assertTrue(PipePipeExtractor.hasSupportedAuthCookie("__Secure-3PAPISID=value"))
        assertFalse(PipePipeExtractor.hasSupportedAuthCookie("SID=value"))
    }

    @Test
    fun authenticatedOnlyRejectsMissingCookieBeforeAnyRequest() {
        try {
            PipePipeExtractor.extract(
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
}
