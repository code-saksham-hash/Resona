package com.resona.music.domain.model

/** A podcast on YouTube Music. [browseId] is the "MPSP..." id its show page loads from. */
data class PodcastShow(
    val browseId: String,
    val title: String,
    // "" when we only know the show from an episode row, which doesn't name the author.
    val author: String,
    val thumbnailUrl: String
)

data class PodcastEpisode(
    val videoId: String,
    val title: String,
    val showTitle: String,
    // "" when the row didn't link back to its show.
    val showBrowseId: String,
    val thumbnailUrl: String,
    val description: String = "",
    // As YouTube words it: "4d ago", "Sep 23", "Nov 30, 2021".
    val publishedText: String = "",
    // Best-effort parse of publishedText, null if it didn't parse.
    val publishedAtMillis: Long? = null,
    // "55 min", "1 hr 1 min". Search rows don't carry one, so often "".
    val durationText: String = "",
    val durationMillis: Long = 0L
) {
    fun toSong() = Song(
        videoId = videoId,
        title = title,
        artist = showTitle,
        thumbnailUrl = thumbnailUrl,
        duration = durationText,
        isPodcastEpisode = true
    )
}

data class PodcastShowPage(
    val show: PodcastShow,
    val description: String,
    val episodes: List<PodcastEpisode>,
    // Token for the next page of episodes, null on the last one.
    val continuation: String?
)

data class PodcastEpisodePage(
    val episodes: List<PodcastEpisode>,
    val continuation: String?
)

data class FollowedShow(
    val show: PodcastShow,
    val followedAtMillis: Long,
    // Bumped whenever the show page is opened. Anything published after
    // this counts as new.
    val lastSeenAtMillis: Long
)

/** Where you got to in an episode. [song] is a snapshot so Continue listening can render offline. */
data class EpisodeProgress(
    val song: Song,
    val positionMillis: Long,
    val durationMillis: Long,
    val updatedAtMillis: Long,
    val finished: Boolean
) {
    val fraction: Float
        get() = if (durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f

    val remainingMillis: Long
        get() = (durationMillis - positionMillis).coerceAtLeast(0L)

    /** Worth offering in Continue listening: started for real and not done yet. */
    val isInProgress: Boolean
        get() = !finished && positionMillis >= MIN_IN_PROGRESS_MILLIS

    companion object {
        const val MIN_IN_PROGRESS_MILLIS = 60_000L
    }
}
