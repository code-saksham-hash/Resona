package com.resona.music.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data class Success(val songs: List<Song>) : PlaylistDetailUiState
    data object Empty : PlaylistDetailUiState
    data class Error(val message: String) : PlaylistDetailUiState
}

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val title: String = savedStateHandle.get<String>(ARG_TITLE).orEmpty()
    private val browseId: String = checkNotNull(savedStateHandle[ARG_BROWSE_ID]) {
        "PlaylistDetailViewModel requires a browseId nav arg"
    }

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = PlaylistDetailUiState.Loading
            _uiState.value = try {
                val songs = musicRepository.getPlaylistSongs(browseId)
                if (songs.isEmpty()) PlaylistDetailUiState.Empty else PlaylistDetailUiState.Success(songs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PlaylistDetailUiState.Error(e.message ?: "Couldn't load this playlist")
            }
        }
    }

    companion object {
        const val ARG_BROWSE_ID = "browseId"
        const val ARG_TITLE = "title"
    }
}
