package com.resona.music.domain.repository

import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.FeaturedPlaylist
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Playlist
import com.resona.music.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun search(query: String): List<Song>
    /** [excludedClients] (InnerTube client names, e.g. "ANDROID_VR") are skipped in the
     *  fallback chain -- for a retry after the CDN itself rejected a previous resolution's
     *  url, not a resolve-time failure (see [StreamSource.clientName]). */
    suspend fun getStreamSource(videoId: String, excludedClients: Set<String> = emptySet()): StreamSource
    suspend fun getHomeFeed(): HomeFeed

    /** Records that [song] was played -- what lets getHomeFeed()'s "Recommended
     *  For You" section drift toward the user's own listening over time. */
    suspend fun recordPlay(song: Song)

    /** The on-device play history, most-recent-first (capped at 30), including the play timestamp. Feeds the Stats and History screens. */
    fun observePlayHistory(): Flow<List<PlayHistoryEntry>>

    /** Real YouTube Music playlists/mixes to show on the Library screen. */
    suspend fun getFeaturedPlaylists(): List<FeaturedPlaylist>

    /** The songs inside the playlist identified by [browseId] (see [FeaturedPlaylist.browseId]). */
    suspend fun getPlaylistSongs(browseId: String): List<Song>

    /** Downloads [song]'s audio for offline playback. A no-op if it's already
     *  downloaded. [onProgress] receives the download fraction (0f..1f); it's
     *  never called when the server omits a Content-Length. */
    suspend fun downloadSong(song: Song, onProgress: (Float) -> Unit = {}): DownloadedSong

    /** All currently-downloaded songs, most-recently-downloaded first. */
    fun observeDownloadedSongs(): Flow<List<DownloadedSong>>

    /** The on-disk path for [videoId]'s downloaded audio, or null if it hasn't been downloaded. */
    fun localFileForSong(videoId: String): String?

    /** Deletes [videoId]'s downloaded file. A no-op if it isn't downloaded. */
    suspend fun deleteDownload(videoId: String)

    /** Adds [song] to liked songs, or removes it if it's already liked. */
    suspend fun toggleLike(song: Song)

    /** All currently-liked songs, most-recently-liked first. */
    fun observeLikedSongs(): Flow<List<Song>>

    /** Whether [videoId] is currently liked. */
    fun isLiked(videoId: String): Boolean

    /** User-created playlists, most-recently-created first. */
    fun observePlaylists(): Flow<List<Playlist>>

    /** Creates a new, empty playlist named [name] (trimmed) and returns it. */
    suspend fun createPlaylist(name: String): Playlist

    /**
     * Imports a YouTube/YouTube Music playlist from its share [url] (a
     * youtube.com/playlist, music.youtube.com/playlist, watch?v=...&list=...,
     * or youtu.be link -- or a bare playlist id pasted directly) as a new
     * on-device playlist, named after and pre-populated with the source
     * playlist's songs. Throws if [url] doesn't contain a recognizable
     * playlist id, or if InnerTube can't browse it (private, requires
     * signing in, deleted).
     */
    suspend fun importPlaylistFromUrl(url: String): Playlist

    /** [videoId]'s lyrics, or null if InnerTube doesn't have/expose any for it. */
    suspend fun getLyrics(videoId: String): String?

    /** Timed/synced lyrics for [title] by [artist] via LRCLIB, or null if unavailable. */
    suspend fun getSyncedLyrics(title: String, artist: String): List<LyricsLine>?

    /**
     * Up to 5 songs by [artistName]. There's no logged-in session (see
     * getHomeFeed's kdoc) so this can't be a real per-listener "most played"
     * ranking -- it's [artistName] run back through the same search already
     * used everywhere else, which surfaces YouTube Music's own relevance
     * ranking for that artist instead.
     */
    suspend fun getTopSongsForArtist(artistName: String): List<Song>

    /** The similar-songs mix YouTube Music builds around [videoId] (its
     *  "song radio"). Backs the auto-built queue that starts playing a
     *  single tapped track (see PlayerViewModel) -- the tapped song itself
     *  is included as the first entry. */
    suspend fun getSongRadio(videoId: String): List<Song>

    /** Explicitly-submitted search queries, most-recently-submitted first. */
    fun observeSearchHistory(): Flow<List<String>>

    /**
     * Records [query] as a submitted search -- call once per explicit
     * submission (the keyboard's search action, tapping a history entry),
     * never on every keystroke of a live query. Re-submitting a query
     * already in history (case-insensitively) just moves it back to the
     * front instead of duplicating it.
     */
    suspend fun recordSearch(query: String)

    /** Removes a single entry from search history. */
    suspend fun removeSearchHistoryEntry(query: String)

    /** Clears search history entirely. */
    suspend fun clearSearchHistory()
}

// A bare url isn't enough -- the CDN rejects requests that don't come from
// the same client identity (userAgent) that resolved it. clientName is the
// InnerTube client (e.g. "ANDROID_VR") that resolved this url -- a caller
// retrying after the CDN itself rejects the url (not a resolve-time
// failure) needs it to exclude that client next time, since a client that
// self-reports a format as resolvable can still have the CDN flatly 403 it,
// and re-resolving without excluding it just lands on the same client and
// the same doomed url again.
data class StreamSource(
    val url: String,
    val userAgent: String,
    val clientName: String,
)

// InnerTube reported a video as unplayable right now -- sign-in required,
// region-locked, etc. status is InnerTube's own playabilityStatus.status.
class PlaybackUnavailableException(
    val status: String,
    val reasonText: String
) : Exception("Playback unavailable ($status): $reasonText")

class StreamCipherRequiredException(message: String) : Exception(message)
