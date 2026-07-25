package com.resona.music.ui.nowplaying

import android.content.res.Configuration
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resona.music.feature.player.R
import com.resona.music.domain.model.Song
import com.resona.music.playback.PlayerUiState
import com.resona.music.ui.theme.ResonaTheme

/**
 * Stateless by design, like [com.resona.music.ui.player.MiniPlayerBar] --
 * [com.resona.music.playback.PlayerViewModel] is Activity-scoped and owned
 * by the nav graph, so this screen only ever receives its state and actions
 * as parameters rather than resolving its own (destination-scoped) instance.
 */
@Composable
fun NowPlayingScreen(
    uiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onQueueClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        NowPlayingTopBar(onBack = onBack, onQueueClick = onQueueClick)

        val track = uiState.currentTrack
        if (track == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = "Nothing is playing",
                    style = MaterialTheme.typography.bodyMedium,
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
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlbumArt(thumbnailUrl = track.thumbnailUrl, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                SeekBar(
                    position = uiState.position,
                    duration = uiState.duration,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                PlaybackControls(
                    isPlaying = uiState.isPlaying,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
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
                    text = { Text("Like") },
                    onClick = { overflowExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
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
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
    )
}

/**
 * While the thumb is being dragged, the displayed value tracks the drag
 * locally instead of [position] -- otherwise the ~500ms position poll in
 * PlayerViewModel would fight the user's finger. [onSeek] (and therefore the
 * real [position]) only updates once the drag ends.
 */
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
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(displayedMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_previous),
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isPlaying) {
                Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = "Pause",
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        IconButton(onClick = onSkipNext, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_skip_next),
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
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

@Preview(showBackground = true, name = "Paused")
@Composable
private fun NowPlayingScreenPausedPreview() {
    ResonaTheme {
        NowPlayingScreen(
            uiState = PlayerUiState(
                currentTrack = previewTrack,
                isPlaying = false,
                position = 30_000L,
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
