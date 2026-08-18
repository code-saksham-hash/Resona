package com.resona.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.Playlist
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** [LibraryViewModel.importState] while importing a playlist from a pasted
 *  url -- own state rather than reusing [PlaylistDetailUiState] since this
 *  drives a dialog on the Library screen itself, not a full-screen load. */
sealed interface ImportPlaylistState {
    data object Idle : ImportPlaylistState
    data object Importing : ImportPlaylistState
    data object Success : ImportPlaylistState
    data class Failed(val message: String) : ImportPlaylistState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    val downloadedSongs: StateFlow<List<DownloadedSong>> = musicRepository.observeDownloadedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedSongs: StateFlow<List<Song>> = musicRepository.observeLikedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<Playlist>> = musicRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportPlaylistState>(ImportPlaylistState.Idle)
    val importState: StateFlow<ImportPlaylistState> = _importState.asStateFlow()

    fun deleteDownload(videoId: String) {
        viewModelScope.launch { musicRepository.deleteDownload(videoId) }
    }

    fun unlike(song: Song) {
        viewModelScope.launch { musicRepository.toggleLike(song) }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { musicRepository.createPlaylist(name) }
    }

    fun importPlaylist(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _importState.value = ImportPlaylistState.Importing
            _importState.value = try {
                musicRepository.importPlaylistFromUrl(url)
                ImportPlaylistState.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportPlaylistState.Failed(e.message ?: "Couldn't import that playlist")
            }
        }
    }

    /** Called once the UI has reacted to a terminal [importState] (closed the
     *  dialog on [ImportPlaylistState.Success], shown the message on
     *  [ImportPlaylistState.Failed]) -- resets it so the next import attempt
     *  starts clean instead of immediately showing the previous result. */
    fun acknowledgeImportResult() {
        _importState.value = ImportPlaylistState.Idle
    }
}
