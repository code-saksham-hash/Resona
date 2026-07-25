package com.resona.music.data.likes

import android.content.Context
import android.util.Log
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

/** Tracks which songs are liked, persisted to a small JSON index so it
 *  survives app restarts. Separate interface so tests can fake it without a
 *  live Context -- same reasoning as JsEngine (see ExtractorModule). */
internal interface LikedSongsStore {
    val likedSongs: StateFlow<List<Song>>
    fun isLiked(videoId: String): Boolean
    suspend fun toggle(song: Song)
}

@Singleton
internal class FileLikedSongsStore @Inject constructor(
    @ApplicationContext context: Context,
) : LikedSongsStore {

    private val indexFile = File(context.filesDir, "liked_songs.json")
    private val mutex = Mutex()

    // Same "read once at construction, everything else is an in-memory
    // lookup" reasoning as FileDownloadedSongsStore.
    private val _likedSongs = MutableStateFlow(readIndex())
    override val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    override fun isLiked(videoId: String): Boolean =
        _likedSongs.value.any { it.videoId == videoId }

    override suspend fun toggle(song: Song) {
        mutex.withLock {
            val isCurrentlyLiked = _likedSongs.value.any { it.videoId == song.videoId }
            val updated = if (isCurrentlyLiked) {
                _likedSongs.value.filterNot { it.videoId == song.videoId }
            } else {
                listOf(song) + _likedSongs.value.filterNot { it.videoId == song.videoId }
            }
            // Written to disk before the in-memory value changes -- a write
            // failure should surface as a failed toggle, not silently revert
            // on the next app restart (see DownloadedSongsStore for the same
            // reasoning, learned the hard way there first).
            withContext(Dispatchers.IO) { writeIndex(updated) }
            _likedSongs.value = updated
            Log.d(TAG, "toggle: ${song.videoId} liked=${!isCurrentlyLiked}, total=${updated.size}")
        }
    }

    private fun readIndex(): List<Song> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<LikedSongRecord>>(indexFile.readText()).map { it.toDomain() }
        }.getOrElse { e ->
            Log.w(TAG, "readIndex: failed to load $indexFile, starting empty", e)
            emptyList()
        }
    }

    private fun writeIndex(songs: List<Song>) {
        indexFile.writeText(Json.encodeToString(songs.map { it.toRecord() }))
    }

    private companion object {
        const val TAG = "LikedSongsStore"
    }
}

@Serializable
private data class LikedSongRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: String,
)

private fun LikedSongRecord.toDomain() =
    Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration)

private fun Song.toRecord() =
    LikedSongRecord(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration)
