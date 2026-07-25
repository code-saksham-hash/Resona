package com.resona.music.domain.repository

import com.resona.music.domain.model.Song

interface MusicRepository {
    suspend fun search(query: String): List<Song>
    suspend fun getStreamSource(videoId: String): StreamSource
}

// A bare url isn't enough -- the CDN rejects requests that don't come from
// the same client identity (userAgent) that resolved it.
data class StreamSource(
    val url: String,
    val userAgent: String,
)

// InnerTube reported a video as unplayable right now -- sign-in required,
// region-locked, etc. status is InnerTube's own playabilityStatus.status.
class PlaybackUnavailableException(
    val status: String,
    val reasonText: String
) : Exception("Playback unavailable ($status): $reasonText")

class StreamCipherRequiredException(message: String) : Exception(message)
