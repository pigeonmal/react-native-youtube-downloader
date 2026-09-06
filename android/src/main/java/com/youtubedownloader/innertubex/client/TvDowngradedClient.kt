package com.youtubedownloader.innertubex.client

object TvDowngradedClient {
    private const val TV_DOWNGRADED_USER_AGENT =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

    val TVHTML5_DOWNGRADED = YouTubeClient(
        clientName = "TVHTML5",
        clientVersion = "5.20260707",
        clientId = "7",
        userAgent = TV_DOWNGRADED_USER_AGENT,
        friendlyName = "TV HTML5 Downgraded",
        loginSupported = true,
        useSignatureTimestamp = true,
        includeUserAgentInContext = true,
    )
}
