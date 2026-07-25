package com.resona.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
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
import java.io.File
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
    val isLooping: Boolean = false
)

/** [PlayerUiState.downloadState] always describes [PlayerUiState.currentTrack], never a stale one. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data object Downloading : DownloadState
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
 * @OptIn below is for [DefaultHttpDataSource.Factory.setUserAgent], which
 * Media3 marks `@UnstableApi` (see [PlayerService]'s kdoc for the same deal).
 */
@OptIn(markerClass = [UnstableApi::class])
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory,
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
                        Log.d(
                            TAG,
                            "onPlayerError: errorCode=${error.errorCodeName}, message=${error.message}",
                            error.cause
                        )
                        _uiState.update {
                            it.copy(isBuffering = false, error = error.message ?: "Playback error")
                        }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _uiState.update { it.copy(isLooping = repeatMode == Player.REPEAT_MODE_ONE) }
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
                            duration = controller.duration.coerceAtLeast(0L),
                            isLooping = controller.repeatMode == Player.REPEAT_MODE_ONE
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
            // Checked up front so the icons reflect the right state as soon
            // as the track becomes current, not only after a download finishes.
            val downloadedFilePath = musicRepository.localFileForSong(song.videoId)
            _uiState.update {
                it.copy(
                    currentTrack = song,
                    isBuffering = true,
                    error = null,
                    downloadState = if (downloadedFilePath != null) DownloadState.Downloaded else DownloadState.Idle,
                    isLiked = musicRepository.isLiked(song.videoId),
                    lyricsState = LyricsState.NotLoaded
                )
            }
            try {
                val mediaUri = if (downloadedFilePath != null) {
                    Uri.fromFile(File(downloadedFilePath))
                } else {
                    val streamSource = musicRepository.getStreamSource(song.videoId)
                    // has to happen before prepare()/play() or ExoPlayer opens
                    // the connection with the wrong user agent and gets rejected
                    httpDataSourceFactory.setUserAgent(streamSource.userAgent)
                    streamSource.url.toUri()
                }
                val controller = controllerReady.await()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.videoId)
                    .setUri(mediaUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(song.highResThumbnailUrl.toUri())
                            .build()
                    )
                    .build()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
                // Only recorded once playback actually starts -- a failed
                // resolve/prepare shouldn't count as a "play" for Home's
                // Recommended For You to chase (see MusicRepositoryImpl).
                musicRepository.recordPlay(song)
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
            _uiState.updateForTrack(track.videoId) { it.copy(downloadState = DownloadState.Downloading) }
            try {
                val downloaded = musicRepository.downloadSong(track)
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

    /** Fetches lyrics for [PlayerUiState.currentTrack], if not already loaded/loading. */
    fun loadLyrics() {
        val track = _uiState.value.currentTrack ?: return
        if (_uiState.value.lyricsState !is LyricsState.NotLoaded) return

        viewModelScope.launch {
            _uiState.updateForTrack(track.videoId) { it.copy(lyricsState = LyricsState.Loading) }
            val lyrics = try {
                musicRepository.getLyrics(track.videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "loadLyrics: failed for videoId=${track.videoId}", e)
                null
            }
            _uiState.updateForTrack(track.videoId) {
                it.copy(lyricsState = lyrics?.let(LyricsState::Available) ?: LyricsState.Unavailable)
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

    /** Toggles repeating the current track (there's no queue yet -- see
     *  skipToNext/Previous -- so "loop" only ever means repeat-one). */
    fun toggleRepeat() {
        viewModelScope.launch {
            val controller = controllerReady.await()
            controller.repeatMode = if (controller.repeatMode == Player.REPEAT_MODE_ONE) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ONE
            }
        }
    }

    /** Stops playback and clears the current track entirely -- what the
     *  mini-player's close button dismisses itself with (its visibility is
     *  driven by currentTrack being non-null). */
    fun stop() {
        viewModelScope.launch {
            val controller = controllerReady.await()
            controller.stop()
            controller.clearMediaItems()
            _uiState.value = PlayerUiState()
        }
    }

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
