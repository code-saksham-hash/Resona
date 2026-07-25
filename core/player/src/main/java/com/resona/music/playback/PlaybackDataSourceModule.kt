package com.resona.music.playback

import androidx.media3.datasource.DefaultHttpDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// One factory shared between PlayerViewModel (sets the User-Agent per track,
// see StreamSource) and PlayerService (ExoPlayer streams with it). Mutating
// a shared factory instead of threading per-item headers through Media3
// works fine here since Resona only ever streams one track at a time.
@Module
@InstallIn(SingletonComponent::class)
internal object PlaybackDataSourceModule {
    @Provides
    @Singleton
    fun provideHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
}
