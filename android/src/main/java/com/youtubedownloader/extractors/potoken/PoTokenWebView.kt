package com.youtubedownloader.extractors.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Headless WebView BotGuard runner based on the public NewPipe/Metrolist approach. */
internal class PoTokenWebView private constructor(
    context: Context,
    private val initialization: Continuation<PoTokenWebView>,
) {
    private val webView = WebView(context.applicationContext)
    private val scope = MainScope()
    private val initializationFinished = AtomicBoolean(false)
    private val continuations = ConcurrentHashMap<String, Continuation<String>>()
    private var requestCounter = 0L

    @Volatile private var closed = false
    @Volatile var isDead = false
        private set
    @Volatile private var expirationAtMillis = 0L

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = USER_AGENT
        webView.settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val text = message.message()
                if (text.contains("Uncaught")) {
                    fail(PoTokenException("BotGuard JavaScript failed"), initializationFinished.get())
                }
                // Never forward console content: it can contain challenge material or tokens.
                return true
            }
        }
        webView.webViewClient = object : WebViewClient() {
            @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                isDead = true
                fail(PoTokenException("BotGuard WebView renderer stopped"), initializationFinished.get())
                return true
            }
        }
    }

    private fun start() {
        scope.launch {
            try {
                val html = withContext(Dispatchers.IO) {
                    webView.context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                }
                val page = html.replaceFirst(
                    "</script>",
                    "\n$JS_INTERFACE.downloadAndRunBotguard()</script>",
                )
                webView.loadDataWithBaseURL("https://www.youtube.com", page, "text/html", "utf-8", null)
            } catch (error: Throwable) {
                fail(PoTokenException("Unable to load BotGuard"), false)
            }
        }
    }

    @JavascriptInterface
    fun downloadAndRunBotguard() {
        requestBotguard("https://www.youtube.com/api/jnn/v1/Create", "[ \"$REQUEST_KEY\" ]") { body ->
            val challenge = parseChallengeData(body)
            webView.evaluateJavascript(
                """try {
                    data = $challenge
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null,
            )
        }
    }

    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        requestBotguard(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { body ->
            try {
                val (integrityToken, lifetimeSeconds) = parseIntegrityTokenData(body)
                expirationAtMillis = System.currentTimeMillis() +
                    ((lifetimeSeconds - 600L).coerceAtLeast(60L) * 1000L)
                webView.evaluateJavascript(
                    """try {
                        this.integrityToken = $integrityToken
                        createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                            $JS_INTERFACE.onMinterCreated()
                        }).catch(function(error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + (error.stack || ""))
                        })
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    }""",
                    null,
                )
            } catch (error: Throwable) {
                fail(PoTokenException("Invalid BotGuard integrity response"), false)
            }
        }
    }

    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        fail(buildExceptionForJsError(error), false)
    }

    @JavascriptInterface
    fun onMinterCreated() {
        if (initializationFinished.compareAndSet(false, true)) initialization.resume(this)
    }

    suspend fun generatePoToken(identifier: String): String {
        if (closed || isDead) throw PoTokenException("BotGuard WebView is unavailable")
        val key = "$identifier#${++requestCounter}"
        return try {
            withTimeout(GENERATE_TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        continuations[key] = continuation
                        continuation.invokeOnCancellation { continuations.remove(key) }
                        webView.evaluateJavascript(
                            """(function() {
                                var requestKey = "$key";
                                try {
                                    var u8Identifier = ${stringToU8(identifier)};
                                    obtainPoToken(u8Identifier).then(function(result) {
                                        $JS_INTERFACE.onObtainPoTokenResult(requestKey, result.join(","));
                                    }).catch(function(error) {
                                        $JS_INTERFACE.onObtainPoTokenError(requestKey, error + "\n" + (error.stack || ""));
                                    });
                                } catch (error) {
                                    $JS_INTERFACE.onObtainPoTokenError(requestKey, error + "\n" + error.stack);
                                }
                            })()""",
                            null,
                        )
                    }
                }
            }
        } catch (error: TimeoutCancellationException) {
            isDead = true
            continuations.remove(key)
            throw PoTokenException("BotGuard token generation timed out")
        }
    }

    @JavascriptInterface
    fun onObtainPoTokenError(key: String, error: String) {
        continuations.remove(key)?.resumeWithException(PoTokenException("BotGuard token generation failed"))
    }

    @JavascriptInterface
    fun onObtainPoTokenResult(key: String, bytes: String) {
        try {
            continuations.remove(key)?.resume(u8ToBase64(bytes))
        } catch (error: Throwable) {
            continuations.remove(key)?.resumeWithException(PoTokenException("Invalid BotGuard token"))
        }
    }

    val isExpired: Boolean
        get() = expirationAtMillis > 0L && System.currentTimeMillis() >= expirationAtMillis

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        continuations.values.forEach { it.resumeWithException(PoTokenException("BotGuard WebView closed")) }
        continuations.clear()
        if (Looper.myLooper() == Looper.getMainLooper()) destroy() else Handler(Looper.getMainLooper()).post { destroy() }
    }

    private fun destroy() {
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }

    private fun fail(error: Throwable, afterInitialization: Boolean) {
        if (afterInitialization) isDead = true
        close()
        if (initializationFinished.compareAndSet(false, true)) {
            runCatching { initialization.resumeWithException(error) }
        }
    }

    private fun requestBotguard(url: String, body: String, callback: (String) -> Unit) {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    HTTP_CLIENT.newCall(
                        Request.Builder()
                            .url(url)
                            .post(body.toRequestBody())
                            .headers(HEADERS)
                            .build(),
                    ).execute().use { result ->
                        if (!result.isSuccessful) throw PoTokenException("BotGuard request failed")
                        result.body?.string().orEmpty()
                    }
                }
                if (response.isBlank()) throw PoTokenException("BotGuard response was empty")
                callback(response)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                fail(PoTokenException("BotGuard request failed"), false)
            }
        }
    }

    companion object {
        private const val TAG = "PoTokenWebView"
        private const val JS_INTERFACE = "PoTokenWebView"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val INIT_TIMEOUT_MS = 45_000L
        private const val GENERATE_TIMEOUT_MS = 15_000L
        private val HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json",
            "Content-Type" to "application/json+protobuf",
            "x-goog-api-key" to GOOGLE_API_KEY,
            "x-user-agent" to "grpc-web-javascript/0.1",
        ).toHeaders()
        private val HTTP_CLIENT = OkHttpClient()

        suspend fun getNewPoTokenGenerator(context: Context): PoTokenWebView {
            var created: PoTokenWebView? = null
            return try {
                withTimeout(INIT_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { continuation ->
                            val instance = PoTokenWebView(context, continuation)
                            created = instance
                            continuation.invokeOnCancellation { instance.close() }
                            instance.start()
                        }
                    }
                }
            } catch (error: TimeoutCancellationException) {
                created?.close()
                throw PoTokenException("BotGuard WebView initialization timed out")
            } catch (error: CancellationException) {
                created?.close()
                throw error
            }
        }
    }
}
