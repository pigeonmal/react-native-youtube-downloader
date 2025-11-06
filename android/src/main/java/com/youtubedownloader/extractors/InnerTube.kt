package com.youtubedownloader.extractors
import com.youtubedownloader.models.YouTubeClient
import com.youtubedownloader.models.YouTubeLocale
import com.youtubedownloader.models.Context
import com.youtubedownloader.models.body.PlayerBody
import java.util.Locale
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.encodeBase64
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
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        install(ContentEncoding) {
            gzip(0.9F)
            deflate(0.8F)
        }

        defaultRequest {
            url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
        }
    }

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient, cookie: String? = null) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId /* Not a typo. The Client-Name header does contain the client id. */)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
            append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
            if (cookie != null && client.loginSupported) {
                append("cookie", cookie)
                val cookieMap = parseCookieString(cookie)
                if ("SAPISID" in cookieMap){
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
                    append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        cookie: String?,
        forceVisitorData: String?,
        signatureTimestamp: Int?,
    ) = httpClient.post("player") {
        ytClient(client, cookie)
        setBody(
            PlayerBody(
                context = client.toContext(locale,  forceVisitorData ?: visitorData, null).let {
                    if (client.isEmbedded) {
                        it.copy(
                            thirdParty = Context.ThirdParty(
                                embedUrl = "https://www.youtube.com/watch?v=${videoId}"
                            )
                        )
                    } else it
                },
                videoId = videoId,
                playlistId = playlistId,
                playbackContext = if (client.useSignatureTimestamp && signatureTimestamp != null) {
                    PlayerBody.PlaybackContext(
                        PlayerBody.PlaybackContext.ContentPlaybackContext(
                            signatureTimestamp
                        )
                    )
                } else null,
            )
        )
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

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
    fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()
}