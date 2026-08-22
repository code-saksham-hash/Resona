package com.resona.music.ui.search

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.ResonaTheme
import com.resona.music.ui.theme.SectionHeaderTextStyle

@Composable
fun SearchPage(
    initialQuery: String = "",
    onSongClick: (Song) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val browseState by viewModel.browseState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) viewModel.onQueryChange(initialQuery)
    }

    SearchPageContent(
        query = query,
        uiState = uiState,
        searchHistory = searchHistory,
        browseState = browseState,
        onQueryChange = viewModel::onQueryChange,
        onSubmitSearch = viewModel::submitSearch,
        onRemoveHistory = viewModel::removeHistoryEntry,
        onClearHistory = viewModel::clearHistory,
        onRetry = viewModel::retry,
        onRetryBrowse = viewModel::retryBrowse,
        onSongClick = onSongClick
    )
}

/**
 * Stateless so it's directly previewable (see the `@Preview`s below) without
 * standing up Hilt -- [SearchPage] above owns the [SearchViewModel] and
 * threads its state through.
 */
@Composable
private fun SearchPageContent(
    query: String,
    uiState: SearchUiState,
    searchHistory: List<String>,
    browseState: BrowseUiState,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRetry: () -> Unit,
    onRetryBrowse: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(20.dp))
        RoundedSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = {
                // Search already runs on a debounce as the user types; this
                // submits it as an explicit search instead (jumps the queue,
                // saves it to history) and dismisses the keyboard.
                onSubmitSearch(query)
                keyboardController?.hide()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (query.isBlank()) {
                SearchLanding(
                    history = searchHistory,
                    browseState = browseState,
                    onQueryClick = onSubmitSearch,
                    onRemoveHistory = onRemoveHistory,
                    onClearHistory = onClearHistory,
                    onRetryBrowse = onRetryBrowse,
                    onSongClick = onSongClick
                )
            } else {
                when (uiState) {
                    SearchUiState.Loading -> SearchSkeletonList()
                    is SearchUiState.Success -> SearchResultsList(
                        results = uiState.results,
                        onSongClick = onSongClick
                    )
                    SearchUiState.Empty -> Text(
                        text = "No results found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    is SearchUiState.Error -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                    ) {
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NocturneOutlinedButton(
                            text = "Retry",
                            onClick = onRetry,
                            borderColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * A search field built on [BasicTextField] rather than Material3's [TextField]
 * -- the stock component's internal padding assumes its ~56dp default height,
 * so constraining it down to a compact pill with `heightIn(max = ...)` left it
 * looking cramped/off-center instead of actually shrinking. Owning the box,
 * padding, and icon layout directly gives a clean, fully-rounded pill at
 * exactly the size this screen wants.
 */
@Composable
private fun RoundedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp),
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
                    text = "Search songs, artists, albums...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
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
                    .clickable { onQueryChange("") }
            )
        }
    }
}

/**
 * The blank-query landing: recent searches (if any) above real, fetched
 * Trending/Suggested shelves (see [BrowseUiState]) -- one scroll, not
 * history and Browse each owning their own nested list.
 */
