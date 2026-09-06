package com.youtubedownloader.innertubex.client

import com.youtubedownloader.innertubex.models.PoTokenBinding

object TvSimplyClient {
    private const val TV_USER_AGENT =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
            "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"

    val TVHTML5_SIMPLY = YouTubeClient(
        clientName = "TVHTML5_SIMPLY",
        clientVersion = "1.0",
        clientId = "75",
        userAgent = TV_USER_AGENT,
        friendlyName = "TV HTML5 Simply",
        loginSupported = false,
        useSignatureTimestamp = true,
        useWebPoTokens = true,
        requirePoToken = true,
        poTokenBinding = PoTokenBinding.VISITOR_DATA,
        useMusicPlayerEndpoint = true,
        includeUserAgentInContext = true,
    )
}
