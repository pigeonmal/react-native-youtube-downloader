package com.youtubedownloader

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.bridge.Promise
import com.youtubedownloader.extractors.PipePipeExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import android.content.Context
import com.facebook.react.bridge.ReadableMap
import com.youtubedownloader.extractors.toWritableMap
import java.util.Locale

@ReactModule(name = YoutubeDownloaderModule.NAME)
class YoutubeDownloaderModule(reactContext: ReactApplicationContext) :
  NativeYoutubeDownloaderSpec(reactContext) {

  private val scope = CoroutineScope(Dispatchers.IO)

  init {
      PipePipeExtractor.configure(reactContext)
      PipePipeExtractor.warmUp()
  }

  override fun getName(): String {
    return NAME
  }

  override fun extractYoutubeStream(
      options: ReadableMap, // or codegen-generated type if using codegen
      promise: Promise
  ) {
      scope.launch {
          try {
              val videoId = options.getString("videoId")?.trim().orEmpty()
              require(videoId.isNotEmpty()) { "videoId is required" }

              val audioQuality = AudioQuality.valueOf(
                  options.getString("audioQuality")
                      ?.uppercase(Locale.ROOT)
                      ?: AudioQuality.AUTO.name
              )

              val videoQuality: VideoQuality? = if (options.hasKey("videoQuality")) {
                  VideoQuality.fromHeight(options.getInt("videoQuality"))
                      ?: throw IllegalArgumentException("Unsupported videoQuality")
              } else {
                  null
              }

              val connectivityManager = reactApplicationContext.getSystemService(
                  Context.CONNECTIVITY_SERVICE
              ) as android.net.ConnectivityManager

              val playbackData = PipePipeExtractor.extract(
                  videoId = videoId,
                  playlistId = if (options.hasKey("playlistId")) options.getString("playlistId") else null,
                  audioQuality = audioQuality,
                  videoQuality = videoQuality,
                  isMetered = connectivityManager.isActiveNetworkMetered,
                  cookie = if (options.hasKey("cookie")) options.getString("cookie") else null,
                  forceVisitorData = if (options.hasKey("forceVisitorData")) options.getString("forceVisitorData") else null
              )

              promise.resolve(playbackData.toWritableMap())
          } catch (e: Exception) {
              promise.reject("YT_STREAM_ERROR", e.message, e)
          }
      }
  }

  companion object {
    const val NAME = "YoutubeDownloader"
  }
}
