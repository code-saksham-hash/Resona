package com.resona.music.domain.model

/** A real album with its full tracklist -- see MusicRepository.getAlbum(). */
data class Album(
    val browseId: String,
    val title: String,
    val artistName: String,
    /** Lets the album page link back to the artist -- null for a
     *  various-artists compilation InnerTube didn't attach a channel to. */
    val artistBrowseId: String?,
    val thumbnailUrl: String,
    val year: String,
    val songs: List<Song> = emptyList(),
)

/** A lightweight artist match -- Search screen's circular-avatar results,
 *  and how ArtistDetailViewModel resolves a plain artist-name reference
 *  (Home/Stats/History never carry a real InnerTube identity) into a
 *  browseId it can hand to MusicRepository.getArtist(). */
data class ArtistSummary(
    val browseId: String,
    val name: String,
    val thumbnailUrl: String,
)

/** One page of a Songs-filtered search -- see MusicRepository.searchSongsPage()/
 *  loadMoreSongResults(). [continuationToken] is null once there's nothing
 *  more to load. */
data class SongSearchPage(
    val songs: List<Song>,
    val continuationToken: String?,
)
