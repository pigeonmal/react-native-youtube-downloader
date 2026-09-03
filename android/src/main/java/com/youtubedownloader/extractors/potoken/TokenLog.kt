package com.youtubedownloader.extractors.potoken

import android.util.Log

internal object TokenLog {
    fun tag(tag: String): Logger = Logger(tag)

    class Logger(private val tag: String) {
        fun d(message: String) = Log.d(tag, message)
        fun w(message: String) = Log.w(tag, message)
        fun e(message: String) = Log.e(tag, message)
        fun e(message: String, throwable: Throwable) = Log.e(tag, message, throwable)
    }
}
