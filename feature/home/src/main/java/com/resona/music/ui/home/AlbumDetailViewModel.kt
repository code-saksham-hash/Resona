package com.resona.music.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.Album
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AlbumDetailUiState {
    data object Loading : AlbumDetailUiState
    data class Success(val album: Album) : AlbumDetailUiState
    data object Empty : AlbumDetailUiState
    data class Error(val message: String) : AlbumDetailUiState
}

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val browseId: String = checkNotNull(savedStateHandle[ARG_BROWSE_ID]) {
        "AlbumDetailViewModel requires a browseId nav arg"
    }

    private val _uiState = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading)
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = AlbumDetailUiState.Loading
            _uiState.value = try {
                val album = musicRepository.getAlbum(browseId)
                if (album.songs.isEmpty()) AlbumDetailUiState.Empty else AlbumDetailUiState.Success(album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AlbumDetailUiState.Error(e.message ?: "Couldn't load this album")
            }
        }
    }

    companion object {
        const val ARG_BROWSE_ID = "browseId"
    }
}
