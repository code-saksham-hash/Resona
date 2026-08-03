package com.resona.music.ui.player

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resona.music.feature.player.R
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.ResonaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun MiniPlayerBar(
    track: Song,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onSkipToNext: () -> Unit,
    onClick: () -> Unit,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(50.dp))
            .alpha(0.7f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
    ) {
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 14.dp, vertical = 3.dp)
            )
        }

        val textMeasurer = rememberTextMeasurer()
        val titleStyle = MaterialTheme.typography.titleSmall
        val marqueeAnim = remember { Animatable(0f) }
        val containerWidthPx = remember { mutableStateOf(0f) }
        val naturalTextWidthPx = remember { mutableStateOf(0f) }

        LaunchedEffect(track.title) {
            naturalTextWidthPx.value = 0f
            naturalTextWidthPx.value = textMeasurer.measure(
                text = AnnotatedString(track.title),
                style = titleStyle,
                maxLines = 1,
                constraints = Constraints()
            ).size.width.toFloat()
        }

        LaunchedEffect(Unit) {
            while (isActive) {
                val cw = containerWidthPx.value
                val tw = naturalTextWidthPx.value
                if (cw <= 0f || tw <= 0f) {
                    marqueeAnim.snapTo(0f)
                    delay(50)
                    continue
                }
                if (tw > cw) {
                    val totalDist = tw + cw + 40f
                    val duration = (totalDist / 30f).toInt().coerceAtLeast(4000)
                    marqueeAnim.snapTo(cw)
                    marqueeAnim.animateTo(
                        targetValue = -(tw + 40f),
                        animationSpec = tween(duration, easing = LinearEasing)
                    )
                } else {
                    marqueeAnim.snapTo(0f)
                    delay(100)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { clip = true }
                        .onSizeChanged { containerWidthPx.value = it.width.toFloat() }
                ) {
                    val density = LocalDensity.current
                    val textWidthDp = with(density) { naturalTextWidthPx.value.toDp() }
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        modifier = Modifier
                            .requiredWidth(textWidthDp)
                            .offset { IntOffset(x = marqueeAnim.value.roundToInt(), y = 0) }
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

            IconButton(onClick = onSkipToPrevious, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(44.dp)) {
                if (isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "Pause",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            IconButton(onClick = onSkipToNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
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
            onSkipToPrevious = {},
            onSkipToNext = {},
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
            onSkipToPrevious = {},
            onSkipToNext = {},
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
            onSkipToPrevious = {},
            onSkipToNext = {},
            onClick = {}
        )
    }
}
