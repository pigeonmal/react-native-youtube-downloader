package com.youtubedownloader

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.bridge.Promise
import com.youtubedownloader.extractors.YTPlayerUtils
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.youtubedownloader.models.AudioQuality
import com.youtubedownloader.models.VideoQuality
import android.content.Context
import com.facebook.react.bridge.ReadableMap
import com.youtubedownloader.extractors.toWritableMap

@ReactModule(name = YoutubeDownloaderModule.NAME)
class YoutubeDownloaderModule(reactContext: ReactApplicationContext) :
  NativeYoutubeDownloaderSpec(reactContext) {

  private val scope = CoroutineScope(Dispatchers.IO)
  override fun getName(): String {
    return NAME
  }

  override fun extractYoutubeStream(
      options: ReadableMap, // or codegen-generated type if using codegen
      promise: Promise
  ) {
      scope.launch {
          try {
              val videoId = options.getString("videoId") ?: ""
              val audioQuality = options.getString("audioQuality") ?: "AUTO"

              val videoQuality: VideoQuality? = if (options.hasKey("videoQuality")) {
                  VideoQuality.fromHeight(options.getInt("videoQuality"))
              } else {
                  null
              }

              val result = YTPlayerUtils.playerResponseForPlayback(
                  videoId = videoId,
                  playlistId = if (options.hasKey("playlistId")) options.getString("playlistId") else null,
                  audioQuality = AudioQuality.valueOf(audioQuality.uppercase()),
                  connectivityManager = reactApplicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
                  videoQuality = videoQuality,
                  cookie = if (options.hasKey("cookie")) options.getString("cookie") else null,
                  forceVisitorData = if (options.hasKey("forceVisitorData")) options.getString("forceVisitorData") else null
              )


              result.onSuccess { playbackData ->
                  promise.resolve(playbackData.toWritableMap())
              }.onFailure { error ->
                  promise.reject("YT_STREAM_ERROR", error)
              }
          } catch (e: Exception) {
              promise.reject("YT_STREAM_EXCEPTION", e)
          }
      }
  }

  companion object {
    const val NAME = "YoutubeDownloader"
  }
}
