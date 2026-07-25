package com.resona.music.domain.repository

import com.resona.music.domain.model.Song

interface MusicRepository {
    suspend fun search(query: String): List<Song>
    suspend fun getStreamUrl(videoId: String): String
}

/**
 * Thrown when InnerTube reports a video can't currently be played -- e.g.
 * sign-in required, region-locked, or otherwise unavailable. [status] is
 * InnerTube's own playabilityStatus.status value (e.g. "LOGIN_REQUIRED",
 * "UNPLAYABLE") so callers can distinguish failure reasons if they need to.
 */
class PlaybackUnavailableException(
    val status: String,
    val reasonText: String
) : Exception("Playback unavailable ($status): $reasonText")

/**
 * Thrown when the best available audio stream is signature-ciphered.
 * Deciphering it requires parsing YouTube's obfuscated player JavaScript,
 * which changes over time and is deliberately not implemented here -- see
 * how yt-dlp or NewPipeExtractor handle signatureCipher/'n'-parameter
 * decoding if this is needed.
 */
class StreamCipherRequiredException(message: String) : Exception(message)
