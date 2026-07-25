package com.resona.music.data.extractor

// Runs a JS transform pulled from YouTube's player JS. yt-dlp-android (see
// NOTICE.md) does this with a bundled native QuickJS runtime; WebViewJsEngine
// does the same job on Android's WebView instead, so nothing here depends on
// a third-party binary.
internal interface JsEngine {

    suspend fun execute(functionCode: String, argument: String): String?

    // For string-table-obfuscated players where functionCode can't be found
    // by static extraction -- see NParamDecipherer.
    suspend fun executeWithPlayerJs(playerJs: String, discoveryScript: String): String?
}
