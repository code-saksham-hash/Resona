package com.resona.music.data.download

import android.content.Context
import android.util.Log
import com.resona.music.domain.model.DownloadedSong
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

/** Tracks which songs have been downloaded and where their files live, persisted
 *  to a small JSON index so it survives app restarts. Separate interface so
 *  tests can fake it without a live Context -- same reasoning as JsEngine
 *  (see ExtractorModule). */
internal interface DownloadedSongsStore {
    val downloads: StateFlow<List<DownloadedSong>>
    fun filePathFor(videoId: String): String?
    suspend fun markDownloaded(song: Song, filePath: String)

    /** Deletes [videoId]'s downloaded file and removes it from the index. A no-op if it isn't downloaded. */
    suspend fun remove(videoId: String)
}

@Singleton
internal class FileDownloadedSongsStore @Inject constructor(
    @ApplicationContext context: Context,
) : DownloadedSongsStore {

    private val indexFile = File(context.filesDir, "downloaded_songs.json")
    private val mutex = Mutex()

    // The index is a handful of KB at most (a personal downloads list), so
    // reading it once, synchronously, at construction keeps every other
    // access (filePathFor, downloads.value) a plain in-memory lookup instead
    // of every caller needing to be suspend.
    private val _downloads = MutableStateFlow(readIndex())
    override val downloads: StateFlow<List<DownloadedSong>> = _downloads.asStateFlow()

    override fun filePathFor(videoId: String): String? =
        _downloads.value.find { it.song.videoId == videoId }?.filePath

    override suspend fun markDownloaded(song: Song, filePath: String) {
        mutex.withLock {
            val updated = _downloads.value.filterNot { it.song.videoId == song.videoId } +
                DownloadedSong(song, filePath)
            // Written to disk *before* updating the in-memory value, and
            // without swallowing a failure here (unlike readIndex, where a
            // missing/corrupt file is fine to treat as "no downloads yet") --
            // otherwise a write failure would silently look like a
            // successful download that then vanishes on the next app
            // restart, instead of surfacing as the failed download it is.
            withContext(Dispatchers.IO) { writeIndex(updated) }
            _downloads.value = updated
            Log.d(TAG, "markDownloaded: persisted ${updated.size} downloaded song(s)")
        }
    }

    override suspend fun remove(videoId: String) {
        mutex.withLock {
            val toRemove = _downloads.value.find { it.song.videoId == videoId } ?: return
            val updated = _downloads.value - toRemove
            withContext(Dispatchers.IO) {
                writeIndex(updated)
                runCatching { File(toRemove.filePath).delete() }
                    .onFailure { e -> Log.w(TAG, "remove: couldn't delete file ${toRemove.filePath}", e) }
            }
            _downloads.value = updated
            Log.d(TAG, "remove: $videoId, ${updated.size} downloaded song(s) left")
        }
    }

    private fun readIndex(): List<DownloadedSong> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<DownloadedSongRecord>>(indexFile.readText())
                .map { it.toDomain() }
                // A file removed outside the app (cleared storage, etc.)
                // shouldn't keep claiming to be downloaded.
                .filter { File(it.filePath).exists() }
        }.getOrElse { e ->
            Log.w(TAG, "readIndex: failed to load $indexFile, starting empty", e)
            emptyList()
        }
    }

    private fun writeIndex(downloads: List<DownloadedSong>) {
        indexFile.writeText(Json.encodeToString(downloads.map { it.toRecord() }))
    }

    private companion object {
        const val TAG = "DownloadedSongsStore"
    }
}

@Serializable
private data class DownloadedSongRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: String,
    val filePath: String,
)

private fun DownloadedSongRecord.toDomain() = DownloadedSong(
    song = Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration),
    filePath = filePath,
)

private fun DownloadedSong.toRecord() = DownloadedSongRecord(
    videoId = song.videoId,
    title = song.title,
    artist = song.artist,
    thumbnailUrl = song.thumbnailUrl,
    duration = song.duration,
    filePath = filePath,
)
