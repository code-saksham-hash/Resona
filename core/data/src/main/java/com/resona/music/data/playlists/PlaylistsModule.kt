package com.resona.music.data.playlists

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Binds UserPlaylistsStore to its implementation. Lives here instead of
// :app's Hilt modules for the same reason as LikesModule and DownloadModule:
// not a domain concept, just an internal detail of how :core:data
// implements MusicRepository.observePlaylists(). See ARCHITECTURE.md.
@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaylistsModule {

    @Binds
    internal abstract fun bindUserPlaylistsStore(impl: FileUserPlaylistsStore): UserPlaylistsStore
}