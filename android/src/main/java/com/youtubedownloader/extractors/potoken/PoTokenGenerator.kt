package com.youtubedownloader.extractors.potoken

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubePoTokenResult
import java.util.HashMap

/** Owns one short-lived BotGuard WebView and mints visitor-bound player PoTokens. */
internal class PoTokenGenerator(context: Context) {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private val preferences: SharedPreferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private var webView: PoTokenWebView? = null
    private var sessionVisitorData: String? = null
    private var sessionCookieKey: String? = null
    private var warmUpKey: String? = null

    /** Prepares BotGuard on the caller's background thread without minting a video token. */
    fun warmUp(cookie: String? = null, preferredVisitorData: String? = null) {
        val requestedKey = contextKey(cookie, preferredVisitorData)
        synchronized(lock) {
            val preferred = preferredVisitorData?.trim().takeUnless { it.isNullOrEmpty() }
            if (preferred != null && isReadyLocked(preferred, cookie.orEmpty())) return
            if (warmUpKey == requestedKey) return
            warmUpKey = requestedKey
            try {
                val visitorData = resolveVisitorData(cookie, preferredVisitorData)
                ensureWebViewLocked(visitorData, cookie)
                TokenLog.tag(TAG).d("BotGuard warmed up")
            } catch (error: Throwable) {
                TokenLog.tag(TAG).w(
                    "BotGuard warm-up failed: ${error::class.simpleName ?: "unknown"}",
                )
            } finally {
                warmUpKey = null
            }
        }
    }

    fun getBlocking(
        videoId: String,
        cookie: String?,
        preferredVisitorData: String?,
    ): YoutubePoTokenResult = synchronized(lock) {
        TokenLog.tag(TAG).d("PoToken requested")
        if (runCatching { CookieManager.getInstance() }.isFailure) {
            TokenLog.tag(TAG).w("Android WebView is unavailable")
            throw BadWebViewException("Android WebView is unavailable")
        }

        val visitorData = resolveVisitorData(cookie, preferredVisitorData)
        TokenLog.tag(TAG).d("Visitor data ready")
        ensureWebViewLocked(visitorData, cookie)

        val active = webView ?: throw PoTokenException("PoToken WebView was not created")
        try {
            val playerPoToken = runBlocking(Dispatchers.IO) {
                active.generatePoToken(videoId)
            }
            TokenLog.tag(TAG).d("Player PoToken ready")
            YoutubePoTokenResult(
                visitorData,
                YoutubeParsingHelper.getClientVersion(),
                playerPoToken,
            )
        } catch (error: Throwable) {
            closeLocked()
            TokenLog.tag(TAG).e("Player PoToken failed: ${error::class.simpleName ?: "unknown"}")
            throw error
        }
    }

    fun close() = synchronized(lock) { closeLocked() }

    private fun getVisitorData(cookie: String?): String {
        val requestInfo = InnertubeClientRequestInfo.ofWebClient()
        requestInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
        val headers = HashMap(YoutubeParsingHelper.getYouTubeHeaders())
        if (!cookie.isNullOrBlank()) {
            headers["Cookie"] = listOf(cookie)
            headers["Authorization"] = listOf(
                YoutubeParsingHelper.getAuthorizationHeader(cookie),
            )
            headers["X-Origin"] = listOf("https://www.youtube.com")
            headers["DNT"] = listOf("1")
        }
        return YoutubeParsingHelper.getVisitorDataFromInnertube(
            requestInfo,
            NewPipe.getPreferredLocalization(),
            NewPipe.getPreferredContentCountry(),
            headers,
            YoutubeParsingHelper.YOUTUBEI_V1_URL,
            null,
            false,
        )
    }

    private fun resolveVisitorData(cookie: String?, preferredVisitorData: String?): String {
        val preferred = preferredVisitorData?.trim().takeUnless { it.isNullOrEmpty() }
        if (preferred != null) return preferred

        val cookieKey = cookie.orEmpty()
        val cachedVisitorData = sessionVisitorData?.takeIf { sessionCookieKey == cookieKey }
            ?: cookieKey.takeIf { it.isEmpty() }?.let {
                preferences.getString(VISITOR_DATA_KEY, null)
            }
        return cachedVisitorData ?: getVisitorData(cookie).also {
            sessionVisitorData = it
            sessionCookieKey = cookieKey
            if (cookieKey.isEmpty()) {
                preferences.edit().putString(VISITOR_DATA_KEY, it).apply()
            }
        }
    }

    private fun ensureWebViewLocked(visitorData: String, cookie: String?) {
        val cookieKey = cookie.orEmpty()
        val current = webView
        if (current != null && !current.isExpired && !current.isDead &&
            sessionVisitorData == visitorData && sessionCookieKey == cookieKey
        ) return

        TokenLog.tag(TAG).d("Creating BotGuard WebView")
        syncYoutubeCookies(cookie)
        closeLocked()
        val created = runBlocking(Dispatchers.IO) {
            PoTokenWebView.getNewPoTokenGenerator(applicationContext)
        }
        try {
            // YouTube expects one visitor-bound token before video-bound tokens are minted.
            runBlocking(Dispatchers.IO) { created.generatePoToken(visitorData) }
            webView = created
            sessionVisitorData = visitorData
            sessionCookieKey = cookieKey
            TokenLog.tag(TAG).d("BotGuard WebView ready")
        } catch (error: Throwable) {
            created.close()
            TokenLog.tag(TAG).e("BotGuard WebView setup failed: ${error::class.simpleName ?: "unknown"}")
            throw error
        }
    }

    private fun isReadyLocked(visitorData: String, cookieKey: String): Boolean {
        val current = webView
        return current != null && !current.isExpired && !current.isDead &&
            sessionVisitorData == visitorData && sessionCookieKey == cookieKey
    }

    private fun contextKey(cookie: String?, preferredVisitorData: String?): String =
        cookie.orEmpty() + "\u0000" + preferredVisitorData.orEmpty()

    private fun closeLocked() {
        webView?.close()
        webView = null
        sessionVisitorData = null
        sessionCookieKey = null
    }

    private fun syncYoutubeCookies(cookie: String?) {
        val activeCookie = cookie?.trim().orEmpty()
        if (activeCookie.isEmpty()) return
        runCatching {
            val cookieManager = CookieManager.getInstance()
            activeCookie.split(';')
                .asSequence()
                .map(String::trim)
                .filter { it.substringBefore('=', "").isNotEmpty() && it.substringAfter('=', "").isNotEmpty() }
                .forEach { pair ->
                    cookieManager.setCookie(
                        "https://www.youtube.com",
                        "$pair; Path=/; Secure",
                    )
                }
            cookieManager.flush()
        }.onFailure {
            TokenLog.tag(TAG).w("Unable to synchronize YouTube cookies with PoToken WebView")
        }
    }

    private companion object {
        const val TAG = "PoTokenGenerator"
        const val PREFERENCES_NAME = "youtube_downloader_potoken"
        const val VISITOR_DATA_KEY = "anonymous_visitor_data"
    }
}
