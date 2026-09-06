package com.margelo.nitro.youtubedownloader

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.Keep
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import com.youtubedownloader.innertubex.YoutubeExtractor
import java.util.concurrent.atomic.AtomicBoolean

/** Nitro entry point for the Android YouTube extractor. */
@Keep
@DoNotStrip
class HybridYoutubeDownloader : HybridYoutubeDownloaderSpec() {
  private val didWarmUp = AtomicBoolean(false)

  private val context: Context
    get() = NitroModules.applicationContext
      ?: throw IllegalStateException("No React ApplicationContext is available")

  private val connectivityManager: ConnectivityManager? by lazy {
    NitroModules.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
  }

  override fun extractYoutubeStream(options: ExtractStreamOptions): Promise<PlaybackData> {
    ensureExtractorConfigured()

    val audioQuality = options.audioQuality.toExtractorAudioQuality()
    val videoQuality = options.videoQuality.toExtractorVideoQuality()
    val isMetered = connectivityManager?.isActiveNetworkMetered == true
    val authenticatedOnly = options.authenticatedOnly == true

    // Synchronous native cache peek: eliminates thread hopping and binder IPC on cache hits (<0.5ms)
    // Only peek cache when no specific client is forced.
    if (options.clientName.isNullOrBlank()) {
      val cached = YoutubeExtractor.peekCache(
        videoId = options.videoId,
        playlistId = options.playlistId,
        audioQuality = audioQuality,
        videoQuality = videoQuality,
        isMetered = isMetered,
        cookie = options.cookie,
        forceVisitorData = options.forceVisitorData,
        authenticatedOnly = authenticatedOnly,
      )
      if (cached != null) {
        return Promise.resolved(cached.toNitroPlaybackData())
      }
    }

    return Promise.async {
      val playback = YoutubeExtractor.extractAsync(
        videoId = options.videoId,
        playlistId = options.playlistId,
        audioQuality = audioQuality,
        videoQuality = videoQuality,
        isMetered = isMetered,
        cookie = options.cookie,
        forceVisitorData = options.forceVisitorData,
        authenticatedOnly = authenticatedOnly,
        targetClientName = options.clientName,
      )
      playback.toNitroPlaybackData()
    }
  }

  override fun generatePoToken(videoId: String): Promise<String> {
    return Promise.async {
      ensureExtractorConfigured()
      YoutubeExtractor.generatePoTokenAsync(videoId.trim())
    }
  }

  private fun ensureExtractorConfigured() {
    YoutubeExtractor.configure(context)
    if (didWarmUp.compareAndSet(false, true)) {
      YoutubeExtractor.warmUp()
    }
  }
}
