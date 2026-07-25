package com.resona.music.ui.library

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.FeaturedPlaylist
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.JosefinSansFontFamily
import com.resona.music.ui.theme.ResonaLogoIcon
import com.resona.music.ui.theme.ResonaSearchEntryBar
import com.resona.music.ui.theme.ResonaTheme

data class QuickLink(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onPlaylistClick: (FeaturedPlaylist) -> Unit = {},
    onDownloadedSongClick: (Song) -> Unit = {},
    onLikedSongClick: (Song) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val featuredPlaylists by viewModel.featuredPlaylists.collectAsStateWithLifecycle()

    LibraryScreenContent(
        downloadedSongs = downloadedSongs,
        likedSongs = likedSongs,
        featuredPlaylists = featuredPlaylists,
        onPlaylistClick = onPlaylistClick,
        onDownloadedSongClick = onDownloadedSongClick,
        onLikedSongClick = onLikedSongClick,
        onDeleteDownload = { song -> viewModel.deleteDownload(song.videoId) },
        onUnlike = viewModel::unlike,
        onSearchClick = onSearchClick,
        onProfileClick = onProfileClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenContent(
    downloadedSongs: List<DownloadedSong> = emptyList(),
    likedSongs: List<Song> = emptyList(),
    featuredPlaylists: List<FeaturedPlaylist> = emptyList(),
    onPlaylistClick: (FeaturedPlaylist) -> Unit = {},
    onDownloadedSongClick: (Song) -> Unit = {},
    onLikedSongClick: (Song) -> Unit = {},
    onDeleteDownload: (Song) -> Unit = {},
    onUnlike: (Song) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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
        Column(modifier = Modifier.fillMaxSize()) {
            LibraryTopBar(onSearchClick = onSearchClick, onProfileClick = onProfileClick)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                item { LibraryHeader() }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item {
                    QuickLinksSection(
                        likedCount = likedSongs.size,
                        downloadedCount = downloadedSongs.size
                    )
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
                if (likedSongs.isNotEmpty()) {
                    item {
                        LikedSongsSection(
                            likedSongs = likedSongs,
                            onSongClick = onLikedSongClick,
                            onUnlike = onUnlike
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
                if (downloadedSongs.isNotEmpty()) {
                    item {
                        DownloadedSongsSection(
                            downloadedSongs = downloadedSongs,
                            onSongClick = onDownloadedSongClick,
                            onDeleteDownload = onDeleteDownload
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
                if (featuredPlaylists.isNotEmpty()) {
                    item { PlaylistsSection(playlists = featuredPlaylists, onPlaylistClick = onPlaylistClick) }
                }
                item { Spacer(modifier = Modifier.height(68.dp)) }
            }
        }
    }
}

@Composable
private fun LibraryTopBar(
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 17.dp)
            .height(61.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = ResonaLogoIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        ResonaSearchEntryBar(
            onClick = onSearchClick,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onProfileClick) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun LibraryHeader(modifier: Modifier = Modifier) {
    Text(
        text = "Your Library",
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        fontFamily = JosefinSansFontFamily,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 17.dp)
    )
}

@Composable
private fun QuickLinksSection(likedCount: Int, downloadedCount: Int, modifier: Modifier = Modifier) {
    val likedSubtitle = if (likedCount > 0) "$likedCount tracks" else "Your favorites"
    val downloadsSubtitle = if (downloadedCount > 0) "$downloadedCount tracks" else "Offline tracks"
    val quickLinks = listOf(
        QuickLink("liked", "Liked Songs", likedSubtitle, Icons.Outlined.FavoriteBorder),
        QuickLink("downloads", "Downloads", downloadsSubtitle, Icons.Outlined.DownloadDone),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        quickLinks.forEach { link ->
            QuickLinkCard(link = link, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickLinkCard(
    link: QuickLink,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable { }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = link.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = link.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = link.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LikedSongsSection(
    likedSongs: List<Song>,
    onSongClick: (Song) -> Unit = {},
    onUnlike: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Liked Songs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = JosefinSansFontFamily,
                fontWeight = FontWeight.Bold
            )
        }

        likedSongs.forEach { song ->
            LibrarySongRow(
                song = song,
                trailingIcon = Icons.Filled.Favorite,
                trailingIconDescription = "Liked",
                onClick = { onSongClick(song) },
                onRemove = { onUnlike(song) }
            )
        }
    }
}

@Composable
private fun DownloadedSongsSection(
    downloadedSongs: List<DownloadedSong>,
    onSongClick: (Song) -> Unit = {},
    onDeleteDownload: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Downloaded",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = JosefinSansFontFamily,
                fontWeight = FontWeight.Bold
            )
        }

        downloadedSongs.forEach { downloaded ->
            LibrarySongRow(
                song = downloaded.song,
                trailingIcon = Icons.Outlined.DownloadDone,
                trailingIconDescription = "Downloaded",
                onClick = { onSongClick(downloaded.song) },
                onRemove = { onDeleteDownload(downloaded.song) }
            )
        }
    }
}

@Composable
private fun LibrarySongRow(
    song: Song,
    trailingIcon: ImageVector,
    trailingIconDescription: String,
    onClick: () -> Unit = {},
    // Tapping the trailing status icon removes it from this list -- null
    // when a row shouldn't be removable at all (there's no such case today,
    // but the split keeps LibrarySongRow reusable for a future non-removable
    // list without a nullability check bleeding into callers that always
    // pass one).
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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
                .background(MaterialTheme.colorScheme.surfaceContainer)
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = "Remove from $trailingIconDescription",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Icon(
                imageVector = trailingIcon,
                contentDescription = trailingIconDescription,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaylistsSection(
    playlists: List<FeaturedPlaylist>,
    onPlaylistClick: (FeaturedPlaylist) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                // Real YouTube Music playlists, not something tied to an
                // account this app doesn't have -- "Featured" instead of
                // "Your" for the same reason Home's artist row isn't "Your
                // Top Artists" either.
                text = "Featured Playlists",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = JosefinSansFontFamily,
                fontWeight = FontWeight.Bold
            )
        }

        playlists.forEach { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: FeaturedPlaylist,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AsyncImage(
                model = playlist.thumbnailUrl,
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
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = playlist.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Outlined.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenLightPreview() {
    ResonaTheme {
        LibraryScreenContent()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryScreenDarkPreview() {
    ResonaTheme {
        LibraryScreenContent()
    }
}
