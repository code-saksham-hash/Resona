package com.resona.music.data.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import javax.inject.Inject
import kotlin.coroutines.resume

// Runs player-JS transform functions in a real WebView instead of bundling a
// third-party native JS engine -- it's already trusted, ships on every
// device, and being an actual browser it doesn't need document/navigator/
// window stubs the way a bare JS engine would.
//
// Each call spins up a throwaway WebView on about:blank (no network, no
// cookies), destroyed once the result's back. Has to run on the main
// thread, hence the Dispatchers.Main hop below.
internal class WebViewJsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : JsEngine {

    override suspend fun execute(functionCode: String, argument: String): String? =
        evaluate("(function(){var fn=$functionCode;return fn(${jsStringLiteral(argument)});})();")

    override suspend fun executeWithPlayerJs(playerJs: String, discoveryScript: String): String? =
        evaluate("$playerJs\n;$discoveryScript")

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun evaluate(script: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.blockNetworkLoads = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(script) { rawResult ->
                        view.destroy()
                        if (continuation.isActive) continuation.resume(decodeResult(rawResult))
                    }
                }
            }
            continuation.invokeOnCancellation { webView.destroy() }
            webView.loadUrl("about:blank")
        }
    }

    // WebView returns its result JSON-encoded -- a JS string comes back as `"..."`.
    private fun decodeResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw == "undefined") return null
        return try {
            if (raw.startsWith("\"")) JSONTokener(raw).nextValue() as? String else raw
        } catch (_: Exception) {
            null
        }
    }

    private fun jsStringLiteral(value: String): String =
        org.json.JSONObject.quote(value)
}
