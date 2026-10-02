package com.resona.music.domain.repository

import com.resona.music.domain.model.EpisodeProgress
import com.resona.music.domain.model.FollowedShow
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastEpisodePage
import com.resona.music.domain.model.PodcastShow
import com.resona.music.domain.model.PodcastShowPage
import com.resona.music.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface PodcastRepository {
    suspend fun searchShows(query: String): List<PodcastShow>

    /** Episode search ranks by popularity and recency, which is what Browse is built on. */
    suspend fun searchEpisodes(query: String): List<PodcastEpisode>

    /** A show's header and its newest episodes. Cached for a few minutes unless [forceRefresh]. */
    suspend fun getShow(browseId: String, forceRefresh: Boolean = false): PodcastShowPage

    /** The next page of [show]'s episodes for a [PodcastShowPage.continuation] token. */
    suspend fun loadMoreEpisodes(show: PodcastShow, continuation: String): PodcastEpisodePage

    /** Followed shows, most recently followed first. */
    fun observeFollowedShows(): Flow<List<FollowedShow>>

    suspend fun followShow(show: PodcastShow)

    suspend fun unfollowShow(browseId: String)

    /** Clears a followed show's new-episode count. A no-op for shows you don't follow. */
    suspend fun markShowSeen(browseId: String)

    /** Saved progress for every episode you've started, keyed by videoId. */
    fun observeEpisodeProgress(): Flow<Map<String, EpisodeProgress>>

    suspend fun getEpisodeProgress(videoId: String): EpisodeProgress?

    suspend fun saveEpisodeProgress(song: Song, positionMillis: Long, durationMillis: Long)
}
