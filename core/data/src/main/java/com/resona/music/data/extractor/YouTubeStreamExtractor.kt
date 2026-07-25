package com.resona.music.data.extractor

import android.util.Log
import com.resona.music.data.extractor.decipher.DecipherService
import com.resona.music.data.extractor.decipher.PlayerJsRepository
import com.resona.music.data.extractor.model.RawPlayerResponse
import com.resona.music.domain.repository.PlaybackUnavailableException
import com.resona.music.domain.repository.StreamSource
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

// Resolves a playable audio stream for a video ID: tries a chain of
// InnerTube client identities until one returns a playable response, then
// deciphers the best audio-only format's URL. Ported from yt-dlp-android
// (see NOTICE.md), trimmed to audio-only and adapted onto Ktor + Resona's
// own exceptions instead of yt-dlp-android's OkHttp/error hierarchy.
internal class YouTubeStreamExtractor @Inject constructor(
    private val client: InnerTubeExtractionClient,
    private val playerJsRepo: PlayerJsRepository,
    private val decipherService: DecipherService,
) {
    // ANDROID_VR first: yt-dlp's own default, no PO token required, returns direct
    // stream URLs as of this writing. The rest are tried in roughly the order
    // yt-dlp-android found most to least likely to work anonymously; WEB is last
    // since it needs a signatureTimestamp and is the most likely to demand a PO token.
    private val clientChain = listOf(
        InnerTubeClientConfig.ANDROID_VR,
        InnerTubeClientConfig.ANDROID,
        InnerTubeClientConfig.TVHTML5_SIMPLY_EMBEDDED,
        InnerTubeClientConfig.IOS,
        InnerTubeClientConfig.MWEB,
        InnerTubeClientConfig.WEB,
    )

    suspend fun resolveStreamUrl(videoId: String): StreamSource {
        // Pre-fetch the player JS URL so WEB/WEB-family clients in the chain can
        // include signatureTimestamp. Non-fatal if it fails -- proceed without it.
        val playerJsUrl = runCatching { playerJsRepo.fetchPlayerJsUrl(videoId) }.getOrNull()
        val signatureTimestamp = playerJsUrl?.let { url ->
            runCatching { playerJsRepo.extractSignatureTimestamp(playerJsRepo.fetchPlayerJs(url)) }
                .getOrNull()
        }

        val (response, winningClient) = fetchWithFallback(videoId, signatureTimestamp, playerJsRepo.currentVisitorData())

        val audioFormats = response.streamingData
            ?.let { it.adaptiveFormats + it.formats }
            ?.filter { it.mimeType.startsWith("audio/") }
            ?.sortedByDescending { it.bitrate }
            ?.takeIf { it.isNotEmpty() }
            ?: throw PlaybackUnavailableException(
                status = "NO_AUDIO_FORMAT",
                reasonText = "No client in the fallback chain returned an audio-only format."
            )

        Log.d(
            TAG,
            "resolveStreamUrl: winningClient=${winningClient.clientName} formats=" +
                audioFormats.joinToString { "itag=${it.itag} br=${it.bitrate} url=${it.url != null} sc=${it.signatureCipher != null}" }
        )

        val resolvedPlayerJsUrl = playerJsUrl
            ?: runCatching { playerJsRepo.fetchPlayerJsUrl(videoId) }.getOrNull()

        // Highest bitrate is usually resolvable, but sometimes comes back with
        // neither url nor signatureCipher (seen with itag 251) -- fall
        // through to the next format instead of failing outright.
        var lastError: Exception? = null
        for (format in audioFormats) {
            try {
                val url = decipherService.buildPlayableUrl(format, resolvedPlayerJsUrl)
                return StreamSource(url = url, userAgent = winningClient.userAgent)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: PlaybackUnavailableException(
            status = "NO_AUDIO_FORMAT",
            reasonText = "No audio format could be resolved to a playable URL."
        )
    }

    private suspend fun fetchWithFallback(
        videoId: String,
        signatureTimestamp: Int?,
        visitorData: String?,
    ): Pair<RawPlayerResponse, InnerTubeClientConfig> {
        var lastStatus = "UNKNOWN"
        var lastReason = "No client in the fallback chain was tried."
        for (config in clientChain) {
            val response = try {
                client.fetchPlayerResponse(videoId, config, signatureTimestamp, visitorData)
            } catch (e: PlaybackUnavailableException) {
                Log.d(TAG, "fetchWithFallback: ${config.clientName} request failed: ${e.reasonText}")
                lastStatus = e.status; lastReason = e.reasonText
                continue
            }
            val status = response.playabilityStatus?.status
            // status OK isn't enough on its own -- YouTube can report a video
            // playable while withholding every format's url/signatureCipher
            // because this client needs a PO token for CDN access. Seen live:
            // ANDROID returns OK with 5/5 formats empty.
            val hasResolvableAudio = response.streamingData
                ?.let { it.adaptiveFormats + it.formats }
                ?.any { it.mimeType.startsWith("audio/") && (it.url != null || it.signatureCipher != null) }
                ?: false
            Log.d(
                TAG,
                "fetchWithFallback: ${config.clientName} status=$status hasResolvableAudio=$hasResolvableAudio"
            )
            if (status == "OK" && hasResolvableAudio) return response to config
            lastStatus = status ?: "UNKNOWN"
            lastReason = if (status == "OK") {
                "This client needs a PO token to unlock playable format URLs."
            } else {
                response.playabilityStatus?.reason ?: "No reason given by InnerTube."
            }
        }
        throw PlaybackUnavailableException(status = lastStatus, reasonText = lastReason)
    }

    private companion object {
        const val TAG = "YouTubeStreamExtractor"
    }
}
