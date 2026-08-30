package com.resona.music.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
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
import com.resona.music.domain.model.Album
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.ResonaTheme

@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit = {},
    onSongClick: (song: Song, songs: List<Song>) -> Unit = { _, _ -> },
    onArtistClick: (name: String, browseId: String) -> Unit = { _, _ -> },
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AlbumDetailScreenContent(
        uiState = uiState,
        onBack = onBack,
        onSongClick = onSongClick,
        onArtistClick = onArtistClick,
        onRetry = viewModel::retry
    )
}

@Composable
private fun AlbumDetailScreenContent(
    uiState: AlbumDetailUiState,
    onBack: () -> Unit = {},
    onSongClick: (song: Song, songs: List<Song>) -> Unit = { _, _ -> },
    onArtistClick: (name: String, browseId: String) -> Unit = { _, _ -> },
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
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (uiState) {
                AlbumDetailUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
                is AlbumDetailUiState.Success -> AlbumContent(
                    album = uiState.album,
                    onSongClick = onSongClick,
                    onArtistClick = onArtistClick,
                )
                AlbumDetailUiState.Empty -> Text(
                    text = "No tracks found for this album",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                is AlbumDetailUiState.Error -> Column(
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
private fun AlbumContent(
    album: Album,
    onSongClick: (song: Song, songs: List<Song>) -> Unit,
    onArtistClick: (name: String, browseId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { AlbumHeader(album = album, onSongClick = onSongClick, onArtistClick = onArtistClick) }
        itemsIndexed(album.songs, key = { _, song -> song.videoId }) { index, song ->
            AlbumTrackRow(
                trackNumber = index + 1,
                song = song,
                onClick = { onSongClick(song, album.songs) }
            )
        }
        // Clears the floating bottom chrome (pill nav, plus the mini-player
        // when a track is playing).
        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@Composable
private fun AlbumHeader(
    album: Album,
    onSongClick: (song: Song, songs: List<Song>) -> Unit,
    onArtistClick: (name: String, browseId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = album.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = listOfNotNull(album.artistName.takeIf { it.isNotBlank() }, album.year.takeIf { it.isNotBlank() })
                .joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .let { base ->
                    val artistBrowseId = album.artistBrowseId
                    if (artistBrowseId != null) base.clickable { onArtistClick(album.artistName, artistBrowseId) } else base
                }
        )

        if (album.songs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onSongClick(album.songs.first(), album.songs) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val shuffled = album.songs.shuffled()
                            onSongClick(shuffled.first(), shuffled)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle play",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    trackNumber: Int,
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = trackNumber.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(28.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
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

private val previewAlbum = Album(
    browseId = "MPREb_1",
    title = "Random Access Memories",
    artistName = "Daft Punk",
    artistBrowseId = "UC123",
    thumbnailUrl = "",
    year = "2013",
    songs = listOf(
        Song(videoId = "1", title = "Give Life Back to Music", artist = "Daft Punk", thumbnailUrl = "", duration = "4:34"),
        Song(videoId = "2", title = "Get Lucky", artist = "Daft Punk", thumbnailUrl = "", duration = "6:10"),
    )
)

@Preview(showBackground = true, name = "Album")
@Composable
private fun AlbumDetailScreenPreview() {
    ResonaTheme {
        AlbumDetailScreenContent(uiState = AlbumDetailUiState.Success(previewAlbum))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Album (dark)")
@Composable
private fun AlbumDetailScreenDarkPreview() {
    ResonaTheme(darkTheme = true) {
        AlbumDetailScreenContent(uiState = AlbumDetailUiState.Success(previewAlbum))
    }
}
