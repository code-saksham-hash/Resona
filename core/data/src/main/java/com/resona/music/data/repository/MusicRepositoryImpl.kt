package com.resona.music.data.repository

import com.resona.music.data.extractor.YouTubeStreamExtractor
import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.data.remote.innertube.models.extractSongs
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import com.resona.music.domain.repository.StreamSource
import javax.inject.Inject

// Constructor is internal, not the class itself -- :app needs the type
// (RepositoryModule binds it) but never constructs it directly, Hilt's
// generated factory does that from within :core:data.
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

    // A single WEB_REMIX request comes back UNPLAYABLE anonymously, so this
    // tries a chain of client identities instead -- see YouTubeStreamExtractor.
    override suspend fun getStreamSource(videoId: String): StreamSource =
        streamExtractor.resolveStreamUrl(videoId)
}
