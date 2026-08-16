package com.resona.music.data.history

import android.content.Context
import android.util.Log
import com.resona.music.domain.model.PlayHistoryEntry
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
 *  Each entry carries the epoch-millis time the play started
 *  ([PlayHistoryEntry.playedAtMillis]); a value of 0 marks a legacy record
 *  that predates timestamps. Separate interface so tests can fake it without
 *  a live Context -- same reasoning as JsEngine (see ExtractorModule). */
internal interface PlayHistoryStore {
    val entries: StateFlow<List<PlayHistoryEntry>>
    suspend fun recordPlay(song: Song)
}

@Singleton
internal class FilePlayHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) : PlayHistoryStore {

    private val indexFile = File(context.filesDir, "play_history.json")
    private val mutex = Mutex()

    private val _entries = MutableStateFlow(readIndex())
    override val entries: StateFlow<List<PlayHistoryEntry>> = _entries.asStateFlow()

    override suspend fun recordPlay(song: Song) {
        mutex.withLock {
            val playedAtMillis = System.currentTimeMillis()
            val entry = PlayHistoryEntry(song = song, playedAtMillis = playedAtMillis)
            val updated = (listOf(entry) + _entries.value.filterNot { it.song.videoId == song.videoId })
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
            _entries.value = updated
        }
    }

    private fun readIndex(): List<PlayHistoryEntry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<PlayHistoryRecord>>(indexFile.readText()).map { it.toDomain() }
        }.getOrElse { e ->
            Log.w(TAG, "readIndex: failed to load $indexFile, starting empty", e)
            emptyList()
        }
    }

    private fun writeIndex(entries: List<PlayHistoryEntry>) {
        indexFile.writeText(Json.encodeToString(entries.map { it.toRecord() }))
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
    // Default 0 keeps old JSON files decodable: kotlinx.serialization treats
    // a property with a default as optional on decode, so records written
    // before this field existed still load, with 0 marking them as legacy.
    val playedAtMillis: Long = 0,
)

private fun PlayHistoryRecord.toDomain() =
    PlayHistoryEntry(
        song = Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration),
        playedAtMillis = playedAtMillis,
    )

private fun PlayHistoryEntry.toRecord() =
    PlayHistoryRecord(
        videoId = song.videoId,
        title = song.title,
        artist = song.artist,
        thumbnailUrl = song.thumbnailUrl,
        duration = song.duration,
        playedAtMillis = playedAtMillis,
    )
