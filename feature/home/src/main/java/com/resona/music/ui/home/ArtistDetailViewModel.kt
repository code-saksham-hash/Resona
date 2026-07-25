package com.resona.music.ui.home

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

sealed interface ArtistDetailUiState {
    data object Loading : ArtistDetailUiState
    data class Success(val songs: List<Song>) : ArtistDetailUiState
    data object Empty : ArtistDetailUiState
    data class Error(val message: String) : ArtistDetailUiState
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val artistName: String = checkNotNull(savedStateHandle[ARG_NAME]) {
        "ArtistDetailViewModel requires a name nav arg"
    }

    private val _uiState = MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading)
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = ArtistDetailUiState.Loading
            _uiState.value = try {
                val songs = musicRepository.getTopSongsForArtist(artistName)
                if (songs.isEmpty()) ArtistDetailUiState.Empty else ArtistDetailUiState.Success(songs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ArtistDetailUiState.Error(e.message ?: "Couldn't load songs for this artist")
            }
        }
    }

    companion object {
        const val ARG_NAME = "name"
    }
}
