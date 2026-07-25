package com.resona.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val imageUrl: String
)

data class DownloadsUiState(
    val tracks: List<DownloadTrack> = emptyList(),
    val isDownloadedOnly: Boolean = true,
    val trackCount: Int = 0,
    val storageUsed: String = ""
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        loadTracks()
    }

    fun toggleDownloadedOnly() {
        _uiState.value = _uiState.value.copy(
            isDownloadedOnly = !_uiState.value.isDownloadedOnly
        )
    }

    private fun loadTracks() {
        viewModelScope.launch {
            _uiState.value = DownloadsUiState(
                tracks = listOf(
                    DownloadTrack("1", "Structure of Silence", "Elias Thorne", "Echoes in White", "4:12", "https://picsum.photos/seed/dl1/400/400"),
                    DownloadTrack("2", "Obsidian Waves", "Mora", "Night Cycles", "5:45", "https://picsum.photos/seed/dl2/400/400"),
                    DownloadTrack("3", "Grid Theory", "Vector Static", "Linear Progression", "3:22", "https://picsum.photos/seed/dl3/400/400"),
                    DownloadTrack("4", "Velvet Hush", "Lydia St. John", "Late Hours", "6:10", "https://picsum.photos/seed/dl4/400/400"),
                    DownloadTrack("5", "Descent", "Aero", "Gravity", "4:44", "https://picsum.photos/seed/dl5/400/400")
                ),
                isDownloadedOnly = true,
                trackCount = 124,
                storageUsed = "14.2 GB"
            )
        }
    }
}
