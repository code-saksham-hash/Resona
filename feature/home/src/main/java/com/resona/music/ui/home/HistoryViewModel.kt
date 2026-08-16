package com.resona.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    val history: StateFlow<List<PlayHistoryEntry>> = musicRepository.observePlayHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
