package com.resona.music.data.extractor.decipher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.resona.music.data.extractor.InnerTubeClientConfig
import com.resona.music.domain.repository.StreamCipherRequiredException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

// Fetches and caches YouTube's player JS (where the sig/n-param transform
// functions live). From yt-dlp-android (see NOTICE.md), simplified to an
// in-memory-only cache since a player JS build is only current for hours
// anyway.
//
// Also owns the app-wide visitor token store. @Singleton: every consumer
// (stream resolution, InnerTube search/radio, player JS) must read and write
// the SAME token -- visitorData handed back by any InnerTube response is
// seeded here so the player request chain never goes out with a stale flag.
@Singleton
class PlayerJsRepository @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context? = null,
) {

    // Keeps the two most-recently-used player JS texts in memory.
    private val memCache = LinkedHashMap<String, String>(4, 0.75f, true)

    // The visitor token survives app restarts (SharedPreferences) so the very
    // first tap of a session resolves on the first player request instead of
    // paying the gated-first-attempt penalty every launch. The token is
    // written through from every learn site (watch-page scrape + gated
    // response) and read back in-memory on startup.
    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Scraped once and reused on every InnerTube request after that -- see
    // currentVisitorData(). Restored from prefs when this code runs again in a
    // fresh process.
    @Volatile private var visitorData: String? = prefs?.getString(PREFS_KEY_VISITOR, null)

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

        Regex(""""VISITOR_DATA":"([^"]+)"""").find(html)?.groupValues?.get(1)?.let {
            visitorData = it
            persistVisitor(it)
        }
        // responseContext.visitorData in youtube.com/v1 responses arrives
        // URL-encoded (%3D for '='); ytcfg's does not. Normalizing keeps both
        // sources comparable and interchangeable when sent back as
        // X-Goog-Visitor-Id / context.user.visitorData.

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
        val normalized = java.net.URLDecoder.decode(token, "UTF-8")
        synchronized(visitorFetchLock) {
            if (normalized != visitorData) {
                lastVisitorDataFetch = System.currentTimeMillis()
                visitorData = normalized
                persistVisitor(normalized)
            }
        }
    }

    // Writes the token through to disk so a later app launch starts with the
    // visitor already known. Best-effort: prefs are gone when the test JVM
    // runs without a context, which is fine -- in-memory behavior is unchanged.
    private fun persistVisitor(token: String) {
        runCatching { prefs?.edit()?.putString(PREFS_KEY_VISITOR, token)?.apply() }
    }

    /**
     * Drops [flagged] and mints a replacement, for when *playback* itself got
     * rejected by the CDN after a resolve that looked fine -- YouTube's PO
     * token verdict is keyed on the visitor identity the /player call
     * carried, not the video or the InnerTube client, so that only shows up
     * once bytes are actually requested and resolution itself never sees it
     * (see PlayerViewModel.onPlayerError). Only clears the cache if [flagged]
     * is still current, so a concurrently-learned good token isn't
     * clobbered. Best-effort: never throws, safe to call speculatively.
     */
    suspend fun remintVisitorData(flagged: String?): String? {
        synchronized(visitorFetchLock) {
            if (visitorData != flagged) {
                Log.d(TAG, "remintVisitorData: $flagged already superseded, current token stands")
                return visitorData
            }
            visitorData = null
            lastVisitorDataFetch = System.currentTimeMillis()
        }
        runCatching { prefs?.edit()?.remove(PREFS_KEY_VISITOR)?.apply() }
        mintVisitorDataViaApi()?.let {
            Log.d(TAG, "remintVisitorData: minted a replacement via visitor_id API (len=${it.length})")
            visitorData = it
            persistVisitor(it)
            return it
        }
        // Fallback: fetchPlayerJsUrl sets/persists visitorData itself as a
        // side effect when its ytcfg scrape succeeds.
        runCatching { fetchPlayerJsUrl(REMINT_VIDEO_ID) }
        Log.d(
            TAG,
            if (visitorData != null) "remintVisitorData: API mint failed, watch-page fallback succeeded"
            else "remintVisitorData: API mint and watch-page fallback both failed"
        )
        return visitorData
    }

    // Dedicated visitor_id InnerTube endpoint: a few hundred bytes and one
    // round trip, versus fetchPlayerJsUrl's ~1.5MB watch-page scrape --
    // remintVisitorData needs a fast mint with no specific video in hand.
    // visitorData in this response is URL-encoded (%3D for the base64
    // padding); the /player payload wants it raw.
    private suspend fun mintVisitorDataViaApi(): String? = runCatching {
        val body = httpClient.post("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("User-Agent", InnerTubeClientConfig.WEB.userAgent)
            setBody(
                """{"context":{"client":{"clientName":"WEB","clientVersion":"${InnerTubeClientConfig.WEB.clientVersion}","hl":"en","gl":"US"}}}"""
            )
        }.bodyAsText()
        Regex(""""visitorData"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
            ?.replace("%3D", "=")?.replace("%3d", "=")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    // sts -- required by some clients for age-restricted videos.
    fun extractSignatureTimestamp(playerJs: String): Int? =
        Regex("""(?:signatureTimestamp|sts)\s*[=:]\s*(\d{5,6})""")
            .find(playerJs)?.groupValues?.get(1)?.toIntOrNull()

    private companion object {
        // How long a seeded visitor token is considered fresh. Tokens last for
        // hours; this bounds background watch-page fetches to a few per session
        // instead of one per track.
        const val VISITOR_DATA_REFRESH_MILLIS = 10 * 60 * 1000L

        const val TAG = "PlayerJsRepository"

        const val PREFS_NAME = "resona_visitor"
        const val PREFS_KEY_VISITOR = "visitor_data"

        // "Me at the zoo" -- the first video ever uploaded to YouTube, kept
        // public indefinitely. remintVisitorData's watch-page fallback needs
        // *some* always-available video id, not one tied to what's playing.
        const val REMINT_VIDEO_ID = "jNQXAC9IVRw"
    }
}
