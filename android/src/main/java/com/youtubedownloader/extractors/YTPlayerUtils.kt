package com.youtubedownloader.extractors

import android.net.ConnectivityManager
import okhttp3.OkHttpClient
import com.youtubedownloader.models.AudioQuality
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
import android.util.Log

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"

    private val httpClient = OkHttpClient.Builder()
        .build()

    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32

    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
     //   WEB_REMIX,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        WEB,
        WEB_CREATOR
    )

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        Log.d(logTag, "Fetching player response for videoId: $videoId, playlistId: $playlistId")
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Log.d(logTag, "Signature timestamp: $signatureTimestamp")

        val isLoggedIn = YouTube.cookie != null
        Log.d(logTag, "Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        Log.d(logTag, "Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        val mainPlayerResponse =
            YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp).getOrThrow()
        val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null

        for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            val client: YouTubeClient
            if (clientIndex == -1) {
                client = MAIN_CLIENT
                streamPlayerResponse = mainPlayerResponse
                Log.d(logTag, "Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Log.d(logTag, "Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    Log.d(logTag, "Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                Log.d(logTag, "Fetching player response for fallback client: ${client.clientName}")
                streamPlayerResponse = YouTube.player(videoId, playlistId, client, signatureTimestamp).getOrNull()
            }

            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Log.d(logTag, "Player response status OK for client: ${client.clientName}")

                format = findFormat(streamPlayerResponse, audioQuality, connectivityManager)

                if (format == null) {
                    Log.d(logTag, "No suitable format found for client: ${client.clientName}")
                    continue
                }

                Log.d(logTag, "Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId)
                if (streamUrl == null) {
                    Log.d(logTag, "Stream URL not found for format")
                    continue
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Log.d(logTag, "Stream expiration time not found")
                    continue
                }

                Log.d(logTag, "Stream expires in: $streamExpiresInSeconds seconds")

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    Log.d(logTag, "Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    break
                }

                if (validateStatus(streamUrl)) {
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
            throw PlaybackException(errorReason, null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
        }

        if (streamExpiresInSeconds == null) {
            Log.e(logTag, "Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            Log.e(logTag, "Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            Log.e(logTag, "Could not find stream url")
            throw Exception("Could not find stream url")
        }

        Log.d(logTag, "Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        PlaybackData(audioConfig, videoDetails, playbackTracking, format, streamUrl, streamExpiresInSeconds)
    }

    suspend fun playerResponseForMetadata(videoId: String, playlistId: String? = null): Result<PlayerResponse> {
        Log.d(logTag, "Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX)
            .onSuccess { Log.d(logTag, "Successfully fetched metadata") }
            .onFailure { Log.e(logTag, "Failed to fetch metadata", it) }
    }

    private fun findFormat(playerResponse: PlayerResponse, audioQuality: AudioQuality, connectivityManager: ConnectivityManager): PlayerResponse.StreamingData.Format? {
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
            reportException(e)
        }
        return false
    }

    private fun getSignatureTimestampOrNull(videoId: String): Int? {
        Log.d(logTag, "Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils.getSignatureTimestamp(videoId)
            .onSuccess { Log.d(logTag, "Signature timestamp obtained: $it") }
            .onFailure {
                Log.e(logTag, "Failed to get signature timestamp", it)
                reportException(it)
            }
            .getOrNull()
    }

    private fun findUrlOrNull(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        Log.d(logTag, "Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        return NewPipeUtils.getStreamUrl(format, videoId)
            .onSuccess { Log.d(logTag, "Stream URL obtained successfully") }
            .onFailure {
                Log.e(logTag, "Failed to get stream URL", it)
                reportException(it)
            }
            .getOrNull()
    }
}
