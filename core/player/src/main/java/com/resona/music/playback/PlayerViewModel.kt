package com.resona.music.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.Playlist
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything any screen needs to render playback: the track shown in the
 * mini-player/Now Playing screen, transport state, and position/duration for
 * a scrubber. [currentTrack] is null when nothing has ever been played --
 * that's what tells the mini-player to render nothing at all.
 */
data class PlayerUiState(
    val currentTrack: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val error: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val isLiked: Boolean = false,
    val lyricsState: LyricsState = LyricsState.NotLoaded,
    val syncedLyrics: List<LyricsLine> = emptyList(),
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Song> = emptyList()
)

/** [PlayerUiState.downloadState] always describes [PlayerUiState.currentTrack], never a stale one. */
sealed interface DownloadState {
    data object Idle : DownloadState
    /** [progress] is null when the server didn't send a Content-Length, so
     *  the UI can't show a determinate ring -- it falls back to spinning. */
    data class Downloading(val progress: Float? = null) : DownloadState
    data object Downloaded : DownloadState
    data class Failed(val message: String) : DownloadState
}

/** [PlayerUiState.lyricsState] always describes [PlayerUiState.currentTrack], never a stale one.
 *  Starts at [NotLoaded] rather than fetching eagerly on every [PlayerViewModel.play] -- lyrics
 *  cost two InnerTube round trips and the section may never be opened. */
sealed interface LyricsState {
    data object NotLoaded : LyricsState
    data object Loading : LyricsState
    data class Available(val text: String) : LyricsState
    data object Unavailable : LyricsState
}

