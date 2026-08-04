package com.resona.music.data.extractor

import com.resona.music.data.extractor.decipher.DecipherService
import com.resona.music.data.extractor.decipher.NParamDecipherer
import com.resona.music.data.extractor.decipher.PlayerJsRepository
import com.resona.music.data.extractor.decipher.SignatureDecipherer
import com.resona.music.domain.repository.StreamSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class YouTubeStreamExtractorTest {

    // A player response where the winning Android client hands back a direct,
    // playable audio url -- the exact shape the fast path relies on.
    private val directUrlPlayerResponse = """
    {
      "playabilityStatus": { "status": "OK" },
      "streamingData": {
        "expiresInSeconds": "21599",
        "adaptiveFormats": [
          { "itag": 251, "mimeType": "audio/webm; codecs=\"opus\"", "bitrate": 136544, "url": "https://video.google.com/stream?n=abcd1234&expire=99999" }
        ],
        "formats": []
      }
    }
    """.trimIndent()

    private class NullEngine : JsEngine {
        override suspend fun execute(functionCode: String, argument: String) = null
        override suspend fun executeWithPlayerJs(playerJs: String, discoveryScript: String) = null
    }

    // The fast path must resolve a direct-url response with exactly one player
    // request -- no blocking watch page or player JS downloads. (The visitor
    // token may be seeded in the background afterwards; that never blocks the
    // result.)
    @Test
    fun directUrlResolveMakesExactlyOnePlayerPost() = runTest {
        val requestLog = Collections.synchronizedList(mutableListOf<String>())
        val mockEngine = MockEngine { request ->
            when (request.method.value) {
                "POST" -> requestLog += "POST-${request.url.encodedPath}"
                else -> requestLog += "GET-${request.url.encodedPath}"
            }
            respond(
                content = directUrlPlayerResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val extractor = YouTubeStreamExtractor(
            client = InnerTubeExtractionClient(httpClient),
            playerJsRepo = PlayerJsRepository(httpClient),
            decipherService = DecipherService(
                playerJsRepo = PlayerJsRepository(httpClient),
                nParamDecipherer = NParamDecipherer(NullEngine()),
                signatureDecipherer = SignatureDecipherer(NullEngine()),
            ),
        )

        val source: StreamSource = extractor.resolveStreamUrl("dQw4w9WgXcQ")

        assertEquals("https://video.google.com/stream?n=abcd1234&expire=99999", source.url)
        val posts = requestLog.filter { it.startsWith("POST-") }
        assertEquals("fast path must make exactly one player request", 1, posts.size)
        assertTrue("player post must target the InnerTube player endpoint", POST_PLAYER_PATH in posts.single())
    }

    // Regression test for the "PO token needed" failure: when the session's
    // first resolve goes out without a visitor identity and the gated
    // responses reveal NO visitor (so mid-chain learning can't help), the
    // watch-page-seeded token is retried once.
    @Test
    fun gatedFirstAttemptIsRetriedOnceWithFreshVisitorToken() = runTest {
        val gatedResponse = """{"playabilityStatus":{"status":"OK"}}"""
        val watchPageHtml = """<html><script>var ytcfg = {"VISITOR_DATA":"Cgt0ZXN0dmlzaXRvcg=="};var ytcfg2={"jsUrl":"/s/player/abc/base.js"};</script></html>"""
        val requestLog = Collections.synchronizedList(mutableListOf<String>())
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/watch") || path.endsWith("base.js") -> {
                    requestLog += "${request.method.value}-$path"
                    respond(
                        content = watchPageHtml,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html")
                    )
                }
                else -> {
                    val visitor = request.headers["X-Goog-Visitor-Id"] != null
                    requestLog += "POST-$path visitor=$visitor"
                    respond(
                        content = if (visitor) directUrlPlayerResponse else gatedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val extractor = YouTubeStreamExtractor(
            client = InnerTubeExtractionClient(httpClient),
            playerJsRepo = PlayerJsRepository(httpClient),
            decipherService = DecipherService(
                playerJsRepo = PlayerJsRepository(httpClient),
                nParamDecipherer = NParamDecipherer(NullEngine()),
                signatureDecipherer = SignatureDecipherer(NullEngine()),
            ),
        )

        val source: StreamSource = extractor.resolveStreamUrl("dQw4w9WgXcQ")

        assertEquals("https://video.google.com/stream?n=abcd1234&expire=99999", source.url)
        val posts = requestLog.filter { it.startsWith("POST-") }
        // First pass: the whole gated chain (6 clients), then a retried chain
        // whose first client wins with the visitor token -> 7 player posts.
        assertEquals("gated attempt must be retried exactly once", 7, posts.size)
        assertTrue(
            "retry must carry the fresh visitor token, got $requestLog",
            posts.last().endsWith("visitor=true")
        )
    }

    // A single gated first client (which carries a fresh visitorData) must not
    // even reach the retry: the visitor is learned mid-chain and the very next
    // client succeeds with it -- the common fast path for slightly-gated IPs.
    @Test
    fun visitorLearnedMidChainResolvesWithoutFullRetry() = runTest {
        val gatedWithVisitorResponse = """{"responseContext":{"visitorData":"Cgt0ZXN0dmlzaXRvcg=="},"playabilityStatus":{"status":"OK"}}"""
        val requestLog = Collections.synchronizedList(mutableListOf<String>())
        val mockEngine = MockEngine { request ->
            when {
                request.method.value == "GET" -> {
                    requestLog += "GET-${request.url.encodedPath}"
                    respond(
                        content = """<html><script>var ytcfg = {};</script></html>""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html")
                    )
                }
                else -> {
                    val visitor = request.headers["X-Goog-Visitor-Id"] != null
                    requestLog += "POST-${request.url.encodedPath} visitor=$visitor"
                    respond(
                        content = if (visitor) directUrlPlayerResponse else gatedWithVisitorResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val extractor = YouTubeStreamExtractor(
            client = InnerTubeExtractionClient(httpClient),
            playerJsRepo = PlayerJsRepository(httpClient),
            decipherService = DecipherService(
                playerJsRepo = PlayerJsRepository(httpClient),
                nParamDecipherer = NParamDecipherer(NullEngine()),
                signatureDecipherer = SignatureDecipherer(NullEngine()),
            ),
        )

        val source: StreamSource = extractor.resolveStreamUrl("dQw4w9WgXcQ")

        assertEquals("https://video.google.com/stream?n=abcd1234&expire=99999", source.url)
        val posts = requestLog.filter { it.startsWith("POST-") }
        assertEquals("visitor learned from first client must win on second", 2, posts.size)
        assertTrue("second post must carry the learned visitor", posts.last().endsWith("visitor=true"))
    }

    private companion object {
        val POST_PLAYER_PATH = "/youtubei/v1/player"
    }
}