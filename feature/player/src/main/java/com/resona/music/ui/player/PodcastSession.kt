package com.resona.music.ui.player

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resona.music.ui.theme.ResonaTheme
import kotlinx.coroutines.delay

/**
 * UI-only podcast session header: two speaker avatars with an animated mic
 * between them. Stateless -- [isSpeaking] is hoisted; wire it to the real
 * voice-activity signal later (see note at the bottom of this file).
 */
@Composable
fun PodcastSession(
    avatarUrlA: String,
    avatarUrlB: String,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    nameA: String? = null,
    nameB: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SpeakerAvatar(
            avatarUrl = avatarUrlA,
            speakerLabel = "Speaker A",
            name = nameA
        )
        AnimatedMic(
            active = isSpeaking,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        SpeakerAvatar(
            avatarUrl = avatarUrlB,
            speakerLabel = "Speaker B",
            name = nameB
        )
    }
}

@Composable
fun SpeakerAvatar(
    avatarUrl: String,
    speakerLabel: String,
    modifier: Modifier = Modifier,
    name: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Same ColorPainter placeholder/error convention as MiniPlayerBar --
        // this module has no avatar drawable, so no new resource was added.
        val fallback = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        AsyncImage(
            model = avatarUrl,
            contentDescription = name ?: "$speakerLabel avatar",
            contentScale = ContentScale.Crop,
            placeholder = fallback,
            error = fallback,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
        )
        if (name != null) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun AnimatedMic(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val stateDescriptionText = if (active) "Speaking" else "Silent"

    // Infinite transition always runs so toggling doesn't restart the
    // clock; when inactive its outputs are blended to neutral (scale 1,
    // ripple alpha 0) via [visibility] instead of cutting abruptly.
    val transition = rememberInfiniteTransition(label = "mic")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )
    val rippleScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "micRippleScale"
    )
    val rippleAlphaRaw by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "micRippleAlpha"
    )
    val visibility by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "micVisibility"
    )

    // Fixed-size box so the expanding ripple never shifts the layout.
    Box(
        modifier = modifier
            .size(80.dp)
            .semantics {
                contentDescription = "Microphone"
                stateDescription = stateDescriptionText
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = rippleScale
                    scaleY = rippleScale
                    alpha = rippleAlphaRaw * visibility
                }
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
        )
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    val s = 1f + (pulseScale - 1f) * visibility
                    scaleX = s
                    scaleY = s
                }
        )
    }
}

/**
 * Self-running demo: flips [isSpeaking] every 2 seconds so the animation
 * can be eyeballed with no backend.
 */
@Composable
fun PodcastSessionDemo(modifier: Modifier = Modifier) {
    var speaking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            speaking = !speaking
        }
    }
    PodcastSession(
        avatarUrlA = "https://picsum.photos/seed/speakerA/200",
        avatarUrlB = "https://picsum.photos/seed/speakerB/200",
        isSpeaking = speaking,
        nameA = "Speaker A",
        nameB = "Speaker B",
        modifier = modifier
    )
}

@Preview(showBackground = true, name = "Podcast active")
@Composable
private fun PodcastSessionActivePreview() {
    ResonaTheme {
        PodcastSession(
            avatarUrlA = "https://picsum.photos/seed/speakerA/200",
            avatarUrlB = "https://picsum.photos/seed/speakerB/200",
            isSpeaking = true,
            nameA = "Speaker A",
            nameB = "Speaker B"
        )
    }
}

@Preview(showBackground = true, name = "Podcast inactive")
@Composable
private fun PodcastSessionInactivePreview() {
    ResonaTheme {
        PodcastSession(
            avatarUrlA = "https://picsum.photos/seed/speakerA/200",
            avatarUrlB = "https://picsum.photos/seed/speakerB/200",
            isSpeaking = false,
            nameA = "Speaker A",
            nameB = "Speaker B"
        )
    }
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    name = "Podcast active (dark)"
)
@Composable
private fun PodcastSessionDarkPreview() {
    ResonaTheme(darkTheme = true) {
        PodcastSession(
            avatarUrlA = "https://picsum.photos/seed/speakerA/200",
            avatarUrlB = "https://picsum.photos/seed/speakerB/200",
            isSpeaking = true,
            nameA = "Speaker A",
            nameB = "Speaker B"
        )
    }
}
