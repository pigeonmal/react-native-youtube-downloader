package com.youtubedownloader.innertubex.extractor

import com.youtubedownloader.innertubex.client.YouTubeClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom

internal object PlayerRequest {
    private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    private val SECURE_RANDOM = SecureRandom()
    private const val NONCE_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    fun execute(
        httpClient: OkHttpClient,
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        cookie: String?,
        visitorData: String?,
        poToken: String?,
        signatureTimestamp: Long? = null,
        includeSABR: Boolean = false,
    ): ParsedPlayerResponse {
        val requestBody = JSONObject().apply {
            put("videoId", videoId)
            put("context", client.buildContext(visitorData))
            put("contentCheckOk", true)
            put("racyCheckOk", true)

            if (!client.useMusicPlayerEndpoint) {
                put("videoCheckOk", true)
            }

            playlistId?.takeIf { it.isNotBlank() }?.let {
                put("playlistId", it)
            }

            if (client.useSignatureTimestamp || client.isEmbedded || signatureTimestamp != null) {
                val contentPlaybackContext = JSONObject()
                if (!client.useMusicPlayerEndpoint) {
                    contentPlaybackContext.put("html5Preference", "HTML5_PREF_WANTS")
                }
                if (client.useSignatureTimestamp && signatureTimestamp != null) {
                    contentPlaybackContext.put("signatureTimestamp", signatureTimestamp)
                }
                val playbackContext = JSONObject()
                playbackContext.put("contentPlaybackContext", contentPlaybackContext)
                put("playbackContext", playbackContext)
            }

            if (client.isEmbedded) {
                val thirdParty = JSONObject()
                thirdParty.put("embedUrl", "https://www.youtube.com/embed/$videoId")
                put("thirdParty", thirdParty)
            }

            poToken?.takeIf { it.isNotBlank() }?.let {
                val dimensions = JSONObject()
                dimensions.put("poToken", it)
                put("serviceIntegrityDimensions", dimensions)
            }
        }

        val (origin, referer) = when {
            client.useMusicPlayerEndpoint || client.clientName == "WEB_REMIX" ->
                "https://music.youtube.com" to "https://music.youtube.com/"
            client.clientName == "MWEB" ->
                "https://m.youtube.com" to "https://m.youtube.com/"
            else ->
                "https://www.youtube.com" to "https://www.youtube.com/"
        }
        val requestReferer = if (client.isEmbedded) "https://www.reddit.com/" else referer

        val builder = Request.Builder()
            .url(client.playerEndpoint())
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .header("Content-Type", JSON_MEDIA_TYPE)
            .header("Accept", "*/*")
            .header("User-Agent", client.userAgent)
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Origin", origin)
            .header("X-Origin", origin)
            .header("Referer", requestReferer)
            .header("Accept-Language", "en-US,en;q=0.9")

        visitorData?.takeIf { it.isNotBlank() }?.let {
            builder.header("X-Goog-Visitor-Id", it)
        }
        if (client.loginSupported && !cookie.isNullOrBlank()) {
            builder.header("Cookie", cookie)
            builder.header("X-Goog-AuthUser", "0")
            val sapisid = extractSapisid(cookie)
            if (!sapisid.isNullOrBlank()) {
                val currentTime = System.currentTimeMillis() / 1000
                val hash = sha1("$currentTime $sapisid $origin")
                builder.header("Authorization", "SAPISIDHASH ${currentTime}_$hash")
            }
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("YouTube ${client.clientName} HTTP ${response.code}: ${body.take(200)}")
            }
            return PlayerResponseParser.parse(client, body, includeSABR)
        }
    }

    private fun extractSapisid(cookie: String): String? {
        val match = Regex("""(?:^|;\s*)(?:__Secure-3PAPISID|SAPISID)=([^;]+)""").find(cookie)
        return match?.groupValues?.get(1)
    }

    private fun sha1(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
