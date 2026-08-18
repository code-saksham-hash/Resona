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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    // Doubles as either a real InnerTube browseId (a Featured Playlist) or a
    // Playlist.id the user created/imported on-device -- see init, which
    // checks observePlaylists() once to tell the two apart. Same nav arg
    // slot either way, so the route/composable don't need to know which
    // kind of playlist they're pointed at.
    private val id: String = checkNotNull(savedStateHandle[ARG_BROWSE_ID]) {
        "PlaylistDetailViewModel requires a browseId nav arg"
    }

    private val _uiState = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isLocalPlaylist = musicRepository.observePlaylists().first().any { it.id == id }
            if (isLocalPlaylist) {
                // Already fully loaded on-device -- no InnerTube round trip,
                // and staying on observePlaylists() (rather than a one-shot
                // snapshot) means a future edit to this same playlist shows
                // up here without navigating away and back.
                musicRepository.observePlaylists()
                    .map { playlists -> playlists.find { it.id == id }?.songs ?: emptyList() }
                    .collect { songs ->
                        _uiState.value = if (songs.isEmpty()) PlaylistDetailUiState.Empty
                        else PlaylistDetailUiState.Success(songs)
                    }
            } else {
                load()
            }
        }
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = PlaylistDetailUiState.Loading
            _uiState.value = try {
                val songs = musicRepository.getPlaylistSongs(id)
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
