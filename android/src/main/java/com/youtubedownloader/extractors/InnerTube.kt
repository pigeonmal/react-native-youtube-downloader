package com.youtubedownloader.extractors

import com.youtubedownloader.models.YouTubeClient
import com.youtubedownloader.models.YouTubeLocale
import com.youtubedownloader.models.Context
import com.youtubedownloader.models.response.PlayerResponse
import com.youtubedownloader.models.body.PlayerBody
import java.util.Locale

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.compression.*
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

import io.ktor.serialization.kotlinx.json.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

import java.security.MessageDigest

object InnerTube {

    private var httpClient = createClient()

    var locale = YouTubeLocale(
        gl = Locale.getDefault().country,
        hl = Locale.getDefault().toLanguageTag()
    )

    var visitorData: String? = null

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                }
            )
        }

        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }

        install(DefaultRequest) {
            url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
        }
    }

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient, cookie: String? = null) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
            append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            if (cookie != null && client.loginSupported) {
                append("cookie", cookie)
                val cookieMap = parseCookieString(cookie)
                if ("SAPISID" in cookieMap) {
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
                    append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                }
            }
            append("User-Agent", client.userAgent) 
        }
        parameter("prettyPrint", false)
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        cookie: String?,
        forceVisitorData: String?,
        signatureTimestamp: Int?,
    ): Result<PlayerResponse> = runCatching {
        httpClient.post("player") {
            ytClient(client, cookie)
            setBody(
                PlayerBody(
                    context = client.toContext(locale, forceVisitorData ?: visitorData, null).let {
                        if (client.isEmbedded) {
                            it.copy(
                                thirdParty = Context.ThirdParty(
                                    embedUrl = "https://www.youtube.com/watch?v=$videoId"
                                )
                            )
                        } else it
                    },
                    videoId = videoId,
                    playlistId = playlistId,
                    playbackContext = if (client.useSignatureTimestamp && signatureTimestamp != null) {
                        PlayerBody.PlaybackContext(
                            PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp)
                        )
                    } else null,
                )
            )
        }.body<PlayerResponse>()
    }

    suspend fun getSwJsData() = httpClient.get("https://music.youtube.com/sw.js_data")

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")

    suspend fun visitorData(): Result<String> = runCatching {
         Json.parseToJsonElement(getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray.first {
                (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                    VISITOR_DATA_REGEX.containsMatchIn(candidate)
                } ?: false
            }
            .jsonPrimitive.content
    }

    fun parseCookieString(cookie: String): Map<String, String> =
        cookie.split("; ")
            .filter { it.isNotEmpty() }
            .mapNotNull { part ->
                val splitIndex = part.indexOf('=')
                if (splitIndex == -1) null
                else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
            }
            .toMap()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun sha1(str: String): String =
        MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()
}
