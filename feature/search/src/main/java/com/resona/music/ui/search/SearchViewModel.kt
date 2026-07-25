@file:OptIn(FlowPreview::class)

package com.resona.music.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    data class Success(val results: List<Song>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Empty)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { query -> performSearch(query) }
        }
    }

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

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState.Empty
            return
        }
        _uiState.value = SearchUiState.Loading
        _uiState.value = try {
            val results = musicRepository.search(query)
            if (results.isEmpty()) SearchUiState.Empty else SearchUiState.Success(results)
        } catch (e: CancellationException) {
            // collectLatest cancels the in-flight search as soon as a newer
            // query arrives -- that cancellation must propagate rather than
            // being reported as an Error state.
            throw e
        } catch (e: Exception) {
            SearchUiState.Error(e.message ?: "Unknown error")
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
