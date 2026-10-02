package com.resona.music.data.repository

import com.resona.music.data.podcasts.EpisodeProgressStore
import com.resona.music.data.podcasts.FollowedShowsStore
import com.resona.music.data.podcasts.PodcastTime
import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.data.remote.innertube.models.InnerTubePodcastEpisode
import com.resona.music.data.remote.innertube.models.extractEpisodeResults
import com.resona.music.data.remote.innertube.models.extractEpisodesContinuation
import com.resona.music.data.remote.innertube.models.extractPodcastHeader
import com.resona.music.data.remote.innertube.models.extractPodcastShows
import com.resona.music.data.remote.innertube.models.extractShowEpisodes
import com.resona.music.domain.model.EpisodeProgress
import com.resona.music.domain.model.FollowedShow
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastEpisodePage
import com.resona.music.domain.model.PodcastShow
import com.resona.music.domain.model.PodcastShowPage
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.PodcastRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepositoryImpl @Inject internal constructor(
    private val api: InnerTubeApi,
    private val followedShowsStore: FollowedShowsStore,
    private val progressStore: EpisodeProgressStore,
) : PodcastRepository {

    // The hub refreshes every followed show each time it opens, so a short
    // cache keeps back-and-forth navigation from refetching all of them.
    private val showCache = ConcurrentHashMap<String, CachedShow>()

    // Responses run 100 to 600 KB of JSON and the hub can pull several at
    // once, so decoding and walking them stays off the main thread.
    override suspend fun searchShows(query: String): List<PodcastShow> = withContext(Dispatchers.Default) {
        api.searchPodcasts(query).extractPodcastShows().map {
            PodcastShow(browseId = it.browseId, title = it.title, author = it.author, thumbnailUrl = it.thumbnailUrl)
        }
    }

    override suspend fun searchEpisodes(query: String): List<PodcastEpisode> = withContext(Dispatchers.Default) {
        val now = ZonedDateTime.now()
        api.searchEpisodes(query).extractEpisodeResults().map { it.toDomain(now) }
    }

    override suspend fun getShow(browseId: String, forceRefresh: Boolean): PodcastShowPage {
        val cached = showCache[browseId]
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.fetchedAtMillis < SHOW_CACHE_MILLIS) {
            return cached.page
        }
        val page = withContext(Dispatchers.Default) {
            val response = api.browse(browseId)
            val header = response.extractPodcastHeader() ?: throw IllegalStateException("Couldn't load this podcast")
            val show = PodcastShow(browseId = browseId, title = header.title, author = header.author, thumbnailUrl = header.thumbnailUrl)
            val now = ZonedDateTime.now()
            PodcastShowPage(
                show = show,
                description = header.description,
                episodes = response.extractShowEpisodes().map { it.toDomain(now, show) },
                continuation = response.extractEpisodesContinuation()
            )
        }
        showCache[browseId] = CachedShow(System.currentTimeMillis(), page)
        followedShowsStore.updateShowInfo(page.show)
        return page
    }

    override suspend fun loadMoreEpisodes(show: PodcastShow, continuation: String): PodcastEpisodePage =
        withContext(Dispatchers.Default) {
            val response = api.browseContinuation(continuation)
            val now = ZonedDateTime.now()
            PodcastEpisodePage(
                episodes = response.extractShowEpisodes().map { it.toDomain(now, show) },
                continuation = response.extractEpisodesContinuation()
            )
        }

    override fun observeFollowedShows(): Flow<List<FollowedShow>> = followedShowsStore.shows

    override suspend fun followShow(show: PodcastShow) = followedShowsStore.follow(show, System.currentTimeMillis())

    override suspend fun unfollowShow(browseId: String) = followedShowsStore.unfollow(browseId)

    override suspend fun markShowSeen(browseId: String) = followedShowsStore.markSeen(browseId, System.currentTimeMillis())

    override fun observeEpisodeProgress(): Flow<Map<String, EpisodeProgress>> = progressStore.progress

    override suspend fun getEpisodeProgress(videoId: String): EpisodeProgress? = progressStore.progress.value[videoId]

    override suspend fun saveEpisodeProgress(song: Song, positionMillis: Long, durationMillis: Long) {
        if (durationMillis <= 0L) return
        val position = positionMillis.coerceIn(0L, durationMillis)
        progressStore.save(
            EpisodeProgress(
                song = song.copy(isPodcastEpisode = true),
                positionMillis = position,
                durationMillis = durationMillis,
                updatedAtMillis = System.currentTimeMillis(),
                // Most shows end on credits or an ad, so the last stretch counts as done.
                finished = durationMillis - position <= FINISHED_SLACK_MILLIS
            )
        )
    }

    private fun InnerTubePodcastEpisode.toDomain(now: ZonedDateTime, show: PodcastShow? = null) = PodcastEpisode(
        videoId = videoId,
        title = title,
        showTitle = show?.title ?: showTitle,
        showBrowseId = show?.browseId ?: showBrowseId,
        thumbnailUrl = thumbnailUrl,
        description = description,
        publishedText = publishedText,
        publishedAtMillis = PodcastTime.parsePublished(publishedText, now),
        durationText = durationText,
        durationMillis = PodcastTime.parseDuration(durationText)
    )

    private class CachedShow(val fetchedAtMillis: Long, val page: PodcastShowPage)

    private companion object {
        const val SHOW_CACHE_MILLIS = 10 * 60_000L
        const val FINISHED_SLACK_MILLIS = 30_000L
    }
}
