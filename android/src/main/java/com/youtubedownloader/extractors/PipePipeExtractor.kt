package com.youtubedownloader.extractors

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.grack.nanojson.JsonObject
import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkHttpResponse
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PlaybackData(
    val audioConfig: AudioConfig?,
    val videoDetails: VideoDetails?,
    val playbackTracking: PlaybackTracking?,
    val streamExpiresInSeconds: Int,
    val audioStream: StreamPlayback,
    val videoStream: StreamPlayback?,
    val clientName: String,
)

data class StreamPlayback(
    val format: StreamFormat,
    val streamUrl: String,
)

data class StreamFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val width: Int?,
    val height: Int?,
    val contentLength: Long?,
    val quality: String,
    val fps: Int?,
    val qualityLabel: String?,
    val approxDurationMs: String?,
    val audioSampleRate: Int?,
    val audioChannels: Int?,
    val loudnessDb: Double?,
)

data class AudioConfig(
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
)

data class VideoDetails(
    val videoId: String,
    val title: String?,
    val author: String?,
    val channelId: String,
    val lengthSeconds: String,
    val musicVideoType: String?,
    val viewCount: String?,
)

data class PlaybackTracking(
    val videostatsPlaybackUrl: String?,
    val videostatsWatchtimeUrl: String?,
    val atrUrl: String?,
)

fun PlaybackData.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    map.putInt("streamExpiresInSeconds", streamExpiresInSeconds)
    map.putMap("audioStream", audioStream.toWritableMap())
    map.putString("clientName", clientName)
    videoStream?.let { map.putMap("videoStream", it.toWritableMap()) }
    audioConfig?.let { map.putMap("audioConfig", it.toWritableMap()) }
    videoDetails?.let { map.putMap("videoDetails", it.toWritableMap()) }
    playbackTracking?.let { map.putMap("playbackTracking", it.toWritableMap()) }
    return map
}

private fun StreamPlayback.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    map.putMap("format", format.toWritableMap())
    map.putString("streamUrl", streamUrl)
    return map
}

private fun StreamFormat.toWritableMap(): WritableMap {
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

private fun AudioConfig.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    loudnessDb?.let { map.putDouble("loudnessDb", it) }
    perceptualLoudnessDb?.let { map.putDouble("perceptualLoudnessDb", it) }
    return map
}

private fun VideoDetails.toWritableMap(): WritableMap {
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

private fun PlaybackTracking.toWritableMap(): WritableMap {
    val map = Arguments.createMap()
    videostatsPlaybackUrl?.let { map.putMap("videostatsPlaybackUrl", trackingUrlMap(it)) }
    videostatsWatchtimeUrl?.let { map.putMap("videostatsWatchtimeUrl", trackingUrlMap(it)) }
    atrUrl?.let { map.putMap("atrUrl", trackingUrlMap(it)) }
    return map
}

private fun trackingUrlMap(url: String): WritableMap = Arguments.createMap().apply {
    putString("baseUrl", url)
}

private class PipePipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    var cookie: String? = null

    @Volatile
    var visitorData: String? = null

    override fun execute(request: ExtractorRequest): ExtractorResponse {
        client.newCall(buildRequest(request)).execute().use { response ->
            return response.toExtractorResponse()
        }
    }

    override fun executeAsync(
        request: ExtractorRequest,
        callback: Downloader.AsyncCallback,
    ): CancellableCall {
        val call = client.newCall(buildRequest(request))
        val cancellableCall = CancellableCall(call)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                try {
                    callback.onError(e)
                } finally {
                    cancellableCall.setFinished()
                }
            }

            override fun onResponse(call: Call, response: OkHttpResponse) {
                try {
                    response.use { callback.onSuccess(it.toExtractorResponse()) }
                } catch (e: Exception) {
                    callback.onError(e)
                } finally {
                    cancellableCall.setFinished()
                }
            }
        })
        return cancellableCall
    }

    private fun buildRequest(request: ExtractorRequest): Request {
        val body = request.dataToSend()?.toRequestBody()
        val builder = Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), body)

        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val activeCookie = cookie?.trim().orEmpty()
        if (activeCookie.isNotEmpty()) {
            val existingCookie = request.headers().entries
                .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
                ?.value
                ?.joinToString("; ")
                .orEmpty()
            val combinedCookie = listOf(activeCookie, existingCookie)
                .filter { it.isNotBlank() }
                .joinToString("; ")
            builder.header("Cookie", combinedCookie)

            runCatching {
                val authorization = org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
                    .getAuthorizationHeader(activeCookie)
                if (request.headers().keys.none { it.equals("Authorization", ignoreCase = true) }) {
                    builder.header("Authorization", authorization)
                }
            }
        }

        val activeVisitorData = visitorData?.trim().orEmpty()
        if (activeVisitorData.isNotEmpty() && request.headers().keys.none {
                it.equals("X-Goog-Visitor-Id", ignoreCase = true)
            }) {
            builder.header("X-Goog-Visitor-Id", activeVisitorData)
        }

        if (request.headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            builder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        return builder.build()
    }

    private fun OkHttpResponse.toExtractorResponse(): ExtractorResponse {
        val responseBody = body?.bytes() ?: ByteArray(0)
        return ExtractorResponse(
            code,
            message,
            headers.toMultimap(),
            String(responseBody, StandardCharsets.UTF_8),
            responseBody,
            request.url.toString(),
        )
    }

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

