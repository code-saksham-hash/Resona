package com.resona.music.ui.library

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.resona.music.domain.model.Playlist
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.ResonaTheme
import com.resona.music.ui.theme.ResonaTopBar
import com.resona.music.ui.theme.rememberVoiceSearchLauncher

data class QuickLink(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color = Color.Unspecified
)

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onDownloadedSongClick: (Song) -> Unit = {},
    onLikedSongClick: (Song) -> Unit = {},
    onUserPlaylistClick: (Playlist) -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    LibraryScreenContent(
        downloadedSongs = downloadedSongs,
        likedSongs = likedSongs,
        playlists = playlists,
        importState = importState,
        onDownloadedSongClick = onDownloadedSongClick,
        onLikedSongClick = onLikedSongClick,
        onDeleteDownload = { song -> viewModel.deleteDownload(song.videoId) },
        onUnlike = viewModel::unlike,
        onCreatePlaylist = viewModel::createPlaylist,
        onImportPlaylist = viewModel::importPlaylist,
        onAcknowledgeImportResult = viewModel::acknowledgeImportResult,
        onUserPlaylistClick = onUserPlaylistClick,
        onSearchQuery = onSearchQuery,
        onSettingsClick = onSettingsClick,
        onStatsClick = onStatsClick,
        onHistoryClick = onHistoryClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenContent(
    downloadedSongs: List<DownloadedSong> = emptyList(),
    likedSongs: List<Song> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    importState: ImportPlaylistState = ImportPlaylistState.Idle,
    onDownloadedSongClick: (Song) -> Unit = {},
    onLikedSongClick: (Song) -> Unit = {},
    onDeleteDownload: (Song) -> Unit = {},
    onUnlike: (Song) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
    onImportPlaylist: (String) -> Unit = {},
    onAcknowledgeImportResult: () -> Unit = {},
    onUserPlaylistClick: (Playlist) -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Reacts to the ViewModel's side of an in-flight import instead of the
    // dialog owning success/failure itself, since the import survives a
    // config change (it's driven by viewModelScope) and this needs to catch
    // up whenever that finishes, not just right after the button is tapped.
    LaunchedEffect(importState) {
        when (importState) {
            is ImportPlaylistState.Success -> {
                showImportDialog = false
                importUrl = ""
                onAcknowledgeImportResult()
            }
            else -> Unit
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                newPlaylistName = ""
                showCreatePlaylistDialog = false
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "New Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(newPlaylistName.trim())
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Create", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showImportDialog) {
        val isImporting = importState is ImportPlaylistState.Importing
        AlertDialog(
            onDismissRequest = {
                if (!isImporting) {
                    importUrl = ""
                    showImportDialog = false
                    onAcknowledgeImportResult()
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Import from YouTube",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = importUrl,
                        onValueChange = { importUrl = it },
                        label = { Text("Playlist link") },
                        placeholder = { Text("https://youtube.com/playlist?list=...") },
                        singleLine = true,
                        enabled = !isImporting,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    if (importState is ImportPlaylistState.Failed) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = importState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onImportPlaylist(importUrl.trim()) },
                    enabled = importUrl.isNotBlank() && !isImporting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Import", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        importUrl = ""
                        showImportDialog = false
                        onAcknowledgeImportResult()
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

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
            val listState = rememberLazyListState()
            val downloadedSectionIndex = if (playlists.isNotEmpty()) 6 else 4
            val sortedPlaylists = playlists.sortedBy { it.createdAtMillis }

            val launchVoiceSearch = rememberVoiceSearchLauncher(onResult = onSearchQuery)
            ResonaTopBar(
                onDownloadsClick = {
                    if (downloadedSongs.isNotEmpty()) {
                        scope.launch { listState.animateScrollToItem(downloadedSectionIndex) }
                    }
                },
                onSettingsClick = onSettingsClick,
                onVoiceSearchClick = launchVoiceSearch
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                item(key = "header") {
                    LibraryHeader(
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onImportPlaylist = { showImportDialog = true }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item(key = "quicklinks") {
                    QuickLinksSection(
                        likedCount = likedSongs.size,
                        downloadedCount = downloadedSongs.size,
                        onLikedClick = {},
                        onDownloadsClick = {
                            if (downloadedSongs.isNotEmpty()) {
                                scope.launch { listState.animateScrollToItem(downloadedSectionIndex) }
                            }
                        },
                        onStatsClick = onStatsClick,
                        onHistoryClick = onHistoryClick
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                if (playlists.isNotEmpty()) {
                    item(key = "user_playlists") {
                        UserPlaylistsSection(
                            playlists = sortedPlaylists,
                            onPlaylistClick = onUserPlaylistClick
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
                if (downloadedSongs.isNotEmpty()) {
                    item(key = "downloaded") {
                        DownloadedSongsSection(
                            downloadedSongs = downloadedSongs,
                            onSongClick = onDownloadedSongClick,
                            onDeleteDownload = onDeleteDownload
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
                // Clears the floating bottom chrome (pill nav, plus the
                // mini-player when a track is playing) -- see HomeScreen's
                // matching spacer for why this needs to be this generous now.
                item { Spacer(modifier = Modifier.height(160.dp)) }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    onCreatePlaylist: () -> Unit = {},
    onImportPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 17.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.Link,
            contentDescription = "Import playlist from YouTube",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onImportPlaylist)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "Create playlist",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onCreatePlaylist)
        )
    }
}

@Composable
private fun QuickLinksSection(
    likedCount: Int,
    downloadedCount: Int,
    onLikedClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val quickLinks = listOf(
        QuickLink("liked", "Liked", "$likedCount tracks", Icons.Filled.Favorite, tint = Color.White) to onLikedClick,
        QuickLink("downloads", "Downloads", "$downloadedCount tracks", Icons.Outlined.DownloadDone) to onDownloadsClick,
        QuickLink("stats", "Stats", "Your listening", Icons.Outlined.Leaderboard) to onStatsClick,
        QuickLink("history", "History", "Recently played", Icons.Outlined.History) to onHistoryClick,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        quickLinks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (link, onClick) ->
                    QuickLinkCard(link = link, onClick = onClick, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickLinkCard(
    link: QuickLink,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = link.icon,
                contentDescription = null,
                tint = link.tint.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = link.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = link.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun UserPlaylistsSection(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        playlists.chunked(2).forEach { row ->
            if (row.size == 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { playlist ->
                        QuickLinkCard(
                            link = QuickLink(
                                id = playlist.id,
                                title = playlist.name,
                                subtitle = if (playlist.songs.isNotEmpty()) "${playlist.songs.size} tracks" else "Empty",
                                icon = Icons.Outlined.LibraryMusic
                            ),
                            onClick = { onPlaylistClick(playlist) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    QuickLinkCard(
                        link = QuickLink(
                            id = row.single().id,
                            title = row.single().name,
                            subtitle = if (row.single().songs.isNotEmpty()) "${row.single().songs.size} tracks" else "Empty",
                            icon = Icons.Outlined.LibraryMusic
                        ),
                        onClick = { onPlaylistClick(row.single()) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
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
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
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
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.small)
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
@Preview(showBackground = true)
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
