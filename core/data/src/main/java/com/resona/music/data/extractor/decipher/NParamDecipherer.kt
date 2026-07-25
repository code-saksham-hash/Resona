package com.resona.music.data.extractor.decipher

import com.resona.music.data.extractor.JsEngine
import javax.inject.Inject

// Transforms the `n` query param YouTube attaches to adaptive-format URLs --
// a throttling countermeasure, CDN throttles any URL whose n value wasn't
// produced by the matching player's transform function. From yt-dlp-android
// (see NOTICE.md): fast path extracts and runs just the transform function
// via regex; if the player's obfuscation defeats that, falls back to loading
// the whole player JS and using a discovery script to find whichever global
// function behaves like the nsig transform. Original wraps that fallback in
// document/navigator/window stubs since QuickJS is a bare JS engine --
// dropped here since WebViewJsEngine runs on a real WebView that already has
// those.
internal class NParamDecipherer @Inject constructor(private val jsEngine: JsEngine) {

    // playerJs.hashCode() -> extracted function code (or FULL_PLAYER_JS_SENTINEL)
    private val functionCache = HashMap<Int, String>(4)

    suspend fun transform(url: String, playerJs: String): String {
        // Group 1 = prefix (?n= or &n=), group 2 = the token value
        val nValue = N_PARAM_RE.find(url)?.groupValues?.get(2) ?: return url
        val fnCode = functionCache.getOrPut(playerJs.hashCode()) { extractNFunction(playerJs) }

        val transformed = if (fnCode == FULL_PLAYER_JS_SENTINEL) {
            transformViaFullPlayerJs(playerJs, nValue)
        } else {
            jsEngine.execute(fnCode, nValue)
        } ?: nValue

        return url.replace(N_PARAM_RE) { m -> m.groupValues[1] + transformed }
    }

    private suspend fun extractNFunction(js: String): String {
        // Try fast regex-based extraction first (works for older players and
        // players that don't use string-table obfuscation).
        val fnName = extractNFunctionName(js)
        if (fnName != null) {
            val body = JsFunctionExtractor.extractFunctionBody(js, fnName)
            if (body != null && isPlausibleNsigFunction(body)) return body
        }
        // Fall back to full-player-JS execution for obfuscated players.
        return FULL_PLAYER_JS_SENTINEL
    }

    // Runs the candidate against two different test strings and rejects it
    // unless the output looks like an actual transform: not a no-op, not
    // pure hex (hash function, not nsig), right length range.
    private suspend fun isPlausibleNsigFunction(fnCode: String): Boolean {
        val testInput1 = "abcdefghijklmnopq"  // 17-char alphanumeric test
        val testInput2 = "qponmlkjihgfedcba"  // reversed -- different output proves it's a real transform
        return try {
            val r1 = jsEngine.execute(fnCode, testInput1) ?: return false
            val r2 = jsEngine.execute(fnCode, testInput2) ?: return false
            val base64url = Regex("[A-Za-z0-9_\\-]+")
            val pureHex = Regex("[0-9a-f]+")  // all-lowercase hex = hash function, not nsig
            r1 != testInput1 && r1 != "undefined" && r1 != "null"
                    && '=' !in r1 && '+' !in r1 && '/' !in r1
                    && r1.length in 5..25 && r1.length <= testInput1.length + 2
                    && base64url.matches(r1) && !pureHex.matches(r1)
                    && r2 != testInput2 && r2.length in 5..25
                    && base64url.matches(r2) && !pureHex.matches(r2)
                    && r1 != r2
        } catch (_: Exception) {
            false
        }
    }

    private fun extractNFunctionName(js: String): String? {
        for (pattern in N_FUNC_NAME_PATTERNS) {
            val raw = pattern.find(js)?.groupValues?.get(1) ?: continue
            return if ('[' in raw) resolveArrayElement(js, raw) else raw
        }
        return null
    }

    private fun resolveArrayElement(js: String, ref: String): String? {
        val arrName = ref.substringBefore('[')
        val idx = ref.substringAfter('[').substringBefore(']').toIntOrNull() ?: 0
        return Regex("""var\s+${Regex.escape(arrName)}\s*=\s*\[([^\]]+)]""")
            .find(js)?.groupValues?.get(1)
            ?.split(',')?.getOrNull(idx)?.trim()
    }

    // Last resort for string-table-obfuscated players: run the whole player
    // JS, then brute-force every 2-4 char global function against a test
    // string and use whichever one looks like a real transform.
    private suspend fun transformViaFullPlayerJs(playerJs: String, nValue: String): String {
        val escapedN = nValue.replace("\\", "\\\\").replace("\"", "\\\"")
        val discovery = buildDiscoveryScript(escapedN)
        return jsEngine.executeWithPlayerJs(playerJs, discovery) ?: nValue
    }

    // Player JS is `var _yt_player={};(function(g){...})(_yt_player)` --
    // everything assigned to g ends up on the global _yt_player, readable
    // even if the IIFE throws partway through (it usually does, since this
    // sandbox doesn't emulate every browser API the player touches).
    private fun buildDiscoveryScript(escapedN: String): String {
        val ti = "abcdefghijklmnopq"
        val ti2 = "qponmlkjihgfedcbaZ"
        return """
(function() {
    var nVal="$escapedN",ti="$ti",ti2="$ti2",res=nVal;
    function ok(r,i){return typeof r==='string'&&r!==i&&r!=='undefined'&&r!=='null'&&r.indexOf('=')<0&&r.indexOf('+')<0&&r.indexOf('/')<0&&r.indexOf(':')<0&&r.length>=5&&r.length<=i.length+2&&/^[A-Za-z0-9_-]+${'$'}/.test(r)&&!/^[0-9a-f]+${'$'}/.test(r);}
    function isRealNsig(fn){try{var r1=fn(ti),r2=fn(ti2);return ok(r1,ti)&&ok(r2,ti2)&&r1!==r2;}catch(e){return false;}}
    try{
        var g=globalThis._yt_player||{};
        var keys=Object.keys(g).filter(function(k){return k.length>=2&&k.length<=4&&typeof g[k]==='function';});
        for(var i=0;i<keys.length;i++){var k=keys[i];if(isRealNsig(g[k])){res=g[k](nVal);break;}}
    }catch(e){}
    return res;
})()
""".trimIndent()
    }

    private companion object {
        // Sentinel stored in functionCache when regex patterns failed;
        // signals that full-player-JS execution should be used.
        const val FULL_PLAYER_JS_SENTINEL = "__FULL_PLAYER_JS__"

        // Group 1 = "?n=" or "&n=", group 2 = the token value
        val N_PARAM_RE = Regex("""([?&]n=)([^&]+)""")

        val N_FUNC_NAME_PATTERNS = listOf(
            Regex("""\.get\("n"\)\)&&\([a-zA-Z0-9_$]+=([a-zA-Z0-9_\[\]$]{2,40})\("""),
            Regex("""[a-zA-Z0-9_$]+&&\([a-zA-Z0-9_$]+=([a-zA-Z0-9_\[\]$]{2,40})\([a-zA-Z0-9_$]"""),
            Regex("""\.get\("n"\)\)&&\([a-zA-Z_$]=([a-zA-Z0-9_$]{2,4})\["""),
            Regex("""([a-zA-Z0-9_$]{3,})\s*=\s*function\s*\([a-zA-Z_$]\)\s*\{var\s+[a-zA-Z_$]\s*=\s*[a-zA-Z_$]\.split\(""\)"""),
        )
    }
}
