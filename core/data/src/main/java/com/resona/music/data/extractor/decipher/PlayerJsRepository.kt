package com.resona.music.data.extractor.decipher

import com.resona.music.domain.repository.StreamCipherRequiredException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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

    @Volatile private var lastVisitorDataFetch = 0L
    @Volatile private var pendingVisitorFetch: Deferred<String?>? = null
    private val visitorFetchLock = Any()

    // Ensures a watch-page fetch that seeds/refreshes the stable visitor token
    // is running, and returns a deferred that completes with the token (null if
    // the fetch failed). The fast stream-resolution path never awaits it -- it
    // exists so that a request which got gated for lacking a visitor identity
    // can be retried once with a fresh one. At most one fetch runs at a time;
    // a cached token is considered fresh for VISITOR_DATA_REFRESH_MILLIS.
    fun ensureVisitorData(videoId: String): Deferred<String?> = synchronized(visitorFetchLock) {
        val now = System.currentTimeMillis()
        if (visitorData != null && now - lastVisitorDataFetch < VISITOR_DATA_REFRESH_MILLIS) {
            return@synchronized CompletableDeferred(visitorData)
        }
        pendingVisitorFetch?.let { return@synchronized it }
        val deferred = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
            try {
                runCatching { fetchPlayerJsUrl(videoId) }
            } finally {
                lastVisitorDataFetch = System.currentTimeMillis()
                // Safe to clear unconditionally: while this fetch is in flight
                // pendingVisitorFetch points at it, so no other fetch can have
                // been started (callers reuse this deferred instead).
                synchronized(visitorFetchLock) {
                    pendingVisitorFetch = null
                }
            }
            visitorData
        }
        pendingVisitorFetch = deferred
        deferred
    }

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

    // Learns a visitor token handed back by an InnerTube response itself
    // (responseContext.visitorData). This is the reliable anonymous-token
    // source: it requires no watch page, which several IPs get redirected to
    // a google.com/sorry bot-check instead of the real page. The token is
    // also kept as the cached one so the whole fallback chain reuses it.
    fun seedVisitorData(token: String?) {
        if (token.isNullOrBlank()) return
        synchronized(visitorFetchLock) {
            if (token != visitorData) {
                lastVisitorDataFetch = System.currentTimeMillis()
                visitorData = token
            }
        }
    }

    // sts -- required by some clients for age-restricted videos.
    fun extractSignatureTimestamp(playerJs: String): Int? =
        Regex("""(?:signatureTimestamp|sts)\s*[=:]\s*(\d{5,6})""")
            .find(playerJs)?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        // How long a seeded visitor token is considered fresh. Tokens last for
        // hours; this bounds background watch-page fetches to a few per session
        // instead of one per track.
        const val VISITOR_DATA_REFRESH_MILLIS = 10 * 60 * 1000L
    }
}
