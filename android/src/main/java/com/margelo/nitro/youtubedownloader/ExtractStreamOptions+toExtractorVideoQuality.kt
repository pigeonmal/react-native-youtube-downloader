package com.margelo.nitro.youtubedownloader

import com.youtubedownloader.models.VideoQuality as ExtractorVideoQuality

internal fun Double?.toExtractorVideoQuality(): ExtractorVideoQuality? {
  if (this == null) return null
  require(isFinite() && this == toInt().toDouble()) {
    "videoQuality must be an integer video height"
  }
  return ExtractorVideoQuality.fromHeight(toInt())
    ?: throw IllegalArgumentException("Unsupported videoQuality: $this")
}
