package com.resona.music.ui.podcasts

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resona.music.domain.model.EpisodeProgress
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastShow
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.ResonaTheme

@Composable
fun PodcastsScreen(
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onShowClick: (PodcastShow) -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    onResume: (Song) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PodcastsScreenContent(
        uiState = uiState,
        onBack = onBack,
        onSearchClick = onSearchClick,
        onRefresh = viewModel::refresh,
        onShowClick = onShowClick,
        onFollow = viewModel::follow,
        onPlayEpisode = onPlayEpisode,
        onResume = onResume,
        onSelectCategory = viewModel::selectCategory,
        onRetryBrowse = viewModel::retryBrowse,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastsScreenContent(
    uiState: PodcastsUiState,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onShowClick: (PodcastShow) -> Unit,
    onFollow: (PodcastShow) -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    onResume: (Song) -> Unit,
    onSelectCategory: (PodcastCategory) -> Unit,
    onRetryBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PodcastTopBar(title = "Podcasts", onBack = onBack) {
            GlassIconButton(icon = Icons.Outlined.Search, contentDescription = "Search podcasts", onClick = onSearchClick)
        }

        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                if (uiState.isFirstVisit) {
                    item(key = "hint") {
                        HintCard(
                            icon = Icons.Outlined.Podcasts,
                            text = "Follow shows you like and their new episodes will show up here."
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                if (uiState.continueListening.isNotEmpty()) {
                    item(key = "continue_header") { SectionHeader("Continue listening") }
                    item { Spacer(modifier = Modifier.height(14.dp)) }
                    item(key = "continue_row") {
                        ContinueRow(items = uiState.continueListening, onResume = onResume)
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }

                if (uiState.followedShows.isNotEmpty()) {
                    item(key = "shows_header") { SectionHeader("Your shows") }
                    item { Spacer(modifier = Modifier.height(14.dp)) }
                    item(key = "shows_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.followedShows, key = { it.show.browseId }) { item ->
                                ShowTile(show = item.show, newCount = item.newCount, onClick = { onShowClick(item.show) })
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }

                if (uiState.newEpisodes.isNotEmpty()) {
                    item(key = "new_header") { SectionHeader("New episodes") }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                    itemsIndexed(uiState.newEpisodes, key = { _, episode -> "new_${episode.videoId}" }) { index, episode ->
                        if (index > 0) EpisodeDivider()
                        EpisodeRow(
                            episode = episode,
                            artworkUrl = uiState.showCovers[episode.showBrowseId]?.takeIf { it.isNotBlank() } ?: episode.thumbnailUrl,
                            progress = uiState.progress[episode.videoId],
                            includeShowInMeta = true,
                            onPlay = { onPlayEpisode(episode) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }

                item(key = "browse_header") { SectionHeader("Browse") }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item(key = "chips") {
                    CategoryChips(selected = uiState.selectedCategory, onSelect = onSelectCategory)
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }

                when (val browse = uiState.browse) {
                    BrowseState.Loading -> item(key = "browse_loading") { LoadingBlock() }
                    is BrowseState.Error -> item(key = "browse_error") {
                        ErrorBlock(message = browse.message, onRetry = onRetryBrowse)
                    }
                    is BrowseState.Loaded -> {
                        if (browse.shows.isNotEmpty()) {
                            item(key = "popular_header") { SubHeader("Popular shows") }
                            item { Spacer(modifier = Modifier.height(10.dp)) }
                            item(key = "popular_row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(browse.shows, key = { it.browseId }) { show ->
                                        PopularShowCard(
                                            show = show,
                                            isFollowing = show.browseId in uiState.followedIds,
                                            onClick = { onShowClick(show) },
                                            onFollow = {
                                                // Unfollowing lives on the show page, so this only ever follows.
                                                if (show.browseId !in uiState.followedIds) onFollow(show) else onShowClick(show)
                                            }
                                        )
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(28.dp)) }
                        }
                        if (browse.episodes.isNotEmpty()) {
                            item(key = "top_header") { SubHeader("Top episodes") }
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                            itemsIndexed(browse.episodes, key = { _, episode -> "top_${episode.videoId}" }) { index, episode ->
                                RankedEpisodeRow(rank = index + 1, episode = episode, onPlay = { onPlayEpisode(episode) })
                            }
                        }
                    }
                }

                // Clears the floating mini-player and nav pill.
                item { Spacer(modifier = Modifier.height(160.dp)) }
            }
        }
    }
}

@Composable
private fun ContinueRow(items: List<EpisodeProgress>, onResume: (Song) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.song.videoId }) { progress ->
            ContinueCard(progress = progress, onClick = { onResume(progress.song) })
        }
    }
}

@Composable
internal fun EpisodeDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.08f))
}

private val previewShow = PodcastShow("MPSP1", "Late Night Science", "Late Night Science", "")
private val previewEpisode = PodcastEpisode(
    videoId = "v1",
    title = "What actually lives at the bottom of the ocean",
    showTitle = "Late Night Science",
    showBrowseId = "MPSP1",
    thumbnailUrl = "",
    publishedText = "Yesterday",
    durationText = "44 min"
)

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Following shows")
@Composable
private fun PodcastsScreenPreview() {
    ResonaTheme(darkTheme = true) {
        PodcastsScreenContent(
            uiState = PodcastsUiState(
                continueListening = listOf(
                    EpisodeProgress(previewEpisode.toSong(), positionMillis = 1_500_000, durationMillis = 2_640_000, updatedAtMillis = 0, finished = false)
                ),
                followedShows = listOf(FollowedShowItem(previewShow, newCount = 2)),
                newEpisodes = listOf(previewEpisode),
                followedIds = setOf(previewShow.browseId),
                browse = BrowseState.Loaded(shows = listOf(previewShow), episodes = listOf(previewEpisode))
            ),
            onBack = {},
            onSearchClick = {},
            onRefresh = {},
            onShowClick = {},
            onFollow = {},
            onPlayEpisode = {},
            onResume = {},
            onSelectCategory = {},
            onRetryBrowse = {}
        )
    }
}