/**
 * Shared across every screen (obtained once, Activity-scoped, at the
 * navigation root) so playback state and controls are available anywhere
 * without each screen owning its own player connection. Talks to
 * [PlayerService] exclusively through a [MediaController] -- it never
 * touches an [androidx.media3.exoplayer.ExoPlayer] directly, so playback
 * keeps running in the service regardless of this ViewModel's lifecycle.
 *
 * Queue/skip/autoplay mechanics live in [PlaybackQueueManager], not here --
 * see its kdoc for why (short version: it has to keep working with no
 * Activity/ViewModel around at all, which this class fundamentally can't).
 * This class delegates to it and mirrors its [PlaybackQueueManager.state]
 * into [PlayerUiState], and otherwise only handles things that genuinely
 * need an open screen to make sense (download, like, lyrics, playlists) plus
 * the purely mechanical transport state ([PlayerUiState.isPlaying]/
 * [PlayerUiState.isBuffering]/position/duration) read straight off the
 * controller.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val queueManager: PlaybackQueueManager,
    private val sleepTimerController: SleepTimerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /**
     * [PlayerUiState.currentTrack] on its own flow. [uiState] re-emits
     * every ~second while playing (the position poll below); the chrome
     * collects these derived flows so it isn't recomposed at that cadence.
     */
    val currentTrack: StateFlow<Song?> = uiState.map { it.currentTrack }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.currentTrack)

    /** [PlayerUiState.isPlaying], deduplicated -- see [currentTrack]. */
    val isPlaying: StateFlow<Boolean> = uiState.map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _uiState.value.isPlaying)

    /** For Now Playing's sleep timer entry -- see [SleepTimerController], which
     *  this only forwards to/from (it's a singleton attached straight to the
     *  service's player so it keeps counting down with no screen open). */
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimerController.state

    /** videoId [uiState] was last updated for, so the [queueManager] state
     *  collector below can tell a genuine track change (which resets
     *  per-track UI fields like [PlayerUiState.lyricsState]) apart from e.g.
     *  just the queue/shuffle/repeat fields changing underneath the same song. */
    private var lastObservedSongId: String? = null

    private val controllerFuture = MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlayerService::class.java))
    ).buildAsync()

    private val controllerReady = CompletableDeferred<MediaController>()

    init {
        controllerFuture.addListener(
            {
                val controller = controllerFuture.get()

                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
                        _uiState.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.update {
                            it.copy(
                                isBuffering = playbackState == Player.STATE_BUFFERING,
                                duration = controller.duration.coerceAtLeast(0L)
                            )
                        }
                    }
                })

                controllerReady.complete(controller)
            },
            MoreExecutors.directExecutor()
        )

        // Player.Listener has no "position changed" callback, so the only
        // way to keep a scrubber live is to poll it while something plays.
        viewModelScope.launch {
            val controller = controllerReady.await()
            while (isActive) {
                if (!queueManager.state.value.isResolving && controller.isPlaying) {
                    _uiState.update { it.copy(position = controller.currentPosition.coerceAtLeast(0L)) }
                }
                delay(POSITION_UPDATE_MILLIS)
            }
        }

        // The single source of truth for which track is current, the queue,
        // and shuffle/repeat. queueManager is a singleton shared with
        // PlayerService (see its kdoc), so it already reflects whatever's
        // playing -- even something started or skipped entirely from the
        // notification while this ViewModel didn't exist yet -- from the
        // very first emission below, no separate reconnect-resync needed.
        viewModelScope.launch {
            queueManager.state.collect { qs ->
                val songChanged = qs.currentSong?.videoId != lastObservedSongId
                lastObservedSongId = qs.currentSong?.videoId
                _uiState.update { current ->
                    if (songChanged) {
                        current.copy(
                            currentTrack = qs.currentSong,
                            queue = qs.queue,
                            isShuffled = qs.isShuffled,
                            repeatMode = qs.repeatMode,
                            error = qs.error,
                            isPlaying = false,
                            isBuffering = qs.currentSong != null,
                            position = 0L,
                            duration = 0L,
                            downloadState = qs.currentSong?.let { song ->
                                if (musicRepository.localFileForSong(song.videoId) != null) {
                                    DownloadState.Downloaded
                                } else {
                                    DownloadState.Idle
                                }
                            } ?: DownloadState.Idle,
                            isLiked = qs.currentSong?.let { musicRepository.isLiked(it.videoId) } ?: false,
                            lyricsState = LyricsState.NotLoaded,
                            syncedLyrics = emptyList(),
                        )
                    } else {
                        current.copy(
                            queue = qs.queue,
                            isShuffled = qs.isShuffled,
                            repeatMode = qs.repeatMode,
                            error = qs.error,
                        )
                    }
                }
            }
        }
    }

    fun play(song: Song, queue: List<Song> = emptyList()) = queueManager.playSong(song, queue)

    /**
     * Downloads [PlayerUiState.currentTrack] for offline playback. Result is
     * both reflected in [downloadState] (for the icon on Now Playing) and
     * toasted -- toasted so it's still noticed if the user has already
     * navigated away from Now Playing by the time the download finishes.
     */
    fun download() {
        val track = _uiState.value.currentTrack ?: return
        val currentState = _uiState.value.downloadState
        if (currentState is DownloadState.Downloading || currentState is DownloadState.Downloaded) {
            Log.d(TAG, "download: ignored, ${track.videoId} already $currentState")
            return
        }

        Log.d(TAG, "download: starting for videoId=${track.videoId} title=${track.title}")
        viewModelScope.launch {
            _uiState.updateForTrack(track.videoId) { it.copy(downloadState = DownloadState.Downloading()) }
            try {
                val downloaded = musicRepository.downloadSong(track) { progress ->
                    _uiState.updateForTrack(track.videoId) {
                        it.copy(downloadState = DownloadState.Downloading(progress))
                    }
                }
                Log.d(TAG, "download: succeeded for videoId=${track.videoId}, file=${downloaded.filePath}")
                _uiState.updateForTrack(track.videoId) { it.copy(downloadState = DownloadState.Downloaded) }
                Toast.makeText(context, "Downloaded \"${track.title}\"", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "download: failed for videoId=${track.videoId}", e)
                val message = e.message ?: "Download failed"
                _uiState.updateForTrack(track.videoId) { it.copy(downloadState = DownloadState.Failed(message)) }
                Toast.makeText(context, "Couldn't download \"${track.title}\": $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Likes/unlikes [PlayerUiState.currentTrack]. */
    fun toggleLike() {
        val track = _uiState.value.currentTrack ?: return
        // Applied optimistically -- the store's own write only fails on a
        // real disk error, not worth blocking a heart icon over.
        _uiState.updateForTrack(track.videoId) { it.copy(isLiked = !it.isLiked) }
        viewModelScope.launch {
            try {
                musicRepository.toggleLike(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "toggleLike: failed for videoId=${track.videoId}, reverting", e)
                _uiState.updateForTrack(track.videoId) { it.copy(isLiked = !it.isLiked) }
                Toast.makeText(context, "Couldn't update Liked Songs", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** The user's on-device playlists, for the Now Playing overflow menu's
     *  "Add to playlist" picker. */
    val playlists: StateFlow<List<Playlist>> = musicRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Adds [PlayerUiState.currentTrack] to [playlistId]. A no-op if nothing's
     *  playing or the track's already in that playlist (see
     *  [MusicRepository.addSongToPlaylist]). */
    fun addCurrentTrackToPlaylist(playlistId: String) {
        val track = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                musicRepository.addSongToPlaylist(playlistId, track)
                Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "addCurrentTrackToPlaylist: failed for videoId=${track.videoId}", e)
                Toast.makeText(context, "Couldn't add to playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Fetches lyrics for [PlayerUiState.currentTrack], if not already loaded/loading. */
    fun loadLyrics() {
        val track = _uiState.value.currentTrack ?: return
        if (_uiState.value.lyricsState !is LyricsState.NotLoaded) return

        viewModelScope.launch {
            _uiState.updateForTrack(track.videoId) { it.copy(lyricsState = LyricsState.Loading) }

            val (plainText, synced) = try {
                val plainDeferred = viewModelScope.async { musicRepository.getLyrics(track.videoId) }
                val syncedDeferred = viewModelScope.async { musicRepository.getSyncedLyrics(track.videoId, track.title, track.artist) }
                plainDeferred.await() to syncedDeferred.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "loadLyrics: failed for videoId=${track.videoId}", e)
                null to null
            }

            _uiState.updateForTrack(track.videoId) {
                it.copy(
                    syncedLyrics = synced ?: emptyList(),
                    lyricsState = when {
                        synced != null && synced.isNotEmpty() -> LyricsState.Available("")
                        plainText != null -> LyricsState.Available(plainText)
                        else -> LyricsState.Unavailable
                    }
                )
            }
        }
    }

    // Guards against a download's/like's/lyrics fetch's result landing after
    // the user has already skipped to a different track -- downloadState/
    // isLiked/lyricsState always describe currentTrack, never a stale one.
    private fun MutableStateFlow<PlayerUiState>.updateForTrack(
        videoId: String,
        block: (PlayerUiState) -> PlayerUiState
    ) {
        update { if (it.currentTrack?.videoId == videoId) block(it) else it }
    }

    /**
     * This used to clamp to [Player.getBufferedPosition] and toast an
     * explanation, because seeking past what was buffered meant opening a
     * new connection partway into the file, and that request got rejected
     * by the source every time under the client Resona resolved streams
     * through back then. Switching which client resolves the stream first
     * (see [InnerTubeClientConfig] in `:core:data`) turned out to fix that
     * at the source: confirmed directly, requesting arbitrary byte ranges
     * deep into a file no longer gets rejected at all. Verified live here
     * too afterward, dragging the scrubber far ahead of the buffered point
     * now lands cleanly with no stall and no reversion, so the clamp was
     * removed rather than kept as unneeded belt-and-suspenders.
     */
    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            val controller = controllerReady.await()
            val target = positionMs.coerceAtLeast(0L)
            controller.seekTo(target)
            // Applied optimistically so the elapsed-time label snaps to the
            // released position immediately, instead of waiting up to
            // POSITION_UPDATE_MILLIS for the poll loop to catch up.
            _uiState.update { it.copy(position = target) }
        }
    }

    /** Relative seek for the episode skip buttons, clamped to the track. */
    fun seekBy(deltaMs: Long) {
        viewModelScope.launch {
            val controller = controllerReady.await()
            val duration = controller.duration
            var target = (controller.currentPosition + deltaMs).coerceAtLeast(0L)
            if (duration > 0L) target = target.coerceAtMost(duration)
            controller.seekTo(target)
            _uiState.update { it.copy(position = target) }
        }
    }

    fun togglePlayPause() {
        Log.d(TAG, "togglePlayPause() called, controllerReady.isCompleted=${controllerReady.isCompleted}")
        viewModelScope.launch {
            val controller = controllerReady.await()
            Log.d(
                TAG,
                "togglePlayPause: controller ready, isPlaying=${controller.isPlaying}, " +
                    "playbackState=${controller.playbackState}"
            )
            val track = _uiState.value.currentTrack
            when {
                controller.isPlaying -> controller.pause()
                // A bare play() on an idle/empty player (e.g. after a
                // playback error, or before anything was ever prepared) is
                // treated by Media3 as a "resume last session" request,
                // which requires MediaSession.Callback.onPlaybackResumption
                // -- unimplemented here, so it throws *inside the session*
                // and the tap silently does nothing. Re-running the normal
                // play() flow re-resolves the stream and actually recovers.
                controller.playbackState == Player.STATE_IDLE && track != null -> play(track)
                else -> controller.play()
            }
            Log.d(TAG, "togglePlayPause: command dispatched")
        }
    }

    fun skipToNext() = queueManager.skipToNext()

    fun skipToPrevious() = queueManager.skipToPrevious()

    fun setShuffleEnabled(enabled: Boolean) = queueManager.setShuffleEnabled(enabled)

    /** Cycles repeat off -> all -> one -> off (matches YouTube Music). "All"
     *  loops the queue itself (see [PlaybackQueueManager.skipToNext]); "one"
     *  repeats just the current track. */
    fun cycleRepeatMode() = queueManager.cycleRepeatMode()

    /** Stops playback and clears the current track entirely -- what the
     *  mini-player's close button dismisses itself with (its visibility is
     *  driven by currentTrack being non-null). */
    fun stop() {
        queueManager.reset()
        viewModelScope.launch {
            val controller = controllerReady.await()
            controller.stop()
            controller.clearMediaItems()
        }
    }

    fun startSleepTimer(durationMillis: Long) = sleepTimerController.startCountdown(durationMillis)

    fun startSleepTimerAtEndOfTrack() = sleepTimerController.startAtEndOfTrack()

    fun cancelSleepTimer() = sleepTimerController.cancel()

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }

    private companion object {
        const val TAG = "PlayerViewModel"
        // Once a second: smooth enough for a scrubber (seekTo updates
        // optimistically), and halves recomposition of uiState collectors.
        const val POSITION_UPDATE_MILLIS = 1_000L
    }
}
