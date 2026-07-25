package com.resona.music.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.FeaturedPlaylist
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    val downloadedSongs: StateFlow<List<DownloadedSong>> = musicRepository.observeDownloadedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val likedSongs: StateFlow<List<Song>> = musicRepository.observeLikedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // No loading/error state exposed here on purpose -- this is a secondary
    // section (unlike Home's feed, which is the whole screen's content), so
    // a failed fetch just leaves the list empty and the section hidden,
    // same as an empty Downloaded/Liked section does.
    private val _featuredPlaylists = MutableStateFlow<List<FeaturedPlaylist>>(emptyList())
    val featuredPlaylists: StateFlow<List<FeaturedPlaylist>> = _featuredPlaylists.asStateFlow()

    init {
        viewModelScope.launch {
            _featuredPlaylists.value = runCatching { musicRepository.getFeaturedPlaylists() }
                .getOrDefault(emptyList())
        }
    }

    fun deleteDownload(videoId: String) {
        viewModelScope.launch { musicRepository.deleteDownload(videoId) }
    }

    fun unlike(song: Song) {
        viewModelScope.launch { musicRepository.toggleLike(song) }
    }
}
