package com.resona.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Full-bleed backdrop for Now Playing: the artwork itself, blurred and
 * scrimmed toward [AlbumArtPalette.background], so the player/queue/lyrics
 * tabs all sit on the same album-derived backdrop. Blur only renders on
 * API 31+ (see [androidx.compose.ui.draw.blur]) -- below that the scrim
 * alone still carries the adaptive tint, just without the soft-focus
 * artwork underneath it.
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
