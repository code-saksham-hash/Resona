package com.resona.music.data.podcasts

import android.content.Context
import android.util.Log
import com.resona.music.domain.model.EpisodeProgress
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Listening position per episode, written every few seconds while one plays. */
internal interface EpisodeProgressStore {
    val progress: StateFlow<Map<String, EpisodeProgress>>
    suspend fun save(progress: EpisodeProgress)
}

@Singleton
internal class FileEpisodeProgressStore @Inject constructor(
    @ApplicationContext context: Context,
) : EpisodeProgressStore {

    private val file = File(context.filesDir, "podcast_progress.json")
    private val mutex = Mutex()

    private val _progress = MutableStateFlow(read())
    override val progress: StateFlow<Map<String, EpisodeProgress>> = _progress.asStateFlow()

    override suspend fun save(progress: EpisodeProgress) {
        mutex.withLock {
            val updated = (_progress.value + (progress.song.videoId to progress))
                .values
                .sortedByDescending { it.updatedAtMillis }
                .take(MAX_ENTRIES)
                .associateBy { it.song.videoId }
            withContext(Dispatchers.IO) { file.writeText(Json.encodeToString(updated.values.map { it.toRecord() })) }
            _progress.value = updated
        }
    }

    private fun read(): Map<String, EpisodeProgress> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<ProgressRecord>>(file.readText())
                .map { it.toDomain() }
                .associateBy { it.song.videoId }
        }.getOrElse { e ->
            Log.w(TAG, "read: couldn't load $file, starting empty", e)
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "EpisodeProgressStore"
        // Plenty for Continue listening and played markers without the file growing forever.
        const val MAX_ENTRIES = 300
    }
}

@Serializable
private data class ProgressRecord(
    val videoId: String,
    val title: String,
    val showTitle: String,
    val thumbnailUrl: String,
    val duration: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val updatedAtMillis: Long,
    val finished: Boolean,
)

private fun ProgressRecord.toDomain() = EpisodeProgress(
    song = Song(
        videoId = videoId,
        title = title,
        artist = showTitle,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        isPodcastEpisode = true
    ),
    positionMillis = positionMillis,
    durationMillis = durationMillis,
    updatedAtMillis = updatedAtMillis,
    finished = finished
)

private fun EpisodeProgress.toRecord() = ProgressRecord(
    videoId = song.videoId,
    title = song.title,
    showTitle = song.artist,
    thumbnailUrl = song.thumbnailUrl,
    duration = song.duration,
    positionMillis = positionMillis,
    durationMillis = durationMillis,
    updatedAtMillis = updatedAtMillis,
    finished = finished
)
