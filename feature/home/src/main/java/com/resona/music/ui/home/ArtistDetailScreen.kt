package com.resona.music.ui.home

import android.content.res.Configuration
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.ResonaTheme

@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit = {},
    onSongClick: (song: Song, songs: List<Song>) -> Unit = { _, _ -> },
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistDetailScreenContent(
        artistName = viewModel.artistName,
        uiState = uiState,
        onBack = onBack,
        onSongClick = onSongClick,
        onRetry = viewModel::retry
    )
}

@Composable
private fun ArtistDetailScreenContent(
    artistName: String,
    uiState: ArtistDetailUiState,
    onBack: () -> Unit = {},
    onSongClick: (song: Song, songs: List<Song>) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
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
            Text(
                text = artistName.ifBlank { "Artist" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (uiState) {
                ArtistDetailUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                is ArtistDetailUiState.Success -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = "Top Songs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp)
                        )
                    }
                    items(uiState.songs, key = { it.videoId }) { song ->
                        ArtistTopSongRow(song = song, onClick = { onSongClick(song, uiState.songs) })
                    }
                    // Clears the floating bottom chrome (pill nav, plus the
                    // mini-player when a track is playing).
                    item { Spacer(modifier = Modifier.height(160.dp)) }
                }
                ArtistDetailUiState.Empty -> Text(
                    text = "No songs found for $artistName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                is ArtistDetailUiState.Error -> Column(
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

@Composable
private fun ArtistTopSongRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

        if (song.duration.isNotBlank()) {
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = song.duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private val previewSongs = listOf(
    Song(videoId = "1", title = "Instant Crush", artist = "Daft Punk", thumbnailUrl = "", duration = "5:38"),
    Song(videoId = "2", title = "Get Lucky", artist = "Daft Punk", thumbnailUrl = "", duration = "6:10"),
)

@Preview(showBackground = true, name = "Artist")
@Composable
private fun ArtistDetailScreenPreview() {
    ResonaTheme {
        ArtistDetailScreenContent(
            artistName = "Daft Punk",
            uiState = ArtistDetailUiState.Success(previewSongs)
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Artist (dark)")
@Composable
private fun ArtistDetailScreenDarkPreview() {
    ResonaTheme(darkTheme = true) {
        ArtistDetailScreenContent(
            artistName = "Daft Punk",
            uiState = ArtistDetailUiState.Success(previewSongs)
        )
    }
}
