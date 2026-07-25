package com.resona.music.data.remote.innertube

import android.util.Log
import com.resona.music.data.remote.innertube.models.ClientInfo
import com.resona.music.data.remote.innertube.models.InnerTubeContext
import com.resona.music.data.remote.innertube.models.PlayerRequest
import com.resona.music.data.remote.innertube.models.PlayerResponse
import com.resona.music.data.remote.innertube.models.SearchRequest
import com.resona.music.data.remote.innertube.models.SearchResponse
import com.resona.music.domain.repository.PlaybackUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import javax.inject.Inject

class InnerTubeApi @Inject constructor(
    private val httpClient: HttpClient
) {

    suspend fun search(query: String): SearchResponse =
        httpClient.post("$BASE_URL/search") {
            applyInnerTubeDefaults(API_KEY)
            setBody(SearchRequest(context = webRemixContext(), query = query))
        }.body()

    suspend fun getPlayerResponse(videoId: String): PlayerResponse {
        val clients = listOf(
            androidMusicContext() to ANDROID_MUSIC_API_KEY,
            androidContext() to ANDROID_API_KEY,
            webRemixContext() to API_KEY
        )
        for ((context, apiKey) in clients) {
            try {
                val response = httpClient.post("$BASE_URL/player") {
                    applyInnerTubeDefaults(apiKey, context.client.clientName)
                    setBody(PlayerRequest(context = context, videoId = videoId))
                }.body<PlayerResponse>()
                if (response.playabilityStatus?.status == "OK") return response
            } catch (_: Exception) { }
        }
        return getPlayerResponseFromWatchPage(videoId)
    }

    private suspend fun getPlayerResponseFromWatchPage(videoId: String): PlayerResponse {
        val response = httpClient.get("https://www.youtube.com/watch?v=$videoId&gl=US&hl=en") {
            header("User-Agent", WATCH_PAGE_USER_AGENT)
        }
        val body = response.bodyAsText()
        val json = extractYtInitialPlayerResponse(body)
            ?: throw PlaybackUnavailableException("NO_DATA", "Could not find ytInitialPlayerResponse in watch page")
        return Json { ignoreUnknownKeys = true }.decodeFromString(json)
    }

    private fun HttpRequestBuilder.applyInnerTubeDefaults(
        apiKey: String,
        clientName: String? = null
    ) {
        url {
            parameters.append("key", apiKey)
            parameters.append("prettyPrint", "false")
        }
        contentType(ContentType.Application.Json)
        header("Origin", "https://music.youtube.com")
        header("Referer", "https://music.youtube.com/")
        header("User-Agent", USER_AGENT)
        header("X-Goog-Api-Format-Version", "1")
        if (clientName == "ANDROID") {
            header("X-YouTube-Client-Name", "1")
            header("X-YouTube-Client-Version", ANDROID_CLIENT_VERSION)
        } else if (clientName == "ANDROID_MUSIC") {
            header("X-YouTube-Client-Name", "5")
            header("X-YouTube-Client-Version", ANDROID_MUSIC_CLIENT_VERSION)
        }
    }

    private fun webRemixContext() = InnerTubeContext(
        client = ClientInfo("WEB_REMIX", WEB_REMIX_CLIENT_VERSION)
    )

    private fun androidMusicContext() = InnerTubeContext(
        client = ClientInfo("ANDROID_MUSIC", ANDROID_MUSIC_CLIENT_VERSION)
    )

    private fun androidContext() = InnerTubeContext(
        client = ClientInfo("ANDROID", ANDROID_CLIENT_VERSION, androidSdkVersion = 34, osVersion = "14")
    )

    private companion object {
        const val BASE_URL = "https://music.youtube.com/youtubei/v1"
        const val API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
        const val ANDROID_API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
        const val ANDROID_MUSIC_API_KEY = "AIzaSyAOghZGza2MQSZkY_zE0Km0OZU0gKjWQlM"
        const val WEB_REMIX_CLIENT_VERSION = "1.20240101.01.00"
        const val ANDROID_MUSIC_CLIENT_VERSION = "6.42.11"
        const val ANDROID_CLIENT_VERSION = "19.09.37"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val WATCH_PAGE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

internal fun extractYtInitialPlayerResponse(html: String): String? {
    val regex = Regex("ytInitialPlayerResponse\\s*=\\s*\\{")
    val match = regex.find(html) ?: return null
    val openIdx = match.range.last

    var depth = 0
    var i = openIdx
    while (i < html.length) {
        when (html[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return html.substring(openIdx, i + 1)
            }
            '"' -> {
                i++
                while (i < html.length) {
                    if (html[i] == '\\') i++
                    else if (html[i] == '"') break
                    i++
                }
            }
        }
        i++
    }
    return null
}