@Composable
private fun SearchLanding(
    history: List<String>,
    browseState: BrowseUiState,
    onQueryClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onRetryBrowse: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (history.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 17.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Searches",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Clear all",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable(onClick = onClearHistory)
                    )
                }
            }
            items(history, key = { "history_$it" }) { pastQuery ->
                SearchHistoryRow(
                    query = pastQuery,
                    onClick = { onQueryClick(pastQuery) },
                    onRemove = { onRemoveHistory(pastQuery) }
                )
            }
            item(key = "history_spacer") { Spacer(modifier = Modifier.height(20.dp)) }
        }

        when (browseState) {
            BrowseUiState.Loading -> item(key = "browse_loading") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    repeat(4) { SearchResultSkeleton() }
                }
            }
            is BrowseUiState.Success -> browseState.sections.forEach { section ->
                item(key = "browse_header_${section.title}") {
                    Text(
                        text = section.title,
                        style = SectionHeaderTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp)
                    )
                }
                items(section.songs, key = { "browse_${section.title}_${it.videoId}" }) { song ->
                    BrowseSongRow(song = song, onClick = { onSongClick(song) })
                }
                item(key = "browse_spacer_${section.title}") { Spacer(modifier = Modifier.height(12.dp)) }
            }
            BrowseUiState.Unavailable -> if (history.isEmpty()) {
                item(key = "browse_unavailable") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp)
                    ) {
                        Text(
                            text = "Search for a song, artist, or album to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NocturneOutlinedButton(
                            text = "Retry",
                            onClick = onRetryBrowse,
                            borderColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        // Clears the floating bottom chrome (pill nav, plus the mini-player
        // when a track is playing).
        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

/** One row of a real Trending/Suggested shelf -- thumbnail, title, artist,
 *  and a play affordance, styled like [SearchResultRow] but with the
 *  trailing play icon the Browse reference design calls for. */
@Composable
private fun BrowseSongRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SearchSkeletonList(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(top = 4.dp)) {
        repeat(12) {
            SearchResultSkeleton()
        }
    }
}

@Composable
private fun SearchResultSkeleton(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateX, y = 0f)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(brush)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<Song>,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(results, key = { it.videoId }) { song ->
            SearchResultRow(song = song, onClick = { onSongClick(song) })
        }
        // Clears the floating bottom chrome -- see SearchLanding's matching spacer.
        item(key = "results_bottom_spacer") { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@Composable
private fun SearchResultRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 17.dp, end = 5.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Remove \"$query\" from search history",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private val previewSongs = listOf(
    Song(videoId = "1", title = "Midnight City", artist = "M83", thumbnailUrl = ""),
    Song(videoId = "2", title = "Nightcall", artist = "Kavinsky", thumbnailUrl = ""),
    Song(
        videoId = "3",
        title = "Instant Crush",
        artist = "Daft Punk ft. Julian Casablancas",
        thumbnailUrl = ""
    )
)

@Preview(showBackground = true, name = "Browse")
@Composable
private fun SearchPageBrowsePreview() {
    ResonaTheme(darkTheme = true) {
        SearchPageContent(
            query = "",
            uiState = SearchUiState.Empty,
            searchHistory = listOf("daft punk", "lofi beats"),
            browseState = BrowseUiState.Success(
                listOf(
                    BrowseSection("Trending Now", previewSongs),
                    BrowseSection("Suggested For You", previewSongs.take(2)),
                )
            ),
            onQueryChange = {},
            onSubmitSearch = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onRetry = {},
            onRetryBrowse = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Browse Light")
@Composable
private fun SearchPageBrowseLightPreview() {
    ResonaTheme(darkTheme = false) {
        SearchPageContent(
            query = "",
            uiState = SearchUiState.Empty,
            searchHistory = emptyList(),
            browseState = BrowseUiState.Success(listOf(BrowseSection("Trending Now", previewSongs))),
            onQueryChange = {},
            onSubmitSearch = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onRetry = {},
            onRetryBrowse = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Results")
@Composable
private fun SearchPageResultsPreview() {
    ResonaTheme(darkTheme = true) {
        SearchPageContent(
            query = "daft punk",
            uiState = SearchUiState.Success(previewSongs),
            searchHistory = emptyList(),
            browseState = BrowseUiState.Loading,
            onQueryChange = {},
            onSubmitSearch = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onRetry = {},
            onRetryBrowse = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun SearchPageLoadingPreview() {
    ResonaTheme(darkTheme = true) {
        SearchPageContent(
            query = "daft punk",
            uiState = SearchUiState.Loading,
            searchHistory = emptyList(),
            browseState = BrowseUiState.Loading,
            onQueryChange = {},
            onSubmitSearch = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onRetry = {},
            onRetryBrowse = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun SearchPageErrorPreview() {
    ResonaTheme(darkTheme = true) {
        SearchPageContent(
            query = "daft punk",
            uiState = SearchUiState.Error("Unable to resolve host \"music.youtube.com\""),
            searchHistory = emptyList(),
            browseState = BrowseUiState.Unavailable,
            onQueryChange = {},
            onSubmitSearch = {},
            onRemoveHistory = {},
            onClearHistory = {},
            onRetry = {},
            onRetryBrowse = {},
            onSongClick = {}
        )
    }
}
