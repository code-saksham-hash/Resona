package com.resona.music.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.AlbumSummary
import com.resona.music.domain.model.Artist
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.ResonaTheme
import com.resona.music.ui.theme.SectionHeaderTextStyle

@Composable
fun ArtistDetailScreen(
    onBack: () -> Unit = {},
    onSongClick: (song: Song, songs: List<Song>) -> Unit = { _, _ -> },
    onShufflePlay: (songs: List<Song>) -> Unit = {},
    onAlbumClick: (album: AlbumSummary) -> Unit = {},
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistDetailScreenContent(
        artistName = viewModel.artistName,
        uiState = uiState,
        onBack = onBack,
        onSongClick = onSongClick,
        onShufflePlay = onShufflePlay,
        onAlbumClick = onAlbumClick,
        onRetry = viewModel::retry
    )
}

@Composable
private fun ArtistDetailScreenContent(
    artistName: String,
    uiState: ArtistDetailUiState,
    onBack: () -> Unit = {},
    onSongClick: (song: Song, songs: List<Song>) -> Unit = { _, _ -> },
    onShufflePlay: (songs: List<Song>) -> Unit = {},
    onAlbumClick: (album: AlbumSummary) -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            ArtistDetailUiState.Loading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            is ArtistDetailUiState.Success -> ArtistProfile(
                artist = uiState.artist,
                onSongClick = onSongClick,
                onShufflePlay = onShufflePlay,
                onAlbumClick = onAlbumClick,
            )
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

        // Floats over the hero image (see ArtistProfile) rather than sharing
        // its own row/height -- a fixed top bar would either clip the image
        // or force a second, redundant title underneath it.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}

/**
 * Fills the screen the way the reference apps' artist pages do: a full-bleed
 * hero image fading into the background, the artist's name and real listener
 * count over it, a Shuffle button, then real "Songs"/"Albums"/"Singles & EPs"
 * shelves -- as opposed to the old plain back-bar-plus-list this replaces.
 * [Artist.description]/[Artist.albums]/[Artist.singles] are simply omitted
 * when empty (the name-only-artist fallback in ArtistDetailViewModel has
 * none of them), so a resolved vs. unresolved artist share this exact same
 * layout rather than two different screens.
 */
@Composable
private fun ArtistProfile(
    artist: Artist,
    onSongClick: (song: Song, songs: List<Song>) -> Unit,
    onShufflePlay: (songs: List<Song>) -> Unit,
    onAlbumClick: (album: AlbumSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { ArtistHero(artist = artist, onShufflePlay = onShufflePlay) }

        if (artist.description.isNotBlank()) {
            item {
                Text(
                    text = artist.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp)
                )
            }
        }

        if (artist.topSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Songs",
                    style = SectionHeaderTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp)
                )
            }
            items(artist.topSongs, key = { it.videoId }) { song ->
                ArtistTopSongRow(song = song, onClick = { onSongClick(song, artist.topSongs) })
            }
        }

        if (artist.albums.isNotEmpty()) {
            item { AlbumShelf(title = "Albums", albums = artist.albums, onAlbumClick = onAlbumClick) }
        }
        if (artist.singles.isNotEmpty()) {
            item { AlbumShelf(title = "Singles & EPs", albums = artist.singles, onAlbumClick = onAlbumClick) }
        }

        // Clears the floating bottom chrome (pill nav, plus the mini-player
        // when a track is playing).
        item { Spacer(modifier = Modifier.height(160.dp)) }
    }
}

@Composable
private fun ArtistHero(
    artist: Artist,
    onShufflePlay: (songs: List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        )
        // Separate scrim layer (rather than baked into the image's own
        // modifier) so it composites over the image instead of being
        // clipped to its content bounds.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 17.dp, vertical = 16.dp)
        ) {
            Text(
                text = artist.name.ifBlank { "Artist" },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.listenerCountText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist.listenerCountText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (artist.topSongs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onShufflePlay(artist.topSongs) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumShelf(
    title: String,
    albums: List<AlbumSummary>,
    onAlbumClick: (AlbumSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = SectionHeaderTextStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 17.dp),
        ) {
            albums.forEachIndexed { index, album ->
                AlbumChip(album = album, onClick = { onAlbumClick(album) })
                if (index != albums.lastIndex) Spacer(modifier = Modifier.width(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun AlbumChip(album: AlbumSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (album.year.isNotBlank()) {
            Text(
                text = album.year,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ArtistTopSongRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 8.dp),
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
            if (song.duration.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}

private val previewArtist = Artist(
    browseId = "UC123",
    name = "Daft Punk",
    thumbnailUrl = "",
    description = "French electronic music duo formed in 1993.",
    listenerCountText = "12.3M monthly listeners",
    topSongs = listOf(
        Song(videoId = "1", title = "Instant Crush", artist = "Daft Punk", thumbnailUrl = "", duration = "5:38"),
        Song(videoId = "2", title = "Get Lucky", artist = "Daft Punk", thumbnailUrl = "", duration = "6:10"),
    ),
    albums = listOf(
        AlbumSummary(browseId = "A1", title = "Random Access Memories", thumbnailUrl = "", year = "2013"),
        AlbumSummary(browseId = "A2", title = "Discovery", thumbnailUrl = "", year = "2001"),
    )
)

@Preview(showBackground = true, name = "Artist")
@Composable
private fun ArtistDetailScreenPreview() {
    ResonaTheme {
        ArtistDetailScreenContent(
            artistName = "Daft Punk",
            uiState = ArtistDetailUiState.Success(previewArtist)
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Artist (dark)")
@Composable
private fun ArtistDetailScreenDarkPreview() {
    ResonaTheme(darkTheme = true) {
        ArtistDetailScreenContent(
            artistName = "Daft Punk",
            uiState = ArtistDetailUiState.Success(previewArtist)
        )
    }
}
