package com.margelo.nitro.youtubedownloader

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider

/** Loads the Nitro shared library; HybridObjects are registered by Nitrogen. */
class YoutubeDownloaderPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
    return null
  }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
    return ReactModuleInfoProvider { HashMap() }
  }

  companion object {
    init {
      System.loadLibrary("youtubedownloader")
    }
  }
}
