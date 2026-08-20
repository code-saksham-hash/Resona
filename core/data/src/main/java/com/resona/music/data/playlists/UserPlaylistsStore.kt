package com.resona.music.data.playlists

import android.content.Context
import android.util.Log
import com.resona.music.domain.model.Playlist
import com.resona.music.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks playlists the user created on-device, persisted to a small JSON
 *  index so they survive app restarts. Separate interface so tests can fake
 *  it without a live Context -- same reasoning as LikesModule/LikedSongsStore. */
internal interface UserPlaylistsStore {
    val playlists: StateFlow<List<Playlist>>
    suspend fun createPlaylist(name: String, songs: List<Song> = emptyList()): Playlist

    /** Appends [song] to [playlistId]'s song list. A no-op if it's already
     *  in that playlist, or if [playlistId] doesn't match any playlist. */
    suspend fun addSongToPlaylist(playlistId: String, song: Song)
}

@Singleton
internal class FileUserPlaylistsStore @Inject constructor(
    @ApplicationContext context: Context,
) : UserPlaylistsStore {

    private val indexFile = File(context.filesDir, "user_playlists.json")
    private val mutex = Mutex()

    private val _playlists = MutableStateFlow(readIndex())
    override val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    override suspend fun createPlaylist(name: String, songs: List<Song>): Playlist {
        val now = System.currentTimeMillis()
        val playlist = Playlist(id = "playlist_$now", name = name.trim(), createdAtMillis = now, songs = songs)
        mutex.withLock {
            val updated = listOf(playlist) + _playlists.value
            withContext(Dispatchers.IO) { writeIndex(updated) }
            _playlists.value = updated
            Log.d(TAG, "createPlaylist: '${playlist.name}' total=${updated.size} songs=${songs.size}")
        }
        return playlist
    }

    override suspend fun addSongToPlaylist(playlistId: String, song: Song) {
        mutex.withLock {
            val target = _playlists.value.find { it.id == playlistId } ?: run {
                Log.w(TAG, "addSongToPlaylist: no playlist with id=$playlistId")
                return
            }
            if (target.songs.any { it.videoId == song.videoId }) return
            val updated = _playlists.value.map {
                if (it.id == playlistId) it.copy(songs = it.songs + song) else it
            }
            withContext(Dispatchers.IO) { writeIndex(updated) }
            _playlists.value = updated
            Log.d(TAG, "addSongToPlaylist: added '${song.title}' to '${target.name}'")
        }
    }

    private fun readIndex(): List<Playlist> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<PlaylistRecord>>(indexFile.readText()).map { it.toDomain() }
        }.getOrElse { e ->
            Log.w(TAG, "readIndex: failed to load $indexFile, starting empty", e)
            emptyList()
        }
    }

    private fun writeIndex(playlists: List<Playlist>) {
        indexFile.writeText(Json.encodeToString(playlists.map { it.toRecord() }))
    }

    private companion object {
        const val TAG = "UserPlaylistsStore"
    }
}

@Serializable
private data class PlaylistRecord(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val songs: List<PlaylistSongRecord> = emptyList(),
)

@Serializable
private data class PlaylistSongRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: String,
)

private fun PlaylistRecord.toDomain() =
    Playlist(id = id, name = name, createdAtMillis = createdAtMillis, songs = songs.map { it.toDomain() })

private fun Playlist.toRecord() =
    PlaylistRecord(id = id, name = name, createdAtMillis = createdAtMillis, songs = songs.map { it.toRecord() })

private fun PlaylistSongRecord.toDomain() =
    Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration)

private fun Song.toRecord() =
    PlaylistSongRecord(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration)