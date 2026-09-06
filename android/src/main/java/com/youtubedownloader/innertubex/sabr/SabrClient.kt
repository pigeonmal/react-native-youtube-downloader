package com.youtubedownloader.innertubex.sabr

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.io.OutputStream

/**
 * Client for communicating with YouTube's SABR (Server Adaptive Bitrate) endpoint via UMP protocol.
 */
object SabrClient {
    private const val UMP_MEDIA_TYPE = "application/vnd.yt-ump"

    fun buildRequest(
        bootstrap: SabrBootstrap,
        selectedItag: Int,
        poToken: String? = null,
        userAgent: String? = null,
    ): Request {
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

        return requestBuilder.build()
    }

    fun fetchMediaChunks(
        httpClient: OkHttpClient,
        bootstrap: SabrBootstrap,
        selectedItag: Int,
        poToken: String? = null,
        userAgent: String? = null,
    ): List<SabrChunk> {
        val request = buildRequest(bootstrap, selectedItag, poToken, userAgent)
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("SABR HTTP ${response.code}: ${response.message}")
            }
            val bytes = response.body?.bytes() ?: return emptyList()
            val parts = UmpReader.parseParts(bytes)
            return SabrProtoCodec.demuxParts(parts)
        }
    }

    /**
     * Streams media chunks directly from SABR to an OutputStream (e.g. FileOutputStream)
     * without loading all bytes in memory at once.
     */
    fun streamMediaTo(
        httpClient: OkHttpClient,
        bootstrap: SabrBootstrap,
        selectedItag: Int,
        output: OutputStream,
        poToken: String? = null,
        userAgent: String? = null,
        onBytesWritten: ((Long) -> Unit)? = null,
    ): Long {
        val request = buildRequest(bootstrap, selectedItag, poToken, userAgent)
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("SABR HTTP ${response.code}: ${response.message}")
            }
            val bodyStream = response.body?.byteStream() ?: return 0L
            var totalBytes = 0L
            while (true) {
                val part = UmpReader.readPart(bodyStream) ?: break
                if (part.type == SabrPart.TYPE_MEDIA && part.payload.isNotEmpty()) {
                    output.write(part.payload)
                    totalBytes += part.payload.size
                    onBytesWritten?.invoke(totalBytes)
                } else if (part.type == SabrPart.TYPE_MEDIA_END) {
                    break
                }
            }
            output.flush()
            return totalBytes
        }
    }

    /**
     * Creates a streaming InputStream wrapping the SABR UMP response.
     */
    fun createInputStream(
        httpClient: OkHttpClient,
        bootstrap: SabrBootstrap,
        selectedItag: Int,
        poToken: String? = null,
        userAgent: String? = null,
    ): InputStream {
        val request = buildRequest(bootstrap, selectedItag, poToken, userAgent)
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("SABR HTTP ${response.code}: ${response.message}")
        }
        val bodyStream = response.body?.byteStream() ?: throw IllegalStateException("Empty response body from SABR")
        return SabrInputStream(response, bodyStream)
    }
}

/**
 * An InputStream that demuxes UMP media parts on the fly from a SABR HTTP response.
 */
class SabrInputStream(
    private val response: okhttp3.Response,
    private val rawStream: InputStream,
) : InputStream() {
    private var currentPayload: ByteArray? = null
    private var currentOffset: Int = 0
    private var isClosed = false
    private var isFinished = false

    override fun read(): Int {
        val single = ByteArray(1)
        val n = read(single, 0, 1)
        return if (n == -1) -1 else (single[0].toInt() and 0xFF)
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isClosed) throw java.io.IOException("Stream closed")
        if (len == 0) return 0
        if (isFinished && (currentPayload == null || currentOffset >= (currentPayload?.size ?: 0))) {
            return -1
        }

        while (currentPayload == null || currentOffset >= (currentPayload?.size ?: 0)) {
            if (isFinished) return -1
            val part = UmpReader.readPart(rawStream)
            if (part == null || part.type == SabrPart.TYPE_MEDIA_END) {
                isFinished = true
                if (currentPayload == null || currentOffset >= (currentPayload?.size ?: 0)) {
                    return -1
                }
                break
            }
            if (part.type == SabrPart.TYPE_MEDIA && part.payload.isNotEmpty()) {
                currentPayload = part.payload
                currentOffset = 0
                break
            }
        }

        val available = (currentPayload?.size ?: 0) - currentOffset
        if (available <= 0) return -1
        val bytesToCopy = minOf(len, available)
        System.arraycopy(currentPayload!!, currentOffset, b, off, bytesToCopy)
        currentOffset += bytesToCopy
        return bytesToCopy
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            try {
                rawStream.close()
            } finally {
                response.close()
            }
        }
    }
}
