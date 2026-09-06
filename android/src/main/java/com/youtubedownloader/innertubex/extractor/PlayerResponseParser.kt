package com.youtubedownloader.innertubex.extractor

import com.youtubedownloader.innertubex.cipher.YoutubeCipher
import com.youtubedownloader.innertubex.client.YouTubeClient
import com.youtubedownloader.innertubex.models.AudioConfig
import com.youtubedownloader.innertubex.models.PlaybackTracking
import com.youtubedownloader.innertubex.models.VideoDetails
import com.youtubedownloader.innertubex.sabr.SabrBootstrap
import com.youtubedownloader.innertubex.sabr.SabrBootstrapFactory
import org.json.JSONArray
import org.json.JSONObject

internal data class ParsedPlayerResponse(
    val root: JSONObject,
    val visitorData: String?,
    val expiresInSeconds: Int,
    val candidates: List<StreamCandidate>,
    val audioConfig: AudioConfig?,
    val videoDetails: VideoDetails?,
    val playbackTracking: PlaybackTracking?,
    val hlsManifestUrl: String? = null,
    val sabrBootstrap: SabrBootstrap? = null,
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

        val directCandidates = buildList {
            addAll(parseFormats(streaming.optJSONArray("formats"), root))
            addAll(parseFormats(streaming.optJSONArray("adaptiveFormats"), root))
        }.filter { it.url.isNotBlank() }

        val hlsManifestUrl = streaming.optString("hlsManifestUrl").takeIf { it.isNotBlank() }

        val candidates = if (directCandidates.isNotEmpty()) {
            directCandidates
        } else if (!hlsManifestUrl.isNullOrBlank()) {
            // HLS fallback: associate adaptive format metadata with hlsManifestUrl
            val hlsFormats = buildList {
                addAll(parseFormats(streaming.optJSONArray("formats"), root, fallbackUrl = hlsManifestUrl, isHls = true))
                addAll(parseFormats(streaming.optJSONArray("adaptiveFormats"), root, fallbackUrl = hlsManifestUrl, isHls = true))
            }
            if (hlsFormats.isNotEmpty()) {
                hlsFormats
            } else {
                listOf(
                    StreamCandidate(
                        url = hlsManifestUrl,
                        itag = 234,
                        mimeType = "application/x-mpegURL",
                        codecs = "mp4a.40.2",
                        bitrate = 128000,
                        averageBitrate = 128000,
                        width = null,
                        height = null,
                        contentLength = null,
                        quality = "medium",
                        qualityLabel = null,
                        fps = null,
                        approxDurationMs = null,
                        audioSampleRate = 44100,
                        audioChannels = 2,
                        isAudio = true,
                        isVideo = false,
                        isHls = true,
                    )
                )
            }
        } else {
            emptyList()
        }

        if (candidates.isEmpty()) {
            throw IllegalStateException("YouTube ${client.clientName} returned no direct or HLS media URLs")
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

        val sabrBootstrap = SabrBootstrapFactory.fromPlayerResponse(root)

        return ParsedPlayerResponse(
            root = root,
            visitorData = visitorData,
            expiresInSeconds = expiresInSeconds,
            candidates = candidates,
            audioConfig = audioConfig,
            videoDetails = videoDetails,
            playbackTracking = tracking,
            hlsManifestUrl = hlsManifestUrl,
            sabrBootstrap = sabrBootstrap,
        )
    }

    private fun parseFormats(
        formats: JSONArray?,
        root: JSONObject,
        fallbackUrl: String? = null,
        isHls: Boolean = false,
    ): List<StreamCandidate> {
        if (formats == null) return emptyList()
        return (0 until formats.length()).mapNotNull { i ->
            val format = formats.optJSONObject(i) ?: return@mapNotNull null
            parseStreamCandidate(format, root, fallbackUrl, isHls)
        }
    }

    private fun parseStreamCandidate(
        format: JSONObject,
        root: JSONObject,
        fallbackUrl: String? = null,
        isHls: Boolean = false,
    ): StreamCandidate? {
        val directUrl = format.optString("url").takeIf { it.isNotBlank() }
        val cipherPayload = format.optString("signatureCipher").takeIf { it.isNotBlank() }
            ?: format.optString("cipher").takeIf { it.isNotBlank() }

        val url = directUrl ?: cipherPayload?.let { YoutubeCipher.resolveUrl(it, root) } ?: fallbackUrl ?: return null
        val mimeTypeRaw = format.optString("mimeType")
        val mimeType = if (isHls && mimeTypeRaw.isBlank()) "application/x-mpegURL" else mimeTypeRaw.substringBefore(';').trim().lowercase()
        val codecs = mimeTypeRaw.substringAfter("codecs=\"", "")
            .substringBefore('"', "")
            .takeIf { it.isNotBlank() }

        val isAudio = mimeType.startsWith("audio/") || (isHls && !mimeType.startsWith("video/"))
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
            isHls = isHls,
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
