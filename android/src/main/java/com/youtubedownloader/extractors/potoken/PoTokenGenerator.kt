package com.youtubedownloader.extractors.potoken

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubePoTokenResult

/** Owns one short-lived BotGuard WebView and mints visitor-bound player PoTokens. */
internal class PoTokenGenerator(context: Context) {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var webView: PoTokenWebView? = null
    private var sessionVisitorData: String? = null

    fun getBlocking(
        videoId: String,
        cookie: String?,
        preferredVisitorData: String?,
    ): YoutubePoTokenResult = synchronized(lock) {
        if (runCatching { CookieManager.getInstance() }.isFailure) {
            throw BadWebViewException("Android WebView is unavailable")
        }

        val visitorData = preferredVisitorData?.trim().takeUnless { it.isNullOrEmpty() }
            ?: getVisitorData(cookie)
        val current = webView
        if (current == null || current.isExpired || current.isDead || sessionVisitorData != visitorData) {
            closeLocked()
            val created = runBlocking(Dispatchers.IO) {
                PoTokenWebView.getNewPoTokenGenerator(applicationContext)
            }
            try {
                // YouTube expects one visitor-bound token before video-bound tokens are minted.
                runBlocking(Dispatchers.IO) { created.generatePoToken(visitorData) }
                webView = created
                sessionVisitorData = visitorData
            } catch (error: Throwable) {
                created.close()
                throw error
            }
        }

        val active = webView ?: throw PoTokenException("PoToken WebView was not created")
        try {
            val playerPoToken = runBlocking(Dispatchers.IO) {
                active.generatePoToken(videoId)
            }
            YoutubePoTokenResult(
                visitorData,
                YoutubeParsingHelper.getClientVersion(),
                playerPoToken,
            )
        } catch (error: Throwable) {
            closeLocked()
            throw error
        }
    }

    fun close() = synchronized(lock) { closeLocked() }

    private fun getVisitorData(cookie: String?): String {
        val requestInfo = InnertubeClientRequestInfo.ofWebClient()
        requestInfo.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
        return YoutubeParsingHelper.getVisitorDataFromInnertube(
            requestInfo,
            NewPipe.getPreferredLocalization(),
            NewPipe.getPreferredContentCountry(),
            YoutubeParsingHelper.getYouTubeHeaders(),
            YoutubeParsingHelper.YOUTUBEI_V1_URL,
            cookie,
            false,
        )
    }

    private fun closeLocked() {
        webView?.close()
        webView = null
        sessionVisitorData = null
    }
}
