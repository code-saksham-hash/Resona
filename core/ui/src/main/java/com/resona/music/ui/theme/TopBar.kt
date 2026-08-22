package com.resona.music.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The app-wide top bar: brand mark on the left, a cluster of glass icon
 * buttons on the right (downloads shortcut, settings, voice search). Shared
 * across Home/Library rather than duplicated per feature module, per
 * CONTRIBUTING.md's rule on where reused UI belongs -- both screens used to
 * carry a near-identical copy with a live search field in between instead;
 * that field now lives on the Search tab itself (see [ResonaSearchEntryBar]
 * usages), so this bar no longer needs to reserve space for one.
 */
@Composable
fun ResonaTopBar(
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onVoiceSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 17.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = ResonaLogoIcon(),
            contentDescription = "Resona",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        TopBarIconButton(
            icon = Icons.Outlined.Download,
            contentDescription = "Downloads",
            onClick = onDownloadsClick
        )
        Spacer(modifier = Modifier.width(10.dp))
        TopBarIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = "Settings",
            onClick = onSettingsClick
        )
        Spacer(modifier = Modifier.width(10.dp))
        TopBarIconButton(
            icon = Icons.Outlined.MusicNote,
            contentDescription = "Search by voice",
            onClick = onVoiceSearchClick,
            accent = true
        )
    }
}

@Composable
private fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "topBarButtonScale"
    )

    // A one-shot expanding ring on tap, on top of the press-scale every
    // button gets -- the voice-search button is the one action here that
    // triggers something happening off-screen (the system speech
    // recognizer), so it gets a more noticeable "heard you" pulse than a
    // bare ripple would give.
    var pulseTrigger by remember { mutableIntStateOf(0) }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger == 0) return@LaunchedEffect
        pulse.snapTo(0f)
        pulse.animateTo(1f, animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        if (accent && pulse.value in 0f..1f && pulseTrigger > 0) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(1f + pulse.value * 0.7f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = (1f - pulse.value) * 0.45f))
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (accent) MaterialTheme.colorScheme.tertiary else Color.White.copy(alpha = 0.08f))
                .border(
                    width = 0.5.dp,
                    color = if (accent) Color.Transparent else Color.White.copy(alpha = 0.12f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (accent) pulseTrigger++
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (accent) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
