package com.resona.music.data.extractor.decipher

import com.resona.music.data.extractor.JsEngine
import com.resona.music.domain.repository.StreamCipherRequiredException
import java.net.URLDecoder
import javax.inject.Inject

// Deciphers a signatureCipher query string (the s/sp/url triple YouTube
// sends instead of a direct URL for signature-protected formats) by finding
// and running the matching transform function from the player JS.
// From yt-dlp-android, see NOTICE.md.
internal class SignatureDecipherer @Inject constructor(private val jsEngine: JsEngine) {

    // playerJs.hashCode() -> extracted function code (ready to pass to JsEngine)
    private val functionCache = HashMap<Int, String>(2)

    suspend fun decrypt(signatureCipher: String, playerJs: String): String {
        val params = signatureCipher.split('&').associate {
            val eq = it.indexOf('=')
            it.substring(0, eq) to it.substring(eq + 1)
        }
        val encSig = params["s"]
            ?: throw StreamCipherRequiredException("Missing 's' in signatureCipher")
        val sp = params["sp"] ?: "sig"
        val baseUrl = params["url"]?.let { URLDecoder.decode(it, "UTF-8") }
            ?: throw StreamCipherRequiredException("Missing 'url' in signatureCipher")

        val fnCode = functionCache.getOrPut(playerJs.hashCode()) { extractSigFunctionCode(playerJs) }
        val decryptedSig = jsEngine.execute(fnCode, encSig)
            ?: throw StreamCipherRequiredException("Signature decipher function returned no result")
        return "$baseUrl&$sp=$decryptedSig"
    }

    private fun extractSigFunctionCode(js: String): String {
        val fnName = SIG_FUNC_PATTERNS.firstNotNullOfOrNull { it.find(js)?.groupValues?.get(1) }
            ?: throw StreamCipherRequiredException("Sig function name not found in player JS")

        val mainFn = JsFunctionExtractor.extractFunctionBody(js, fnName)
            ?: throw StreamCipherRequiredException("Could not extract sig function body for: $fnName")

        // The sig function typically delegates to a helper object -- include it in the returned code.
        val helperName = Regex("""([a-zA-Z0-9$]{2,4})\.[a-zA-Z0-9$]+\(""").find(mainFn)
            ?.groupValues?.get(1)

        val helperObj = helperName?.let { JsFunctionExtractor.extractObjectLiteral(js, it) }

        return if (helperObj != null) {
            // Wrap in IIFE so the helper is in scope when the returned function is invoked.
            "(function(){var $helperName=$helperObj;\nreturn $mainFn;})()"
        } else {
            mainFn
        }
    }

    private companion object {
        val SIG_FUNC_PATTERNS = listOf(
            Regex("""\.sig\|\|([a-zA-Z0-9$]+)\("""),
            Regex("""\.signature\s*=\s*([a-zA-Z0-9$]+)\("""),
            Regex(""""signature"\s*,\s*([a-zA-Z0-9$]+)\("""),
        )
    }
}