object PipePipeExtractor {
    private val EXPIRE_QUERY_PATTERN = Regex("[?&]expire=(\\d+)")
    private val extractionLock = Any()
    private val downloader = PipePipeDownloader()
    init {
        NewPipe.init(downloader)
    }

    fun extract(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        videoQuality: VideoQuality?,
        isMetered: Boolean,
        cookie: String?,
        forceVisitorData: String?,
    ): PlaybackData {
        val normalizedVideoId = videoId.trim()
        require(VIDEO_ID_PATTERN.matches(normalizedVideoId)) {
            "Invalid YouTube video ID"
        }

        synchronized(extractionLock) {
            downloader.cookie = cookie
            downloader.visitorData = forceVisitorData
            try {
                val extractor = ServiceList.YouTube.getStreamExtractor(
                    buildVideoUrl(normalizedVideoId, playlistId)
                ) as YoutubeStreamExtractor
                extractor.fetchPage()
                val audioStream = selectAudioStream(
                    extractor.getAudioStreams()
                        .filter { it.isUrl && it.content.isNotBlank() }
                        .map { stream ->
                            RankedAudioStream(stream, stream.getBitrateOrAverage())
                        },
                    audioQuality,
                    isMetered,
                ) ?: throw IllegalStateException("PipePipeExtractor returned no audio URL")

                val videoStream = videoQuality?.let { requestedQuality ->
                    val availableVideoStreams = (
                        extractor.getVideoOnlyStreams().ifEmpty {
                            extractor.getVideoStreams()
                        }
                        )
                        .filter { it.isUrl && it.content.isNotBlank() }
                        .map { stream ->
                            RankedVideoStream(
                                stream,
                                stream.heightOrZero(),
                                stream.getBitrate(),
                            )
                        }
                    selectVideoStream(availableVideoStreams, requestedQuality, isMetered)
                        ?: throw IllegalStateException(
                            "PipePipeExtractor returned no video URL for $requestedQuality"
                        )
                }

                return createPlaybackData(
                    extractor,
                    audioStream,
                    videoStream,
                    "default",
                )
            } finally {
                downloader.cookie = null
                downloader.visitorData = null
            }
        }
    }

