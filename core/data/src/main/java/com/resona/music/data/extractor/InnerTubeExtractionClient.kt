package com.resona.music.data.extractor

import com.resona.music.data.extractor.model.RawPlayerResponse
import com.resona.music.domain.repository.PlaybackUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

// Posts a player request for one InnerTubeClientConfig and parses the
// response. Separate from InnerTubeApi (which still owns search) since this
// needs per-client headers the WEB_REMIX-only search flow never did. From
// yt-dlp-android's InnerTubeClient (see NOTICE.md).
//
// visitorData gets sent on every client here, not just WEB like the
// original -- anonymous requests without a stable visitor token get
// throttled to LOGIN_REQUIRED noticeably faster in practice.
internal class InnerTubeExtractionClient @Inject constructor(private val httpClient: HttpClient) {

    suspend fun fetchPlayerResponse(
        videoId: String,
        config: InnerTubeClientConfig,
        signatureTimestamp: Int? = null,
        visitorData: String? = null,
    ): RawPlayerResponse {
        val response = try {
            httpClient.post(config.playerApiUrl()) {
                contentType(ContentType.Application.Json)
                header("User-Agent", config.userAgent)
                header("X-YouTube-Client-Name", config.clientNumber)
                header("X-YouTube-Client-Version", config.clientVersion)
                header("Origin", "https://www.youtube.com")
                header("Referer", "https://www.youtube.com/")
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                setBody(config.buildRequestBody(videoId, signatureTimestamp, visitorData))
            }
        } catch (e: Exception) {
            throw PlaybackUnavailableException(
                status = "NETWORK_ERROR",
                reasonText = e.message ?: "Request to InnerTube failed"
            )
        }
        return response.body()
    }
}
