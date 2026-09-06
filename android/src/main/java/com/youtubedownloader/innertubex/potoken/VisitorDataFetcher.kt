package com.youtubedownloader.innertubex.potoken

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONTokener

/**
 * High-performance visitorData fetcher based on InnerTubeX's sw.js_data strategy.
 * Fetches visitorData in <100ms without doing a heavy player API extraction.
 */
object VisitorDataFetcher {
    private const val VISITOR_DATA_URL = "https://www.youtube.com/sw.js_data"
    private const val FALLBACK_MUSIC_URL = "https://music.youtube.com"
    private val VISITOR_DATA_REGEX = Regex(""""(?:VISITOR_DATA|visitorData)"\s*:\s*"([^"]+)"""")

    fun fetchVisitorData(httpClient: OkHttpClient, userAgent: String): String? {
        // Fast path: sw.js_data
        runCatching {
            val request = Request.Builder()
                .url(VISITOR_DATA_URL)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json,text/plain,*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    parseServiceWorkerVisitorData(raw)?.let { return it }
                }
            }
        }

        // Fallback path: music.youtube.com regex scrape
        return runCatching {
            val request = Request.Builder()
                .url(FALLBACK_MUSIC_URL)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
                } else null
            }
        }.getOrNull()
    }

    private fun parseServiceWorkerVisitorData(responseText: String): String? = runCatching {
        val jsonText = responseText.substringAfter('\n').trimStart()
        val tokener = JSONTokener(jsonText)
        val root = JSONArray(tokener)
        // Structure: root[0][2][0][0][13] is visitorData string
        val arr0 = root.getJSONArray(0)
        val arr2 = arr0.getJSONArray(2)
        val inner0 = arr2.getJSONArray(0)
        val item0 = inner0.getJSONArray(0)
        val visitorData = item0.getString(13)
        visitorData.takeIf { it.isNotBlank() }
    }.getOrNull()
}
