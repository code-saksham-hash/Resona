package com.resona.music.ui.library

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
import com.resona.music.ui.theme.JosefinSansFontFamily
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.ResonaTheme

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlaylistDetailScreenContent(
        title = viewModel.title,
        uiState = uiState,
        onBack = onBack,
        onSongClick = onSongClick,
        onRetry = viewModel::retry
    )
}

@Composable
private fun PlaylistDetailScreenContent(
    title: String,
    uiState: PlaylistDetailUiState,
    onBack: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
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
                text = title.ifBlank { "Playlist" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = JosefinSansFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (uiState) {
                PlaylistDetailUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                is PlaylistDetailUiState.Success -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.songs, key = { it.videoId }) { song ->
                        PlaylistSongRow(song = song, onClick = { onSongClick(song) })
                    }
                }
                PlaylistDetailUiState.Empty -> Text(
                    text = "This playlist has no tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                is PlaylistDetailUiState.Error -> Column(
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
private fun PlaylistSongRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
    Song(videoId = "1", title = "Thinking Out Loud", artist = "Ed Sheeran", thumbnailUrl = "", duration = "4:50"),
    Song(videoId = "2", title = "Someone You Loved", artist = "Lewis Capaldi", thumbnailUrl = "", duration = "3:02"),
)

@Preview(showBackground = true, name = "Playlist")
@Composable
private fun PlaylistDetailScreenPreview() {
    ResonaTheme {
        PlaylistDetailScreenContent(
            title = "Mellow Pop Classics",
            uiState = PlaylistDetailUiState.Success(previewSongs)
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Playlist (dark)")
@Composable
private fun PlaylistDetailScreenDarkPreview() {
    ResonaTheme(darkTheme = true) {
        PlaylistDetailScreenContent(
            title = "Mellow Pop Classics",
            uiState = PlaylistDetailUiState.Success(previewSongs)
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun PlaylistDetailScreenLoadingPreview() {
    ResonaTheme {
        PlaylistDetailScreenContent(title = "Mellow Pop Classics", uiState = PlaylistDetailUiState.Loading)
    }
}
