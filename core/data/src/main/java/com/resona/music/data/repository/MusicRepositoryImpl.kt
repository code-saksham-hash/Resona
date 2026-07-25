package com.resona.music.data.repository

import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.data.remote.innertube.models.AdaptiveFormat
import com.resona.music.data.remote.innertube.models.extractSongs
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import com.resona.music.domain.repository.PlaybackUnavailableException
import com.resona.music.domain.repository.StreamCipherRequiredException
import java.net.URLDecoder
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
                thumbnailUrl = song.thumbnailUrl.highQuality()
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

        return bestAudioFormat.resolvedUrl()
    }

    private fun AdaptiveFormat.resolvedUrl(): String {
        if (url != null) return url
        val cipher = signatureCipher ?: cipher
            ?: throw StreamCipherRequiredException(
                "itag $itag has no url, signatureCipher, or cipher field"
            )
        val params = cipher.split("&").associate { param ->
            val idx = param.indexOf("=")
            if (idx < 0) param to "" else param.substring(0, idx) to URLDecoder.decode(param.substring(idx + 1), "UTF-8")
        }
        val streamUrl = params["url"] ?: throw StreamCipherRequiredException("itag $itag cipher has no url")
        val sp = params["sp"] ?: "signature"
        val sig = params["s"] ?: return streamUrl
        return "$streamUrl&$sp=$sig"
    }
}

private fun String.highQuality(): String {
    if (!contains("googleusercontent.com") && !contains("ytimg.com")) return this
    return replace(Regex("=[a-z]\\d+(-[a-z]\\d+)*([-][a-z]+)?"), "=w720-h720")
}
