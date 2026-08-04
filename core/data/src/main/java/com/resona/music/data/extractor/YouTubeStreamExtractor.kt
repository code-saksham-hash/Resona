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
        // Seed a stable visitor identity without blocking: the first attempt
        // goes out with whatever token is already cached, and if it gets gated
        // (every client in the chain returns no playable audio -- the "PO token
        // needed" pattern) while the chain revealed a fresh token, the chain is
        // retried once with it. Anonymous player requests without a visitor
        // identity get gated noticeably faster. The watch-page fetch is a
        // best-effort fallback source here, but the reliable one is the
        // visitorData YouTube itself hands back inside gated responses.
        val initialVisitor = playerJsRepo.currentVisitorData()
        val visitorFetch = playerJsRepo.ensureVisitorData(videoId)

        val (response, winningClient) = try {
            fetchWithFallback(videoId, initialVisitor)
        } catch (e: PlaybackUnavailableException) {
            val learned = playerJsRepo.currentVisitorData()
            val token = when {
                learned != null && learned != initialVisitor -> learned
                learned == null && initialVisitor == null -> visitorFetch.await()
                else -> null
            }
            if (token == null) throw e
            Log.d(TAG, "resolveStreamUrl: first attempt gated, retrying chain with a fresh visitor token")
            fetchWithFallback(videoId, token)
        }

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

        // Direct urls are the norm; a player JS is only fetched (best-effort)
        // the rare times a winning client hands back a signature-ciphered
        // format.
        val resolvedPlayerJsUrl = if (audioFormats.any { it.url == null }) {
            runCatching { playerJsRepo.fetchPlayerJsUrl(videoId) }.getOrNull()
        } else null

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
        visitorData: String?,
    ): Pair<RawPlayerResponse, InnerTubeClientConfig> {
        var lastStatus = "UNKNOWN"
        var lastReason = "No client in the fallback chain was tried."
        // A client can be gated for lacking a visitor identity yet still hand
        // one back in responseContext.visitorData -- learn it and reuse it for
        // the rest of the chain instead of waiting for the next attempt.
        var visitor = visitorData
        // The first client (ANDROID_VR) is the one whose direct URLs are known
        // to play through Resona's player stack. If it gets gated on the very
        // first attempt but reveals a fresh visitor, retry it once with that
        // visitor before settling for a fallback client: fallback clients can
        // hand back resolvable URLs that the player's HTTP stack still rejects
        // (403, surfaced as "Source error"). Retried at most once per resolve.
        var firstClientRetriedWithLearnedVisitor = false
        // Crawled lazily, only the first time a client that needs a
        // signatureTimestamp (WEB) is actually reached -- the fast-path clients
        // above it handle the common case and never touch the watch page or
        // the multi-MB player JS.
        var playerJsUrl: String? = null
        var signatureTimestamp: Int? = null
        val firstClient = clientChain.first()
        for (config in clientChain) {
            if (config.includeSignatureTimestamp && signatureTimestamp == null && playerJsUrl == null) {
                playerJsUrl = runCatching { playerJsRepo.fetchPlayerJsUrl(videoId) }.getOrNull()
                signatureTimestamp = playerJsUrl?.let { url ->
                    runCatching { playerJsRepo.extractSignatureTimestamp(playerJsRepo.fetchPlayerJs(url)) }
                        .getOrNull()
                }
            }
            val response = try {
                client.fetchPlayerResponse(videoId, config, signatureTimestamp, visitor)
            } catch (e: PlaybackUnavailableException) {
                Log.d(TAG, "fetchWithFallback: ${config.clientName} request failed: ${e.reasonText}")
                lastStatus = e.status; lastReason = e.reasonText
                continue
            }
            response.responseContext?.visitorData?.takeIf { it.isNotBlank() }?.let {
                if (it != visitor) {
                    Log.d(TAG, "fetchWithFallback: ${config.clientName} returned visitorData, reusing it for the chain")
                    playerJsRepo.seedVisitorData(it)
                    visitor = it
                }
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
            if (config == firstClient && !firstClientRetriedWithLearnedVisitor && visitor != visitorData) {
                firstClientRetriedWithLearnedVisitor = true
                Log.d(
                    TAG,
                    "fetchWithFallback: $firstClient got gated but revealed a visitor, retrying it once with it"
                )
                val retried = try {
                    client.fetchPlayerResponse(videoId, firstClient, signatureTimestamp, visitor)
                } catch (e: PlaybackUnavailableException) {
                    Log.d(TAG, "fetchWithFallback: $firstClient retry request failed: ${e.reasonText}")
                    lastStatus = e.status; lastReason = e.reasonText
                    continue
                }
                val retriedStatus = retried.playabilityStatus?.status
                val retriedHasAudio = retried.streamingData
                    ?.let { it.adaptiveFormats + it.formats }
                    ?.any { it.mimeType.startsWith("audio/") && (it.url != null || it.signatureCipher != null) }
                    ?: false
                Log.d(
                    TAG,
                    "fetchWithFallback: $firstClient retry status=$retriedStatus hasResolvableAudio=$retriedHasAudio"
                )
                if (retriedStatus == "OK" && retriedHasAudio) return retried to firstClient
            }
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
