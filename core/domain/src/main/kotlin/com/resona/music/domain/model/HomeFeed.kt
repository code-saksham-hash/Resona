package com.resona.music.domain.model

/** The whole Home screen's content in one shot -- see MusicRepository.getHomeFeed(). */
data class HomeFeed(
    val sections: List<HomeFeedSection>,
    val popularArtists: List<ArtistSpotlight>
)

data class HomeFeedSection(
    val id: String,
    val title: String,
    val songs: List<Song>
)

data class ArtistSpotlight(
    val name: String,
    val thumbnailUrl: String
)
