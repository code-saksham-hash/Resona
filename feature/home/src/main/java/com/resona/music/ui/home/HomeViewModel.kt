package com.resona.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [isLoading] only covers the very first load (nothing to show yet);
 * [isRefreshing] covers every load after that (pull-to-refresh), so an
 * already-populated feed never gets replaced by a blank loading screen just
 * because the user pulled to refresh.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val feed: HomeFeed? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = it.feed == null, isRefreshing = it.feed != null, errorMessage = null)
            }
            try {
                val feed = musicRepository.getHomeFeed()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, feed = feed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = e.message ?: "Couldn't load your feed")
                }
            }
        }
    }
}
