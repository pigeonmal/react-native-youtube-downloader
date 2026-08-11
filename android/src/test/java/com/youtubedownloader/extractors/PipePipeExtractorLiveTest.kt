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

        val playback = PipePipeExtractor.extract(
            videoId = "dQw4w9WgXcQ",
            playlistId = null,
            audioQuality = AudioQuality.AUTO,
            videoQuality = VideoQuality.QUALITY_360P,
            isMetered = false,
            cookie = null,
            forceVisitorData = null,
        )

        assertTrue(playback.audioStream.streamUrl.startsWith("https://"))
        assertTrue(playback.videoStream?.streamUrl?.startsWith("https://") == true)
        assertStreamIsReachable(playback.audioStream.streamUrl)
        assertStreamIsReachable(requireNotNull(playback.videoStream).streamUrl)
    }

    private fun assertStreamIsReachable(streamUrl: String) {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=0-1023")
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            val responseCode = connection.responseCode
            assertTrue("Expected a playable stream response, got HTTP $responseCode", responseCode in 200..299)
            connection.inputStream.use { it.read() }
        } finally {
            connection.disconnect()
        }
    }
}
