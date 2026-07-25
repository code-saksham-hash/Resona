package com.resona.music.data.download

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Binds the download subsystem's interfaces to their implementations. Lives
// here instead of :app's Hilt modules for the same reason as
// extractor/ExtractorModule.kt: SongDownloader/DownloadedSongsStore aren't
// domain concepts, just internal details of how :core:data implements
// MusicRepository.downloadSong(). See ARCHITECTURE.md.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DownloadModule {

    @Binds
    internal abstract fun bindSongDownloader(impl: HttpSongDownloader): SongDownloader

    @Binds
    internal abstract fun bindDownloadedSongsStore(impl: FileDownloadedSongsStore): DownloadedSongsStore
}
