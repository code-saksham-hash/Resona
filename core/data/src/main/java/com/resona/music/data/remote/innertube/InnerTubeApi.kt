package com.resona.music.data.remote.innertube

import com.resona.music.data.remote.innertube.models.BrowseRequest
import com.resona.music.data.remote.innertube.models.BrowseResponse
import com.resona.music.data.remote.innertube.models.ClientInfo
import com.resona.music.data.remote.innertube.models.InnerTubeContext
import com.resona.music.data.remote.innertube.models.NextRequest
import com.resona.music.data.remote.innertube.models.NextResponse
import com.resona.music.data.remote.innertube.models.PlayerRequest
import com.resona.music.data.remote.innertube.models.PlayerResponse
import com.resona.music.data.remote.innertube.models.SearchRequest
import com.resona.music.data.remote.innertube.models.SearchResponse
import com.resona.music.data.extractor.decipher.PlayerJsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject

/**
 * Thrown when InnerTube responds with a non-2xx status. The shared
 * [HttpClient] sets `expectSuccess = false` (so error bodies can be
 * inspected instead of Ktor throwing generically), which means every call
 * site is responsible for checking [HttpResponse.status] itself -- otherwise
 * a 4xx/5xx response just gets deserialized as if it were a normal, empty
 * result (every field in these response models is nullable/defaulted), and
 * a real outage or block silently looks like "no results" instead of an
 * error.
 */
class InnerTubeHttpException(val statusCode: Int) :
    Exception("InnerTube request failed with HTTP $statusCode")

/**
 * Thin wrapper around YouTube Music's undocumented InnerTube API.
 *
 * YouTube Music has no public API. This follows the same request shape used
 * by unofficial clients such as InnerTune, ViMusic, and Metrolist: a
 * WEB_REMIX client context posted to the youtubei/v1 endpoints with Google's
 * public InnerTube key. The endpoint paths, context shape, and search
 * response structure below were verified directly against the live API, not
 * assumed from memory.
 *
 * The player endpoint (used for stream resolution) currently returns
 * LOGIN_REQUIRED / UNPLAYABLE for anonymous requests across every client
 * context tested (WEB_REMIX, ANDROID_MUSIC, IOS_MUSIC, ANDROID, MWEB) as of
 * this writing -- Google has been progressively gating anonymous player
 * access across the whole InnerTube ecosystem. The player requests
 * themselves live in the extractor package (InnerTubeExtractionClient +
 * YouTubeStreamExtractor), which key on the visitor token seeded here from
 * these non-gated endpoints. See MusicRepositoryImpl for how that failure
 * is surfaced rather than silently swallowed.
 */
class InnerTubeApi @Inject constructor(
    private val httpClient: HttpClient,
    // The app-wide visitor store: every response here hands back a current
    // visitorData in responseContext, which is seeded so the player request
    // chain (YouTubeStreamExtractor) never goes out with a stale identity --
    // YouTube answers stale tokens with LOGIN_REQUIRED on /player, and the
    // search/browse/next endpoints below are the reliable anonymous source
    // of fresh ones (the watch page is bot-gated on some IPs).
    private val visitorRepo: PlayerJsRepository,
) {

    suspend fun search(query: String): SearchResponse {
        val response = httpClient.post("$BASE_URL/search") {
            applyInnerTubeDefaults()
            setBody(SearchRequest(context = webRemixContext(), query = query))
        }
        if (!response.status.isSuccess()) throw InnerTubeHttpException(response.status.value)
        val body: SearchResponse = response.body()
        return body.also { learnVisitor(it.responseContext?.visitorData) }
    }

    suspend fun getPlayerResponse(videoId: String): PlayerResponse =
        httpClient.post("$BASE_URL/player") {
            applyInnerTubeDefaults()
            setBody(PlayerRequest(context = webRemixContext(), videoId = videoId))
        }.body()

    /** Backs both the home feed's playlist carousels (browseId "FEmusic_home")
     *  and a single playlist's track listing (browseId "VL<playlistId>"). */
    suspend fun browse(browseId: String): BrowseResponse {
        val response = httpClient.post("$BASE_URL/browse") {
            applyInnerTubeDefaults()
            setBody(BrowseRequest(context = webRemixContext(), browseId = browseId))
        }
        if (!response.status.isSuccess()) throw InnerTubeHttpException(response.status.value)
        val body: BrowseResponse = response.body()
        return body.also { learnVisitor(it.responseContext?.visitorData) }
    }

    /** The "up next" watch panel for [videoId] -- only used for its Lyrics tab
     *  browseId (see NextResponse.extractLyricsBrowseId in LyricsModels.kt)
     *  and, with [playlistId] set to a "RDAMVM<videoId>" radio id, as the
     *  similar-songs mix (see NextResponse.extractRadioSongs in
     *  RadioModels.kt). */
    suspend fun next(videoId: String, playlistId: String? = null): NextResponse {
        val response = httpClient.post("$BASE_URL/next") {
            applyInnerTubeDefaults()
            setBody(NextRequest(context = webRemixContext(), videoId = videoId, playlistId = playlistId))
        }
        if (!response.status.isSuccess()) throw InnerTubeHttpException(response.status.value)
        val body: NextResponse = response.body()
        return body.also { learnVisitor(it.responseContext?.visitorData) }
    }

    // Best-effort: a missing/blank token is ignored by PlayerJsRepository.
    private fun learnVisitor(visitorData: String?) = visitorRepo.seedVisitorData(visitorData)

    private fun HttpRequestBuilder.applyInnerTubeDefaults() {
        url {
            parameters.append("key", API_KEY)
            parameters.append("prettyPrint", "false")
        }
        contentType(ContentType.Application.Json)
        header("Origin", "https://music.youtube.com")
        header("Referer", "https://music.youtube.com/")
        header("User-Agent", USER_AGENT)
        header("X-Goog-Api-Format-Version", "1")
    }

    private fun webRemixContext() = InnerTubeContext(
        client = ClientInfo(
            clientName = "WEB_REMIX",
            clientVersion = CLIENT_VERSION
        )
    )

    private companion object {
        const val BASE_URL = "https://music.youtube.com/youtubei/v1"

        // Google's public InnerTube key for the YouTube Music web client --
        // the same key used by music.youtube.com itself and by every
        // unofficial client that talks to it.
        const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        const val CLIENT_VERSION = "1.20240101.01.00"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
