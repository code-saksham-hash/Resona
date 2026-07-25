package com.resona.music.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.resona.music.feature.player.R
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.ResonaTheme

/**
 * Pinned above the bottom navigation on every screen once something is
 * playing. Separation comes from a 1dp top divider, not elevation. Tapping
 * the play/pause button toggles playback; tapping anywhere else on the bar
 * opens the full Now Playing screen.
 */
@Composable
fun MiniPlayerBar(
    track: Song,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Only the thumbnail/title/artist are wrapped in the "open Now
            // Playing" tap target -- kept as a sibling of IconButton below,
            // not an ancestor, so there's no nested-clickable region for the
            // play/pause tap to fight with.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onTogglePlayPause) {
                if (isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "Pause",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private val previewTrack = Song(
    videoId = "1",
    title = "Midnight City",
    artist = "M83",
    thumbnailUrl = ""
)

@Preview(showBackground = true, name = "Playing")
@Composable
private fun MiniPlayerBarPlayingPreview() {
    ResonaTheme {
        MiniPlayerBar(
            track = previewTrack,
            isPlaying = true,
            onTogglePlayPause = {},
            onClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Paused")
@Composable
private fun MiniPlayerBarPausedPreview() {
    ResonaTheme {
        MiniPlayerBar(
            track = previewTrack,
            isPlaying = false,
            onTogglePlayPause = {},
            onClick = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Playing (dark)")
@Composable
private fun MiniPlayerBarDarkPreview() {
    ResonaTheme(darkTheme = true) {
        MiniPlayerBar(
            track = previewTrack,
            isPlaying = true,
            onTogglePlayPause = {},
            onClick = {}
        )
    }
}
