package com.resona.music.ui.podcasts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastShow
import com.resona.music.domain.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastSearchUiState(
    val query: String = "",
    // What the results below are for, which can lag behind what's typed.
    val submittedQuery: String = "",
    val isLoading: Boolean = false,
    val shows: List<PodcastShow> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class PodcastSearchViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastSearchUiState())
    val uiState: StateFlow<PodcastSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) = _uiState.update { it.copy(query = query) }

    // Only on submit, not per keystroke: every search is two InnerTube calls.
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        _uiState.update { it.copy(submittedQuery = query, isLoading = true, error = null) }
        searchJob = viewModelScope.launch {
            try {
                coroutineScope {
                    val shows = async { repository.searchShows(query) }
                    val episodes = async {
                        try {
                            repository.searchEpisodes(query)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Shows are the main result, so a failed episode search just leaves that section out.
                            Log.w(TAG, "search: episodes for '$query' failed", e)
                            emptyList()
                        }
                    }
                    val showResults = shows.await()
                    val episodeResults = episodes.await()
                    _uiState.update { it.copy(isLoading = false, shows = showResults, episodes = episodeResults) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "search: '$query' failed", e)
                _uiState.update {
                    it.copy(isLoading = false, shows = emptyList(), episodes = emptyList(), error = "Search failed. Check your connection and try again.")
                }
            }
        }
    }

    private companion object {
        const val TAG = "PodcastSearchViewModel"
    }
}
