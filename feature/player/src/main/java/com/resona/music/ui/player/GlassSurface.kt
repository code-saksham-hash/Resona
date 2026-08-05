package com.resona.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Frosted-glass tint for a compact surface: a diagonal wash between
 * [AlbumArtPalette.background] and [AlbumArtPalette.accent], a faint sheen,
 * and a light edge so it reads as glass rather than a flat fill. Used by
 * the mini-player.
 */
fun Modifier.glassPanel(palette: AlbumArtPalette, shape: Shape): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                palette.background.copy(alpha = 0.55f),
                palette.accent.copy(alpha = if (palette.isAdaptive) 0.30f else 0.55f),
            )
        )
    )
    .background(Color.White.copy(alpha = 0.05f))
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f))
        ),
        shape = shape,
    )

/**
 * Full-bleed backdrop for Now Playing: the artwork itself, blurred and
 * scrimmed toward [AlbumArtPalette.background], so the player/queue/lyrics
 * tabs all sit on the same glass-over-the-album look as the mini-player.
 * Blur only renders on API 31+ (see [androidx.compose.ui.draw.blur]) --
 * below that the scrim alone still carries the adaptive tint, just without
 * the soft-focus artwork underneath it.
 */
@Composable
fun AlbumArtBackdrop(
    artworkUrl: String?,
    palette: AlbumArtPalette,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(72.dp)
                    .alpha(0.55f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        )
    }
}
