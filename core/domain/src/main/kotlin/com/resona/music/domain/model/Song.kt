package com.resona.music.domain.model

data class Song(
    val videoId: String,
    val title: String,
    // "" when InnerTube omitted the artist run entirely for this row (e.g. a
    // search result whose artist matches the search query itself -- see
    // extractSongs()). Render [displayArtist], not this, so that never shows
    // up as a blank line.
    val artist: String,
    val thumbnailUrl: String,
    // "" when InnerTube didn't report a duration for this row.
    val duration: String = "",
    // Podcast episodes ride the same player as songs, but skip radio and
    // play history, resume where you left off, and get skip back/forward
    // controls instead of prev/next.
    val isPodcastEpisode: Boolean = false
) {
    val highResThumbnailUrl: String
        get() {
            val pattern = Regex("https?://i\\.ytimg\\.com/vi/$videoId/")
            if (pattern.containsMatchIn(thumbnailUrl)) {
                return thumbnailUrl.replace(pattern, "https://i.ytimg.com/vi/$videoId/")
                    .replaceAfterLast("/", "maxresdefault.jpg")
                    .trimEnd('/')
            }
            return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        }

    val displayArtist: String
        get() = artist.ifBlank { "Unknown Artist" }
}
