package com.resona.music.ui.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastShow

@Composable
fun PodcastSearchScreen(
    onBack: () -> Unit,
    onShowClick: (PodcastShow) -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodcastSearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (uiState.submittedQuery.isEmpty()) focusRequester.requestFocus()
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(end = ScreenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            SearchField(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    keyboard?.hide()
                    viewModel.search()
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
            )
        }

        when {
            uiState.isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            uiState.error != null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ErrorBlock(message = uiState.error.orEmpty(), onRetry = viewModel::search)
            }
            uiState.submittedQuery.isEmpty() -> FullScreenMessage(
                text = "Search for a show or an episode",
                modifier = Modifier.weight(1f)
            )
            uiState.shows.isEmpty() && uiState.episodes.isEmpty() -> FullScreenMessage(
                text = "Nothing found for \"${uiState.submittedQuery}\"",
                modifier = Modifier.weight(1f)
            )
            else -> SearchResults(
                shows = uiState.shows,
                episodes = uiState.episodes,
                onShowClick = onShowClick,
                onPlayEpisode = onPlayEpisode,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SearchResults(
    shows: List<PodcastShow>,
    episodes: List<PodcastEpisode>,
    onShowClick: (PodcastShow) -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (shows.isNotEmpty()) {
            item(key = "shows_header") { SubHeader("Shows", modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) }
            items(shows.take(MAX_SHOWS), key = { "show_${it.browseId}" }) { show ->
                ShowResultRow(show = show, onClick = { onShowClick(show) })
            }
        }
        if (episodes.isNotEmpty()) {
            item(key = "episodes_header") { SubHeader("Episodes", modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
            itemsIndexed(episodes, key = { _, episode -> "episode_${episode.videoId}" }) { index, episode ->
                if (index > 0) EpisodeDivider()
                EpisodeRow(
                    episode = episode,
                    artworkUrl = episode.thumbnailUrl,
                    progress = null,
                    includeShowInMeta = true,
                    onPlay = { onPlayEpisode(episode) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@Composable
private fun ShowResultRow(show: PodcastShow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = show.title, role = Role.Button, onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(url = show.thumbnailUrl, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = show.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (show.author.isNotBlank()) {
                Text(
                    text = show.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Same pill as the main Search tab's field.
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Shows and episodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable(role = Role.Button) { onQueryChange("") }
            )
        }
    }
}

private const val MAX_SHOWS = 8
