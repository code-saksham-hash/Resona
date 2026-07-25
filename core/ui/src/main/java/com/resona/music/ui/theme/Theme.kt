package com.resona.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Resona's design system: a strict monochrome palette (see Color.kt) applied
 * unconditionally in both light and dark mode. Material You dynamic color is
 * intentionally unsupported -- the fixed black/white/gray palette is the
 * whole point, so there is no dynamicColor escape hatch here.
 */
@Composable
fun ResonaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
