package com.youtubedownloader.extractors

import android.net.ConnectivityManager
import okhttp3.OkHttpClient
import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import com.youtubedownloader.models.YouTubeClient
import com.youtubedownloader.models.response.PlayerResponse
import com.youtubedownloader.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.youtubedownloader.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.youtubedownloader.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.youtubedownloader.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.youtubedownloader.models.YouTubeClient.Companion.IOS
import com.youtubedownloader.models.YouTubeClient.Companion.IPADOS
import com.youtubedownloader.models.YouTubeClient.Companion.MOBILE
import com.youtubedownloader.models.YouTubeClient.Companion.TVHTML5
import com.youtubedownloader.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.youtubedownloader.models.YouTubeClient.Companion.WEB
import com.youtubedownloader.models.YouTubeClient.Companion.WEB_CREATOR
import com.youtubedownloader.models.YouTubeClient.Companion.WEB_REMIX
import com.youtubedownloader.models.response.toWritableMap
import android.util.Log
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments


    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val streamExpiresInSeconds: Int,
        val audioStream: StreamPlayback,
        val videoStream: StreamPlayback?,
        val clientName: String
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


data class StreamPlayback(
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
)

fun StreamPlayback.toWritableMap(): WritableMap {
        val map = Arguments.createMap()
        map.putMap("format", format.toWritableMap())
        map.putString("streamUrl", streamUrl)
        return map
}

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .build()

    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
        ANDROID_CREATOR,
        MOBILE,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        WEB,
        WEB_CREATOR,
        WEB_REMIX
    )


    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        videoQuality: VideoQuality?,
        cookie: String?,
        forceVisitorData: String?
    ): Result<PlaybackData> = runCatching {
        if (InnerTube.visitorData == null) {
          val vsdata = InnerTube.visitorData().getOrNull()
          Log.d(logTag, "Generated visitorData random $vsdata")
          InnerTube.visitorData = vsdata?.take(80)
        }
        Log.d(logTag, "Fetching player response for videoId: $videoId, playlistId: $playlistId")
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Log.d(logTag, "Signature timestamp: $signatureTimestamp")

        val isLoggedIn = cookie != null
        Log.d(logTag, "Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        val getAlsoVideo = videoQuality != null
        Log.d(logTag, "Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")

        var audioConfig: PlayerResponse.PlayerConfig.AudioConfig? = null
        var videoDetails: PlayerResponse.VideoDetails? = null
        var playbackTracking: PlayerResponse.PlaybackTracking? = null
        var audioStream: StreamPlayback? = null
        var videoStream: StreamPlayback? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var clientName: String = ""

        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            audioStream = null
            videoStream = null
            streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {
                client = MAIN_CLIENT
                clientName = MAIN_CLIENT.clientName
                streamPlayerResponse = InnerTube.player(MAIN_CLIENT, videoId, playlistId, cookie, forceVisitorData, signatureTimestamp).getOrThrow()
                Log.d(logTag, "Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                clientName = client.clientName
                Log.d(logTag, "Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn) {
                    Log.d(logTag, "Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                Log.d(logTag, "Fetching player response for fallback client: ${client.clientName}")
                streamPlayerResponse = InnerTube.player(client, videoId, playlistId, cookie, forceVisitorData, signatureTimestamp).getOrNull()
            }
            if (streamPlayerResponse != null) {
                if (audioConfig == null) {
                    audioConfig = streamPlayerResponse.playerConfig?.audioConfig
                }
                if (videoDetails == null) {
                    videoDetails = streamPlayerResponse.videoDetails
                }
                if (playbackTracking == null) {
                    playbackTracking = streamPlayerResponse.playbackTracking
                }
            }


            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Log.d(logTag, "Player response status OK for client: ${client.clientName}")

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Log.d(logTag, "Stream expiration time not found")
                    continue
                }
                val audioFormat = findAudioFormat(streamPlayerResponse, audioQuality, connectivityManager)

                if (audioFormat == null) {
                    Log.d(logTag, "No suitable audio format found for client: ${client.clientName}")
                    continue
                }

                Log.d(logTag, "Audio format found: ${audioFormat.mimeType}, bitrate: ${audioFormat.bitrate}")

                val audioStreamUrl = findUrlOrNull(audioFormat, videoId)
                if (audioStreamUrl == null) {
                    Log.d(logTag, "Audio stream URL not found for format")
                    continue
                }

                audioStream = StreamPlayback(audioFormat, audioStreamUrl)

                if (getAlsoVideo) {
                    val videoFormat = findVideoFormat(streamPlayerResponse, videoQuality, connectivityManager)
                     if (videoFormat == null) {
                        Log.d(logTag, "No suitable video format found for client: ${client.clientName}")
                        continue
                    }
                    Log.d(logTag, "Video format found: ${videoFormat.mimeType}, bitrate: ${videoFormat.bitrate}")
                    val videoStreamUrl = findUrlOrNull(videoFormat, videoId)
                    if (videoStreamUrl == null) {
                        Log.d(logTag, "Video stream URL not found for format")
                        continue
                    }
                    videoStream = StreamPlayback(videoFormat, videoStreamUrl)
                }


                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    Log.d(logTag, "Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    break
                }

                if (validateStatus(audioStreamUrl)) {
                    Log.d(logTag, "Stream validated successfully with client: ${client.clientName}")
                    break
                } else {
                    Log.d(logTag, "Stream validation failed for client: ${client.clientName}")
                }
            } else {
                Log.d(logTag, "Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Log.e(logTag, "Bad stream player response - all clients failed")
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Log.e(logTag, "Playability status not OK: $errorReason")
            throw Exception("Playability status not OK: $errorReason")
        }

        if (streamExpiresInSeconds == null) {
            Log.e(logTag, "Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (audioStream == null) {
            Log.e(logTag, "Could not find audio stream")
            throw Exception("Could not find audio stream")
        }

         if (getAlsoVideo && videoStream == null) {
            Log.e(logTag, "Could not find video stream")
            throw Exception("Could not find video stream")
        }

        Log.d(logTag, "Successfully obtained playback data.")
        PlaybackData(audioConfig, videoDetails, playbackTracking, streamExpiresInSeconds, audioStream, videoStream, clientName)
    }

    private fun findAudioFormat(playerResponse: PlayerResponse, audioQuality: AudioQuality, connectivityManager: ConnectivityManager): PlayerResponse.StreamingData.Format? {
        Log.d(logTag, "Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")
        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) -1 else 1
                    AudioQuality.HIGH -> 1
                    AudioQuality.LOW -> -1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0)
            }

        if (format != null) Log.d(logTag, "Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        else Log.d(logTag, "No suitable audio format found")

        return format
    }

     private fun findVideoFormat(playerResponse: PlayerResponse, videoQuality: VideoQuality, connectivityManager: ConnectivityManager): PlayerResponse.StreamingData.Format? {
        Log.d(logTag, "Finding format with videoQuality: $videoQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")
        val targetVideoQuality: VideoQuality =
            if (videoQuality == VideoQuality.AUTO)
                if (connectivityManager.isActiveNetworkMetered)
                    VideoQuality.QUALITY_720P
                else
                    VideoQuality.QUALITY_1080P
            else
                videoQuality


        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isVideo && it.height!! <= targetVideoQuality.heightPixels }
            ?.maxByOrNull {
               it.height!!
            }

        if (format != null) Log.d(logTag, "Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        else Log.d(logTag, "No suitable video format found")

        return format
    }

    private fun validateStatus(url: String): Boolean {
        Log.d(logTag, "Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder().head().url(url)
            val response = httpClient.newCall(requestBuilder.build()).execute()
            val isSuccessful = response.isSuccessful
            Log.d(logTag, "Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
            return isSuccessful
        } catch (e: Exception) {
            Log.e(logTag, "Stream URL validation failed with exception", e)
        }
        return false
    }

    private fun getSignatureTimestampOrNull(videoId: String): Int? {
        Log.d(logTag, "Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Log.d(logTag, "Signature timestamp obtained: $it") }
            .onFailure {
                Log.e(logTag, "Failed to get signature timestamp", it)
            }
            .getOrNull()
    }

    private fun findUrlOrNull(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        Log.d(logTag, "Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        return NewPipeUtils.getStreamUrl(format, videoId)
            .onSuccess { Log.d(logTag, "Stream URL obtained successfully") }
            .onFailure {
                Log.e(logTag, "Failed to get stream URL", it)
            }
            .getOrNull()
    }
}
