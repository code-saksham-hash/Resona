package com.resona.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.repository.MusicRepository
import com.resona.music.domain.stats.PlayStats
import com.resona.music.domain.stats.computePlayStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private val emptyStats = PlayStats(
    totalPlays = 0,
    listeningSecondsToday = 0L,
    listeningSecondsThisWeek = 0L,
    listeningSecondsAllTime = 0L,
    uniqueTracks = 0,
    uniqueArtists = 0,
    topArtists = emptyList(),
    topTracks = emptyList(),
    streakDays = 0
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    /**
     * The aggregated stats, derived from the on-device play history.
     * Recomputed on each history change.
     */
    val stats: StateFlow<PlayStats> = musicRepository.observePlayHistory()
        .map { computePlayStats(it, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyStats)
}
