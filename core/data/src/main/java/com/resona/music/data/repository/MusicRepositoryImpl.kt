package com.resona.music.data.repository

import com.resona.music.data.extractor.YouTubeStreamExtractor
import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.data.remote.innertube.models.extractSongs
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import com.resona.music.domain.repository.StreamSource
import javax.inject.Inject

class MusicRepositoryImpl @Inject internal constructor(
    private val api: InnerTubeApi,
    private val streamExtractor: YouTubeStreamExtractor,
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

    override suspend fun getStreamSource(videoId: String): StreamSource =
        streamExtractor.resolveStreamUrl(videoId)
}
