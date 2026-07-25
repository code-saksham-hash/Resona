package com.resona.music.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.resona.music.core.player.R

/**
 * Hosts the single [ExoPlayer]/[MediaSession] pair for the whole app. Holds
 * no dependency on [com.resona.music.domain.repository.MusicRepository] --
 * [PlayerViewModel] resolves each stream URL before handing this service a
 * ready-to-play [androidx.media3.common.MediaItem], so this class only ever
 * deals with playback mechanics (player, session, notification, foreground
 * lifecycle), never networking.
 *
 * [DefaultMediaNotificationProvider] is marked `@UnstableApi` by Media3
 * itself (via androidx.annotation.RequiresOptIn, not Kotlin's own
 * @OptIn/@RequiresOptIn) -- that's what the class-level @OptIn below is
 * silencing, not a real stability concern in how it's used here.
 */
@OptIn(markerClass = [UnstableApi::class])
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://music.youtube.com",
                    "Referer" to "https://music.youtube.com/"
                )
            )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
