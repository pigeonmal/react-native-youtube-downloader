package com.youtubedownloader.extractors

import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectionTest {
    @Test
    fun lowAndMeteredAutoPreferTheLowestAudioBitrate() {
        val streams = listOf(
            RankedAudioStream("high", 192_000),
            RankedAudioStream("low", 64_000),
        )

        assertEquals("low", selectAudioStream(streams, AudioQuality.LOW, false))
        assertEquals("low", selectAudioStream(streams, AudioQuality.AUTO, true))
    }

    @Test
    fun highAndUnmeteredAutoPreferTheHighestAudioBitrate() {
        val streams = listOf(
            RankedAudioStream("high", 192_000),
            RankedAudioStream("low", 64_000),
        )

        assertEquals("high", selectAudioStream(streams, AudioQuality.HIGH, true))
        assertEquals("high", selectAudioStream(streams, AudioQuality.AUTO, false))
    }

    @Test
    fun videoSelectionStaysAtOrBelowRequestedResolutionWhenAvailable() {
        val streams = listOf(
            RankedVideoStream("360p", 360, 1_000_000),
            RankedVideoStream("720p", 720, 2_000_000),
            RankedVideoStream("1080p", 1080, 3_000_000),
        )

        assertEquals(
            "720p",
            selectVideoStream(streams, VideoQuality.QUALITY_720P, false),
        )
    }

    @Test
    fun emptySelectionReturnsNull() {
        assertNull(selectAudioStream(emptyList(), AudioQuality.AUTO, false))
        assertNull(selectVideoStream(emptyList(), VideoQuality.AUTO, false))
    }
}
