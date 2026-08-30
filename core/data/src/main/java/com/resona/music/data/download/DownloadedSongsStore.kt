package com.resona.music.data.download

import android.content.Context
import android.util.Log
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.LyricsLine
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

/** Lyrics fetched once at download time (see [MusicRepository.downloadSong]/
 *  `MusicRepositoryImpl`) so a downloaded song has them available with no
 *  network at all. Either field alone can be non-null; both null means the
 *  fetch ran and genuinely found nothing (still a cache hit -- distinct from
 *  no entry existing at all, which is what a null [DownloadedSongsStore.cachedLyrics]
 *  return means). */
internal data class CachedLyrics(val plain: String?, val synced: List<LyricsLine>?)

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

    /** [videoId]'s cached lyrics, or null if it isn't downloaded or lyrics were never cached for it. */
    fun cachedLyrics(videoId: String): CachedLyrics?

    /** Best-effort persists [plain]/[synced] alongside [videoId]'s download record. A no-op if [videoId] isn't downloaded. */
    suspend fun cacheLyrics(videoId: String, plain: String?, synced: List<LyricsLine>?)
}

@Singleton
internal class FileDownloadedSongsStore @Inject constructor(
    @ApplicationContext context: Context,
) : DownloadedSongsStore {

    private val indexFile = File(context.filesDir, "downloaded_songs.json")
    private val mutex = Mutex()

    // The index is a handful of KB at most (a personal downloads list), so
    // reading it once, synchronously, at construction keeps every other
    // access (filePathFor, downloads.value, cachedLyrics) a plain in-memory
    // lookup instead of every caller needing to be suspend.
    private val initialRecords = readRecords()

    private val _downloads = MutableStateFlow(initialRecords.map { it.toDomain() })
    override val downloads: StateFlow<List<DownloadedSong>> = _downloads.asStateFlow()

    // Kept separately from `downloads` rather than folded into the public
    // DownloadedSong model -- nothing outside this store and
    // MusicRepositoryImpl needs to know a download carries cached lyrics.
    // @Volatile for the same reason _downloads is a StateFlow: cachedLyrics()
    // can be read from a different thread than the one that last wrote it.
    @Volatile
    private var lyricsById: Map<String, CachedLyrics> = initialRecords.associate { record ->
        record.videoId to CachedLyrics(record.plainLyrics, record.syncedLyrics?.map { LyricsLine(it.timestamp, it.text) })
    }

    override fun filePathFor(videoId: String): String? =
        _downloads.value.find { it.song.videoId == videoId }?.filePath

    override fun cachedLyrics(videoId: String): CachedLyrics? = lyricsById[videoId]

    override suspend fun markDownloaded(song: Song, filePath: String) {
        mutex.withLock {
            val updated = _downloads.value.filterNot { it.song.videoId == song.videoId } +
                DownloadedSong(song, filePath)
            // Written to disk *before* updating the in-memory value, and
            // without swallowing a failure here (unlike readRecords, where a
            // missing/corrupt file is fine to treat as "no downloads yet") --
            // otherwise a write failure would silently look like a
            // successful download that then vanishes on the next app
            // restart, instead of surfacing as the failed download it is.
            withContext(Dispatchers.IO) { writeIndex(updated) }
            _downloads.value = updated
            Log.d(TAG, "markDownloaded: persisted ${updated.size} downloaded song(s)")
        }
    }

    override suspend fun cacheLyrics(videoId: String, plain: String?, synced: List<LyricsLine>?) {
        mutex.withLock {
            if (_downloads.value.none { it.song.videoId == videoId }) return
            lyricsById = lyricsById + (videoId to CachedLyrics(plain, synced))
            withContext(Dispatchers.IO) { writeIndex(_downloads.value) }
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
            lyricsById = lyricsById - videoId
            Log.d(TAG, "remove: $videoId, ${updated.size} downloaded song(s) left")
        }
    }

    private fun readRecords(): List<DownloadedSongRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<DownloadedSongRecord>>(indexFile.readText())
                // A file removed outside the app (cleared storage, etc.)
                // shouldn't keep claiming to be downloaded.
                .filter { File(it.filePath).exists() }
        }.getOrElse { e ->
            Log.w(TAG, "readRecords: failed to load $indexFile, starting empty", e)
            emptyList()
        }
    }

    private fun writeIndex(downloads: List<DownloadedSong>) {
        val records = downloads.map { it.toRecord(lyricsById[it.song.videoId]) }
        indexFile.writeText(Json.encodeToString(records))
    }

    private companion object {
        const val TAG = "DownloadedSongsStore"
    }
}

@Serializable
private data class SyncedLyricsLineRecord(val timestamp: Long, val text: String)

@Serializable
private data class DownloadedSongRecord(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: String,
    val filePath: String,
    val plainLyrics: String? = null,
    val syncedLyrics: List<SyncedLyricsLineRecord>? = null,
)

private fun DownloadedSongRecord.toDomain() = DownloadedSong(
    song = Song(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration),
    filePath = filePath,
)

private fun DownloadedSong.toRecord(lyrics: CachedLyrics?) = DownloadedSongRecord(
    videoId = song.videoId,
    title = song.title,
    artist = song.artist,
    thumbnailUrl = song.thumbnailUrl,
    duration = song.duration,
    filePath = filePath,
    plainLyrics = lyrics?.plain,
    syncedLyrics = lyrics?.synced?.map { SyncedLyricsLineRecord(it.timestamp, it.text) },
)