    private fun createPlaybackData(
        extractor: YoutubeStreamExtractor,
        audioStream: AudioStream,
        videoStream: VideoStream?,
        clientName: String,
    ): PlaybackData {
        val response = extractor.playerResponse
        val details = response?.getObject("videoDetails")
        val playerConfig = response?.getObject("playerConfig")?.getObject("audioConfig")
        val playbackTracking = response?.getObject("playbackTracking")

        return PlaybackData(
            audioConfig = playerConfig?.let {
                AudioConfig(
                    it.doubleOrNull("loudnessDb"),
                    it.doubleOrNull("perceptualLoudnessDb"),
                )
            },
            videoDetails = details?.let {
                VideoDetails(
                    videoId = it.stringOrNull("videoId") ?: extractor.id,
                    title = it.stringOrNull("title"),
                    author = it.stringOrNull("author"),
                    channelId = it.stringOrNull("channelId").orEmpty(),
                    lengthSeconds = it.stringOrNull("lengthSeconds").orEmpty(),
                    musicVideoType = it.stringOrNull("musicVideoType"),
                    viewCount = it.stringOrNull("viewCount"),
                )
            },
            playbackTracking = playbackTracking?.let {
                PlaybackTracking(
                    it.nestedStringOrNull("videostatsPlaybackUrl", "baseUrl"),
                    it.nestedStringOrNull("videostatsWatchtimeUrl", "baseUrl"),
                    it.nestedStringOrNull("atrUrl", "baseUrl"),
                )
            },
            streamExpiresInSeconds = expiresInSeconds(audioStream.content),
            audioStream = StreamPlayback(audioStream.toStreamFormat(), audioStream.content),
            videoStream = videoStream?.let { StreamPlayback(it.toStreamFormat(), it.content) },
            clientName = clientName.uppercase(Locale.ROOT),
        )
    }

    private fun buildVideoUrl(videoId: String, playlistId: String?): String =
        "https://www.youtube.com/watch?v=$videoId" +
            playlistId?.takeIf { it.matches(PLAYLIST_ID_PATTERN) }?.let { "&list=$it" }.orEmpty()

    private fun expiresInSeconds(streamUrl: String): Int {
        val expiresAt = EXPIRE_QUERY_PATTERN.find(streamUrl)?.groupValues?.getOrNull(1)
            ?.toLongOrNull() ?: return 0
        return (expiresAt - System.currentTimeMillis() / 1000L)
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun AudioStream.getBitrateOrAverage(): Int =
        getBitrate().takeIf { it > 0 } ?: getAverageBitrate().coerceAtLeast(0)

    private fun AudioStream.toStreamFormat(): StreamFormat {
        val itag = itagItem
        val mediaFormat = format
        return StreamFormat(
            itag = getItag(),
            mimeType = mediaFormat?.mimeType ?: "",
            bitrate = getBitrateOrAverage(),
            width = null,
            height = null,
            contentLength = itag?.contentLength?.takeIf { it > 0 },
            quality = getQuality().orEmpty(),
            fps = null,
            qualityLabel = null,
            approxDurationMs = itag?.approxDurationMs?.takeIf { it > 0 }?.toString(),
            audioSampleRate = itag?.sampleRate?.takeIf { it > 0 },
            audioChannels = itag?.audioChannels?.takeIf { it > 0 },
            loudnessDb = null,
        )
    }

    private fun VideoStream.heightOrZero(): Int =
        getHeight().takeIf { it > 0 } ?: itagItem?.height?.takeIf { it > 0 } ?: 0

    private fun VideoStream.toStreamFormat(): StreamFormat {
        val itag = itagItem
        val mediaFormat = format
        return StreamFormat(
            itag = getItag(),
            mimeType = mediaFormat?.mimeType ?: "",
            bitrate = getBitrate().coerceAtLeast(0),
            width = getWidth().takeIf { it > 0 },
            height = heightOrZero().takeIf { it > 0 },
            contentLength = itag?.contentLength?.takeIf { it > 0 },
            quality = getQuality().orEmpty(),
            fps = getFps().takeIf { it > 0 },
            qualityLabel = getResolution().takeIf { it.isNotBlank() },
            approxDurationMs = itag?.approxDurationMs?.takeIf { it > 0 }?.toString(),
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        runCatching { getString(key) }.getOrNull()

    private fun JsonObject.doubleOrNull(key: String): Double? =
        runCatching { if (has(key)) getDouble(key) else null }.getOrNull()

    private fun JsonObject.nestedStringOrNull(parent: String, key: String): String? =
        runCatching { getObject(parent)?.getString(key) }.getOrNull()

    private val VIDEO_ID_PATTERN = Regex("[A-Za-z0-9_-]{11}")
    private val PLAYLIST_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
}
