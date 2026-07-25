package com.resona.music.data.repository

import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.data.remote.innertube.models.extractSongs
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import com.resona.music.domain.repository.PlaybackUnavailableException
import com.resona.music.domain.repository.StreamCipherRequiredException
import javax.inject.Inject

class MusicRepositoryImpl @Inject constructor(
    private val api: InnerTubeApi
) : MusicRepository {

    override suspend fun search(query: String): List<Song> =
        api.search(query).extractSongs().map { song ->
            Song(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl
            )
        }

    override suspend fun getStreamUrl(videoId: String): String {
        val response = api.getPlayerResponse(videoId)

        val status = response.playabilityStatus
        if (status?.status != "OK") {
            throw PlaybackUnavailableException(
                status = status?.status ?: "UNKNOWN",
                reasonText = status?.reason ?: "No reason given by InnerTube."
            )
        }

        val bestAudioFormat = response.streamingData?.adaptiveFormats
            ?.filter { it.mimeType?.startsWith("audio/") == true }
            ?.maxByOrNull { it.bitrate ?: 0 }
            ?: throw PlaybackUnavailableException(
                status = "NO_AUDIO_FORMAT",
                reasonText = "InnerTube returned no audio-only adaptive format for this video."
            )

        return bestAudioFormat.url ?: throw StreamCipherRequiredException(
            "itag ${bestAudioFormat.itag} is signature-ciphered. Deciphering it requires " +
                "parsing YouTube's obfuscated player JavaScript, which is not implemented in " +
                "this minimal client."
        )
    }
}
