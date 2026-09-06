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

    @Test
    fun testEveryClientExtractionAndStreamUrl() {
        assumeTrue("Set YOUTUBE_LIVE_TEST=1 to run the network smoke test", System.getenv("YOUTUBE_LIVE_TEST") == "1")

        val clientsToTest = listOf(
            "VISIONOS" to com.youtubedownloader.innertubex.client.VisionOsClient.VISIONOS,
            "VISIONOS_0_1" to com.youtubedownloader.innertubex.client.VisionOsClient.VISIONOS_0_1,
            "ANDROID_VR_1_65_10" to com.youtubedownloader.innertubex.client.AndroidVrClient.ANDROID_VR_1_65_10,
            "ANDROID_VR_1_61_48" to com.youtubedownloader.innertubex.client.AndroidVrClient.ANDROID_VR_1_61_48,
            "ANDROID_VR_1_43_32" to com.youtubedownloader.innertubex.client.AndroidVrClient.ANDROID_VR_1_43_32,
            "ANDROID" to com.youtubedownloader.innertubex.client.AndroidClient.ANDROID,
            "IOS" to com.youtubedownloader.innertubex.client.IosClient.IOS,
            "IPADOS" to com.youtubedownloader.innertubex.client.IosClient.IPADOS,
            "TVHTML5_SIMPLY" to com.youtubedownloader.innertubex.client.TvSimplyClient.TVHTML5_SIMPLY,
            "TVHTML5_EMBEDDED" to com.youtubedownloader.innertubex.client.TvSimplyClient.TVHTML5_EMBEDDED,
            "MWEB" to com.youtubedownloader.innertubex.client.MWebClient.MWEB,
            "WEB" to com.youtubedownloader.innertubex.client.WebClient.WEB,
        )

        val results = mutableMapOf<String, String>()

        for ((id, client) in clientsToTest) {
            val start = System.currentTimeMillis()
            try {
                val playback = YoutubeExtractor.extractWithClient(
                    clientName = id,
                    videoId = "dQw4w9WgXcQ",
                )
                assertTrue("Stream URL for $id must not be blank", playback.audioStream.streamUrl.isNotBlank())
                assertStreamIsReachable(playback.audioStream)
                val duration = System.currentTimeMillis() - start
                results[id] = "SUCCESS (${duration}ms, isHls=${playback.audioStream.isHls}, itag=${playback.audioStream.format.itag})"
                println("[CLIENT TEST] $id: SUCCESS (${duration}ms)")
            } catch (e: Throwable) {
                val duration = System.currentTimeMillis() - start
                results[id] = "FAILED (${duration}ms): ${e.message}"
                println("[CLIENT TEST] $id: FAILED - ${e.message}")
            }
        }

        // Anonymous top-tier clients must succeed with reachable streams
        assertTrue("VISIONOS must succeed", results["VISIONOS"]?.startsWith("SUCCESS") == true)
        assertTrue("VISIONOS_0_1 must succeed", results["VISIONOS_0_1"]?.startsWith("SUCCESS") == true)
        assertTrue("ANDROID_VR_1_65_10 must succeed", results["ANDROID_VR_1_65_10"]?.startsWith("SUCCESS") == true)
        assertTrue("ANDROID_VR_1_61_48 must succeed", results["ANDROID_VR_1_61_48"]?.startsWith("SUCCESS") == true)
        assertTrue("ANDROID_VR_1_43_32 must succeed", results["ANDROID_VR_1_43_32"]?.startsWith("SUCCESS") == true)
        assertTrue("ANDROID must succeed", results["ANDROID"]?.startsWith("SUCCESS") == true)
        assertTrue("IOS must succeed", results["IOS"]?.startsWith("SUCCESS") == true)
        assertTrue("IPADOS must succeed", results["IPADOS"]?.startsWith("SUCCESS") == true)
    }

    @Test
    fun testSabrBootstrapAndExtraction() {
        assumeTrue("Set YOUTUBE_LIVE_TEST=1 to run the network smoke test", System.getenv("YOUTUBE_LIVE_TEST") == "1")

        // Parse a live player response and verify SABR bootstrap is extracted
        val response = com.youtubedownloader.innertubex.extractor.PlayerRequest.execute(
            httpClient = okhttp3.OkHttpClient(),
            client = com.youtubedownloader.innertubex.client.AndroidVrClient.ANDROID_VR_1_65_10,
            videoId = "dQw4w9WgXcQ",
            playlistId = null,
            cookie = null,
            visitorData = null,
            poToken = null,
        )

        val sabr = response.sabrBootstrap
        assertTrue("SABR bootstrap must be present in player response", sabr != null)
        assertTrue("serverAbrStreamingUrl must start with https://", sabr!!.serverAbrStreamingUrl.startsWith("https://"))
        assertTrue("candidateItags must not be empty", sabr.candidateItags.isNotEmpty())
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
        if (stream.isHls) {
            val connection = URL(stream.streamUrl).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                stream.requestHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000
                val responseCode = connection.responseCode
                assertEquals("HLS stream URL must return HTTP 200, got $responseCode", HttpURLConnection.HTTP_OK, responseCode)
                val body = connection.inputStream.bufferedReader().readText()
                assertTrue("HLS playlist must start with #EXTM3U", body.startsWith("#EXTM3U"))
            } finally {
                connection.disconnect()
            }
        } else {
            assertRangeIsReachable(stream.streamUrl, "bytes=0-1023", stream.requestHeaders)
            assertRangeIsReachable(stream.streamUrl, "bytes=1024-2047", stream.requestHeaders)
        }
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
