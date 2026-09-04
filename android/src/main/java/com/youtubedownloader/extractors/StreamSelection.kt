package com.youtubedownloader.extractors

import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality

internal data class RankedAudioStream<T>(
    val value: T,
    val bitrate: Int,
)

internal data class RankedVideoStream<T>(
    val value: T,
    val height: Int,
    val bitrate: Int,
)

internal fun <T> selectAudioStream(
    streams: List<RankedAudioStream<T>>,
    quality: AudioQuality,
    isMetered: Boolean,
): T? {
    val validStreams = streams.filter { it.bitrate > 0 }.ifEmpty { streams }
    if (validStreams.isEmpty()) return null

    val useLowestBitrate = quality == AudioQuality.LOW

    return if (useLowestBitrate) {
        validStreams.minWithOrNull(compareBy<RankedAudioStream<T>> { it.bitrate })?.value
    } else {
        validStreams.maxWithOrNull(compareBy<RankedAudioStream<T>> { it.bitrate })?.value
    }
}

internal fun <T> selectVideoStream(
    streams: List<RankedVideoStream<T>>,
    quality: VideoQuality,
    isMetered: Boolean,
): T? {
    if (streams.isEmpty()) return null

    val targetHeight = if (quality == VideoQuality.AUTO) {
        if (isMetered) 720 else 1080
    } else {
        quality.heightPixels
    }

    val streamsWithinTarget = streams.filter { it.height <= targetHeight }
    return (streamsWithinTarget.ifEmpty { streams })
        .maxWithOrNull(compareBy<RankedVideoStream<T>> { it.height }.thenBy { it.bitrate })
        ?.value
}
