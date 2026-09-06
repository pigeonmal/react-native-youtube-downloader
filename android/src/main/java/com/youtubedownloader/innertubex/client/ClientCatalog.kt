package com.youtubedownloader.innertubex.client

object ClientCatalog {
    /**
     * Unauthenticated clients prioritised for speed and reliability.
     * Ordered: visionOS -> Android VR -> TV Simply (PoToken) -> Mobile Web -> Web -> TV Downgraded.
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
        TvDowngradedClient.TVHTML5_DOWNGRADED,
    )

    /**
     * Authenticated clients used when a cookie is available or requested.
     * Ordered: Web Remix (Music) -> TV HTML5 -> TV Simply -> Mobile Web -> Web -> TV Downgraded.
     */
    val authenticatedClients: List<YouTubeClient> = listOf(
        WebRemixClient.WEB_REMIX,
        TvHtml5Client.TVHTML5,
        TvSimplyClient.TVHTML5_SIMPLY,
        WebClient.WEB_EMBEDDED_PLAYER,
        MWebClient.MWEB,
        WebClient.WEB,
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
