package com.youtubedownloader.extractors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
