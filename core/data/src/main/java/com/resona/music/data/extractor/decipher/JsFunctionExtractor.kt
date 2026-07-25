package com.resona.music.data.extractor.decipher

// Pulls a function body (or an object literal it depends on) out of
// YouTube's player JS by brace-matching -- it's not valid enough JS for a
// real parser. From yt-dlp-android, see NOTICE.md.
internal object JsFunctionExtractor {

    // Handles both `fnName=function(a){...}` and `function fnName(a){...}`.
    fun extractFunctionBody(js: String, fnName: String): String? {
        val escaped = Regex.escape(fnName)
        val startPattern = Regex("""(?:$escaped\s*=\s*function|function\s+$escaped)\s*\([^)]*\)\s*\{""")
        val match = startPattern.find(js) ?: return null
        val openBrace = js.indexOf('{', match.range.first)
        if (openBrace == -1) return null
        val closePos = findMatchingBrace(js, openBrace) ?: return null
        return js.substring(match.range.first, closePos + 1)
    }

    fun extractObjectLiteral(js: String, name: String): String? {
        val escaped = Regex.escape(name)
        val match = Regex("""var\s+$escaped\s*=\s*\{""").find(js) ?: return null
        val openBrace = js.indexOf('{', match.range.first)
        if (openBrace == -1) return null
        val closePos = findMatchingBrace(js, openBrace) ?: return null
        return js.substring(openBrace, closePos + 1)
    }

    // Walks forward from openPos tracking brace depth, skipping strings.
    fun findMatchingBrace(js: String, openPos: Int): Int? {
        var depth = 0
        var inString = false
        var stringChar = ' '
        var i = openPos
        while (i < js.length) {
            val c = js[i]
            if (inString) {
                if (c == stringChar && (i == 0 || js[i - 1] != '\\')) inString = false
            } else {
                when (c) {
                    '"', '\'', '`' -> { inString = true; stringChar = c }
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return i }
                }
            }
            i++
        }
        return null
    }
}
