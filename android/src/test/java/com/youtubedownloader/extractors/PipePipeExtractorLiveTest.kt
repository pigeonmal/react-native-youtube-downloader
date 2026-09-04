package com.youtubedownloader.extractors

import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class PipePipeExtractorLiveTest {
    @Test
    fun extractsPlayableAudioAndVideoUrlsFromYouTube() {
        assumeTrue("Set YOUTUBE_LIVE_TEST=1 to run the network smoke test", System.getenv("YOUTUBE_LIVE_TEST") == "1")

        val playback = extractPublicVideo()

        assertTrue("Audio stream URL must not be blank", playback.audioStream.streamUrl.isNotBlank())
        assertStreamIsReachable(playback.audioStream.streamUrl)
        playback.videoStream?.let {
            assertTrue("Video stream URL must not be blank", it.streamUrl.isNotBlank())
            assertStreamIsReachable(it.streamUrl)
        }
    }

    @Test
    fun extractsPublicVideoWhenAuthenticationCookieIsPresent() {
        assumeTrue("Set YOUTUBE_LIVE_TEST=1 to run the network smoke test", System.getenv("YOUTUBE_LIVE_TEST") == "1")

        // Regression test for YouTube's logged-in extraction block. The public
        // attempt must not send this cookie to the anonymous player clients.
        val playback = PipePipeExtractor.extract(
            videoId = "dQw4w9WgXcQ",
            playlistId = null,
            audioQuality = AudioQuality.AUTO,
            videoQuality = null,
            isMetered = false,
            cookie = "SAPISID=test-cookie; SID=test-cookie",
            forceVisitorData = "test-visitor-data",
        )

        assertTrue("Audio stream URL must not be blank", playback.audioStream.streamUrl.isNotBlank())
        assertStreamIsReachable(playback.audioStream.streamUrl)
    }

    private fun extractPublicVideo() = PipePipeExtractor.extract(
        videoId = "dQw4w9WgXcQ",
        playlistId = null,
        audioQuality = AudioQuality.AUTO,
        videoQuality = VideoQuality.QUALITY_360P,
        isMetered = false,
        cookie = null,
        forceVisitorData = null,
    )

    private fun assertSuccessfulRangeResponse(responseCode: Int, rangeHeader: String) {
        assertTrue(
            "Range $rangeHeader must return a successful HTTP response, got $responseCode",
            responseCode in 200..299,
        )
    }

    private fun assertStreamIsReachable(streamUrl: String) {
        assertRangeIsReachable(streamUrl, "bytes=0-1023")
        assertRangeIsReachable(streamUrl, "bytes=1000000-1500000")
    }

    private fun assertRangeIsReachable(streamUrl: String, rangeHeader: String) {
        val testRanges = listOf(
            "bytes=0-1023",
            "bytes=1024-2047",
            "bytes=0-65535",
            "bytes=65536-131071",
            "bytes=0-524287",
            "bytes=524288-1048575",
            "bytes=0-1048575",
            "bytes=1000000-1500000"
        )
        for (r in testRanges) {
            val connection = URL(streamUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Range", r)
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                val responseCode = connection.responseCode
                assertSuccessfulRangeResponse(responseCode, r)
            } finally {
                connection.disconnect()
            }
        }
    }
}
