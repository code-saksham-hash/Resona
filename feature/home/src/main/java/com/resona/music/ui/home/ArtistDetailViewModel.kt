package com.resona.music.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.Artist
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
    data class Success(val artist: Artist) : ArtistDetailUiState
    data object Empty : ArtistDetailUiState
    data class Error(val message: String) : ArtistDetailUiState
}

/**
 * [ARG_BROWSE_ID] is only ever supplied by a caller that already has a real
 * InnerTube artist identity (currently just the Search screen's circular
 * artist results, which parse it straight off the search response -- see
 * SearchModels.extractArtists()). Every older call site (Home's artist
 * chips, Stats, History) only ever had a plain name string to begin with,
 * so [load] resolves a browseId for those by name via [resolveBrowseId] --
 * one extra search, run once per screen open, not per row -- before it can
 * load the real profile through [MusicRepository.getArtist]. If that
 * resolution itself comes up empty (an obscure/unlisted artist InnerTube's
 * own search doesn't surface as a channel), [getTopSongsForArtist]'s older
 * text-search approximation is still there as a last resort so the screen
 * never just goes empty -- it's folded into the same [Artist] shape so the
 * screen only has one layout to render either way (see [load]).
 */
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val artistName: String = checkNotNull(savedStateHandle[ARG_NAME]) {
        "ArtistDetailViewModel requires a name nav arg"
    }
    private val providedBrowseId: String? = savedStateHandle.get<String>(ARG_BROWSE_ID)?.takeIf { it.isNotBlank() }

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
                val browseId = providedBrowseId ?: resolveBrowseId(artistName)
                val artist = if (browseId != null) {
                    musicRepository.getArtist(browseId)
                } else {
                    val songs = musicRepository.getTopSongsForArtist(artistName)
                    Artist(
                        browseId = "",
                        name = artistName,
                        thumbnailUrl = songs.firstOrNull()?.thumbnailUrl ?: "",
                        topSongs = songs,
                    )
                }
                if (artist.topSongs.isEmpty() && artist.albums.isEmpty() && artist.singles.isEmpty()) {
                    ArtistDetailUiState.Empty
                } else {
                    ArtistDetailUiState.Success(artist)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ArtistDetailUiState.Error(e.message ?: "Couldn't load songs for this artist")
            }
        }
    }

    private suspend fun resolveBrowseId(name: String): String? =
        runCatching { musicRepository.searchArtists(name) }
            .getOrNull()
            ?.let { matches -> matches.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: matches.firstOrNull() }
            ?.browseId

    companion object {
        const val ARG_NAME = "name"
        const val ARG_BROWSE_ID = "browseId"
    }
}
