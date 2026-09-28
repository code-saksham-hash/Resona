package com.resona.music.data.repository

import android.util.Log
import com.resona.music.data.download.CachedLyrics
import com.resona.music.data.download.DownloadedSongsStore
import com.resona.music.data.download.SongDownloader
import com.resona.music.data.extractor.YouTubeStreamExtractor
import com.resona.music.data.history.PlayHistoryStore
import com.resona.music.data.history.SearchHistoryStore
import com.resona.music.data.likes.LikedSongsStore
import com.resona.music.data.playlists.UserPlaylistsStore
import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.data.remote.innertube.models.InnerTubeAlbumSummary
import com.resona.music.data.remote.innertube.models.InnerTubeArtist
import com.resona.music.data.remote.innertube.models.InnerTubeSong
import com.resona.music.data.remote.innertube.models.extractAlbumHeader
import com.resona.music.data.remote.innertube.models.extractAlbumTracks
import com.resona.music.data.remote.innertube.models.extractArtistDiscographyShelf
import com.resona.music.data.remote.innertube.models.extractArtistHeader
import com.resona.music.data.remote.innertube.models.extractArtists
import com.resona.music.data.remote.innertube.models.extractFeaturedPlaylists
import com.resona.music.data.remote.innertube.models.extractFilteredSongs
import com.resona.music.data.remote.innertube.models.extractLyricsBrowseId
import com.resona.music.data.remote.innertube.models.extractLyricsText
import com.resona.music.data.remote.innertube.models.extractPlaylistSongs
import com.resona.music.data.remote.innertube.models.extractPlaylistTitle
import com.resona.music.data.remote.innertube.models.extractRadioSongs
import com.resona.music.data.remote.innertube.models.extractSearchContinuation
import com.resona.music.data.remote.innertube.models.extractSongs
import com.resona.music.domain.model.Album
import com.resona.music.domain.model.AlbumSummary
import com.resona.music.domain.model.Artist
import com.resona.music.domain.model.ArtistSpotlight
import com.resona.music.domain.model.ArtistSummary
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.FeaturedPlaylist
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.model.HomeFeedSection
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Playlist
import com.resona.music.domain.model.SongSearchPage
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

// Comparing titles and artist names across the song and video shelves has
// to survive the cosmetic differences between them: the catalog track is
// "Blinding Lights" where the artist's own upload of the same recording is
// "Blinding Lights (Official Video)", with casing and punctuation drifting
// either way. Letters are matched as \p{L} rather than a-z so this works on
// the non-Latin scripts a lot of the affected music is titled in.
private val PARENTHETICAL_REGEX = Regex("""[(\[][^)\]]*[)\]]""")
private val NON_ALPHANUMERIC_REGEX = Regex("""[^\p{L}\p{N}]+""")

private fun String.normalizedForMatching(): String =
    lowercase()
        .replace(PARENTHETICAL_REGEX, " ")
        .replace(NON_ALPHANUMERIC_REGEX, " ")
        .trim()

