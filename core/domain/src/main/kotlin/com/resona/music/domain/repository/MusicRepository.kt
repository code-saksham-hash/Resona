package com.resona.music.domain.repository

import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.FeaturedPlaylist
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun search(query: String): List<Song>
    suspend fun getStreamSource(videoId: String): StreamSource
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

    /** Downloads [song]'s audio for offline playback. A no-op if it's already downloaded. */
    suspend fun downloadSong(song: Song): DownloadedSong

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
