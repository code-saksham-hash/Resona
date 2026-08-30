package com.resona.music.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RepeatMode { OFF, ALL, ONE }

/** Everything a UI needs to render the queue, mirrored from [PlaybackQueueManager.state]. */
data class QueueState(
    val currentSong: Song? = null,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isResolving: Boolean = false,
    val error: String? = null,
)

/**
 * Owns queue state and the lazy-resolution "placeholder item" mechanism, attached
 * directly to [PlayerService]'s [Player] rather than reached through a
 * [androidx.media3.session.MediaController]. That's the whole reason this exists as
 * a separate Hilt singleton instead of living in [PlayerViewModel] like it used to:
 * a MediaController -- and any [Player.Listener] registered on one -- dies with the
 * Activity that created it, but [PlayerService] deliberately keeps running (see its
 * onTaskRemoved()) after the app's task is swiped away, so system media controls
 * (notification, lock screen) and end-of-track auto-advance both need to keep
 * working with zero Activity/ViewModel around. Injected into both [PlayerService]
 * (which [attach]es the live player once, in onCreate()) and [PlayerViewModel]
 * (which delegates play/skip/shuffle/repeat calls here and observes [state] instead
 * of duplicating queue state) -- same one-instance-shared-with-the-service pattern
 * this module already uses for [DefaultHttpDataSource.Factory] (see
 * PlaybackDataSourceModule).
 *
 * Resolving a playable stream for a [Song] is an async network call
 * ([MusicRepository.getStreamSource]), but ExoPlayer needs a real URI up front to
 * populate a timeline slot at all -- so the previous/next slot is filled with a
 * "placeholder" [MediaItem] that reuses the *currently playing* item's
 * already-resolved URI (so it's always immediately playable, no stall) but carries
 * the *neighboring* song's real metadata (title/artist/art) via a
 * [DUMMY_PREV]/[DUMMY_NEXT]-prefixed mediaId, so system media controls show
 * correct-looking prev/next info and enable the skip buttons before paying for a
 * real resolve. [attach]'s [Player.Listener] detects landing on one of these (by
 * that id prefix) and swaps in the real resolved stream -- this fires identically
 * whether the transition was a user tapping skip (in-app or from system controls)
 * or ExoPlayer's own end-of-track auto-advance, which is what makes both "skip" and
 * "autoplay into the next song" work, headless or not.
 */
