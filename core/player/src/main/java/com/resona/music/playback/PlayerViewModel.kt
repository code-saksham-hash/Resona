package com.resona.music.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val error: String? = null
)

/**
 * Shared across every screen (obtained once, Activity-scoped, at the
 * navigation root) so playback state and controls are available anywhere
 * without each screen owning its own player connection. Talks to
 * [PlayerService] exclusively through a [MediaController] -- it never
 * touches an [androidx.media3.exoplayer.ExoPlayer] directly, so playback
 * keeps running in the service regardless of this ViewModel's lifecycle.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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

                    override fun onPlayerError(error: PlaybackException) {
                        Log.d(TAG, "onPlayerError: errorCode=${error.errorCodeName}, message=${error.message}")
                        _uiState.update {
                            it.copy(isBuffering = false, error = error.message ?: "Playback error")
                        }
                    }
                })

                // Resync with playback already under way -- the foreground
                // service can outlive this ViewModel (screen rotation,
                // process restart, returning from the background), so on a
                // fresh connection it may already have an active track.
                if (controller.currentMediaItem != null) {
                    _uiState.update {
                        it.copy(
                            currentTrack = controller.currentMediaItem?.toSong(),
                            isPlaying = controller.isPlaying,
                            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                            position = controller.currentPosition.coerceAtLeast(0L),
                            duration = controller.duration.coerceAtLeast(0L)
                        )
                    }
                }

                controllerReady.complete(controller)
            },
            MoreExecutors.directExecutor()
        )

        // Player.Listener has no "position changed" callback, so the only
        // way to keep a scrubber live is to poll it while something plays.
        viewModelScope.launch {
            val controller = controllerReady.await()
            while (isActive) {
                if (controller.isPlaying) {
                    _uiState.update { it.copy(position = controller.currentPosition.coerceAtLeast(0L)) }
                }
                delay(POSITION_UPDATE_MILLIS)
            }
        }
    }

    fun play(song: Song) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentTrack = song, isBuffering = true, error = null) }
            try {
                val streamUrl = musicRepository.getStreamUrl(song.videoId)
                val controller = controllerReady.await()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.videoId)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(song.thumbnailUrl.toUri())
                            .build()
                    )
                    .build()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "play: failed to resolve/prepare stream", e)
                _uiState.update {
                    it.copy(isBuffering = false, error = e.message ?: "Unable to play this track")
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            val controller = controllerReady.await()
            controller.seekTo(positionMs)
            // Applied optimistically so the elapsed-time label snaps to the
            // released position immediately, instead of waiting up to
            // POSITION_UPDATE_MILLIS for the poll loop to catch up.
            _uiState.update { it.copy(position = positionMs.coerceAtLeast(0L)) }
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

    /** No-op until there's a real queue to move through -- wired up now so
     *  callers have a stable API when that queue lands. */
    fun skipToNext() = Unit

    /** No-op until there's a real queue to move through -- wired up now so
     *  callers have a stable API when that queue lands. */
    fun skipToPrevious() = Unit

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }

    private fun MediaItem.toSong(): Song = Song(
        videoId = mediaId,
        title = mediaMetadata.title?.toString() ?: "",
        artist = mediaMetadata.artist?.toString() ?: "",
        thumbnailUrl = mediaMetadata.artworkUri?.toString() ?: ""
    )

    private companion object {
        const val TAG = "PlayerViewModel"
        const val POSITION_UPDATE_MILLIS = 500L
    }
}
