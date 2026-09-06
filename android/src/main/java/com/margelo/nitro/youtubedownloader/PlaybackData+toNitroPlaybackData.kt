package com.margelo.nitro.youtubedownloader

import com.youtubedownloader.innertubex.AudioConfig as ExtractorAudioConfig
import com.youtubedownloader.innertubex.PlaybackData as ExtractorPlaybackData
import com.youtubedownloader.innertubex.PlaybackTracking as ExtractorPlaybackTracking
import com.youtubedownloader.innertubex.StreamFormat as ExtractorStreamFormat
import com.youtubedownloader.innertubex.StreamPlayback as ExtractorStreamPlayback
import com.youtubedownloader.innertubex.VideoDetails as ExtractorVideoDetails

internal fun ExtractorPlaybackData.toNitroPlaybackData(): PlaybackData = PlaybackData(
  audioConfig = audioConfig?.toNitroAudioConfig(),
  videoDetails = videoDetails?.toNitroVideoDetails(),
  playbackTracking = playbackTracking?.toNitroPlaybackTracking(),
  streamExpiresInSeconds = streamExpiresInSeconds.toDouble(),
  audioStream = audioStream.toNitroStreamPlayback(),
  videoStream = videoStream?.toNitroStreamPlayback(),
  clientName = clientName,
  extractionDurationMs = extractionDurationMs,
  poTokenDurationMs = poTokenDurationMs,
  sabrStreamingUrl = sabrStreamingUrl,
)

private fun ExtractorStreamPlayback.toNitroStreamPlayback(): StreamPlayback = StreamPlayback(
  format = format.toNitroStreamFormat(),
  streamUrl = streamUrl,
  requestHeaders = requestHeaders.takeIf { it.isNotEmpty() },
  rangeChunkSizeBytes = rangeChunkSizeBytes.takeIf { it > 0L }?.toDouble(),
  isHls = isHls.takeIf { it },
)

private fun ExtractorStreamFormat.toNitroStreamFormat(): StreamFormat = StreamFormat(
  itag = itag.toDouble(),
  mimeType = mimeType,
  bitrate = bitrate.toDouble(),
  width = width?.toDouble(),
  height = height?.toDouble(),
  contentLength = contentLength?.toDouble(),
  quality = quality,
  fps = fps?.toDouble(),
  qualityLabel = qualityLabel,
  approxDurationMs = approxDurationMs,
  audioSampleRate = audioSampleRate?.toDouble(),
  audioChannels = audioChannels?.toDouble(),
  loudnessDb = loudnessDb,
)

private fun ExtractorAudioConfig.toNitroAudioConfig(): AudioConfig = AudioConfig(
  loudnessDb = loudnessDb,
  perceptualLoudnessDb = perceptualLoudnessDb,
)

private fun ExtractorVideoDetails.toNitroVideoDetails(): VideoDetails = VideoDetails(
  videoId = videoId,
  title = title,
  author = author,
  channelId = channelId,
  lengthSeconds = lengthSeconds,
  musicVideoType = musicVideoType,
  viewCount = viewCount,
)

private fun ExtractorPlaybackTracking.toNitroPlaybackTracking(): PlaybackTracking = PlaybackTracking(
  videostatsPlaybackUrl = videostatsPlaybackUrl?.let(::TrackingUrl),
  videostatsWatchtimeUrl = videostatsWatchtimeUrl?.let(::TrackingUrl),
  atrUrl = atrUrl?.let(::TrackingUrl),
)
