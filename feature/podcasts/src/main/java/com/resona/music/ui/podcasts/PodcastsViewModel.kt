package com.resona.music.ui.podcasts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.EpisodeProgress
import com.resona.music.domain.model.FollowedShow
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastShow
import com.resona.music.domain.repository.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** Browse chips. Each one runs an episode search, which ranks by what people actually play. */
enum class PodcastCategory(val label: String, val query: String) {
    Popular("Popular", "podcast"),
    Comedy("Comedy", "comedy podcast"),
    News("News", "news podcast"),
    Tech("Tech", "technology podcast"),
    TrueCrime("True crime", "true crime podcast"),
    Business("Business", "business podcast"),
    Health("Health", "health podcast"),
    Science("Science", "science podcast"),
    Sports("Sports", "sports podcast"),
    History("History", "history podcast"),
}

sealed interface BrowseState {
    data object Loading : BrowseState
    data class Loaded(val shows: List<PodcastShow>, val episodes: List<PodcastEpisode>) : BrowseState
    data class Error(val message: String) : BrowseState
}

data class FollowedShowItem(val show: PodcastShow, val newCount: Int)

data class PodcastsUiState(
    val continueListening: List<EpisodeProgress> = emptyList(),
    val followedShows: List<FollowedShowItem> = emptyList(),
    val newEpisodes: List<PodcastEpisode> = emptyList(),
    val followedIds: Set<String> = emptySet(),
    // Lets New episodes rows show the show's square cover instead of a video frame.
    val showCovers: Map<String, String> = emptyMap(),
    val progress: Map<String, EpisodeProgress> = emptyMap(),
    val selectedCategory: PodcastCategory = PodcastCategory.Popular,
    val browse: BrowseState = BrowseState.Loading,
    val isRefreshing: Boolean = false,
) {
    val isFirstVisit: Boolean
        get() = followedShows.isEmpty() && continueListening.isEmpty()
}

