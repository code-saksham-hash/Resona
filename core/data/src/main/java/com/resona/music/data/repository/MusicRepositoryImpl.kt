package com.resona.music.data.repository

import android.util.Log
import com.resona.music.data.download.DownloadedSongsStore
import com.resona.music.data.download.SongDownloader
import com.resona.music.data.extractor.YouTubeStreamExtractor
import com.resona.music.data.history.PlayHistoryStore
import com.resona.music.data.likes.LikedSongsStore
import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.data.remote.innertube.models.extractFeaturedPlaylists
import com.resona.music.data.remote.innertube.models.extractLyricsBrowseId
import com.resona.music.data.remote.innertube.models.extractLyricsText
import com.resona.music.data.remote.innertube.models.extractPlaylistSongs
import com.resona.music.data.remote.innertube.models.extractSongs
import com.resona.music.domain.model.ArtistSpotlight
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.FeaturedPlaylist
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.model.HomeFeedSection
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import com.resona.music.domain.repository.StreamSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MusicRepositoryImpl @Inject internal constructor(
    private val api: InnerTubeApi,
    private val streamExtractor: YouTubeStreamExtractor,
    private val songDownloader: SongDownloader,
    private val downloadedSongsStore: DownloadedSongsStore,
    private val likedSongsStore: LikedSongsStore,
    private val playHistoryStore: PlayHistoryStore,
    private val httpClient: HttpClient,
) : MusicRepository {

    override suspend fun search(query: String): List<Song> =
        api.search(query).extractSongs().map { song ->
            Song(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                duration = song.duration
            )
        }

    override suspend fun getStreamSource(videoId: String): StreamSource =
        streamExtractor.resolveStreamUrl(videoId)

    // There's no logged-in session anywhere in this app, so InnerTube's own
    // browse/FEmusic_home feed has nothing personalized to return -- tried
    // live, it comes back as two generic playlist shelves plus a "sign in to
    // build your taste profile" prompt, none of which are individually
    // playable songs (they're playlist/mix browseIds, not videoIds -- see
    // getFeaturedPlaylists() below for where those carousels *are* used).
    // Search results are: real InnerTube data, already verified to parse
    // correctly, and directly playable through the exact same pipeline as
    // every other song in the app. So the home feed's own sections are
    // assembled from a handful of curated searches instead -- "Recommended
    // For You" specifically is re-targeted at the user's own most-played
    // artist once recordPlay() has built up some history, rather than
    // always being the same static query (see recommendedQueryFor).
    //
    // Each section's query is *rotated* rather than fixed (see pickQuery
    // below): InnerTube returns essentially the same ranking for the same
    // query text every time, so without this, pull-to-refresh would re-run
    // the identical searches and the screen would look like it never
    // actually refreshed at all.
    override suspend fun getHomeFeed(): HomeFeed = coroutineScope {
        val trendingQuery = pickQuery(TRENDING_QUERIES, lastTrendingQuery).also { lastTrendingQuery = it }
        val newQuery = pickQuery(NEW_QUERIES, lastNewQuery).also { lastNewQuery = it }
        val querySpecs = listOf(
            HomeFeedQuery("recommended", "Recommended For You", recommendedQueryFor(playHistoryStore.recentPlays.value)),
            HomeFeedQuery("trending", "Trending Now", trendingQuery),
            HomeFeedQuery("new", "New For You", newQuery),
        )

        val sectionResults = querySpecs.map { spec ->
            async {
                spec to runCatching { search(spec.query) }.getOrElse { e ->
                    Log.d(TAG, "getHomeFeed: '${spec.query}' failed: ${e.message}")
                    emptyList()
                }
            }
        }.awaitAll()

        val sections = sectionResults.mapNotNull { (spec, songs) ->
            HomeFeedSection(id = spec.id, title = spec.title, songs = songs).takeIf { songs.isNotEmpty() }
        }

        val popularArtists = sectionResults
            .flatMap { (_, songs) -> songs }
            .filter { it.artist.isNotBlank() }
            .distinctBy { it.artist }
            .shuffled()
            .take(MAX_POPULAR_ARTISTS)
            .map { ArtistSpotlight(name = it.artist, thumbnailUrl = it.thumbnailUrl) }

        HomeFeed(sections = sections, popularArtists = popularArtists)
    }

    // Once there's enough history to name favorites, chase *those* instead
    // of a one-size-fits-all chart -- "radio" nudges InnerTube's search
    // toward a mix/related-songs result rather than just that one artist's
    // exact discography repeated every time. Rotated across the user's top 3
    // most-played artists (not always the single #1) for the same reason
    // every other section's query rotates: so a refresh actually looks
    // different instead of repeating the last fetch verbatim.
    private fun recommendedQueryFor(recentPlays: List<Song>): String {
        val topArtists = recentPlays
            .filter { it.artist.isNotBlank() }
            .groupingBy { it.artist }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(MAX_RECOMMENDED_ARTIST_CANDIDATES)
            .map { "${it.key} radio" }
        val pool = topArtists.ifEmpty { RECOMMENDED_FALLBACK_QUERIES }
        return pickQuery(pool, lastRecommendedQuery).also { lastRecommendedQuery = it }
    }

    // Avoids immediately repeating the same query two refreshes in a row
    // (plain random() can still coincidentally repeat, especially from a
    // small pool) -- state lives on this singleton instance, so it persists
    // for the rest of the app session, same lifetime as the search itself.
    private fun pickQuery(pool: List<String>, last: String?): String =
        if (pool.size == 1) pool.first() else pool.filterNot { it == last }.random()

    private var lastRecommendedQuery: String? = null
    private var lastTrendingQuery: String? = null
    private var lastNewQuery: String? = null

    override suspend fun recordPlay(song: Song) = playHistoryStore.recordPlay(song)

    // FEmusic_home's carousels are exactly the "playlist/mix, not individual
    // song" shelves getHomeFeed() above can't use -- but that's precisely
    // what a "Featured Playlists" library section wants.
    override suspend fun getFeaturedPlaylists(): List<FeaturedPlaylist> =
        api.browse(FEATURED_PLAYLISTS_BROWSE_ID).extractFeaturedPlaylists().map { playlist ->
            FeaturedPlaylist(
                browseId = playlist.browseId,
                title = playlist.title,
                subtitle = playlist.subtitle,
                thumbnailUrl = playlist.thumbnailUrl
            )
        }

    override suspend fun getPlaylistSongs(browseId: String): List<Song> =
        api.browse(browseId).extractPlaylistSongs().map { song ->
            Song(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                duration = song.duration
            )
        }

    override suspend fun downloadSong(song: Song): DownloadedSong {
        downloadedSongsStore.filePathFor(song.videoId)?.let { existingPath ->
            Log.d(TAG, "downloadSong: ${song.videoId} already downloaded at $existingPath")
            return DownloadedSong(song, existingPath)
        }
        Log.d(TAG, "downloadSong: resolving stream for ${song.videoId}")
        val streamSource = getStreamSource(song.videoId)
        Log.d(TAG, "downloadSong: got stream, fetching bytes for ${song.videoId}")
        val file = songDownloader.download(song, streamSource)
        Log.d(TAG, "downloadSong: wrote ${file.absolutePath} (${file.length()} bytes), persisting index")
        downloadedSongsStore.markDownloaded(song, file.absolutePath)
        return DownloadedSong(song, file.absolutePath)
    }

    override fun observeDownloadedSongs(): Flow<List<DownloadedSong>> = downloadedSongsStore.downloads

    override fun localFileForSong(videoId: String): String? = downloadedSongsStore.filePathFor(videoId)

    override suspend fun deleteDownload(videoId: String) = downloadedSongsStore.remove(videoId)

    override suspend fun toggleLike(song: Song) = likedSongsStore.toggle(song)

    override fun observeLikedSongs(): Flow<List<Song>> = likedSongsStore.likedSongs

    override fun isLiked(videoId: String): Boolean = likedSongsStore.isLiked(videoId)

    override suspend fun getLyrics(videoId: String): String? {
        val lyricsBrowseId = runCatching { api.next(videoId).extractLyricsBrowseId() }.getOrNull()
        if (lyricsBrowseId == null) {
            Log.d(TAG, "getLyrics: no Lyrics tab for $videoId")
            return null
        }
        val lyrics = runCatching { api.browse(lyricsBrowseId).extractLyricsText() }.getOrNull()
        Log.d(TAG, "getLyrics: $videoId -> ${if (lyrics != null) "${lyrics.length} chars" else "unavailable"}")
        return lyrics
    }

    override suspend fun getSyncedLyrics(title: String, artist: String): List<LyricsLine>? {
        return runCatching {
            val url = "https://lrclib.net/api/get?artist_name=${artist.take(100)}&track_name=${title.take(100)}"
            Log.d(TAG, "getSyncedLyrics: fetching from LRCLIB for title=$title artist=$artist")
            val response = httpClient.get(url)
            val body = response.bodyAsText()
            val syncedKey = "\"syncedLyrics\":\""
            val start = body.indexOf(syncedKey)
            if (start == -1) {
                Log.d(TAG, "getSyncedLyrics: no syncedLyrics field in response")
                return@runCatching null
            }
            val lrcStart = start + syncedKey.length
            val lrcEnd = body.indexOf("\"", lrcStart)
            if (lrcEnd == -1) {
                Log.d(TAG, "getSyncedLyrics: malformed JSON")
                return@runCatching null
            }
            val lrcText = body.substring(lrcStart, lrcEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            val lines = parseLrc(lrcText)
            Log.d(TAG, "getSyncedLyrics: parsed ${lines.size} lines")
            lines
        }.onFailure { e ->
            Log.w(TAG, "getSyncedLyrics: failed", e)
        }.getOrNull()
    }

    private fun parseLrc(lrcText: String): List<LyricsLine> {
        val pattern = Regex("""\[(\d+):(\d+(?:\.\d+)?)\](.*)""")
        return lrcText.lines().mapNotNull { line ->
            pattern.find(line)?.let { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@let null
                val rawSeconds = match.groupValues[2]
                val millis = if (rawSeconds.contains('.')) {
                    val parts = rawSeconds.split(".")
                    val secs = parts[0].toLongOrNull() ?: return@let null
                    val frac = parts.getOrNull(1)?.let { it.take(3).padEnd(3, '0').toLongOrNull() } ?: 0L
                    secs * 1000L + frac
                } else {
                    rawSeconds.toLongOrNull()?.let { it * 1000L } ?: return@let null
                }
                val text = match.groupValues[3].trim()
                if (text.isBlank()) return@let null
                LyricsLine(timestamp = minutes * 60_000L + millis, text = text)
            }
        }
    }

    override suspend fun getTopSongsForArtist(artistName: String): List<Song> {
        val results = search(artistName)
        // Search for an artist's name also returns other artists' songs that
        // merely feature/reference them -- their own songs (exact artist
        // match) are what "top songs for this artist" actually means, so
        // those come first; anything else only fills out the rest.
        val (ownSongs, other) = results.partition { it.artist.equals(artistName, ignoreCase = true) }
        return (ownSongs + other).distinctBy { it.videoId }.take(MAX_ARTIST_TOP_SONGS)
    }

    private data class HomeFeedQuery(val id: String, val title: String, val query: String)

    private companion object {
        const val TAG = "MusicRepositoryImpl"
        const val MAX_POPULAR_ARTISTS = 8
        const val MAX_ARTIST_TOP_SONGS = 5
        const val MAX_RECOMMENDED_ARTIST_CANDIDATES = 3
        const val FEATURED_PLAYLISTS_BROWSE_ID = "FEmusic_home"

        // One of these is picked at random per section per getHomeFeed()
        // call (see pickQuery) -- plain synonyms/rephrasings of the same
        // idea, not different concepts, so every pick is still a reasonable
        // answer to "what's trending" / "what's new" / "what do I like".
        val TRENDING_QUERIES = listOf(
            "trending music",
            "trending songs right now",
            "viral hits",
            "what's trending",
            "chart toppers",
        )
        val NEW_QUERIES = listOf(
            "new music releases",
            "new songs this week",
            "latest releases",
            "fresh new tracks",
            "new singles",
        )
        val RECOMMENDED_FALLBACK_QUERIES = listOf(
            "today's top hits",
            "popular hits right now",
            "feel good hits",
            "chart hits",
        )
    }
}