@Singleton
@OptIn(markerClass = [UnstableApi::class])
class PlaybackQueueManager @Inject constructor(
    private val musicRepository: MusicRepository,
    private val httpDataSourceFactory: DefaultHttpDataSource.Factory,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var player: Player

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    /** The effective play order [skipToNext]/[skipToPrevious] walk -- the real
     *  queue when not shuffled, or the shuffled-remainder order when shuffled
     *  (see [setShuffleEnabled]). */
    private var queue: List<Song> = emptyList()
    private var currentQueueIndex: Int = 0

    /** Snapshot of [queue] from immediately before shuffling was turned on, so
     *  turning it back off restores exact original order. Null whenever shuffle
     *  is off. */
    private var preShuffleQueue: List<Song>? = null

    private var repeatMode: RepeatMode = RepeatMode.OFF

    private var radioGeneration = 0
    private var radioQueueJob: Job? = null
    private var retryJob: Job? = null

    private var streamRetryCount = 0
    private val excludedClients = mutableSetOf<String>()

    fun attach(player: Player) {
        this.player = player
        player.addListener(object : Player.Listener {
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
                val track = _state.value.currentSong
                val isCurrentTrackStream = track != null && player.currentMediaItem?.mediaId == track.videoId
                // See resolveAndPlay's catch block for why this is retried rather
                // than surfaced immediately -- same CDN-rejects-a-resolvable-url
                // situation, just caught after ExoPlayer already opened the
                // connection instead of before.
                if (track != null && isCurrentTrackStream &&
                    error.errorCode in RETRYABLE_ERROR_CODES &&
                    streamRetryCount < MAX_STREAM_RETRIES
                ) {
                    streamRetryCount++
                    excludedClients.clear()
                    Log.d(
                        TAG,
                        "onPlayerError: retrying ${track.videoId} (attempt $streamRetryCount/$MAX_STREAM_RETRIES)"
                    )
                    retryJob = scope.launch {
                        musicRepository.refreshStreamIdentity()
                        delay(STREAM_RETRY_DELAY_MILLIS)
                        resolveAndPlay(track)
                    }
                    return
                }
                _state.update { it.copy(isResolving = false, error = error.message ?: "Playback error") }
            }
        })
    }

    /** Starts a brand-new queue context (user tapped a song from a playlist,
     *  search result, etc). Resets shuffle -- for advancing within the *same*
     *  queue, see [skipToNext]/[skipToPrevious], which don't. */
    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        this.queue = queue
        currentQueueIndex = if (queue.isNotEmpty()) {
            queue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        } else 0
        preShuffleQueue = null

        // A single-track tap (queue empty) gets its continuation queue from a
        // background radio fetch instead -- see attachRadioQueue. Bump the
        // generation and cancel any in-flight fetch so a superseding tap can't
        // attach a stale mix.
        radioGeneration++
        radioQueueJob?.cancel()
        radioQueueJob = if (queue.isEmpty()) {
            scope.launch { attachRadioQueue(song, radioGeneration) }
        } else null

        _state.update { it.copy(isShuffled = false) }
        resolveAndPlay(song)
    }

    fun skipToNext() {
        if (queue.isEmpty()) return
        var nextIndex = currentQueueIndex + 1
        if (nextIndex >= queue.size) {
            if (repeatMode == RepeatMode.ALL) nextIndex = 0 else return
        }
        currentQueueIndex = nextIndex
        radioGeneration++
        radioQueueJob?.cancel()
        resolveAndPlay(queue[nextIndex])
    }

    fun skipToPrevious() {
        if (queue.isEmpty()) {
            player.seekTo(0)
            return
        }
        val prevIndex = currentQueueIndex - 1
        if (prevIndex < 0) {
            player.seekTo(0)
            return
        }
        currentQueueIndex = prevIndex
        radioGeneration++
        radioQueueJob?.cancel()
        resolveAndPlay(queue[prevIndex])
    }

    /** Reorders the *upcoming* portion of [queue] only -- the currently playing
     *  song and everything before it stay put, matching YouTube Music. Turning
     *  shuffle back off restores the exact pre-shuffle order via [preShuffleQueue]. */
    fun setShuffleEnabled(enabled: Boolean) {
        if (enabled == (preShuffleQueue != null)) return
        if (enabled) {
            preShuffleQueue = queue
            val splitAt = (currentQueueIndex + 1).coerceIn(0, queue.size)
            queue = queue.subList(0, splitAt) + queue.subList(splitAt, queue.size).shuffled()
        } else {
            val original = preShuffleQueue ?: return
            val currentId = _state.value.currentSong?.videoId
            queue = original
            currentQueueIndex = original.indexOfFirst { it.videoId == currentId }.coerceAtLeast(0)
            preShuffleQueue = null
        }
        reindexPlaceholders()
        _state.update { it.copy(queue = queue, currentIndex = currentQueueIndex, isShuffled = enabled) }
    }

    fun cycleRepeatMode() {
        val next = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        repeatMode = next
        // Only ONE maps to a native repeat mode -- ALL loops over this manager's
        // own queue (see skipToNext), not the raw 2-3 item placeholder window
        // ExoPlayer's own timeline holds, so REPEAT_MODE_ALL would do the wrong
        // thing here.
        player.repeatMode = if (next == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _state.update { it.copy(repeatMode = next) }
        // Refreshes the next-track placeholder so toggling ALL on/off while
        // already parked on the last track takes effect immediately instead
        // of only on the next skip (see rebuildPlaceholders).
        reindexPlaceholders()
    }

    fun reset() {
        radioGeneration++
        radioQueueJob?.cancel()
        radioQueueJob = null
        retryJob?.cancel()
        retryJob = null
        queue = emptyList()
        currentQueueIndex = 0
        preShuffleQueue = null
        repeatMode = RepeatMode.OFF
        _state.value = QueueState()
    }

    /** Cheap best-effort connectivity check -- only used to fail a
     *  non-downloaded song fast with a clear reason (see [resolveAndPlay])
     *  instead of spending the whole network-retry budget discovering the
     *  device is plainly offline. Never used to gate a *downloaded* song,
     *  which must play regardless of connectivity. */
    private fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun resolveAndPlay(song: Song) {
        if (song.videoId != _state.value.currentSong?.videoId) {
            streamRetryCount = 0
            excludedClients.clear()
        }
        // A source-error retry schedules itself a beat in the future. If the
        // user skips or taps another track before that beat is up, this stops
        // it from firing afterward and quietly undoing the skip.
        retryJob?.cancel()

        _state.update {
            it.copy(currentSong = song, queue = queue, currentIndex = currentQueueIndex, isResolving = true, error = null)
        }

        scope.launch {
            val downloadedFilePath = musicRepository.localFileForSong(song.videoId)

            // A non-downloaded song has no path to play offline at all --
            // fail immediately with a clear reason instead of burning the
            // whole retry budget finding out the network genuinely isn't
            // there (see MAX_STREAM_RETRIES/STREAM_RETRY_DELAY_MILLIS below;
            // each attempt round-trips refreshStreamIdentity() too, which
            // would itself just fail the same way). Never applies to a
            // downloaded song, which must keep playing regardless of
            // connectivity. Fixes #17: without this, resolution simply threw
            // below with nothing having touched the player yet, so whatever
            // was already loaded (e.g. a previously-downloaded track) kept
            // playing right through the failure -- silently contradicting
            // the error (and this song's title/art) now showing on top of it.
            if (downloadedFilePath == null && !isOnline()) {
                Log.d(TAG, "resolveAndPlay: ${song.videoId} isn't downloaded and there's no network")
                player.stop()
                player.clearMediaItems()
                _state.update {
                    it.copy(isResolving = false, error = "\"${song.title}\" isn't downloaded, and you're offline")
                }
                return@launch
            }

            try {
                val mediaUri = if (downloadedFilePath != null) {
                    Uri.fromFile(File(downloadedFilePath))
                } else {
                    val streamSource = musicRepository.getStreamSource(song.videoId, excludedClients)
                    excludedClients += streamSource.clientName
                    // has to happen before prepare()/play() or ExoPlayer opens the
                    // connection with the wrong user agent and gets rejected
                    httpDataSourceFactory.setUserAgent(streamSource.userAgent)
                    streamSource.url.toUri()
                }
                player.setMediaItem(buildRealMediaItem(song, mediaUri))
                player.prepare()
                player.play()
                _state.update { it.copy(isResolving = false) }
                musicRepository.recordPlay(song)
                rebuildPlaceholders(mediaUri)
            } catch (e: CancellationException) {
                _state.update { it.copy(isResolving = false) }
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "resolveAndPlay: failed to resolve/prepare stream for ${song.videoId}", e)
                if (streamRetryCount < MAX_STREAM_RETRIES) {
                    streamRetryCount++
                    excludedClients.clear()
                    Log.d(
                        TAG,
                        "resolveAndPlay: retrying ${song.videoId} (attempt $streamRetryCount/$MAX_STREAM_RETRIES) " +
                            "after resolve failure: ${e.message}"
                    )
                    retryJob = scope.launch {
                        musicRepository.refreshStreamIdentity()
                        delay(STREAM_RETRY_DELAY_MILLIS)
                        resolveAndPlay(song)
                    }
                    return@launch
                }
                // Retries exhausted -- same reasoning as the offline
                // fast-path above: nothing here has given the player a
                // playable item for `song` yet, so whatever it already had
                // loaded (if anything) must be cleared rather than left
                // playing under this song's now-failed UI.
                player.stop()
                player.clearMediaItems()
                _state.update { it.copy(isResolving = false, error = e.message ?: "Unable to play this track") }
            }
        }
    }

    /**
     * Background half of a single-track tap (see [playSong]): resolves the
     * similar-songs radio for [song] and attaches it as this track's queue once
     * it arrives. If there's no network (or InnerTube just has nothing for this
     * song), falls back to other downloaded songs -- shuffled, excluding [song]
     * itself -- so offline playback of a single downloaded track still autoplays
     * into something instead of just stopping once it ends. Running as its own
     * coroutine, it only mutates queue state while it still matches the
     * generation captured at launch; anything stale (user tapped another track
     * meanwhile) just returns.
     */
    private suspend fun attachRadioQueue(song: Song, generation: Int) {
        var radio = try {
            musicRepository.getSongRadio(song.videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "attachRadioQueue: no radio for ${song.videoId}: ${e.message}")
            emptyList()
        }
        if (radio.isEmpty()) {
            radio = try {
                musicRepository.observeDownloadedSongs().first()
                    .map { it.song }
                    .filterNot { it.videoId == song.videoId }
                    .shuffled()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (radio.isNotEmpty()) {
                Log.d(TAG, "attachRadioQueue: no network radio for ${song.videoId}, falling back to ${radio.size} downloaded song(s)")
            }
        }
        if (radio.isEmpty() || generation != radioGeneration) return

        // The tapped song may still be preparing (resolveAndPlay's coroutine
        // resolves the stream concurrently) -- wait briefly for it to become the
        // current item so the next-placeholder is inserted right after it.
        val radioTimeoutMillis = SystemClock.elapsedRealtime() + RADIO_ATTACH_TIMEOUT_MILLIS
        while (player.currentMediaItem?.mediaId != song.videoId &&
            SystemClock.elapsedRealtime() < radioTimeoutMillis
        ) {
            delay(50L)
            if (generation != radioGeneration) return
        }
        if (player.currentMediaItem?.mediaId != song.videoId) return

        // Radio mix normally leads with the tapped track itself, matching the
        // currently-playing item (index 0) exactly -- but pin it explicitly and
        // dedupe so index 0 is *always* the tapped song regardless of how the
        // mix (or the downloaded-songs fallback) was shaped.
        queue = (listOf(song) + radio).distinctBy { it.videoId }
        currentQueueIndex = 0
        // Shuffle may have been toggled on while this fetch was still in
        // flight -- preShuffleQueue would otherwise be left holding the
        // pre-radio (e.g. empty) queue, corrupting a later shuffle-off (it
        // would restore that stale queue instead of the real one). Re-sync
        // it to this freshly attached queue and shuffle its tail, same
        // transform setShuffleEnabled(true) applies.
        if (preShuffleQueue != null) {
            preShuffleQueue = queue
            queue = queue.subList(0, 1) + queue.subList(1, queue.size).shuffled()
        }
        _state.update { it.copy(queue = queue, currentIndex = 0) }

        if (queue.size > 1) {
            val mediaUri = player.currentMediaItem?.localConfiguration?.uri ?: return
            player.addMediaItem(1, buildPlaceholderMediaItem(DUMMY_NEXT, queue[1], mediaUri))
        }
    }

    /** Rebuilds the prev/next placeholder slots around [currentQueueIndex] using
     *  [currentMediaUri] (the just-resolved real item's own URI). Shared by
     *  [resolveAndPlay] and [attachRadioQueue]. */
    private fun rebuildPlaceholders(currentMediaUri: Uri) {
        if (currentQueueIndex > 0) {
            player.addMediaItem(0, buildPlaceholderMediaItem(DUMMY_PREV, queue[currentQueueIndex - 1], currentMediaUri))
        }
        // On the last track, ExoPlayer's own timeline has nothing left to
        // auto-advance into at end-of-track -- a repeat-ALL wrap has to be
        // modeled as an explicit placeholder back to queue[0], or playback
        // just ends there instead of looping (skipToNext already wraps fine
        // on its own; this is only for the *unattended* end-of-track case).
        val nextSong = when {
            queue.isEmpty() -> null
            currentQueueIndex < queue.size - 1 -> queue[currentQueueIndex + 1]
            repeatMode == RepeatMode.ALL -> queue[0]
            else -> null
        }
        if (nextSong != null) {
            val nextPos = if (currentQueueIndex > 0) 2 else 1
            player.addMediaItem(nextPos, buildPlaceholderMediaItem(DUMMY_NEXT, nextSong, currentMediaUri))
        }
    }

    /** Drops every item but the one currently playing, then calls
     *  [rebuildPlaceholders] to add fresh placeholders for the new neighbors --
     *  used after [setShuffleEnabled] changes which songs those are. */
    private fun reindexPlaceholders() {
        val current = player.currentMediaItem ?: return
        val mediaUri = current.localConfiguration?.uri ?: return
        val currentWindowIndex = player.currentMediaItemIndex
        if (currentWindowIndex + 1 < player.mediaItemCount) {
            player.removeMediaItems(currentWindowIndex + 1, player.mediaItemCount)
        }
        if (currentWindowIndex > 0) {
            player.removeMediaItems(0, currentWindowIndex)
        }
        rebuildPlaceholders(mediaUri)
    }

    private fun buildRealMediaItem(song: Song, uri: Uri): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.videoId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.highResThumbnailUrl.toUri())
                    .build()
            )
            .build()

    private fun buildPlaceholderMediaItem(idPrefix: String, song: Song, uri: Uri): MediaItem =
        MediaItem.Builder()
            .setMediaId("$idPrefix${song.videoId}")
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.highResThumbnailUrl.toUri())
                    .build()
            )
            .build()

    private companion object {
        const val TAG = "PlaybackQueueManager"
        const val DUMMY_NEXT = "__queue_next__"
        const val DUMMY_PREV = "__queue_prev__"

        // Upper bound on how long a background radio fetch waits for the tapped
        // song to become the current media item before giving up.
        const val RADIO_ATTACH_TIMEOUT_MILLIS = 5_000L

        // Each retry mints a fresh visitor identity and gets a clean shot at the
        // whole client chain again, so this is really "how many different
        // identities are worth trying" rather than a client count.
        // STREAM_RETRY_DELAY_MILLIS is just pacing between attempts.
        const val MAX_STREAM_RETRIES = 4
        const val STREAM_RETRY_DELAY_MILLIS = 600L

        // The whole ERROR_CODE_IO_* family (2000-2008) -- a gated/rejected
        // request doesn't always fail as a clean "403 status" IOException. Media3
        // only assigns BAD_HTTP_STATUS when the failure is a
        // HttpDataSource.InvalidResponseCodeException specifically; a connection
        // the CDN drops or resets mid-read surfaces as the generic
        // ERROR_CODE_IO_UNSPECIFIED instead, which a narrower code-by-code set
        // would silently let through unretried.
        val RETRYABLE_ERROR_CODES =
            (PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
                .toSet()
    }
}
