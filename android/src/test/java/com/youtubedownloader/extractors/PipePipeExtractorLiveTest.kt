package com.youtubedownloader.extractors

import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import org.junit.Assert.assertEquals
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

    private fun assertSuccessfulRangeResponse(
        connection: HttpURLConnection,
        responseCode: Int,
        rangeHeader: String,
    ) {
        assertEquals(
            "Range $rangeHeader must return HTTP 206, got $responseCode",
            HttpURLConnection.HTTP_PARTIAL,
            responseCode,
        )
        val requestedRange = RANGE_PATTERN.matchEntire(rangeHeader)
            ?: throw AssertionError("Invalid test range: $rangeHeader")
        val contentRange = connection.getHeaderField("Content-Range")?.trim()
        val returnedRange = contentRange?.let(CONTENT_RANGE_PATTERN::matchEntire)
        assertTrue(
            "Range $rangeHeader must return a matching Content-Range, got $contentRange",
            returnedRange != null,
        )
        if (returnedRange != null) {
            assertEquals(requestedRange.groupValues[1], returnedRange.groupValues[1])
            assertEquals(requestedRange.groupValues[2], returnedRange.groupValues[2])
        }
    }

    private fun assertStreamIsReachable(streamUrl: String) {
        assertRangeIsReachable(streamUrl, "bytes=0-1023")
        assertRangeIsReachable(streamUrl, "bytes=1000000-1500000")
    }

    private fun assertRangeIsReachable(streamUrl: String, rangeHeader: String) {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", rangeHeader)
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            val responseCode = connection.responseCode
            assertSuccessfulRangeResponse(connection, responseCode, rangeHeader)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val RANGE_PATTERN = Regex("bytes=(\\d+)-(\\d+)")
        val CONTENT_RANGE_PATTERN = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
    }
}
