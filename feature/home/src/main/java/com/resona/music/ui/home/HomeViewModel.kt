package com.resona.music.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeAlbum(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val videoId: String
)

data class HomeArtist(
    val id: String,
    val name: String,
    val imageUrl: String,
    val videoId: String
)

data class HomeTrack(
    val id: String,
    val number: Int,
    val title: String,
    val artist: String,
    val duration: String,
    val imageUrl: String
)

data class HomeUiState(
    val recommended: List<HomeAlbum> = emptyList(),
    val trending: List<HomeAlbum> = emptyList(),
    val topArtists: List<HomeArtist> = emptyList(),
    val newTracks: List<HomeTrack> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun retry() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val queries = listOf("popular music", "new releases", "trending")
                var allSongs = emptyList<Song>()

                for (query in queries) {
                    val results = musicRepository.search(query)
                    if (results.isNotEmpty()) {
                        allSongs = results
                        break
                    }
                }

                if (allSongs.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "No results found")
                    return@launch
                }

                val topSongs = allSongs.take(12)

                _uiState.value = HomeUiState(
                    recommended = topSongs.take(3).map { song ->
                        HomeAlbum(song.videoId, song.title, song.artist, song.thumbnailUrl, song.videoId)
                    },
                    trending = topSongs.drop(3).take(3).map { song ->
                        HomeAlbum(song.videoId, song.title, song.artist, song.thumbnailUrl, song.videoId)
                    },
                    topArtists = allSongs.distinctBy { it.artist }.take(6).map { artistSong ->
                        HomeArtist(artistSong.videoId, artistSong.artist, artistSong.thumbnailUrl, artistSong.videoId)
                    },
                    newTracks = allSongs.take(10).mapIndexed { i, song ->
                        HomeTrack(song.videoId, i + 1, song.title, song.artist, "", song.thumbnailUrl)
                    },
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e(TAG, "loadData failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
