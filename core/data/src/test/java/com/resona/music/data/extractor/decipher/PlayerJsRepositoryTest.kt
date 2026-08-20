package com.resona.music.data.extractor.decipher

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class PlayerJsRepositoryTest {

    private val visitorIdSuccessResponse = """{"responseContext":{"visitorData":"NEWTOKEN%3D"}}"""
    private val visitorIdEmptyResponse = """{"responseContext":{}}"""
    private val watchPageWithVisitorAndJs =
        """<html><script>var ytcfg = {"VISITOR_DATA":"FALLBACKTOKEN"};var ytcfg2={"jsUrl":"/s/player/abc/base.js"};</script></html>"""

    // The common case: the dedicated visitor_id endpoint answers directly, so
    // a playback-failure remint costs one small round trip, not a watch-page
    // fetch.
    @Test
    fun remintMintsViaDedicatedApiEndpoint() = runTest {
        val requestLog = Collections.synchronizedList(mutableListOf<String>())
        val mockEngine = MockEngine { request ->
            requestLog += "${request.method.value}-${request.url.encodedPath}"
            respond(
                content = visitorIdSuccessResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = PlayerJsRepository(HttpClient(mockEngine))
        repo.seedVisitorData("OLDTOKEN")

        val result = repo.remintVisitorData("OLDTOKEN")

        // %3D is the visitor_id endpoint's own escaping of base64 padding --
        // the /player payload wants it unescaped.
        assertEquals("NEWTOKEN=", result)
        assertEquals("NEWTOKEN=", repo.currentVisitorData())
        assertEquals(listOf("POST-/youtubei/v1/visitor_id"), requestLog)
    }

    // A token already replaced by a concurrent learn (e.g. a gated response's
    // responseContext.visitorData landing mid-retry) must not be clobbered by
    // a remint that was decided against the now-stale value.
    @Test
    fun remintDoesNotClobberAConcurrentlyLearnedToken() = runTest {
        var requestCount = 0
        val mockEngine = MockEngine {
            requestCount++
            respond(
                content = visitorIdSuccessResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = PlayerJsRepository(HttpClient(mockEngine))
        repo.seedVisitorData("CURRENT")

        val result = repo.remintVisitorData("STALE")

        assertEquals("CURRENT", result)
        assertEquals("CURRENT", repo.currentVisitorData())
        assertEquals(
            "a flagged token that no longer matches the current one must not trigger a mint",
            0,
            requestCount
        )
    }

    // If the dedicated endpoint comes back empty (rare, but it's a plain
    // anonymous request like any other InnerTube call), the watch-page scrape
    // is the last resort -- same mechanism ensureVisitorData already relies on.
    @Test
    fun remintFallsBackToWatchPageScrapeWhenApiMintFails() = runTest {
        val requestLog = Collections.synchronizedList(mutableListOf<String>())
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            requestLog += "${request.method.value}-$path"
            if (path.endsWith("/visitor_id")) {
                respond(
                    content = visitorIdEmptyResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = watchPageWithVisitorAndJs,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            }
        }
        val repo = PlayerJsRepository(HttpClient(mockEngine))
        repo.seedVisitorData("OLDTOKEN")

        val result = repo.remintVisitorData("OLDTOKEN")

        assertEquals("FALLBACKTOKEN", result)
        assertEquals("FALLBACKTOKEN", repo.currentVisitorData())
        assertTrue(
            "must try the dedicated endpoint before falling back, got $requestLog",
            requestLog.first().endsWith("/visitor_id")
        )
    }
}
