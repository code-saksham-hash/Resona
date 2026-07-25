package com.resona.music.ui.search

import android.content.res.Configuration
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.ResonaOutlinedButton
import com.resona.music.ui.theme.ResonaTheme

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
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (uiState) {
                SearchUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )

                is SearchUiState.Success -> SearchResultsList(
                    results = uiState.results,
                    onSongClick = onSongClick
                )

                SearchUiState.Empty -> Text(
                    text = if (query.isBlank()) "Search for songs" else "No results found",
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
                    ResonaOutlinedButton(
                        text = "Retry",
                        onClick = onRetry,
                        borderColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Plain [OutlinedTextField]: Resona's ColorScheme already maps `outline` to
 * gray and `onSurface`/`primary` to black, so passing them explicitly here
 * (rather than leaning on M3 defaults) is what gives the "gray border, black
 * on focus" look with zero gradients or tinted glow.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text("Search songs") },
        leadingIcon = {
            Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
        },
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp
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

@Preview(showBackground = true, name = "Empty")
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

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Empty (dark)")
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
