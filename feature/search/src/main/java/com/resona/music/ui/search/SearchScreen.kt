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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
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

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onSongClick: (Song) -> Unit = {},
    onGenreClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExploreContent(
        query = query,
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onSongClick = onSongClick,
        onGenreClick = onGenreClick,
        onProfileClick = onProfileClick,
        modifier = modifier
    )
}

@Composable
private fun ExploreContent(
    query: String,
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onSongClick: (Song) -> Unit,
    onGenreClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ExploreTopBar(onProfileClick = onProfileClick)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            item { SearchBarSection(query = query, onQueryChange = onQueryChange) }

            if (query.isBlank()) {
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item { RecentTags() }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                item { GenreGrid(onGenreClick = onGenreClick) }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                item { TrendingNowSection() }
                item { Spacer(modifier = Modifier.height(48.dp)) }
                item { FeaturedCreatorsSection() }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            } else {
                item { Spacer(modifier = Modifier.height(16.dp)) }

                when (val state = uiState) {
                    is SearchUiState.Loading -> {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    is SearchUiState.Success -> {
                        items(state.results) { song ->
                            SearchResultRow(
                                song = song,
                                onClick = { onSongClick(song) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                    is SearchUiState.Error -> {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                NocturneOutlinedButton(text = "RETRY", onClick = onRetry)
                            }
                        }
                    }
                    is SearchUiState.Empty -> {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No results found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
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
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExploreTopBar(
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Resona",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onProfileClick) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun SearchBarSection(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Search...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
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

@Composable
private fun RecentTags(modifier: Modifier = Modifier) {
    val tags = listOf("Deep Ambient", "Glitch Jazz", "Post-Piano")
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    onGenreClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Text(
            text = "Sonic Landscapes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GenreCard(
                    name = "EXPERIMENTAL",
                    imageUrl = "https://picsum.photos/seed/genre1/400/400",
                    isFeatured = true,
                    onClick = { onGenreClick("Experimental") },
                    modifier = Modifier.weight(2f)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    GenreCard(name = "JAZZ", imageUrl = "https://picsum.photos/seed/genre2/400/400", onClick = { onGenreClick("Jazz") })
                    GenreCard(name = "TECHNO", imageUrl = "https://picsum.photos/seed/genre3/400/400", onClick = { onGenreClick("Techno") })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GenreCard(name = "PIANO", imageUrl = "https://picsum.photos/seed/genre4/400/400", onClick = { onGenreClick("Piano") }, modifier = Modifier.weight(1f))
                GenreCard(name = "GLITCH", imageUrl = "https://picsum.photos/seed/genre5/400/400", onClick = { onGenreClick("Glitch") }, modifier = Modifier.weight(1f))
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
            .aspectRatio(if (isFeatured) 1f else 1f)
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
            modifier = Modifier
                .fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)
                )
        )
        Box(
            modifier = Modifier
                .align(if (isFeatured) Alignment.BottomStart else Alignment.Center)
                .padding(if (isFeatured) 24.dp else 0.dp)
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
                .padding(horizontal = 20.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TrendingCard(
                title = "Ether Drift",
                artist = "Vanish In Dust",
                imageUrl = "https://picsum.photos/seed/trending1/400/300",
                onClick = {}
            )
            TrendingCard(
                title = "Monolith IV",
                artist = "Structural Integrity",
                imageUrl = "https://picsum.photos/seed/trending2/400/300",
                onClick = {}
            )
            TrendingCard(
                title = "Surface Tension",
                artist = "Kinesis",
                imageUrl = "https://picsum.photos/seed/trending3/400/300",
                onClick = {}
            )
        }
    }
}

@Composable
private fun TrendingCard(
    title: String,
    artist: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(240.dp)
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
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f))
                    .clickable { },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
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
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Featured CREATORS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CreatorAvatar(name = "AELOS", imageUrl = "https://picsum.photos/seed/creator1/200/200")
            CreatorAvatar(name = "KØHL", imageUrl = "https://picsum.photos/seed/creator2/200/200")
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
                .size(100.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
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
        ExploreContent(
            query = "",
            uiState = SearchUiState.Empty,
            onQueryChange = {},
            onRetry = {},
            onSongClick = {}
        )
    }
}
