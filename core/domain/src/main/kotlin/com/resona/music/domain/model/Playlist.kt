package com.resona.music.domain.model

/**
 * A playlist the user created on-device (["Your Library" quick links], in
 * contrast to [FeaturedPlaylist], which is a read-only YouTube Music shelf).
 * Created playlists start empty; full song management comes later.
 */
data class Playlist(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val songs: List<Song> = emptyList()
)