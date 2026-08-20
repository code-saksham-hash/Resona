package com.resona.music.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.resona.music.core.player.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the single [ExoPlayer]/[MediaSession] pair for the whole app. Holds
 * no dependency on [com.resona.music.domain.repository.MusicRepository] --
 * [PlayerViewModel] resolves each stream URL before handing this service a
 * ready-to-play [androidx.media3.common.MediaItem], so this class only ever
 * deals with playback mechanics (player, session, notification, foreground
 * lifecycle), never networking. One exception: [httpDataSourceFactory]'s
 * User-Agent has to match whatever InnerTube client resolved the current
 * track (see [PlaybackDataSourceModule]) -- [PlayerViewModel] updates it,
 * this service just wires it into ExoPlayer.
 *
 * [httpDataSourceFactory] is wrapped in [RangedHttpDataSource.Factory] first
 * (see its kdoc for why every request needs an explicit byte range), then in
 * a [DefaultDataSource.Factory]. That outer wrap is what makes `file://`
 * MediaItems (downloaded tracks, played back offline) work at all, since
 * neither of the http-only layers beneath it understands anything but
 * http(s).
 *
 * [DefaultMediaNotificationProvider] is marked `@UnstableApi` by Media3
 * itself (via androidx.annotation.RequiresOptIn, not Kotlin's own
 * @OptIn/@RequiresOptIn) -- that's what the class-level @OptIn below is
 * silencing, not a real stability concern in how it's used here.
 */
@AndroidEntryPoint
@OptIn(markerClass = [UnstableApi::class])
class PlayerService : MediaSessionService() {

    @Inject lateinit var httpDataSourceFactory: DefaultHttpDataSource.Factory

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val dataSourceFactory = DefaultDataSource.Factory(this, RangedHttpDataSource.Factory(httpDataSourceFactory))
        // The default LoadControl waits for 2.5 seconds of buffered audio
        // before it will start playback at all, which was most of the delay
        // between tapping a song and actually hearing it. Audio is cheap to
        // buffer compared to video, and RangedHttpDataSource's first request
        // already lands well under a second on a normal connection, so
        // there's little to protect against by waiting that long up front.
        // Kept a bit more cautious after a rebuffer, since that already
        // means something along the way is struggling.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_playback)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification_equalizer)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Swiping the app from recents shouldn't kill an active/queued playback,
     * but should stop the service if nothing is or will be playing --
     * otherwise it lingers as a silent foreground service forever.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "resona_playback"
    }
}
