package com.resona.music.data.podcasts

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Internal storage details of PodcastRepositoryImpl, bound here for the same
// reason as PlaylistsModule. The domain binding itself lives in :app.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PodcastsModule {

    @Binds
    internal abstract fun bindFollowedShowsStore(impl: FileFollowedShowsStore): FollowedShowsStore

    @Binds
    internal abstract fun bindEpisodeProgressStore(impl: FileEpisodeProgressStore): EpisodeProgressStore
}
