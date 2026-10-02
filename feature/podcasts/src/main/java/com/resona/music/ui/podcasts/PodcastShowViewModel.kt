package com.resona.music.ui.podcasts

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.EpisodeProgress
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastShowPage
import com.resona.music.domain.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShowUiState {
    data object Loading : ShowUiState
    data class Loaded(
        val page: PodcastShowPage,
        val episodes: List<PodcastEpisode>,
        val continuation: String?,
        val isLoadingMore: Boolean = false,
    ) : ShowUiState
    data class Error(val message: String) : ShowUiState
}

@HiltViewModel
class PodcastShowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PodcastRepository,
) : ViewModel() {

    private val browseId: String = checkNotNull(savedStateHandle[ARG_BROWSE_ID]) {
        "PodcastShowViewModel needs a browseId nav arg"
    }

    private val _uiState = MutableStateFlow<ShowUiState>(ShowUiState.Loading)
    val uiState: StateFlow<ShowUiState> = _uiState.asStateFlow()

    val isFollowing: StateFlow<Boolean> = repository.observeFollowedShows()
        .map { shows -> shows.any { it.show.browseId == browseId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val progress: StateFlow<Map<String, EpisodeProgress>> = repository.observeEpisodeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        load()
    }

    fun retry() = load()

    fun toggleFollow() {
        val loaded = _uiState.value as? ShowUiState.Loaded ?: return
        viewModelScope.launch {
            val following = repository.observeFollowedShows().first().any { it.show.browseId == browseId }
            if (following) repository.unfollowShow(browseId) else repository.followShow(loaded.page.show)
        }
    }

    fun loadMore() {
        val loaded = _uiState.value as? ShowUiState.Loaded ?: return
        val token = loaded.continuation ?: return
        if (loaded.isLoadingMore) return
        _uiState.value = loaded.copy(isLoadingMore = true)
        viewModelScope.launch {
            _uiState.value = try {
                val next = repository.loadMoreEpisodes(loaded.page.show, token)
                loaded.copy(
                    episodes = (loaded.episodes + next.episodes).distinctBy { it.videoId },
                    continuation = next.continuation,
                    isLoadingMore = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "loadMore: $browseId failed", e)
                // Keeps the token, so the button just works again on the next tap.
                loaded.copy(isLoadingMore = false)
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ShowUiState.Loading
            _uiState.value = try {
                val page = repository.getShow(browseId)
                // Opening the show is what clears its NEW badge on the hub.
                repository.markShowSeen(browseId)
                ShowUiState.Loaded(page = page, episodes = page.episodes, continuation = page.continuation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "load: $browseId failed", e)
                ShowUiState.Error("Couldn't load this podcast. Check your connection and try again.")
            }
        }
    }

    companion object {
        const val ARG_BROWSE_ID = "browseId"
        private const val TAG = "PodcastShowViewModel"
    }
}