@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    // Newest episodes per followed show, keyed by show browseId.
    private val latestEpisodes = MutableStateFlow<Map<String, List<PodcastEpisode>>>(emptyMap())
    private val selectedCategory = MutableStateFlow(PodcastCategory.Popular)
    private val browse = MutableStateFlow<Map<PodcastCategory, BrowseState>>(emptyMap())
    private val isRefreshing = MutableStateFlow(false)

    private val inFlight = mutableSetOf<String>()
    // Following lots of shows shouldn't fire lots of requests at once.
    private val fetchPermits = Semaphore(3)

    val uiState: StateFlow<PodcastsUiState> = combine(
        repository.observeFollowedShows(),
        repository.observeEpisodeProgress(),
        latestEpisodes,
        combine(selectedCategory, browse, isRefreshing) { category, browse, refreshing -> Triple(category, browse, refreshing) }
    ) { followed, progress, latest, (category, browse, refreshing) ->
        buildState(followed, progress, latest, category, browse[category] ?: BrowseState.Loading, refreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodcastsUiState())

    init {
        loadBrowse(PodcastCategory.Popular)
        viewModelScope.launch {
            repository.observeFollowedShows().collect { followed ->
                val ids = followed.map { it.show.browseId }.toSet()
                latestEpisodes.update { current -> current.filterKeys(ids::contains) }
                fetchLatest(followed, force = false)
            }
        }
    }

    fun selectCategory(category: PodcastCategory) {
        selectedCategory.value = category
        loadBrowse(category)
    }

    fun retryBrowse() {
        browse.update { it - selectedCategory.value }
        loadBrowse(selectedCategory.value)
    }

    fun follow(show: PodcastShow) {
        viewModelScope.launch { repository.followShow(show) }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val showJobs = fetchLatest(repository.observeFollowedShows().first(), force = true)
            browse.update { it - selectedCategory.value }
            val browseJob = loadBrowse(selectedCategory.value)
            (showJobs + listOfNotNull(browseJob)).joinAll()
            isRefreshing.value = false
        }
    }

    private fun fetchLatest(followed: List<FollowedShow>, force: Boolean): List<Job> =
        followed.mapNotNull { item ->
            val id = item.show.browseId
            if (id in inFlight || (!force && id in latestEpisodes.value)) return@mapNotNull null
            inFlight += id
            viewModelScope.launch {
                try {
                    val page = fetchPermits.withPermit { repository.getShow(id, forceRefresh = force) }
                    latestEpisodes.update { it + (id to page.episodes) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "fetchLatest: $id failed", e)
                } finally {
                    inFlight -= id
                }
            }
        }

    private fun loadBrowse(category: PodcastCategory): Job? {
        val current = browse.value[category]
        if (current is BrowseState.Loaded || current is BrowseState.Loading) return null
        browse.update { it + (category to BrowseState.Loading) }
        return viewModelScope.launch {
            val state = try {
                val episodes = repository.searchEpisodes(category.query)
                BrowseState.Loaded(shows = showsFrom(episodes), episodes = episodes.take(TOP_EPISODES))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "loadBrowse: ${category.name} failed", e)
                BrowseState.Error("Couldn't load podcasts. Check your connection and try again.")
            }
            browse.update { it + (category to state) }
        }
    }

    // Episode results come from the biggest shows, so the shows behind them
    // make a decent "popular" list. They carry no cover of their own, so the
    // episode frame stands in until the show page is opened.
    private fun showsFrom(episodes: List<PodcastEpisode>): List<PodcastShow> =
        episodes.filter { it.showBrowseId.isNotBlank() }
            .distinctBy { it.showBrowseId }
            .take(POPULAR_SHOWS)
            .map { PodcastShow(browseId = it.showBrowseId, title = it.showTitle, author = "", thumbnailUrl = it.thumbnailUrl) }

    private fun buildState(
        followed: List<FollowedShow>,
        progress: Map<String, EpisodeProgress>,
        latest: Map<String, List<PodcastEpisode>>,
        category: PodcastCategory,
        browse: BrowseState,
        refreshing: Boolean,
    ): PodcastsUiState {
        val followedItems = followed.map { item ->
            val newCount = latest[item.show.browseId].orEmpty().count { episode ->
                val published = episode.publishedAtMillis
                published != null && published > item.lastSeenAtMillis && progress[episode.videoId] == null
            }
            FollowedShowItem(item.show, newCount)
        }.sortedByDescending { it.newCount > 0 }

        // Stays here until it's far enough in to show up under Continue listening, or done.
        val cutoff = System.currentTimeMillis() - NEW_EPISODE_WINDOW_MILLIS
        val newEpisodes = followed.flatMap { latest[it.show.browseId].orEmpty() }
            .filter { episode ->
                val saved = progress[episode.videoId]
                (episode.publishedAtMillis ?: 0L) >= cutoff && saved?.isInProgress != true && saved?.finished != true
            }
            .sortedByDescending { it.publishedAtMillis }
            .take(MAX_NEW_EPISODES)

        return PodcastsUiState(
            continueListening = progress.values
                .filter { it.isInProgress }
                .sortedByDescending { it.updatedAtMillis }
                .take(MAX_CONTINUE),
            followedShows = followedItems,
            newEpisodes = newEpisodes,
            followedIds = followed.map { it.show.browseId }.toSet(),
            showCovers = followed.associate { it.show.browseId to it.show.thumbnailUrl },
            progress = progress,
            selectedCategory = category,
            browse = browse,
            isRefreshing = refreshing,
        )
    }

    private companion object {
        const val TAG = "PodcastsViewModel"
        const val TOP_EPISODES = 6
        const val POPULAR_SHOWS = 8
        const val MAX_CONTINUE = 10
        const val MAX_NEW_EPISODES = 5
        const val NEW_EPISODE_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
