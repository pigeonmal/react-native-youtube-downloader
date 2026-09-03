package com.youtubedownloader.extractors.potoken

internal class PoTokenException(message: String) : Exception(message)

internal class BadWebViewException(message: String) : Exception(message)

internal fun buildExceptionForJsError(error: String): Exception =
    if (error.contains("SyntaxError")) BadWebViewException(error) else PoTokenException(error)
