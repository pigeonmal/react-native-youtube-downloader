package com.youtubedownloader.innertubex

import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class YoutubeExtractorLiveTest {
    private companion object {
        val RANGE_PATTERN = Regex("bytes=(\\d+)-(\\d+)")
        val CONTENT_RANGE_PATTERN = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")
    }

    @Test
    fun extractsPlayableAudioAndVideoUrlsFromYouTube() {
        assumeTrue("Set YOUTUBE_LIVE_TEST=1 to run the network smoke test", System.getenv("YOUTUBE_LIVE_TEST") == "1")

        val playback = extractPublicVideo()

        assertTrue("Audio stream URL must not be blank", playback.audioStream.streamUrl.isNotBlank())
        assertStreamIsReachable(playback.audioStream)
        playback.videoStream?.let {
            assertTrue("Video stream URL must not be blank", it.streamUrl.isNotBlank())
            assertStreamIsReachable(it)
        }
    }

    @Test
    fun extractsPublicVideoWhenAuthenticationCookieIsPresent() {
        assumeTrue("Set YOUTUBE_LIVE_TEST=1 to run the network smoke test", System.getenv("YOUTUBE_LIVE_TEST") == "1")

        // Regression test for YouTube's logged-in extraction block. The public
        // attempt must not send this cookie to the anonymous player clients.
        val playback = YoutubeExtractor.extract(
            videoId = "dQw4w9WgXcQ",
            playlistId = null,
            audioQuality = AudioQuality.AUTO,
            videoQuality = null,
            isMetered = false,
            cookie = "SAPISID=test-cookie; SID=test-cookie",
            forceVisitorData = "test-visitor-data",
        )

        assertTrue("Audio stream URL must not be blank", playback.audioStream.streamUrl.isNotBlank())
        assertStreamIsReachable(playback.audioStream)
    }

    @Test
    fun extractsMazicaAudioOnlyVideo() {
        assumeTrue("Set YOUTUBE_LIVE_TEST=1 to run the network smoke test", System.getenv("YOUTUBE_LIVE_TEST") == "1")

        val playback = YoutubeExtractor.extract(
            videoId = "kJQP7kiw5Fk",
            playlistId = null,
            audioQuality = AudioQuality.AUTO,
            videoQuality = null,
            isMetered = false,
            cookie = null,
            forceVisitorData = null,
        )

        assertTrue("Audio stream URL must not be blank", playback.audioStream.streamUrl.isNotBlank())
        assertStreamIsReachable(playback.audioStream)
    }

    private fun extractPublicVideo() = YoutubeExtractor.extract(
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

    private fun assertStreamIsReachable(stream: StreamPlayback) {
        assertRangeIsReachable(stream.streamUrl, "bytes=0-1023", stream.requestHeaders)
        // Keep the second probe small enough for short audio formats while
        // still proving that a non-zero seek starts at the requested offset.
        assertRangeIsReachable(stream.streamUrl, "bytes=1024-2047", stream.requestHeaders)
    }

    private fun assertRangeIsReachable(
        streamUrl: String,
        rangeHeader: String,
        headers: Map<String, String>,
    ) {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", rangeHeader)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            val responseCode = connection.responseCode
            assertSuccessfulRangeResponse(connection, responseCode, rangeHeader)
        } finally {
            connection.disconnect()
        }
    }

}
