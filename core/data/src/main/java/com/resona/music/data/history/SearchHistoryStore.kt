package com.resona.music.data.history

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks explicitly-submitted search queries (most-recent-first, capped),
 *  persisted to a small JSON index -- what backs the Search screen's recent-
 *  searches list. Separate interface so tests can fake it without a live
 *  Context -- same reasoning as JsEngine (see ExtractorModule). */
internal interface SearchHistoryStore {
    val recentSearches: StateFlow<List<String>>
    suspend fun record(query: String)
    suspend fun remove(query: String)
    suspend fun clear()
}

@Singleton
internal class FileSearchHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) : SearchHistoryStore {

    private val indexFile = File(context.filesDir, "search_history.json")
    private val mutex = Mutex()

    private val _recentSearches = MutableStateFlow(readIndex())
    override val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    override suspend fun record(query: String) {
        mutex.withLock {
            // Case-insensitive dedupe: to a user, "Queen" and "queen" are the
            // same past search, not two -- keep whichever casing was just
            // typed and move it to the front.
            val updated = (listOf(query) + _recentSearches.value.filterNot { it.equals(query, ignoreCase = true) })
                .take(MAX_HISTORY_SIZE)
            persistPassively("record", updated)
            _recentSearches.value = updated
        }
    }

    override suspend fun remove(query: String) {
        mutex.withLock {
            val updated = _recentSearches.value.filterNot { it == query }
            persistPassively("remove", updated)
            _recentSearches.value = updated
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            persistPassively("clear", emptyList())
            _recentSearches.value = emptyList()
        }
    }

    // Same reasoning as PlayHistoryStore.recordPlay: this is passive
    // bookkeeping alongside the real action (running/clearing a search), not
    // a user-facing action with its own success/failure state, so a disk
    // error here is logged and swallowed rather than surfaced.
    private suspend fun persistPassively(op: String, queries: List<String>) {
        withContext(Dispatchers.IO) {
            runCatching { writeIndex(queries) }
                .onFailure { e -> Log.w(TAG, "$op: failed to persist", e) }
        }
    }

    private fun readIndex(): List<String> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            Json.decodeFromString<List<String>>(indexFile.readText())
        }.getOrElse { e ->
            Log.w(TAG, "readIndex: failed to load $indexFile, starting empty", e)
            emptyList()
        }
    }

    private fun writeIndex(queries: List<String>) {
        indexFile.writeText(Json.encodeToString(queries))
    }

    private companion object {
        const val TAG = "SearchHistoryStore"
        const val MAX_HISTORY_SIZE = 20
    }
}
