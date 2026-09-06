package com.youtubedownloader.innertubex.sabr

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for communicating with YouTube's SABR (Server Adaptive Bitrate) endpoint via UMP protocol.
 */
object SabrClient {
    private const val UMP_MEDIA_TYPE = "application/vnd.yt-ump"

    fun fetchMediaChunks(
        httpClient: OkHttpClient,
        bootstrap: SabrBootstrap,
        selectedItag: Int,
        poToken: String? = null,
        userAgent: String? = null,
    ): List<SabrChunk> {
        val payload = SabrProtoCodec.buildAbrRequest(
            selectedItag = selectedItag,
            ustreamerConfigBase64 = bootstrap.videoPlaybackUstreamerConfig,
            poToken = poToken,
        )

        val requestBuilder = Request.Builder()
            .url(bootstrap.serverAbrStreamingUrl)
            .post(payload.toRequestBody(UMP_MEDIA_TYPE.toMediaType()))
            .header("Content-Type", UMP_MEDIA_TYPE)
            .header("Accept", UMP_MEDIA_TYPE)

        if (!userAgent.isNullOrBlank()) {
            requestBuilder.header("User-Agent", userAgent)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("SABR HTTP ${response.code}: ${response.message}")
            }
            val bytes = response.body?.bytes() ?: return emptyList()
            val parts = UmpReader.parseParts(bytes)
            return SabrProtoCodec.demuxParts(parts)
        }
    }
}
