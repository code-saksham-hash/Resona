package com.resona.music.domain.model

data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    // "" when InnerTube didn't report one for this row (e.g. omitted when the
    // search query already names the artist -- see extractSongs()).
    val duration: String = ""
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
}
