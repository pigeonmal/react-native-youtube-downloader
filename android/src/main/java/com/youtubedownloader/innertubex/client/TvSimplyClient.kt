package com.youtubedownloader.innertubex.client

object TvSimplyClient {
    private const val TV_USER_AGENT =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
            "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"

    val TVHTML5_SIMPLY = YouTubeClient(
        clientName = "TVHTML5_SIMPLY",
        clientVersion = "1.0",
        clientId = "75",
        userAgent = TV_USER_AGENT,
        platform = "TV",
        friendlyName = "TV HTML5 Simply",
        loginSupported = false,
        useSignatureTimestamp = true,
        useWebPoTokens = false,
        requirePoToken = false,
        useMusicPlayerEndpoint = false,
        includeUserAgentInContext = true,
    )

    val TVHTML5_EMBEDDED = YouTubeClient(
        clientName = "TVHTML5_SIMPLY",
        clientVersion = "1.0",
        clientId = "75",
        userAgent = TV_USER_AGENT,
        platform = "TV",
        friendlyName = "TV HTML5 Embedded",
        loginSupported = false,
        useSignatureTimestamp = true,
        isEmbedded = true,
        useWebPoTokens = false,
        requirePoToken = false,
        useMusicPlayerEndpoint = false,
        includeUserAgentInContext = true,
    )
}

