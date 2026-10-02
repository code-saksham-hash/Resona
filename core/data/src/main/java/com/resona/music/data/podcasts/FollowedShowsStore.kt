package com.resona.music.data.podcasts

import android.content.Context
import android.util.Log
import com.resona.music.domain.model.FollowedShow
import com.resona.music.domain.model.PodcastShow
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

/** Podcasts the user follows, kept in a small JSON file like the playlist store. */
internal interface FollowedShowsStore {
    val shows: StateFlow<List<FollowedShow>>
    suspend fun follow(show: PodcastShow, atMillis: Long)
    suspend fun unfollow(browseId: String)
    suspend fun markSeen(browseId: String, atMillis: Long)

    /** Refreshes title/author/cover from a fresh show page. A no-op if not followed. */
    suspend fun updateShowInfo(show: PodcastShow)
}

@Singleton
internal class FileFollowedShowsStore @Inject constructor(
    @ApplicationContext context: Context,
) : FollowedShowsStore {

    private val file = File(context.filesDir, "followed_podcasts.json")
    private val mutex = Mutex()

    private val _shows = MutableStateFlow(read())
    override val shows: StateFlow<List<FollowedShow>> = _shows.asStateFlow()

    override suspend fun follow(show: PodcastShow, atMillis: Long) = edit { current ->
        if (current.any { it.show.browseId == show.browseId }) {
            current
        } else {
            listOf(FollowedShow(show, followedAtMillis = atMillis, lastSeenAtMillis = atMillis)) + current
        }
    }

    override suspend fun unfollow(browseId: String) = edit { current ->
        current.filterNot { it.show.browseId == browseId }
    }

    override suspend fun markSeen(browseId: String, atMillis: Long) = edit { current ->
        current.map { if (it.show.browseId == browseId) it.copy(lastSeenAtMillis = atMillis) else it }
    }

    override suspend fun updateShowInfo(show: PodcastShow) = edit { current ->
        current.map { followed ->
            if (followed.show.browseId != show.browseId) return@map followed
            // Following from an episode row stores a stand-in cover and no
            // author, so only ever upgrade fields, never blank them out.
            val merged = followed.show.copy(
                title = show.title.ifBlank { followed.show.title },
                author = show.author.ifBlank { followed.show.author },
                thumbnailUrl = show.thumbnailUrl.ifBlank { followed.show.thumbnailUrl }
            )
            followed.copy(show = merged)
        }
    }

    private suspend fun edit(transform: (List<FollowedShow>) -> List<FollowedShow>) {
        mutex.withLock {
            val updated = transform(_shows.value)
            if (updated == _shows.value) return
            withContext(Dispatchers.IO) { file.writeText(Json.encodeToString(updated.map { it.toRecord() })) }
            _shows.value = updated
        }
    }

    private fun read(): List<FollowedShow> {
        if (!file.exists()) return emptyList()
        return runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<List<FollowedShowRecord>>(file.readText()).map { it.toDomain() }
        }.getOrElse { e ->
            Log.w(TAG, "read: couldn't load $file, starting empty", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "FollowedShowsStore"
    }
}

@Serializable
private data class FollowedShowRecord(
    val browseId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val followedAtMillis: Long,
    val lastSeenAtMillis: Long,
)

private fun FollowedShowRecord.toDomain() = FollowedShow(
    show = PodcastShow(browseId, title, author, thumbnailUrl),
    followedAtMillis = followedAtMillis,
    lastSeenAtMillis = lastSeenAtMillis
)

private fun FollowedShow.toRecord() = FollowedShowRecord(
    browseId = show.browseId,
    title = show.title,
    author = show.author,
    thumbnailUrl = show.thumbnailUrl,
    followedAtMillis = followedAtMillis,
    lastSeenAtMillis = lastSeenAtMillis
)
