package com.margelo.nitro.youtubedownloader

import com.youtubedownloader.models.AudioQuality as ExtractorAudioQuality

internal fun AudioQuality.toExtractorAudioQuality(): ExtractorAudioQuality = when (this) {
  AudioQuality.AUTO -> ExtractorAudioQuality.AUTO
  AudioQuality.LOW -> ExtractorAudioQuality.LOW
  AudioQuality.HIGH -> ExtractorAudioQuality.HIGH
}
