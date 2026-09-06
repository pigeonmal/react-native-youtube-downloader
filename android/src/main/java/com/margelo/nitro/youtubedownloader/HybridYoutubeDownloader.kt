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

  override fun extractYoutubeStream(options: ExtractStreamOptions): Promise<PlaybackData> {
    return Promise.async {
      ensureExtractorConfigured()
      val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager
      val playback = YoutubeExtractor.extractAsync(
        videoId = options.videoId,
        playlistId = options.playlistId,
        audioQuality = options.audioQuality.toExtractorAudioQuality(),
        videoQuality = options.videoQuality.toExtractorVideoQuality(),
        isMetered = connectivityManager?.isActiveNetworkMetered == true,
        cookie = options.cookie,
        forceVisitorData = options.forceVisitorData,
        authenticatedOnly = options.authenticatedOnly == true,
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
