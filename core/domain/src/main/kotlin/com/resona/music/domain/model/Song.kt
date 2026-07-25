package com.resona.music.domain.model

data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String
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
