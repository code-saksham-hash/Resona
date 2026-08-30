@file:OptIn(FlowPreview::class)

package com.resona.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.ArtistSummary
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Result-area state for the search screen. The in-progress query text is
 * tracked separately via [SearchViewModel.query] since it changes on every
 * keystroke while this only changes once a (debounced) search resolves --
 * keeping them apart avoids re-rendering the results list on every keypress.
 */
sealed interface SearchUiState {
    data object Loading : SearchUiState
    /**
     * [songs] comes from a Songs-*category* search (see
     * [MusicRepository.searchSongsPage]), which returns a real ~20-per-page
     * shelf with genuine pagination -- not the old mixed-category overview,
     * which could top out under 10 with nothing more to fetch. [continuationToken]
     * is the next page's token, null once there's nothing left to load; [isLoadingMore]
     * covers just that in-flight "Load more" tap so the rest of the list stays put.
     * [artists] is separate -- a handful of circular-avatar candidates at most,
     * from the *unfiltered* search (see [MusicRepository.searchArtists]), never
     * paginated.
     */
    data class Success(
        val songs: List<Song>,
        val artists: List<ArtistSummary>,
        val continuationToken: String?,
        val isLoadingMore: Boolean = false,
    ) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/** One real, fetched shelf of songs on the blank-query Browse landing (see
 *  [BrowseUiState]) -- e.g. "Trending Now" or "Suggested For You". */
data class BrowseSection(val title: String, val songs: List<Song>)

/** Browse-landing state -- shown in place of search history/results while
 *  [SearchViewModel.query] is blank. Pulled from [MusicRepository.getHomeFeed],
 *  the same real, InnerTube-backed source Home's own Trending/Recommended
 *  sections use, never static/mock data. */
sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Success(val sections: List<BrowseSection>) : BrowseUiState
    /** The feed loaded but had nothing playable, or the load itself failed
     *  -- collapsed into one case since the landing screen reacts to both
     *  the same way (just don't show a Browse section; search history, if
     *  any, still renders above it). */
    data object Unavailable : BrowseUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Empty)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Recent searches, most-recently-submitted first -- what the Search
     *  screen shows in place of results while [query] is blank. */
    val searchHistory: StateFlow<List<String>> = musicRepository.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _browseState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val browseState: StateFlow<BrowseUiState> = _browseState.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { query -> performSearch(query) }
        }
        loadBrowse()
    }

    /** Runs once per ViewModel lifetime (this screen survives tab switches
     *  via the bottom nav's saved-state, so this isn't re-fetched on every
     *  visit) -- pull-to-refresh isn't wired here the way Home's is, since
     *  this landing is secondary to the search field itself. [retryBrowse]
     *  covers the one case that needs a manual re-trigger: the initial
     *  fetch failing outright. */
    private fun loadBrowse() {
        viewModelScope.launch {
            _browseState.value = try {
                val feed = musicRepository.getHomeFeed()
                val sections = listOfNotNull(
                    feed.sectionToBrowse("trending", "Trending Now"),
                    feed.sectionToBrowse("recommended", "Suggested For You"),
                )
                if (sections.isEmpty()) BrowseUiState.Unavailable else BrowseUiState.Success(sections)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BrowseUiState.Unavailable
            }
        }
    }

    fun retryBrowse() {
        _browseState.value = BrowseUiState.Loading
        loadBrowse()
    }

    private fun HomeFeed.sectionToBrowse(id: String, title: String): BrowseSection? =
        sections.find { it.id == id }?.songs?.takeIf { it.isNotEmpty() }?.let { BrowseSection(title, it) }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        // Clear stale results/errors immediately instead of waiting out the
        // debounce window once the field is emptied.
        if (newQuery.isBlank()) {
            _uiState.value = SearchUiState.Empty
        }
    }

    fun retry() {
        viewModelScope.launch { performSearch(_query.value) }
    }

    /**
     * Runs [query] right away (skipping the debounce) and records it to
     * search history. This is the screen's only "explicit submission"
     * gesture -- the keyboard's search action and tapping a history entry
     * both funnel through it -- as opposed to the live-as-you-type search
     * above, which never touches history.
     */
    fun submitSearch(query: String = _query.value) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _query.value = trimmed
        viewModelScope.launch {
            musicRepository.recordSearch(trimmed)
            performSearch(trimmed)
        }
    }

    fun removeHistoryEntry(query: String) {
        viewModelScope.launch { musicRepository.removeSearchHistoryEntry(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { musicRepository.clearSearchHistory() }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState.Empty
            return
        }
        _uiState.value = SearchUiState.Loading
        _uiState.value = try {
            coroutineScope {
                // Two genuinely different InnerTube requests (a Songs-category
                // filter vs. the default mixed one -- see searchSongsPage()'s
                // kdoc), fired concurrently rather than one after the other.
                // Artist lookup failing shouldn't fail the whole search --
                // songs are the primary content here, the avatar row is a bonus.
                val songsDeferred = async { musicRepository.searchSongsPage(query) }
                val artistsDeferred = async {
                    runCatching { musicRepository.searchArtists(query) }.getOrDefault(emptyList())
                }
                val songsPage = songsDeferred.await()
                val artists = artistsDeferred.await()
                if (songsPage.songs.isEmpty() && artists.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Success(songsPage.songs, artists, songsPage.continuationToken)
                }
            }
        } catch (e: CancellationException) {
            // collectLatest cancels the in-flight search as soon as a newer
            // query arrives -- that cancellation must propagate rather than
            // being reported as an Error state.
            throw e
        } catch (e: Exception) {
            SearchUiState.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Appends the next page of song results using [SearchUiState.Success.continuationToken].
     * A no-op if there's nothing more, or a load is already in flight. Re-reads
     * [_uiState] after the network call (rather than trusting the [SearchUiState.Success]
     * captured at the start) and bails if [query] changed underneath it, so a slow
     * "load more" can't stomp on results from a newer search that superseded it.
     *
     * Deduped by videoId against what's already on screen: verified live that
     * consecutive pages of the same continuation chain aren't guaranteed
     * disjoint (InnerTube's own ranking drifted enough between two real
     * requests to repeat several songs across a page boundary) -- without
     * this, "Load more" could visibly repeat a row.
     */
    fun loadMoreResults() {
        val current = _uiState.value
        if (current !is SearchUiState.Success) return
        val token = current.continuationToken ?: return
        if (current.isLoadingMore) return
        val forQuery = _query.value

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)
            val page = try {
                musicRepository.loadMoreSongResults(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            if (_query.value != forQuery) return@launch
            val latest = _uiState.value
            if (latest !is SearchUiState.Success) return@launch
            _uiState.value = if (page != null) {
                val existingIds = latest.songs.mapTo(mutableSetOf()) { it.videoId }
                latest.copy(
                    songs = latest.songs + page.songs.filterNot { it.videoId in existingIds },
                    continuationToken = page.continuationToken,
                    isLoadingMore = false,
                )
            } else {
                // Best-effort -- a failed "load more" shouldn't blow away the
                // results already on screen, just stop offering more for now
                // (the user can tap it again).
                latest.copy(isLoadingMore = false)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