class MusicRepositoryImpl @Inject internal constructor(
    private val api: InnerTubeApi,
    private val streamExtractor: YouTubeStreamExtractor,
    private val songDownloader: SongDownloader,
    private val downloadedSongsStore: DownloadedSongsStore,
    private val likedSongsStore: LikedSongsStore,
    private val playHistoryStore: PlayHistoryStore,
    private val userPlaylistsStore: UserPlaylistsStore,
    private val searchHistoryStore: SearchHistoryStore,
    private val httpClient: HttpClient,
) : MusicRepository {

    override suspend fun search(query: String): List<Song> =
        api.search(query).extractSongs().map { it.toSong() }

    override suspend fun searchSongsPage(query: String): SongSearchPage = coroutineScope {
        // Two separate category shelves for one query, fetched concurrently
        // rather than one after the other. The video shelf is an enrichment
        // on top of the song shelf, so a failure there is swallowed: songs
        // are the primary results and should still land if it goes wrong.
        val songsDeferred = async { api.searchSongs(query) }
        val videosDeferred = async {
            runCatching { api.searchVideos(query).extractFilteredSongs() }.getOrDefault(emptyList())
        }
        val response = songsDeferred.await()
        // distinctBy is defense-in-depth against a single page ever
        // repeating a videoId internally -- not observed live, but the
        // Search screen keys its list by videoId (see SearchResultsList),
        // and a duplicate key there is a hard Compose crash, not just a
        // cosmetic repeat -- worth guarding cheaply regardless.
        val songs = response.extractFilteredSongs().map { it.toSong() }.distinctBy { it.videoId }
        SongSearchPage(
            songs = mergeInVideoUploads(songs, videosDeferred.await().map { it.toSong() }),
            continuationToken = response.extractSearchContinuation(),
        )
    }

    /**
     * Folds a query's Videos-category results into its Songs-category page.
     *
     * The Songs category only covers YouTube Music's own ingested catalog,
     * so a track that exists on YouTube purely as a video upload is missing
     * from search entirely, however far the user pages (see
     * InnerTubeApi.searchVideos, which has the live "asma njk" case this
     * was found through). A lot of regional and independent music is in
     * exactly that position.
     *
     * The catch is that the video shelf is also where every karaoke
     * version, guitar lesson, lyric re-upload and reaction clip lives, and
     * merging it wholesale would wreck results for ordinary queries. What
     * separates the real track from all of that is who uploaded it: the
     * genuine one comes from the artist's own channel, and that artist is
     * already all over the song shelf, while the derivatives come from
     * channels that appear nowhere in it. So a video is kept only when its
     * uploader is an artist this same query has already established. The
     * exception is a query whose song shelf came back empty, where there is
     * no such evidence to be had and the videos are all there is.
     *
     * Kept videos hold the rank the video shelf gave them and are
     * interleaved with the songs rather than appended, because a
     * video-only track is usually the strongest match for the query that
     * went looking for it and appending would bury it under twenty weaker
     * song rows.
     *
     * This runs on the first page only: [loadMoreSongResults] follows the
     * song shelf's continuation chain alone. Paging deeper is someone
     * working through ranked results, and the track this exists to rescue
     * is the one they expected at the top, so it has already either shown
     * up or turned out not to exist.
     */
    private fun mergeInVideoUploads(songs: List<Song>, videos: List<Song>): List<Song> {
        if (videos.isEmpty()) return songs

        val knownArtists = songs.mapTo(mutableSetOf()) { it.artist.normalizedForMatching() }
        val songIds = songs.mapTo(mutableSetOf()) { it.videoId }
        val seenTracks = songs.mapTo(mutableSetOf()) { it.trackKey() }

        // Rank-keyed rather than a flat list so the interleave below can put
        // each survivor back where the video shelf ranked it.
        val keptByRank = mutableMapOf<Int, Song>()
        videos.forEachIndexed { rank, video ->
            val byKnownArtist = songs.isEmpty() ||
                video.artist.normalizedForMatching() in knownArtists
            // An official upload and its catalog counterpart are the same
            // recording under two videoIds, so identity has to be the
            // title/artist pair, not the id. seenTracks.add doing the
            // checking means this also drops a video that repeats one the
            // shelf already offered further up.
            if (byKnownArtist && video.videoId !in songIds && seenTracks.add(video.trackKey())) {
                keptByRank[rank] = video
            }
        }
        if (keptByRank.isEmpty()) return songs

        val merged = ArrayList<Song>(songs.size + keptByRank.size)
        repeat(maxOf(songs.size, videos.size)) { rank ->
            songs.getOrNull(rank)?.let(merged::add)
            keptByRank[rank]?.let(merged::add)
        }
        return merged
    }

    private fun Song.trackKey() = "${title.normalizedForMatching()}|${artist.normalizedForMatching()}"

    override suspend fun loadMoreSongResults(continuationToken: String): SongSearchPage {
        val response = api.searchSongsContinuation(continuationToken)
        return SongSearchPage(
            songs = response.extractFilteredSongs().map { it.toSong() }.distinctBy { it.videoId },
            continuationToken = response.extractSearchContinuation(),
        )
    }

    override suspend fun searchArtists(query: String): List<ArtistSummary> =
        api.search(query).extractArtists().map { it.toArtistSummary() }

    override suspend fun getArtist(browseId: String): Artist {
        val response = api.browse(browseId)
        val header = response.extractArtistHeader()
        return Artist(
            browseId = browseId,
            name = header?.name ?: "",
            thumbnailUrl = header?.thumbnailUrl ?: "",
            description = header?.description ?: "",
            listenerCountText = header?.listenerCountText ?: "",
            // "Top songs" is shaped exactly like a playlist track row (no
            // "Song" type label, since every row here is already known to be
            // one) -- see extractPlaylistSongs()'s own kdoc.
            topSongs = response.extractPlaylistSongs().map { it.toSong() },
            albums = response.extractArtistDiscographyShelf("Albums").map { it.toAlbumSummary() },
            singles = response.extractArtistDiscographyShelf("Singles & EPs").map { it.toAlbumSummary() },
        )
    }

    override suspend fun getAlbum(browseId: String): Album {
        val response = api.browse(browseId)
        val header = response.extractAlbumHeader()
        val coverUrl = header?.thumbnailUrl ?: ""
        return Album(
            browseId = browseId,
            title = header?.title ?: "",
            artistName = header?.artistName ?: "",
            artistBrowseId = header?.artistBrowseId,
            thumbnailUrl = coverUrl,
            year = header?.year ?: "",
            // An album track row has no thumbnail of its own (every track
            // shares the album cover) -- see extractAlbumTracks()'s kdoc.
            songs = response.extractAlbumTracks().map { it.toSong().withFallbackThumbnail(coverUrl) },
        )
    }

    private fun InnerTubeSong.toSong() = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        duration = duration
    )

    private fun Song.withFallbackThumbnail(url: String): Song =
        if (thumbnailUrl.isBlank()) copy(thumbnailUrl = url) else this

    private fun InnerTubeArtist.toArtistSummary() =
        ArtistSummary(browseId = browseId, name = name, thumbnailUrl = thumbnailUrl)

    private fun InnerTubeAlbumSummary.toAlbumSummary() =
        AlbumSummary(browseId = browseId, title = title, thumbnailUrl = thumbnailUrl, year = year)

    // Same as search(), but for callers that already know every result
    // "should" be by a single named artist (right now, just getHomeFeed's
    // recommended section, whose query literally *is* an artist's name) --
    // that's exactly the condition under which InnerTube omits the artist run
    // (see extractSongs()), so a blank result here can be safely attributed
    // to fallbackArtist instead of an unrelated song showing no artist at all.
    private suspend fun search(query: String, fallbackArtist: String?): List<Song> {
        val songs = search(query)
        if (fallbackArtist == null) return songs
        return songs.map { if (it.artist.isBlank()) it.copy(artist = fallbackArtist) else it }
    }

    override suspend fun getStreamSource(videoId: String, excludedClients: Set<String>): StreamSource =
        streamExtractor.resolveStreamUrl(videoId, excludedClients)

    override suspend fun refreshStreamIdentity() = streamExtractor.refreshVisitorIdentity()

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
        val recommended = recommendedQueryFor(playHistoryStore.entries.value)
        val querySpecs = listOf(
            HomeFeedQuery("recommended", "Recommended For You", recommended.query, recommended.artistHint),
            HomeFeedQuery("trending", "Trending Now", trendingQuery),
            HomeFeedQuery("new", "New For You", newQuery),
        )

        val sectionResults = querySpecs.map { spec ->
            async {
                spec to runCatching { search(spec.query, spec.fallbackArtist) }.getOrElse { e ->
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
    private fun recommendedQueryFor(history: List<PlayHistoryEntry>): RecommendedQuery {
        val topArtists = history
            .map { it.song }
            .filter { it.artist.isNotBlank() }
            .groupingBy { it.artist }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(MAX_RECOMMENDED_ARTIST_CANDIDATES)
            .map { "${it.key}$ARTIST_RADIO_SUFFIX" }
        val pool = topArtists.ifEmpty { RECOMMENDED_FALLBACK_QUERIES }
        val query = pickQuery(pool, lastRecommendedQuery).also { lastRecommendedQuery = it }
        // Only the artist-radio pool names a single artist to fall back to --
        // the fallback pool ("today's top hits" etc.) spans many artists at
        // once, so a blank row there is left alone rather than mislabeled.
        val artistHint = query.removeSuffix(ARTIST_RADIO_SUFFIX).takeIf { it != query }
        return RecommendedQuery(query, artistHint)
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

    override fun observePlayHistory(): Flow<List<PlayHistoryEntry>> = playHistoryStore.entries

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

    override suspend fun downloadSong(song: Song, onProgress: (Float) -> Unit): DownloadedSong {
        downloadedSongsStore.filePathFor(song.videoId)?.let { existingPath ->
            Log.d(TAG, "downloadSong: ${song.videoId} already downloaded at $existingPath")
            return DownloadedSong(song, existingPath)
        }
        Log.d(TAG, "downloadSong: resolving stream for ${song.videoId}")
        val streamSource = getStreamSource(song.videoId, excludedClients = emptySet())
        Log.d(TAG, "downloadSong: got stream, fetching bytes for ${song.videoId}")
        val file = songDownloader.download(song, streamSource, onProgress)
        Log.d(TAG, "downloadSong: wrote ${file.absolutePath} (${file.length()} bytes), persisting index")
        downloadedSongsStore.markDownloaded(song, file.absolutePath)

        // Best-effort: lyrics are what make offline playback of a downloaded
        // song fall short otherwise (both getLyrics/getSyncedLyrics are
        // network-only), so fetch and cache them now while a connection is
        // presumably available -- but never let a lyrics failure undo an
        // otherwise-successful download.
        runCatching {
            val plain = runCatching { fetchLyrics(song.videoId) }.getOrNull()
            val synced = runCatching { fetchSyncedLyrics(song.title, song.artist) }.getOrNull()
            downloadedSongsStore.cacheLyrics(song.videoId, plain, synced)
            Log.d(TAG, "downloadSong: cached lyrics for ${song.videoId} (plain=${plain != null}, synced=${synced?.size ?: 0} lines)")
        }.onFailure { e -> Log.w(TAG, "downloadSong: lyrics caching failed for ${song.videoId}", e) }

        return DownloadedSong(song, file.absolutePath)
    }

    override fun observeDownloadedSongs(): Flow<List<DownloadedSong>> = downloadedSongsStore.downloads

    override fun localFileForSong(videoId: String): String? = downloadedSongsStore.filePathFor(videoId)

    override suspend fun deleteDownload(videoId: String) = downloadedSongsStore.remove(videoId)

    override suspend fun toggleLike(song: Song) = likedSongsStore.toggle(song)

    override fun observeLikedSongs(): Flow<List<Song>> = likedSongsStore.likedSongs

    override fun isLiked(videoId: String): Boolean = likedSongsStore.isLiked(videoId)

    override fun observePlaylists(): Flow<List<Playlist>> = userPlaylistsStore.playlists

    override suspend fun createPlaylist(name: String): Playlist = userPlaylistsStore.createPlaylist(name)

    override suspend fun addSongToPlaylist(playlistId: String, song: Song) =
        userPlaylistsStore.addSongToPlaylist(playlistId, song)

    override suspend fun removeSongFromPlaylist(playlistId: String, videoId: String) =
        userPlaylistsStore.removeSongFromPlaylist(playlistId, videoId)

    override suspend fun deletePlaylist(playlistId: String) =
        userPlaylistsStore.deletePlaylist(playlistId)

    override suspend fun importPlaylistFromUrl(url: String): Playlist {
        val playlistId = extractPlaylistId(url)
            ?: throw IllegalArgumentException("That doesn't look like a YouTube playlist link.")
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val response = api.browse(browseId)
        val songs = response.extractPlaylistSongs().map { song ->
            Song(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                duration = song.duration
            )
        }
        // A playlist InnerTube can't actually show anonymously (private,
        // sign-in required, deleted, or just a bad/mistyped id) still comes
        // back HTTP 200 with an empty/absent contents tree rather than a
        // clean error -- verified live -- so an empty song list is the
        // signal to treat this as a failure, not a real 0-track playlist.
        if (songs.isEmpty()) {
            throw IllegalStateException("This playlist is empty, private, or requires signing in to view.")
        }
        val title = response.extractPlaylistTitle()?.takeIf { it.isNotBlank() } ?: "Imported Playlist"
        return userPlaylistsStore.createPlaylist(title, songs)
    }

    // Accepts a playlist share url (youtube.com/playlist?list=..., music.
    // youtube.com/playlist?list=..., youtube.com/watch?v=...&list=...,
    // youtu.be/...?list=...) or a bare playlist id pasted directly.
    // Hand-rolled regex rather than android.net.Uri: this needs to behave
    // the same way in MusicRepositoryImplTest's plain-JVM tests as on-device,
    // and Uri.parse() returns null under this module's isReturnDefaultValues
    // test setting (see core/data/build.gradle.kts) instead of actually
    // parsing anything.
    private fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val fromUrl = PLAYLIST_URL_LIST_PARAM_REGEX.find(trimmed)?.groupValues?.get(1)
        return fromUrl ?: trimmed.takeIf { BARE_PLAYLIST_ID_REGEX.matches(it) }
    }

    override suspend fun getLyrics(videoId: String): String? {
        downloadedSongsStore.cachedLyrics(videoId)?.let { cached ->
            Log.d(TAG, "getLyrics: using cached copy for $videoId")
            return cached.plain
        }
        val downloaded = downloadedSongsStore.downloads.value.find { it.song.videoId == videoId }
        if (downloaded != null) return backfillLyricsCache(downloaded.song).plain
        return fetchLyrics(videoId)
    }

    override suspend fun getSyncedLyrics(videoId: String, title: String, artist: String): List<LyricsLine>? {
        downloadedSongsStore.cachedLyrics(videoId)?.let { cached ->
            Log.d(TAG, "getSyncedLyrics: using cached copy for $videoId")
            return cached.synced
        }
        val downloaded = downloadedSongsStore.downloads.value.find { it.song.videoId == videoId }
        if (downloaded != null) return backfillLyricsCache(downloaded.song).synced
        return fetchSyncedLyrics(title, artist)
    }

    /**
     * A song downloaded before lyrics-caching existed (see [downloadSong])
     * has no [DownloadedSongsStore.cachedLyrics] entry at all yet -- this
     * fetches both plain and synced lyrics for it live, the same as
     * [downloadSong] does at download time, and persists them so the song
     * self-heals into having an offline copy the next time it's viewed
     * (still requires a connection *this* one time). Shared by
     * [getLyrics]/[getSyncedLyrics] rather than caching a lone field from
     * either individually -- [DownloadedSongsStore.cacheLyrics] replaces
     * both fields at once, so caching from only one side would clobber
     * whatever the other side had already found.
     */
    private suspend fun backfillLyricsCache(song: Song): CachedLyrics {
        val plain = runCatching { fetchLyrics(song.videoId) }.getOrNull()
        val synced = runCatching { fetchSyncedLyrics(song.title, song.artist) }.getOrNull()
        runCatching { downloadedSongsStore.cacheLyrics(song.videoId, plain, synced) }
            .onFailure { e -> Log.w(TAG, "backfillLyricsCache: failed to persist for ${song.videoId}", e) }
        return CachedLyrics(plain, synced)
    }

    private suspend fun fetchLyrics(videoId: String): String? {
        val lyricsBrowseId = runCatching { api.next(videoId).extractLyricsBrowseId() }.getOrNull()
        if (lyricsBrowseId == null) {
            Log.d(TAG, "fetchLyrics: no Lyrics tab for $videoId")
            return null
        }
        val lyrics = runCatching { api.browse(lyricsBrowseId).extractLyricsText() }.getOrNull()
        Log.d(TAG, "fetchLyrics: $videoId -> ${if (lyrics != null) "${lyrics.length} chars" else "unavailable"}")
        return lyrics
    }

    private suspend fun fetchSyncedLyrics(title: String, artist: String): List<LyricsLine>? {
        return runCatching {
            val url = "https://lrclib.net/api/get?artist_name=${artist.take(100)}&track_name=${title.take(100)}"
            Log.d(TAG, "fetchSyncedLyrics: fetching from LRCLIB for title=$title artist=$artist")
            val response = httpClient.get(url)
            val body = response.bodyAsText()
            val syncedKey = "\"syncedLyrics\":\""
            val start = body.indexOf(syncedKey)
            if (start == -1) {
                Log.d(TAG, "fetchSyncedLyrics: no syncedLyrics field in response")
                return@runCatching null
            }
            val lrcStart = start + syncedKey.length
            val lrcEnd = body.indexOf("\"", lrcStart)
            if (lrcEnd == -1) {
                Log.d(TAG, "fetchSyncedLyrics: malformed JSON")
                return@runCatching null
            }
            val lrcText = body.substring(lrcStart, lrcEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
            val lines = parseLrc(lrcText)
            Log.d(TAG, "fetchSyncedLyrics: parsed ${lines.size} lines")
            lines
        }.onFailure { e ->
            Log.w(TAG, "fetchSyncedLyrics: failed", e)
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

    override suspend fun getSongRadio(videoId: String): List<Song> =
        api.next(videoId, playlistId = "RDAMVM$videoId").extractRadioSongs().map { song ->
            Song(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                duration = song.duration
            )
        }

    override fun observeSearchHistory(): Flow<List<String>> = searchHistoryStore.recentSearches

    override suspend fun recordSearch(query: String) = searchHistoryStore.record(query)

    override suspend fun removeSearchHistoryEntry(query: String) = searchHistoryStore.remove(query)

    override suspend fun clearSearchHistory() = searchHistoryStore.clear()

    private data class HomeFeedQuery(
        val id: String,
        val title: String,
        val query: String,
        val fallbackArtist: String? = null
    )

    private data class RecommendedQuery(val query: String, val artistHint: String?)

    private companion object {
        const val TAG = "MusicRepositoryImpl"
        const val MAX_POPULAR_ARTISTS = 8
        const val MAX_ARTIST_TOP_SONGS = 20
        const val MAX_RECOMMENDED_ARTIST_CANDIDATES = 3
        const val FEATURED_PLAYLISTS_BROWSE_ID = "FEmusic_home"
        const val ARTIST_RADIO_SUFFIX = " radio"

        // "list=" wins whether it's the first query param (.../playlist?list=PLxxx)
        // or a later one (.../watch?v=xxx&list=PLxxx) -- verified live for both
        // youtube.com and music.youtube.com share links.
        val PLAYLIST_URL_LIST_PARAM_REGEX = Regex("""[?&]list=([\w-]+)""")
        // Fallback for a bare id pasted with no url around it at all.
        val BARE_PLAYLIST_ID_REGEX = Regex("""^[\w-]{10,}$""")

        // One of these is picked at random per section per getHomeFeed() call
        // (see pickQuery). Deliberately spread across genres/moods rather
        // than just synonyms of "popular right now" -- a handful of
        // near-synonymous phrasings (the old pool: "trending music", "viral
        // hits", "chart toppers"...) all rank against essentially the same
        // handful of globally-dominant hits on YouTube's side, so rotating
        // between them barely changed what showed up and the feed read as
        // static. Genre/mood variety is what actually makes two consecutive
        // picks surface different songs.
        val TRENDING_QUERIES = listOf(
            "trending music",
            "trending songs right now",
            "viral hits",
            "chart toppers",
            "trending pop",
            "trending hip hop",
            "trending r&b",
            "trending electronic dance music",
            "trending rock",
            "trending indie",
            "trending k-pop",
            "trending afrobeats",
            "trending latin music",
            "trending country",
        )
        val NEW_QUERIES = listOf(
            "new music releases",
            "new songs this week",
            "latest releases",
            "fresh new tracks",
            "new pop releases",
            "new hip hop releases",
            "new rock releases",
            "new indie releases",
            "new electronic music",
            "new r&b releases",
            "new k-pop releases",
            "new singles this week",
        )
        val RECOMMENDED_FALLBACK_QUERIES = listOf(
            "today's top hits",
            "popular hits right now",
            "feel good hits",
            "chart hits",
            "pop hits",
            "hip hop hits",
            "rock classics",
            "chill indie hits",
            "r&b hits",
            "dance hits",
            "throwback hits",
        )
    }
}
