package com.youtubedownloader.innertubex.client

object IosClient {
    private const val IOS_USER_AGENT =
        "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X; en_US)"

    val IOS = YouTubeClient(
        clientName = "IOS",
        clientVersion = "21.26.4",
        clientId = "5",
        userAgent = IOS_USER_AGENT,
        osName = "iOS",
        osVersion = "17.5.1.21F90",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2",
        platform = "MOBILE",
        friendlyName = "iOS",
        loginSupported = true,
        useSignatureTimestamp = false,
        useMusicPlayerEndpoint = false,
    )
}
