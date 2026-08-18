package com.resona.music.data.history

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Binds SearchHistoryStore to its implementation. Lives here instead of
// :app's Hilt modules for the same reason as extractor/ExtractorModule.kt,
// download/DownloadModule.kt, likes/LikesModule.kt, and this package's own
// PlayHistoryModule.kt: not a domain concept, just an internal detail of how
// :core:data implements MusicRepository's search-history methods. See
// ARCHITECTURE.md.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchHistoryModule {

    @Binds
    internal abstract fun bindSearchHistoryStore(impl: FileSearchHistoryStore): SearchHistoryStore
}
