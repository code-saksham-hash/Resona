package com.resona.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The entire palette. Every color used anywhere in the app must be one of
// these six values -- no hues, no tints, no Material You dynamic color.
//
// The one deliberate exception: once a song is playing, :feature:player's
// mini-player/Now Playing/lyrics surfaces take on that track's album-art
// colors (see AlbumArtPalette.kt there). It's scoped to that module and
// falls back to this monochrome scheme whenever a color hasn't been
// extracted yet -- nothing else in the app should reach for hue.
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Gray900 = Color(0xFF1A1A1A)
val Gray800 = Color(0xFF2E2E2E)
val Gray600 = Color(0xFF757575)
val Gray300 = Color(0xFFE0E0E0)

val NocturneSurface = Color(0xFF000000)
val NocturneSurfaceContainerLowest = Color(0xFF0D0E0F)

// error/onError etc. are deliberately mapped to the same black/white pairing
// as primary rather than left unset -- Material3's defaults for those roles
// are a saturated red, which would smuggle color back into the palette.
//
// secondaryContainer is intentionally solid black rather than a soft gray:
// it's what renders behind the selected item in the bottom navigation bar,
// and a gray-on-gray pill there is invisible against the Gray300 bar
// background. Every other *Container role keeps the softer Gray300/Gray800
// fill.
val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = Gray300,
    onPrimaryContainer = Black,
    inversePrimary = White,
    secondary = Gray600,
    onSecondary = White,
    secondaryContainer = Black,
    onSecondaryContainer = White,
    tertiary = Gray600,
    onTertiary = White,
    tertiaryContainer = Gray300,
    onTertiaryContainer = Black,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = Gray300,
    onSurfaceVariant = Gray600,
    surfaceTint = Black,
    inverseSurface = Black,
    inverseOnSurface = White,
    error = Black,
    onError = White,
    errorContainer = Gray300,
    onErrorContainer = Black,
    outline = Gray600,
    outlineVariant = Gray300,
    scrim = Black,
    surfaceBright = White,
    surfaceDim = Gray300,
    surfaceContainer = Gray300,
    surfaceContainerHigh = Gray300,
    surfaceContainerHighest = Gray300,
    surfaceContainerLow = White,
    surfaceContainerLowest = White
)

val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = Gray800,
    onPrimaryContainer = White,
    inversePrimary = Black,
    secondary = Gray600,
    onSecondary = Black,
    secondaryContainer = White,
    onSecondaryContainer = Black,
    tertiary = Gray600,
    onTertiary = Black,
    tertiaryContainer = Gray800,
    onTertiaryContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Gray900,
    onSurfaceVariant = Gray600,
    surfaceTint = White,
    inverseSurface = White,
    inverseOnSurface = Black,
    error = White,
    onError = Black,
    errorContainer = Gray800,
    onErrorContainer = White,
    outline = Gray600,
    outlineVariant = Gray800,
    scrim = Black,
    surfaceBright = Gray800,
    surfaceDim = Black,
    surfaceContainer = Gray900,
    surfaceContainerHigh = Gray800,
    surfaceContainerHighest = Gray800,
    surfaceContainerLow = Gray900,
    surfaceContainerLowest = Black
)
