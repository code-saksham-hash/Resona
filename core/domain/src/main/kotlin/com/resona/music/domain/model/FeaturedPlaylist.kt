package com.resona.music.domain.model

/** A real YouTube Music playlist/mix, as shown on the Library screen. Its
 *  songs aren't included here -- see MusicRepository.getPlaylistSongs(). */
data class FeaturedPlaylist(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String
)
