package com.resona.music.domain.model

data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    // "" when InnerTube didn't report one for this row (e.g. omitted when the
    // search query already names the artist -- see extractSongs()).
    val duration: String = ""
)
