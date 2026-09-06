package com.youtubedownloader.innertubex.models

data class StreamPlayback(
    val format: StreamFormat,
    val streamUrl: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val rangeChunkSizeBytes: Long = 0L,
)
