package com.youtubedownloader.extractors

import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality

internal data class RankedAudioStream<T>(
    val value: T,
    val bitrate: Int,
    val mimeType: String? = null,
    val audioChannels: Int? = null,
    val sampleRate: Int? = null,
)

internal data class RankedVideoStream<T>(
    val value: T,
    val height: Int,
    val bitrate: Int,
)

internal fun <T> selectAudioStream(
    streams: List<RankedAudioStream<T>>,
    quality: AudioQuality,
): T? {
    val validStreams = streams.filter { it.bitrate > 0 }.ifEmpty { streams }
    if (validStreams.isEmpty()) return null

    // Match InnerTubeX: AUTO prefers WebM/Opus and scores channel count,
    // bitrate and sample rate. LOW is the explicit low-bandwidth profile.
    return when (quality) {
        AudioQuality.LOW -> {
            validStreams
                .filter { it.mimeType?.contains("audio/mp4", ignoreCase = true) == true }
                .minByOrNull { it.bitrate }
                ?.value
                ?: validStreams.minByOrNull { it.bitrate }?.value
        }

        AudioQuality.AUTO -> {
            validStreams
                .filter { it.mimeType?.contains("audio/webm", ignoreCase = true) == true }
                .maxWithOrNull(compareBy<RankedAudioStream<T>> { audioFormatScore(it) })
                ?.value
                ?: validStreams.maxWithOrNull(compareBy { audioFormatScore(it) })?.value
        }

        AudioQuality.HIGH -> {
            validStreams.maxWithOrNull(compareBy { audioFormatScore(it) })?.value
        }
    }
}

private fun <T> audioFormatScore(stream: RankedAudioStream<T>): Long {
    val codecRank = when {
        stream.mimeType?.contains("audio/webm", ignoreCase = true) == true -> 100L
        stream.mimeType?.contains("audio/mp4", ignoreCase = true) == true -> 50L
        else -> 0L
    }
    val channelBonus = when (stream.audioChannels) {
        2 -> 50_000L
        1 -> 0L
        else -> 25_000L
    }
    val sampleRate = (stream.sampleRate ?: 0).coerceIn(0, 48_000) / 10
    return codecRank * 1_000_000L + channelBonus + stream.bitrate + sampleRate
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
