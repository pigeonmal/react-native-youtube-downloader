package com.youtubedownloader.innertubex.client

object ClientCatalog {
    /**
     * Unauthenticated clients prioritised for speed and reliability.
     * 1. visionOS (ultra-fast ~80ms, no PoToken needed)
     * 2. Android VR (fast ~110ms, no PoToken needed)
     * 3. TV Simply (PoToken)
     * 4. Mobile Web (PoToken)
     * 5. Desktop Web (PoToken)
     * 6. iOS (HLS fallback)
     * 7. TV Downgraded
     */
    val anonymousClients: List<YouTubeClient> = listOf(
        VisionOsClient.VISIONOS,
        VisionOsClient.VISIONOS_0_1,
        AndroidVrClient.ANDROID_VR_1_65_10,
        AndroidVrClient.ANDROID_VR_1_61_48,
        TvSimplyClient.TVHTML5_SIMPLY,
        WebClient.WEB_EMBEDDED_PLAYER,
        MWebClient.MWEB,
        WebClient.WEB,
        IosClient.IOS,
        TvDowngradedClient.TVHTML5_DOWNGRADED,
    )

    /**
     * Authenticated clients used when a cookie is available or requested.
     * Ordered: Web Remix (Music) -> TV HTML5 -> Mobile Web -> Web -> iOS -> TV Simply -> TV Downgraded.
     */
    val authenticatedClients: List<YouTubeClient> = listOf(
        WebRemixClient.WEB_REMIX,
        TvHtml5Client.TVHTML5,
        WebClient.WEB_EMBEDDED_PLAYER,
        MWebClient.MWEB,
        WebClient.WEB,
        IosClient.IOS,
        TvSimplyClient.TVHTML5_SIMPLY,
        TvDowngradedClient.TVHTML5_DOWNGRADED,
    )

    fun getClients(cookie: String?, authenticatedOnly: Boolean, excludedClientNames: Set<String>): List<YouTubeClient> {
        val list = if (authenticatedOnly) {
            authenticatedClients
        } else {
            // Anonymous first priority; if cookie is present, append authenticated clients as fallback
            anonymousClients + if (!cookie.isNullOrBlank()) authenticatedClients else emptyList()
        }
        return list.filter { it.clientName !in excludedClientNames }
    }
}
