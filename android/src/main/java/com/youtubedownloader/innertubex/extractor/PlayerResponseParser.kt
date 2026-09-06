package com.youtubedownloader.innertubex.extractor

import com.youtubedownloader.innertubex.cipher.YoutubeCipher
import com.youtubedownloader.innertubex.client.YouTubeClient
import com.youtubedownloader.innertubex.models.AudioConfig
import com.youtubedownloader.innertubex.models.PlaybackTracking
import com.youtubedownloader.innertubex.models.VideoDetails
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal data class ParsedPlayerResponse(
    val root: JSONObject,
    val visitorData: String?,
    val expiresInSeconds: Int,
    val candidates: List<StreamCandidate>,
    val audioConfig: AudioConfig?,
    val videoDetails: VideoDetails?,
    val playbackTracking: PlaybackTracking?,
)

internal object PlayerResponseParser {
    private const val DEFAULT_STREAM_TTL_SECONDS = 5 * 60

    fun parse(client: YouTubeClient, body: String): ParsedPlayerResponse {
        val root = JSONObject(body)
        val playability = root.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()

        if (status != "OK" && !client.skipPlayerResponseValidation) {
            val reason = playability?.optString("reason").takeUnless { it.isNullOrBlank() }
                ?: playability?.optJSONArray("messages")?.optString(0)
            throw IllegalStateException(
                "YouTube ${client.clientName} is not playable" +
                    (reason?.let { ": $it" } ?: ""),
            )
        }

        val streaming = root.optJSONObject("streamingData")
            ?: throw IllegalStateException("YouTube ${client.clientName} returned no streaming data")

        val expiresInSeconds = streaming.optInt("expiresInSeconds", DEFAULT_STREAM_TTL_SECONDS)
            .takeIf { it > 0 } ?: DEFAULT_STREAM_TTL_SECONDS

        val candidates = buildList {
            addAll(parseFormats(streaming.optJSONArray("formats"), root))
            addAll(parseFormats(streaming.optJSONArray("adaptiveFormats"), root))
        }.filter { it.url.isNotBlank() }

        if (candidates.isEmpty()) {
            throw IllegalStateException("YouTube ${client.clientName} returned no direct media URLs")
        }

        val visitorData = root.optJSONObject("responseContext")
            ?.optString("visitorData")
            ?.takeIf { it.isNotBlank() }

        val audioConfig = root.optJSONObject("playerConfig")
            ?.optJSONObject("audioConfig")
            ?.let { ac ->
                AudioConfig(
                    loudnessDb = ac.optDoubleOrNull("loudnessDb"),
                    perceptualLoudnessDb = ac.optDoubleOrNull("perceptualLoudnessDb"),
                )
            }

        val videoDetails = root.optJSONObject("videoDetails")?.let { vd ->
            VideoDetails(
                videoId = vd.optString("videoId"),
                title = vd.optString("title").takeIf { it.isNotBlank() },
                author = vd.optString("author").takeIf { it.isNotBlank() },
                channelId = vd.optString("channelId"),
                lengthSeconds = vd.optString("lengthSeconds"),
                musicVideoType = vd.optString("musicVideoType").takeIf { it.isNotBlank() },
                viewCount = vd.optString("viewCount").takeIf { it.isNotBlank() },
            )
        }

        val tracking = root.optJSONObject("playbackTracking")?.let { pt ->
            PlaybackTracking(
                videostatsPlaybackUrl = pt.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.takeIf { it.isNotBlank() },
                videostatsWatchtimeUrl = pt.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.takeIf { it.isNotBlank() },
                atrUrl = pt.optJSONObject("atrUrl")?.optString("baseUrl")?.takeIf { it.isNotBlank() },
            )
        }

        return ParsedPlayerResponse(
            root = root,
            visitorData = visitorData,
            expiresInSeconds = expiresInSeconds,
            candidates = candidates,
            audioConfig = audioConfig,
            videoDetails = videoDetails,
            playbackTracking = tracking,
        )
    }

    private fun parseFormats(formats: JSONArray?, root: JSONObject): List<StreamCandidate> {
        if (formats == null) return emptyList()
        return (0 until formats.length()).mapNotNull { i ->
            val format = formats.optJSONObject(i) ?: return@mapNotNull null
            parseStreamCandidate(format, root)
        }
    }

    private fun parseStreamCandidate(format: JSONObject, root: JSONObject): StreamCandidate? {
        val directUrl = format.optString("url").takeIf { it.isNotBlank() }
        val cipherPayload = format.optString("signatureCipher").takeIf { it.isNotBlank() }
            ?: format.optString("cipher").takeIf { it.isNotBlank() }

        val url = directUrl ?: cipherPayload?.let { YoutubeCipher.resolveUrl(it, root) } ?: return null
        val mimeTypeRaw = format.optString("mimeType")
        val mimeType = mimeTypeRaw.substringBefore(';').trim().lowercase()
        val codecs = mimeTypeRaw.substringAfter("codecs=\"", "")
            .substringBefore('"', "")
            .takeIf { it.isNotBlank() }

        val isAudio = mimeType.startsWith("audio/")
        val isVideo = mimeType.startsWith("video/")
        val itag = format.optInt("itag")
        val bitrate = format.optInt("bitrate")
        val averageBitrate = format.optInt("averageBitrate", bitrate).takeIf { it > 0 } ?: bitrate

        return StreamCandidate(
            url = url,
            itag = itag,
            mimeType = mimeType,
            codecs = codecs,
            bitrate = bitrate,
            averageBitrate = averageBitrate,
            width = format.optIntOrNull("width"),
            height = format.optIntOrNull("height"),
            contentLength = format.optLongOrNull("contentLength"),
            quality = format.optString("quality"),
            qualityLabel = format.optString("qualityLabel").takeIf { it.isNotBlank() },
            fps = format.optIntOrNull("fps"),
            approxDurationMs = format.optString("approxDurationMs").takeIf { it.isNotBlank() },
            audioSampleRate = format.optIntOrNull("audioSampleRate"),
            audioChannels = format.optIntOrNull("audioChannels"),
            isAudio = isAudio,
            isVideo = isVideo,
        )
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) {
            val v = opt(name)
            when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
        } else null

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null
}
