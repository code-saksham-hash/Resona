package com.resona.music.data.extractor.decipher

import com.resona.music.data.extractor.JsEngine
import com.resona.music.data.extractor.model.RawFormat
import com.resona.music.domain.repository.StreamCipherRequiredException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URLEncoder

class DecipherServiceTest {

    // Player JS whose only recognisable member is a simple signature function:
    // enough for SignatureDecipherer to extract a function body, while the
    // nsig fast-path patterns find nothing (so NParamDecipherer falls back to
    // the full-player-JS route, which the fake JsEngine below serves).
    private val playerJs = """
        var A=function(x){var t=x.split("");t.push("z");return t.join("")};
        maximaleN.decision&&(puppeteer=A(b,c));
        delegate.sig||A(b,c);
    """.trimIndent()

    private fun decipherService(jsEngine: JsEngine): DecipherService {
        val mockEngine = MockEngine { request ->
            respond(
                content = playerJs,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/javascript")
            )
        }
        val httpClient = HttpClient(mockEngine)
        return DecipherService(
            playerJsRepo = PlayerJsRepository(httpClient),
            nParamDecipherer = NParamDecipherer(jsEngine),
            signatureDecipherer = SignatureDecipherer(jsEngine),
        )
    }

    // A fake engine whose output is unmistakable, so the test can assert
    // exactly where the transform pipeline ran.
    private class TransformTracingEngine : JsEngine {
        var executeCalls = 0
        override suspend fun execute(functionCode: String, argument: String): String? {
            executeCalls++
            return "TRANSFORMED"
        }

        override suspend fun executeWithPlayerJs(playerJs: String, discoveryScript: String): String? {
            executeCalls++
            return "TRANSFORMED"
        }
    }

    // Regression test for the "source error" failure mode: direct urls from
    // the Android/iOS/TV clients that win the extraction chain are playable
    // exactly as returned, and must NOT be re-run through the nsig transform
    // (the transform may return a plausible-but-wrong token, turning a
    // working url into a CDN 403).
    @Test
    fun directUrlIsReturnedWithoutNsigTransform() = runTest {
        val engine = TransformTracingEngine()
        val service = decipherService(engine)

        val directUrl = "https://video.google.com/video?itag=251&n=abc123def&expire=99999"
        val playable = service.buildPlayableUrl(
            format = RawFormat(itag = 251, url = directUrl, mimeType = "audio/webm"),
            playerJsUrl = "/s/player/ca042962/base.js",
        )

        assertEquals(directUrl, playable)
        assertEquals("nsig transform must not run for direct urls", 0, engine.executeCalls)
    }

    // urls rebuilt from a signatureCipher (web-style formats) still go
    // through signature deciphering and the nsig transform.
    @Test
    fun cipheredUrlIsDecipheredAndTransformed() = runTest {
        val engine = TransformTracingEngine()
        val service = decipherService(engine)

        val baseUrl = "https://video.google.com/video?v=abc&n=nsigvalue"
        val cipher =
            "s=someSig&sp=sig&url=${URLEncoder.encode(baseUrl, "UTF-8")}"

        val playable = service.buildPlayableUrl(
            format = RawFormat(itag = 251, url = null, signatureCipher = cipher, mimeType = "audio/webm"),
            playerJsUrl = "/s/player/ca042962/base.js",
        )

        assertTrue(playable, playable.startsWith("https://video.google.com/video?v=abc"))
        assertTrue("nsig transform must run for ciphered urls", "n=TRANSFORMED" in playable)
        assertTrue("signature param must be appended", "sig=TRANSFORMED" in playable)
        assertTrue(engine.executeCalls >= 2)
    }

    // A format with neither url nor signatureCipher must keep failing loudly.
    @Test
    fun formatWithoutUrlOrCipherThrows() = runTest {
        val service = decipherService(TransformTracingEngine())
        try {
            service.buildPlayableUrl(
                format = RawFormat(itag = 251, url = null, signatureCipher = null, mimeType = "audio/webm"),
                playerJsUrl = "/s/player/ca042962/base.js",
            )
            fail("Expected StreamCipherRequiredException")
        } catch (_: StreamCipherRequiredException) {
            // expected
        }
    }

    // Direct urls stay intact even when there is no player JS at all (watch
    // page blocked) -- avoiding the transform is exactly what makes this safe.
    @Test
    fun directUrlWorksWithoutPlayerJs() = runTest {
        val engine = TransformTracingEngine()
        val service = DecipherService(
            playerJsRepo = PlayerJsRepository(HttpClient(MockEngine { respond("", HttpStatusCode.OK) })),
            nParamDecipherer = NParamDecipherer(engine),
            signatureDecipherer = SignatureDecipherer(engine),
        )

        val directUrl = "https://video.google.com/video?itag=140&n=xyz987&expire=99999"
        val playable = service.buildPlayableUrl(
            format = RawFormat(itag = 140, url = directUrl, mimeType = "audio/mp4"),
            playerJsUrl = null,
        )

        assertEquals(directUrl, playable)
        assertFalse(engine.executeCalls > 0)
    }
}