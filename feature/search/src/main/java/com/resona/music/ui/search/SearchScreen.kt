package com.resona.music.ui.search

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.ResonaLogoIcon
import com.resona.music.ui.theme.ResonaTheme

data class Genre(
    val name: String,
    val imageUrl: String,
    val isFeatured: Boolean = false
)

data class TrendingAlbum(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String
)

data class Creator(
    val id: String,
    val name: String,
    val imageUrl: String
)

private val mockGenres = listOf(
    Genre("Experimental", "https://picsum.photos/seed/genre1/400/400", isFeatured = true),
    Genre("Jazz", "https://picsum.photos/seed/genre2/400/400"),
    Genre("Techno", "https://picsum.photos/seed/genre3/400/400"),
    Genre("Piano", "https://picsum.photos/seed/genre4/400/400"),
    Genre("Glitch", "https://picsum.photos/seed/genre5/400/400"),
)

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onSongClick: (Song) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SearchScreenContent(
        query = query,
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onSongClick = onSongClick,
        modifier = modifier
    )
}

@Composable
private fun SearchScreenContent(
    query: String,
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchBarSection(query = query, onQueryChange = onQueryChange)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (query.isBlank()) {
                ExploreLanding()
            } else {
                when (uiState) {
                    SearchUiState.Loading -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
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

@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 11.dp, end = 17.dp, top = 7.dp, bottom = 7.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .height(38.dp)
            .padding(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Search...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.offset(y = (-1).dp)
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExploreLanding(modifier: Modifier = Modifier) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                delay(1500)
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }
            item { RecentTags() }
            item { Spacer(modifier = Modifier.height(27.dp)) }
            item { GenreGrid() }
            item { Spacer(modifier = Modifier.height(27.dp)) }
            item { TrendingNowSection() }
            item { Spacer(modifier = Modifier.height(41.dp)) }
            item { FeaturedCreatorsSection() }
            item { Spacer(modifier = Modifier.height(68.dp)) }
        }
    }
}

@Composable
private fun RecentTags(modifier: Modifier = Modifier) {
    val tags = listOf("Deep Ambient", "Glitch Jazz", "Post-Piano")
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tags.forEachIndexed { index, tag ->
            SuggestionChip(
                onClick = { },
                label = {
                    Text(
                        text = if (index == 0) "Recent: $tag" else tag,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = if (index == 0)
                        MaterialTheme.colorScheme.surfaceContainer
                    else
                        Color.Transparent,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    enabled = true
                ),
                shape = MaterialTheme.shapes.extraLarge
            )
        }
    }
}

@Composable
private fun GenreGrid(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 17.dp)) {
        Text(
            text = "Sonic Landscapes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GenreCard(
                    name = mockGenres[0].name,
                    imageUrl = mockGenres[0].imageUrl,
                    isFeatured = true,
                    modifier = Modifier.weight(2f)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    GenreCard(name = mockGenres[1].name, imageUrl = mockGenres[1].imageUrl)
                    GenreCard(name = mockGenres[2].name, imageUrl = mockGenres[2].imageUrl)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GenreCard(name = mockGenres[3].name, imageUrl = mockGenres[3].imageUrl, modifier = Modifier.weight(1f))
                GenreCard(name = mockGenres[4].name, imageUrl = mockGenres[4].imageUrl, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GenreCard(
    name: String,
    imageUrl: String,
    isFeatured: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )
        Box(
            modifier = modifier
                .align(if (isFeatured) Alignment.BottomStart else Alignment.Center)
                .padding(if (isFeatured) 20.dp else 0.dp)
        ) {
            if (isFeatured) {
                Column {
                    Text(
                        text = "GENRE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun TrendingNowSection(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
        ) {
            Column {
                Text(
                    text = "Trending Now",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = "High Fidelity Signals",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TrendingCard(
                title = "Ether Drift",
                artist = "Vanish In Dust",
                imageUrl = "https://picsum.photos/seed/trending1/400/300"
            )
            TrendingCard(
                title = "Monolith IV",
                artist = "Structural Integrity",
                imageUrl = "https://picsum.photos/seed/trending2/400/300"
            )
            TrendingCard(
                title = "Surface Tension",
                artist = "Kinesis",
                imageUrl = "https://picsum.photos/seed/trending3/400/300"
            )
        }
    }
}

@Composable
private fun TrendingCard(
    title: String,
    artist: String,
    imageUrl: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(204.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun FeaturedCreatorsSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Featured CREATORS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CreatorAvatar(name = "AELOS", imageUrl = "https://picsum.photos/seed/creator1/200/200")
            CreatorAvatar(name = "K\u00d8HL", imageUrl = "https://picsum.photos/seed/creator2/200/200")
            CreatorAvatar(name = "OBSCURA", imageUrl = "https://picsum.photos/seed/creator3/200/200")
        }
    }
}

@Composable
private fun CreatorAvatar(
    name: String,
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .size(85.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
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

@Preview(showBackground = true, name = "Explore")
@Composable
private fun SearchScreenPreview() {
    ResonaTheme {
        SearchScreenContent(
            query = "",
            uiState = SearchUiState.Empty,
            onQueryChange = {},
            onRetry = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Explore Dark")
@Composable
private fun SearchScreenDarkPreview() {
    ResonaTheme(darkTheme = true) {
        SearchScreenContent(
            query = "",
            uiState = SearchUiState.Empty,
            onQueryChange = {},
            onRetry = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Results")
@Composable
private fun SearchScreenResultsPreview() {
    ResonaTheme {
        SearchScreenContent(
            query = "daft punk",
            uiState = SearchUiState.Success(previewSongs),
            onQueryChange = {},
            onRetry = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun SearchScreenLoadingPreview() {
    ResonaTheme {
        SearchScreenContent(
            query = "daft punk",
            uiState = SearchUiState.Loading,
            onQueryChange = {},
            onRetry = {},
            onSongClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun SearchScreenErrorPreview() {
    ResonaTheme {
        SearchScreenContent(
            query = "daft punk",
            uiState = SearchUiState.Error("Unable to resolve host \"music.youtube.com\""),
            onQueryChange = {},
            onRetry = {},
            onSongClick = {}
        )
    }
}

@Composable
fun ExploreScreen(
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 17.dp, end = 17.dp)
                .height(61.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = ResonaLogoIcon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(55.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onProfileClick) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ExploreLanding()
    }
}

@Composable
fun SearchPage(
    onBack: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(end = 17.dp)
                .height(61.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .heightIn(max = 38.dp),
                placeholder = {
                    Text(
                        "Search...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(50)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (query.isBlank()) {
                // Search history or blank state
            } else {
                val state = uiState
                when (state) {
                    SearchUiState.Loading -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    is SearchUiState.Success -> SearchResultsList(
                        results = state.results,
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
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NocturneOutlinedButton(
                            text = "Retry",
                            onClick = viewModel::retry,
                            borderColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
