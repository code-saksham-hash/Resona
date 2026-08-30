package com.resona.music.playback

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SleepTimerState {
    data object Off : SleepTimerState
    data class Counting(val remainingMillis: Long, val totalMillis: Long) : SleepTimerState
    /** Armed, but nothing to count down -- pauses once the currently
     *  playing track is no longer current (see [SleepTimerController.startAtEndOfTrack]). */
    data object EndOfTrack : SleepTimerState
}

/**
 * A [Player.pause] that fires on its own after a delay -- attached directly
 * to [PlayerService]'s [Player] (same `attach()`-from-`onCreate()` pattern
 * [PlaybackQueueManager] uses) rather than living behind
 * [androidx.media3.session.MediaController]/[PlayerViewModel], for the same
 * reason: a sleep timer that stops working the moment the screen turns off
 * and the Activity/ViewModel go away would defeat its entire purpose.
 *
 * [startAtEndOfTrack] depends on [queueManager] rather than [Player]'s own
 * `onMediaItemTransition` because the queue manager's placeholder-item
 * mechanism (see its kdoc) fires that event once for landing on a
 * lazily-resolved placeholder and again once the real track swaps in --
 * pausing on the first would just get silently undone by the resolve's own
 * `player.play()` a moment later. [PlaybackQueueManager.state]'s
 * `currentSong` only changes once, exactly when a genuinely different real
 * track becomes current, which is the actual signal this needs.
 */
@Singleton
class SleepTimerController @Inject constructor(
    private val queueManager: PlaybackQueueManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var player: Player

    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null

    fun attach(player: Player) {
        this.player = player
    }

    /** Pauses playback in [durationMillis]. Replaces any timer already running. */
    fun startCountdown(durationMillis: Long) {
        job?.cancel()
        job = scope.launch {
            var remaining = durationMillis
            while (remaining > 0) {
                _state.value = SleepTimerState.Counting(remaining, durationMillis)
                delay(TICK_MILLIS.coerceAtMost(remaining))
                remaining -= TICK_MILLIS
            }
            player.pause()
            _state.value = SleepTimerState.Off
        }
    }

    /** Pauses playback as soon as the current track is no longer current --
     *  i.e. lets it finish, then pauses right at the start of whatever
     *  would've played next instead of cutting it off mid-song. Replaces
     *  any timer already running. */
    fun startAtEndOfTrack() {
        job?.cancel()
        _state.value = SleepTimerState.EndOfTrack
        val armedForSongId = queueManager.state.value.currentSong?.videoId
        job = scope.launch {
            queueManager.state.map { it.currentSong?.videoId }.first { it != armedForSongId }
            player.pause()
            _state.value = SleepTimerState.Off
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = SleepTimerState.Off
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
    }
}
