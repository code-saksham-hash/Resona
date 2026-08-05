package com.resona.music.ui.player

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A small color scheme lifted from a track's artwork. Resona's base design
 * system is strict monochrome (see :core:ui's Color.kt), but the playback
 * surfaces -- mini-player, Now Playing, lyrics -- are a deliberate, scoped
 * exception: once a song is playing, those screens take on its colors.
 *
 * [background]/[onBackground] are for large tinted areas (the Now Playing
 * backdrop, the mini-player's glass); [accent]/[onAccent] are for the
 * smaller interactive bits that should pop against them (seek bar, play
 * button, the active lyrics line).
 */
data class AlbumArtPalette(
    val background: Color,
    val accent: Color,
    val onBackground: Color,
    val onAccent: Color,
    val isAdaptive: Boolean,
) {
    companion object {
        /** What every playback surface looks like before a color is known -- plain theme monochrome, no flash of the wrong look. */
        fun neutral(background: Color, onBackground: Color) = AlbumArtPalette(
            background = background,
            accent = onBackground,
            onBackground = onBackground,
            onAccent = background,
            isAdaptive = false,
        )
    }
}

/** Downsampled before handing to [Palette] -- swatch analysis doesn't need, or want, full resolution. */
private const val PALETTE_SAMPLE_PX = 112

/** How long a track's colors take to fade into place, both on first load and when skipping tracks. */
private val paletteAnimationSpec = tween<Color>(durationMillis = 600)

/**
 * Resolves to [AlbumArtPalette.neutral] immediately, then eases into the
 * artwork's extracted colors once [Palette] finishes running on a
 * background thread. Two calls with the same [artworkUrl] converge on the
 * same result -- Coil's memory cache guarantees the mini-player and Now
 * Playing screen never disagree about what a track's colors are.
 */
@Composable
fun rememberAlbumArtPalette(artworkUrl: String?): AlbumArtPalette {
    val context = LocalContext.current
    val neutral = AlbumArtPalette.neutral(
        background = MaterialTheme.colorScheme.surfaceContainer,
        onBackground = MaterialTheme.colorScheme.onSurface,
    )

    val extracted by produceState(initialValue = neutral, artworkUrl, neutral) {
        value = artworkUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { extractAlbumArtPalette(context, it) }
            ?: neutral
    }

    return AlbumArtPalette(
        background = animateColorAsState(extracted.background, paletteAnimationSpec, "albumPaletteBackground").value,
        accent = animateColorAsState(extracted.accent, paletteAnimationSpec, "albumPaletteAccent").value,
        onBackground = animateColorAsState(extracted.onBackground, paletteAnimationSpec, "albumPaletteOnBackground").value,
        onAccent = animateColorAsState(extracted.onAccent, paletteAnimationSpec, "albumPaletteOnAccent").value,
        isAdaptive = extracted.isAdaptive,
    )
}

private suspend fun extractAlbumArtPalette(context: Context, artworkUrl: String): AlbumArtPalette? =
    withContext(Dispatchers.Default) {
        val request = ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(PALETTE_SAMPLE_PX)
            .allowHardware(false) // Palette reads pixels straight off the bitmap
            .build()

        val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            ?: return@withContext null

        val palette = Palette.from(bitmap).generate()
        val accent = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
            ?: return@withContext null
        val background = palette.darkMutedSwatch ?: palette.dominantSwatch ?: accent

        val backgroundColor = Color(background.rgb)
        val accentColor = Color(accent.rgb)
        AlbumArtPalette(
            background = backgroundColor,
            accent = accentColor,
            onBackground = backgroundColor.contrastingOnColor(),
            onAccent = accentColor.contrastingOnColor(),
            isAdaptive = true,
        )
    }

private fun Color.contrastingOnColor(): Color = if (luminance() > 0.42f) Color.Black else Color.White
