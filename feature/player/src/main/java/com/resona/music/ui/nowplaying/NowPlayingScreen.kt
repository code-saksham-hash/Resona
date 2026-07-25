package com.resona.music.ui.nowplaying

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resona.music.domain.model.Song
import com.resona.music.feature.player.R
import com.resona.music.playback.DownloadState
import com.resona.music.playback.LyricsState
import com.resona.music.playback.PlayerUiState
import com.resona.music.ui.theme.ResonaTheme

@Composable
fun NowPlayingScreen(
    uiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onQueueClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onLoadLyrics: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NowPlayingTopBar(
            onBack = onBack,
            onQueueClick = onQueueClick,
            downloadState = uiState.downloadState,
            onDownloadClick = onDownloadClick
        )

        val track = uiState.currentTrack
        if (track == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = "Nothing is playing",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                AlbumArt(
                    thumbnailUrl = track.highResThumbnailUrl,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                SeekBar(
                    position = uiState.position,
                    duration = uiState.duration,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                PlaybackControls(
                    isPlaying = uiState.isPlaying,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    isLiked = uiState.isLiked,
                    onToggleLike = onToggleLike,
                    downloadState = uiState.downloadState,
                    onDownloadClick = onDownloadClick
                )

                val playError = uiState.error
                if (playError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = playError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }

                val downloadError = (uiState.downloadState as? DownloadState.Failed)?.message
                if (downloadError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Download failed: $downloadError",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                LyricsSection(
                    lyricsState = uiState.lyricsState,
                    onExpand = onLoadLyrics,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LyricsSection(
    lyricsState: LyricsState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    expanded = !expanded
                    if (expanded) onExpand()
                }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse lyrics" else "Expand lyrics",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                when (lyricsState) {
                    LyricsState.NotLoaded, LyricsState.Loading -> CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    is LyricsState.Available -> Text(
                        text = lyricsState.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LyricsState.Unavailable -> Text(
                        text = "Lyrics not available for this track",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    downloadState: DownloadState = DownloadState.Idle,
    onDownloadClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onQueueClick) {
            Icon(
                painter = painterResource(R.drawable.ic_queue),
                contentDescription = "Queue",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Box {
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = { overflowExpanded = false }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            when (downloadState) {
                                DownloadState.Downloading -> "Downloading…"
                                DownloadState.Downloaded -> "Downloaded"
                                else -> "Download"
                            }
                        )
                    },
                    enabled = downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Downloaded,
                    onClick = {
                        onDownloadClick()
                        overflowExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = { overflowExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun AlbumArt(thumbnailUrl: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
    )
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val safeDuration = duration.coerceAtLeast(1L)
    val displayedMillis = dragPosition?.toLong() ?: position

    Column(modifier = modifier) {
        Slider(
            value = (dragPosition ?: position.toFloat()).coerceIn(0f, safeDuration.toFloat()),
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                dragPosition?.let { onSeek(it.toLong()) }
                dragPosition = null
            },
            valueRange = 0f..safeDuration.toFloat(),
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(displayedMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    downloadState: DownloadState = DownloadState.Idle,
    onDownloadClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleLike,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "Pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDownloadClick,
                enabled = downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Downloaded,
                modifier = Modifier.size(44.dp)
            ) {
                when (downloadState) {
                    DownloadState.Downloading -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DownloadState.Downloaded -> Icon(
                        imageVector = Icons.Outlined.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                    is DownloadState.Failed -> Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Download failed, tap to retry",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                    DownloadState.Idle -> Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private val previewTrack = Song(
    videoId = "1",
    title = "Midnight City",
    artist = "M83",
    thumbnailUrl = ""
)

@Preview(showBackground = true, name = "Playing")
@Composable
private fun NowPlayingScreenPlayingPreview() {
    ResonaTheme {
        NowPlayingScreen(
            uiState = PlayerUiState(
                currentTrack = previewTrack,
                isPlaying = true,
                position = 87_000L,
                duration = 244_000L
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onQueueClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Playing (dark)")
@Composable
private fun NowPlayingScreenPlayingDarkPreview() {
    ResonaTheme(darkTheme = true) {
        NowPlayingScreen(
            uiState = PlayerUiState(
                currentTrack = previewTrack,
                isPlaying = true,
                position = 87_000L,
                duration = 244_000L
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onQueueClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun NowPlayingScreenEmptyPreview() {
    ResonaTheme {
        NowPlayingScreen(
            uiState = PlayerUiState(),
            onTogglePlayPause = {},
            onSeek = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onQueueClick = {},
            onBack = {}
        )
    }
}
