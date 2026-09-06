package com.youtubedownloader.innertubex.client

object TvHtml5Client {
    private const val TV_USER_AGENT =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
            "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"

    val TVHTML5 = YouTubeClient(
        clientName = "TVHTML5",
        clientVersion = "7.20260707.07.00",
        clientId = "7",
        userAgent = TV_USER_AGENT,
        friendlyName = "TV HTML5",
        loginSupported = true,
        useSignatureTimestamp = true,
        useWebPoTokens = true,
        requirePoToken = true,
        includeUserAgentInContext = true,
    )
}
