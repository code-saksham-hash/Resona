package com.resona.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
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
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
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
    val syncedLyrics: List<LyricsLine> = emptyList(),
    val isLooping: Boolean = false,
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

    /** Songs queued for sequential playback. Empty while a single tapped
     *  track is playing *and* its similar-songs radio queue hasn't finished
     *  loading yet (see [attachRadioQueue]). */
    private var queue: List<Song> = emptyList()

    /** Index into [queue] for the currently playing song. */
    private var currentQueueIndex: Int = 0

    /** Bumped on every play() so the radio background fetch can tell whether
     *  it's still the active one -- a stale mix must never attach to a
     *  different track that superseded it while it was loading. */
    private var radioGeneration = 0

    /** In-flight background radio fetch for a single-song tap (see
     *  [attachRadioQueue]) -- cancelled whenever a new play() supersedes it
     *  so a stale mix can never attach to a different track. */
    private var radioQueueJob: Job? = null

    @Volatile
    private var isTransitioning: Boolean = false

    /** How many times the current track has been auto-retried after a
     *  retryable playback error (see [Player.Listener.onPlayerError] below).
     *  Reset whenever [play] starts a genuinely different track. */
    private var streamRetryCount = 0

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

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val id = mediaItem?.mediaId ?: return
                        when {
                            id.startsWith(DUMMY_NEXT) -> skipToNext()
                            id.startsWith(DUMMY_PREV) -> skipToPrevious()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.d(
                            TAG,
                            "onPlayerError: errorCode=${error.errorCodeName}, message=${error.message}",
                            error.cause
                        )
                        val track = _uiState.value.currentTrack
                        val isCurrentTrackStream = track != null &&
                            controller.currentMediaItem?.mediaId == track.videoId
                        // A format url InnerTube marked resolvable can still get
                        // flatly rejected by the CDN (403, "source error") when
                        // the client identity that requested it hasn't picked up
                        // a fully warmed-up visitor token yet -- see
                        // PlayerJsRepository. That's only observable here, once
                        // the player actually opens the connection, so this is
                        // the one place it can be caught. Re-running play()
                        // re-resolves from scratch through the whole client
                        // fallback chain, which usually succeeds once some
                        // request in the meantime has warmed the token up.
                        // Capped, and scoped to IO errors only, so a genuinely
                        // broken/unplayable video still surfaces an error
                        // instead of retrying forever.
                        if (track != null && isCurrentTrackStream &&
                            error.errorCode in RETRYABLE_ERROR_CODES &&
                            streamRetryCount < MAX_STREAM_RETRIES
                        ) {
                            streamRetryCount++
                            Log.d(
                                TAG,
                                "onPlayerError: retrying ${track.videoId} " +
                                    "(attempt $streamRetryCount/$MAX_STREAM_RETRIES)"
                            )
                            viewModelScope.launch {
                                delay(STREAM_RETRY_DELAY_MILLIS)
                                play(track, queue)
                            }
                            return
                        }
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
                if (!isTransitioning && controller.isPlaying) {
                    _uiState.update { it.copy(position = controller.currentPosition.coerceAtLeast(0L)) }
                }
                delay(POSITION_UPDATE_MILLIS)
            }
        }
    }

    fun play(song: Song, queue: List<Song> = emptyList()) {
        if (song.videoId != _uiState.value.currentTrack?.videoId) {
            streamRetryCount = 0
        }
        this.queue = queue
        currentQueueIndex = if (queue.isNotEmpty()) {
            queue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        } else 0

        // A single-track tap (artist/playlist screens always pass their full
        // song list as `queue`) has no queue at all -- the stream should
        // start playing immediately, not wait on a network fetch. So the
        // similar-songs radio is fetched in the background and attached as
        // this track's queue once it lands (see attachRadioQueue). Bump the
        // generation and cancel any in-flight fetch so a superseding tap
        // (or the same track tapped twice) can't attach a stale mix.
        radioGeneration++
        radioQueueJob?.cancel()
        radioQueueJob = if (queue.isEmpty()) {
            viewModelScope.launch { attachRadioQueue(song, radioGeneration) }
        } else {
            null
        }

        viewModelScope.launch {
            val controller = controllerReady.await()

            isTransitioning = true

            val downloadedFilePath = musicRepository.localFileForSong(song.videoId)
            _uiState.update {
                it.copy(
                    currentTrack = song,
                    isPlaying = false,
                    isBuffering = true,
                    position = 0L,
                    duration = 0L,
                    error = null,
                    downloadState = if (downloadedFilePath != null) DownloadState.Downloaded else DownloadState.Idle,
                    isLiked = musicRepository.isLiked(song.videoId),
                    lyricsState = LyricsState.NotLoaded,
                    syncedLyrics = emptyList(),
                    queue = queue
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
                isTransitioning = false
                musicRepository.recordPlay(song)

                if (currentQueueIndex > 0) {
                    val prevSong = queue[currentQueueIndex - 1]
                    val prevDummy = MediaItem.Builder()
                        .setMediaId("${DUMMY_PREV}${prevSong.videoId}")
                        .setUri(mediaUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(prevSong.title)
                                .setArtist(prevSong.artist)
                                .setArtworkUri(prevSong.highResThumbnailUrl.toUri())
                                .build()
                        )
                        .build()
                    controller.addMediaItem(0, prevDummy)
                }
                if (queue.isNotEmpty() && currentQueueIndex < queue.size - 1) {
                    val nextSong = queue[currentQueueIndex + 1]
                    val nextDummy = MediaItem.Builder()
                        .setMediaId("${DUMMY_NEXT}${nextSong.videoId}")
                        .setUri(mediaUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(nextSong.title)
                                .setArtist(nextSong.artist)
                                .setArtworkUri(nextSong.highResThumbnailUrl.toUri())
                                .build()
                        )
                        .build()
                    val nextPos = if (currentQueueIndex > 0) 2 else 1
                    controller.addMediaItem(nextPos, nextDummy)
                }
            } catch (e: CancellationException) {
                isTransitioning = false
                throw e
            } catch (e: Exception) {
                isTransitioning = false
                Log.d(TAG, "play: failed to resolve/prepare stream", e)
                // getStreamSource() can fail outright -- every client in the
                // fallback chain gated, or every format's signature failed to
                // decipher -- before ExoPlayer ever gets a MediaItem to try,
                // so onPlayerError's retry never gets a chance to run. Same
                // retry budget as onPlayerError (shared streamRetryCount):
                // this is the exact "no stream at all" counterpart to that
                // one's "stream url the CDN then rejected".
                if (streamRetryCount < MAX_STREAM_RETRIES) {
                    streamRetryCount++
                    Log.d(
                        TAG,
                        "play: retrying ${song.videoId} (attempt $streamRetryCount/$MAX_STREAM_RETRIES) " +
                            "after resolve failure: ${e.message}"
                    )
                    delay(STREAM_RETRY_DELAY_MILLIS)
                    play(song, queue)
                    return@launch
                }
                _uiState.update {
                    it.copy(isBuffering = false, error = e.message ?: "Unable to play this track")
                }
            }
        }
    }

    /**
     * Background half of a single-track tap (see [play]): resolves the
     * similar-songs radio for [song] and attaches it as this track's queue
     * once it arrives -- the tapped song itself already got [play]'s full
     * immediate path (setMediaItem/prepare/play), so nothing here holds up
     * first-audio. Running as its own coroutine, it only mutates queue state
     * while it still matches the generation captured at launch; anything
     * stale (user tapped another track meanwhile) just returns.
     */
    private suspend fun attachRadioQueue(song: Song, generation: Int) {
        val radio = try {
            musicRepository.getSongRadio(song.videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "attachRadioQueue: no radio for ${song.videoId}: ${e.message}")
            emptyList()
        }
        if (radio.isEmpty() || generation != radioGeneration) return

        val controller = controllerReady.await()

        // The tapped song may be still preparing (play()'s coroutine resolves
        // the stream concurrently) -- wait briefly for it to be the current
        // item so the next-dummy is inserted right after it.
        val radioTimeoutMillis = SystemClock.elapsedRealtime() + RADIO_ATTACH_TIMEOUT_MILLIS
        while (controller.currentMediaItem?.mediaId != song.videoId &&
            SystemClock.elapsedRealtime() < radioTimeoutMillis
        ) {
            delay(50L)
            if (generation != radioGeneration) return
        }
        if (controller.currentMediaItem?.mediaId != song.videoId) return

        // Radio mix normally leads with the tapped track itself, which
        // matches the currently-playing media item (index 0) exactly -- but
        // pin it explicitly and dedupe so index 0 is *always* the tapped
        // song regardless of how the panel was shaped.
        this.queue = (listOf(song) + radio).distinctBy { it.videoId }
        currentQueueIndex = 0
        _uiState.update { it.copy(queue = this.queue) }
        Log.d(TAG, "attachRadioQueue: attached ${this.queue.size} songs as queue for ${song.videoId}")

        if (this.queue.size > 1) {
            val nextSong = this.queue[1]
            val mediaUri = controller.currentMediaItem?.localConfiguration?.uri ?: return
            val nextDummy = MediaItem.Builder()
                .setMediaId("${DUMMY_NEXT}${nextSong.videoId}")
                .setUri(mediaUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(nextSong.title)
                        .setArtist(nextSong.artist)
                        .setArtworkUri(nextSong.highResThumbnailUrl.toUri())
                        .build()
                )
                .build()
            controller.addMediaItem(1, nextDummy)
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

    /** Fetches lyrics for [PlayerUiState.currentTrack], if not already loaded/loading. */
    fun loadLyrics() {
        val track = _uiState.value.currentTrack ?: return
        if (_uiState.value.lyricsState !is LyricsState.NotLoaded) return

        viewModelScope.launch {
            _uiState.updateForTrack(track.videoId) { it.copy(lyricsState = LyricsState.Loading) }

            val (plainText, synced) = try {
                val plainDeferred = viewModelScope.async { musicRepository.getLyrics(track.videoId) }
                val syncedDeferred = viewModelScope.async { musicRepository.getSyncedLyrics(track.title, track.artist) }
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

    fun skipToNext() {
        if (queue.isEmpty()) return
        val nextIndex = currentQueueIndex + 1
        if (nextIndex >= queue.size) return
        currentQueueIndex = nextIndex
        play(queue[nextIndex], queue)
    }

    fun skipToPrevious() {
        if (queue.isEmpty()) {
            seekTo(0)
            return
        }
        val prevIndex = currentQueueIndex - 1
        if (prevIndex < 0) {
            seekTo(0)
            return
        }
        currentQueueIndex = prevIndex
        play(queue[prevIndex], queue)
    }

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
        // A superseding stop() must not have a stale radio queue land after it.
        radioGeneration++
        radioQueueJob?.cancel()
        radioQueueJob = null
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
        // Upper bound on how long a background radio fetch waits for the
        // tapped song to become the current media item before giving up.
        const val RADIO_ATTACH_TIMEOUT_MILLIS = 5_000L
        const val DUMMY_NEXT = "__queue_next__"
        const val DUMMY_PREV = "__queue_prev__"

        // See onPlayerError. A couple of quick retries covers a cold-start
        // gated token; a short delay before each one gives an in-flight
        // background visitor-token fetch (PlayerJsRepository.ensureVisitorData)
        // a beat to land first.
        const val MAX_STREAM_RETRIES = 2
        const val STREAM_RETRY_DELAY_MILLIS = 600L
        // The whole ERROR_CODE_IO_* family (2000-2008) -- a gated/rejected
        // request doesn't always fail as a clean "403 status" IOException.
        // Media3 only assigns BAD_HTTP_STATUS when the failure is a
        // HttpDataSource.InvalidResponseCodeException specifically; a
        // connection the CDN drops or resets mid-read (which is exactly how
        // some anti-abuse rejections behave) surfaces as the generic
        // ERROR_CODE_IO_UNSPECIFIED instead, which a narrower code-by-code
        // set would silently let through unretried.
        val RETRYABLE_ERROR_CODES =
            (PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
                .toSet()
    }
}
