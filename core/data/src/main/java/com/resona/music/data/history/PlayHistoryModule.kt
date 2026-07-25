package com.resona.music.data.history

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Binds PlayHistoryStore to its implementation. Lives here instead of :app's
// Hilt modules for the same reason as extractor/ExtractorModule.kt,
// download/DownloadModule.kt, and likes/LikesModule.kt: not a domain
// concept, just an internal detail of how :core:data implements
// MusicRepository.recordPlay()/getHomeFeed(). See ARCHITECTURE.md.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlayHistoryModule {

    @Binds
    internal abstract fun bindPlayHistoryStore(impl: FilePlayHistoryStore): PlayHistoryStore
}
