package com.resona.music.domain.model

/** A real artist channel profile -- see MusicRepository.getArtist(). */
data class Artist(
    val browseId: String,
    val name: String,
    val thumbnailUrl: String,
    val description: String = "",
    /** "20.5M subscribers"/"6.65M monthly audience"-style text, or blank if
     *  this artist was reached by name only and InnerTube's real channel
     *  couldn't be resolved (see ArtistDetailViewModel) -- callers should
     *  hide the stat entirely rather than render it blank. */
    val listenerCountText: String = "",
    val topSongs: List<Song> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val singles: List<AlbumSummary> = emptyList(),
)

/** An album/single card on an artist's page -- enough to render the chip and
 *  navigate into the full [Album] via [browseId]. */
data class AlbumSummary(
    val browseId: String,
    val title: String,
    val thumbnailUrl: String,
    val year: String,
)
