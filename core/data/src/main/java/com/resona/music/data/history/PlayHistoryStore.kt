package com.resona.music.data.history

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

/** Tracks recently-played songs (most-recent-first, capped), persisted to a
 *  small JSON index -- this is what lets "Recommended For You" adapt to what
 *  the user actually plays instead of always being the same static query.
 *  Separate interface so tests can fake it without a live Context -- same
 *  reasoning as JsEngine (see ExtractorModule). */
internal interface PlayHistoryStore {
    val recentPlays: StateFlow<List<Song>>
    suspend fun recordPlay(song: Song)
}

@Singleton
internal class FilePlayHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) : PlayHistoryStore {

    private val indexFile = File(context.filesDir, "play_history.json")
    private val mutex = Mutex()

    private val _recentPlays = MutableStateFlow(readIndex())
    override val recentPlays: StateFlow<List<Song>> = _recentPlays.asStateFlow()

    override suspend fun recordPlay(song: Song) {
        mutex.withLock {
            val updated = (listOf(song) + _recentPlays.value.filterNot { it.videoId == song.videoId })
                .take(MAX_HISTORY_SIZE)
            // Unlike DownloadedSongsStore/LikedSongsStore, a persistence
            // failure here is swallowed rather than left to propagate: this
            // is passive bookkeeping called from the middle of every play(),
            // not a user-visible action with its own success/failure state,
            // so it should never be able to interrupt actual playback --
            // worst case this one play just isn't remembered for later.
            withContext(Dispatchers.IO) {
                runCatching { writeIndex(updated) }
                    .onFailure { e -> Log.w(TAG, "recordPlay: failed to persist", e) }
            }
            _recentPlays.value = updated
        }
    }

    private fun readIndex(): List<Song> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<PlayHistoryRecord>>(indexFile.readText()).map { it.toDomain() }
        }.getOrElse { e ->
            Log.w(TAG, "readIndex: failed to load $indexFile, starting empty", e)
            emptyList()
        }
    }

    private fun writeIndex(plays: List<Song>) {
        indexFile.writeText(Json.encodeToString(plays.map { it.toRecord() }))
    }

    private companion object {
        const val TAG = "PlayHistoryStore"
        const val MAX_HISTORY_SIZE = 30
    }
}

@Serializable
private data class PlayHistoryRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: String,
)

private fun PlayHistoryRecord.toDomain() =
    Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration)

private fun Song.toRecord() =
    PlayHistoryRecord(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration)
