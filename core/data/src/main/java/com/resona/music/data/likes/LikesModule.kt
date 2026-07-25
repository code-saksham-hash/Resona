package com.resona.music.data.likes

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Binds LikedSongsStore to its implementation. Lives here instead of :app's
// Hilt modules for the same reason as extractor/ExtractorModule.kt and
// download/DownloadModule.kt: not a domain concept, just an internal detail
// of how :core:data implements MusicRepository.toggleLike(). See
// ARCHITECTURE.md.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LikesModule {

    @Binds
    internal abstract fun bindLikedSongsStore(impl: FileLikedSongsStore): LikedSongsStore
}
