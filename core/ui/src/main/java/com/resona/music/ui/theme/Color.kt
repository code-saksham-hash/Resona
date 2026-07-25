package com.resona.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val NocturneSurface = Color(0xFF121414)
val NocturneSurfaceDim = Color(0xFF121414)
val NocturneSurfaceBright = Color(0xFF383939)
val NocturneSurfaceContainerLowest = Color(0xFF0D0E0F)
val NocturneSurfaceContainerLow = Color(0xFF1B1C1C)
val NocturneSurfaceContainer = Color(0xFF1F2020)
val NocturneSurfaceContainerHigh = Color(0xFF292A2A)
val NocturneSurfaceContainerHighest = Color(0xFF343535)
val NocturneOnSurface = Color(0xFFE3E2E2)
val NocturneOnSurfaceVariant = Color(0xFFC4C7C8)
val NocturnePrimary = Color(0xFFFDFDFC)
val NocturneOnPrimary = Color(0xFF2F3131)
val NocturnePrimaryContainer = Color(0xFFE0E0E0)
val NocturneOnPrimaryContainer = Color(0xFF626363)
val NocturneOutline = Color(0xFF8E9192)
val NocturneOutlineVariant = Color(0xFF444748)
val NocturneInverseSurface = Color(0xFFE3E2E2)
val NocturneInverseOnSurface = Color(0xFF303031)
val NocturneError = Color(0xFFFFB4AB)
val NocturneOnError = Color(0xFF690005)
val NocturneErrorContainer = Color(0xFF93000A)
val NocturneOnErrorContainer = Color(0xFFFFDAD6)

val NocturneColorScheme: ColorScheme = darkColorScheme(
    primary = NocturnePrimary,
    onPrimary = NocturneOnPrimary,
    primaryContainer = NocturnePrimaryContainer,
    onPrimaryContainer = NocturneOnPrimaryContainer,
    secondary = NocturneOnSurfaceVariant,
    onSecondary = NocturneSurface,
    secondaryContainer = NocturneSurfaceContainerHigh,
    onSecondaryContainer = NocturneOnSurfaceVariant,
    tertiary = NocturneOnSurface,
    onTertiary = NocturneSurface,
    tertiaryContainer = NocturneSurfaceContainerHighest,
    onTertiaryContainer = NocturneOnSurface,
    background = NocturneSurface,
    onBackground = NocturneOnSurface,
    surface = NocturneSurface,
    onSurface = NocturneOnSurface,
    surfaceVariant = NocturneSurfaceContainerHighest,
    onSurfaceVariant = NocturneOnSurfaceVariant,
    surfaceTint = NocturnePrimary,
    inverseSurface = NocturneInverseSurface,
    inverseOnSurface = NocturneInverseOnSurface,
    error = NocturneError,
    onError = NocturneOnError,
    errorContainer = NocturneErrorContainer,
    onErrorContainer = NocturneOnErrorContainer,
    outline = NocturneOutline,
    outlineVariant = NocturneOutlineVariant,
    scrim = Color.Black,
    surfaceBright = NocturneSurfaceBright,
    surfaceDim = NocturneSurfaceDim,
    surfaceContainer = NocturneSurfaceContainer,
    surfaceContainerHigh = NocturneSurfaceContainerHigh,
    surfaceContainerHighest = NocturneSurfaceContainerHighest,
    surfaceContainerLow = NocturneSurfaceContainerLow,
    surfaceContainerLowest = NocturneSurfaceContainerLowest
)
