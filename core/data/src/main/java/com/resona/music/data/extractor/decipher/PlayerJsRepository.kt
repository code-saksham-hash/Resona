package com.resona.music.data.extractor.decipher

import com.resona.music.domain.repository.StreamCipherRequiredException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import javax.inject.Inject

// Fetches and caches YouTube's player JS (where the sig/n-param transform
// functions live). From yt-dlp-android (see NOTICE.md), simplified to an
// in-memory-only cache since a player JS build is only current for hours
// anyway.
internal class PlayerJsRepository @Inject constructor(private val httpClient: HttpClient) {

    // Keeps the two most-recently-used player JS texts in memory.
    private val memCache = LinkedHashMap<String, String>(4, 0.75f, true)

    // Scraped once from the first watch page fetched this app session and
    // reused on every InnerTube request after that -- see currentVisitorData().
    @Volatile private var visitorData: String? = null

    suspend fun fetchPlayerJs(playerJsUrl: String): String {
        memCache[playerJsUrl]?.let { return it }

        val fullUrl = if (playerJsUrl.startsWith("http")) playerJsUrl
        else "https://www.youtube.com$playerJsUrl"

        val text = httpClient.get(fullUrl).bodyAsText()
        if (memCache.size >= 2) memCache.entries.iterator().let { it.next(); it.remove() }
        memCache[playerJsUrl] = text
        return text
    }

    suspend fun fetchPlayerJsUrl(videoId: String): String {
        val html = httpClient.get("https://www.youtube.com/watch?v=$videoId") {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }.bodyAsText()

        Regex(""""VISITOR_DATA":"([^"]+)"""").find(html)?.groupValues?.get(1)?.let { visitorData = it }

        return Regex(""""jsUrl"\s*:\s*"(/s/player/[a-f0-9]+/[^"]+base\.js)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""(/s/player/[a-f0-9]+/player_ias\.vflset/[^"]+base\.js)""").find(html)?.groupValues?.get(1)
            ?: throw StreamCipherRequiredException("Could not find player JS URL on the watch page for $videoId")
    }

    // Reused on every InnerTube request for the rest of the session -- makes
    // anonymous traffic look like one returning visitor instead of a fresh
    // one per call, which YouTube's anti-abuse system is noticeably less
    // trigger-happy about. Null until the first watch page fetch succeeds.
    fun currentVisitorData(): String? = visitorData

    // sts -- required by some clients for age-restricted videos.
    fun extractSignatureTimestamp(playerJs: String): Int? =
        Regex("""(?:signatureTimestamp|sts)\s*[=:]\s*(\d{5,6})""")
            .find(playerJs)?.groupValues?.get(1)?.toIntOrNull()
}
