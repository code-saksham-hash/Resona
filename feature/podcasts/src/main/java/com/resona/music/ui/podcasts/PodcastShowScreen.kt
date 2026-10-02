package com.resona.music.ui.podcasts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resona.music.domain.model.EpisodeProgress
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastShowPage
import com.resona.music.ui.theme.NocturneOutlinedButton

@Composable
fun PodcastShowScreen(
    onBack: () -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastShowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFollowing by viewModel.isFollowing.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        PodcastTopBar(title = "", onBack = onBack)
        when (val state = uiState) {
            ShowUiState.Loading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is ShowUiState.Error -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ErrorBlock(message = state.message, onRetry = viewModel::retry)
            }
            is ShowUiState.Loaded -> ShowContent(
                state = state,
                isFollowing = isFollowing,
                progress = progress,
                onToggleFollow = viewModel::toggleFollow,
                onLoadMore = viewModel::loadMore,
                onPlayEpisode = onPlayEpisode,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ShowContent(
    state: ShowUiState.Loaded,
    isFollowing: Boolean,
    progress: Map<String, EpisodeProgress>,
    onToggleFollow: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "header") {
            ShowHeader(page = state.page, isFollowing = isFollowing, onToggleFollow = onToggleFollow)
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item(key = "episodes_header") { SectionHeader("Episodes") }
        item { Spacer(modifier = Modifier.height(6.dp)) }

        if (state.episodes.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No episodes yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp)
                )
            }
        }

        itemsIndexed(state.episodes, key = { _, episode -> episode.videoId }) { index, episode ->
            if (index > 0) EpisodeDivider()
            EpisodeRow(
                episode = episode,
                artworkUrl = episode.thumbnailUrl,
                progress = progress[episode.videoId],
                includeShowInMeta = false,
                onPlay = { onPlayEpisode(episode) }
            )
        }

        if (state.continuation != null) {
            item(key = "load_more") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    } else {
                        NocturneOutlinedButton(
                            text = "Load more episodes",
                            onClick = onLoadMore,
                            borderColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@Composable
private fun ShowHeader(page: PodcastShowPage, isFollowing: Boolean, onToggleFollow: () -> Unit) {
    val show = page.show
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Artwork(url = show.thumbnailUrl, modifier = Modifier.size(140.dp), shape = MaterialTheme.shapes.large)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = show.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (show.author.isNotBlank() && show.author != show.title) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = show.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                FollowButton(isFollowing = isFollowing, onClick = onToggleFollow, showTitle = show.title)
            }
        }
        if (page.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            ExpandableDescription(text = page.description)
        }
    }
}

@Composable
private fun ExpandableDescription(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(
                onClickLabel = if (expanded) "Show less" else "Show more",
                role = Role.Button,
                onClick = { expanded = !expanded }
            )
    )
}
