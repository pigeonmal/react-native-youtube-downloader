package com.youtubedownloader.models.response

import com.youtubedownloader.models.ResponseContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments

/**
 * PlayerResponse with YouTubeClient.WEB_REMIX client
 */
@Serializable
data class PlayerResponse(
    val responseContext: ResponseContext,
    val playabilityStatus: PlayabilityStatus,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    @SerialName("playbackTracking")
    val playbackTracking: PlaybackTracking?,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String,
        val reason: String?,
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig,
    ) {
        @Serializable
        data class AudioConfig(
            val loudnessDb: Double?,
            val perceptualLoudnessDb: Double?,
        )
    }

    @Serializable
    data class StreamingData(
        val formats: List<Format>?,
        val adaptiveFormats: List<Format>,
        val expiresInSeconds: Int,
    ) {
        @Serializable
        data class Format(
            val itag: Int,
            val url: String?,
            val mimeType: String,
            val bitrate: Int,
            val width: Int?,
            val height: Int?,
            val contentLength: Long?,
            val quality: String,
            val fps: Int?,
            val qualityLabel: String?,
            val averageBitrate: Int?,
            val audioQuality: String?,
            val approxDurationMs: String?,
            val audioSampleRate: Int?,
            val audioChannels: Int?,
            val loudnessDb: Double?,
            val lastModified: Long?,
            val signatureCipher: String?,
            val audioTrack: AudioTrack?
        ) {
            val isAudio: Boolean
                get() = width == null
            val isOriginal: Boolean
                get() = audioTrack?.isAutoDubbed == null
            val isVideo: Boolean
                get() = width != null && height != null

            @Serializable
            data class AudioTrack(
                val displayName: String?,
                val id: String?,
                val isAutoDubbed: Boolean?,
            )
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String,
        val title: String?,
        val author: String?,
        val channelId: String,
        val lengthSeconds: String,
        val musicVideoType: String?,
        val viewCount: String?,
    )

    @Serializable
    data class PlaybackTracking(
        @SerialName("videostatsPlaybackUrl")
        val videostatsPlaybackUrl: VideostatsPlaybackUrl?,
        @SerialName("videostatsWatchtimeUrl")
        val videostatsWatchtimeUrl: VideostatsWatchtimeUrl?,
        @SerialName("atrUrl")
        val atrUrl: AtrUrl?,
    ) {
        @Serializable
        data class VideostatsPlaybackUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )
        @Serializable
        data class VideostatsWatchtimeUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )
        @Serializable
        data class AtrUrl(
            @SerialName("baseUrl")
            val baseUrl: String?,
        )
    }
}

fun PlayerResponse.StreamingData.Format.toWritableMap(): WritableMap {
    val map = Arguments.createMap()

    map.putInt("itag", itag)
    map.putString("mimeType", mimeType)
    map.putInt("bitrate", bitrate)
    width?.let { map.putInt("width", it) }
    height?.let { map.putInt("height", it) }
    contentLength?.let { map.putDouble("contentLength", it.toDouble()) }
    map.putString("quality", quality)
    fps?.let { map.putInt("fps", it) }
    qualityLabel?.let { map.putString("qualityLabel", it) }
    approxDurationMs?.let { map.putString("approxDurationMs", it) }
    audioSampleRate?.let { map.putInt("audioSampleRate", it) }
    audioChannels?.let { map.putInt("audioChannels", it) }
    loudnessDb?.let { map.putDouble("loudnessDb", it) }

    return map
}

fun PlayerResponse.PlayerConfig.AudioConfig.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    loudnessDb?.let { map.putDouble("loudnessDb", it) }
    perceptualLoudnessDb?.let { map.putDouble("perceptualLoudnessDb", it) }
    return map
}

fun PlayerResponse.VideoDetails.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    map.putString("videoId", videoId)
    title?.let { map.putString("title", it) }
    author?.let { map.putString("author", it) }
    map.putString("channelId", channelId)
    map.putString("lengthSeconds", lengthSeconds)
    musicVideoType?.let { map.putString("musicVideoType", it) }
    viewCount?.let { map.putString("viewCount", it) }
    return map
}

fun PlayerResponse.PlaybackTracking.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    map.putMap("videostatsPlaybackUrl", videostatsPlaybackUrl?.toWritableMap())
    map.putMap("videostatsWatchtimeUrl", videostatsWatchtimeUrl?.toWritableMap())
    map.putMap("atrUrl", atrUrl?.toWritableMap())
    return map
}

fun PlayerResponse.PlaybackTracking.VideostatsPlaybackUrl.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    baseUrl?.let { map.putString("baseUrl", it) }
    return map
}

fun PlayerResponse.PlaybackTracking.VideostatsWatchtimeUrl.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    baseUrl?.let { map.putString("baseUrl", it) }
    return map
}

fun PlayerResponse.PlaybackTracking.AtrUrl.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    baseUrl?.let { map.putString("baseUrl", it) }
    return map
}