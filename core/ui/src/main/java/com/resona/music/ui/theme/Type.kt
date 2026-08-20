package com.resona.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Josefin Sans is the brand face (geometric, elegant): it titles the app.
 * Bundled as regular + bold statics, so the display and headline tiers use
 * it; everything below title scale renders in Montserrat instead, which was
 * tuned for smaller sizes.
 */
val JosefinSansFontFamily = FontFamily(
    Font(com.resona.music.core.ui.R.font.josefin_sans_regular, FontWeight.Normal),
    Font(com.resona.music.core.ui.R.font.josefin_sans_bold, FontWeight.Bold),
)

/**
 * Inter is the industry-standard substitute for the geometric sans-serifs
 * (Spotify's Circular, Apple's SF Pro) music apps use for section headings.
 * Bundled as regular + semibold + bold statics.
 */
val InterFontFamily = FontFamily(
    Font(com.resona.music.core.ui.R.font.inter_regular, FontWeight.Normal),
    Font(com.resona.music.core.ui.R.font.inter_semibold, FontWeight.SemiBold),
    Font(com.resona.music.core.ui.R.font.inter_bold, FontWeight.Bold),
)

/** Kept for legacy screens that reference it by name; the live app uses the
 *  typography scale below. */
val MontserratFontFamily = FontFamily(
    Font(com.resona.music.core.ui.R.font.montserrat_regular, FontWeight.Normal),
    Font(com.resona.music.core.ui.R.font.montserrat_bold, FontWeight.Bold),
)

/**
 * The three voices in this app, and where each belongs:
 * - **Josefin Sans** (`displayX`/`headlineX` below) -- page-level titles: a
 *   screen's own name ("Your Library", an artist/playlist name), the song
 *   title on Now Playing. The brand voice; used sparingly, once per screen.
 * - **Inter** ([SectionHeaderTextStyle]) -- section headers *within* a
 *   screen that has several ("Recommended For You", "Trending Now"). Same
 *   weight class as a headline but a plainer face, so it doesn't compete
 *   with the page's own Josefin title above it.
 * - **Montserrat** (`titleX`/`bodyX`/`labelX` below) -- everything else:
 *   list rows, buttons, captions, anything read at length.
 *
 * Reach for one of these three named styles instead of specifying
 * `fontFamily` inline -- an inline override is how the app ends up with a
 * fourth, undocumented voice by accident.
 */
val SectionHeaderTextStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp
)

// Montserrat is bundled as a variable font spanning the whole weight range
// (minSdk 26 maps a requested weight onto the variable axis), so a single
// file backs every weight the scale needs.
private val Montserrat = FontFamily(
    Font(com.resona.music.core.ui.R.font.montserrat_regular, FontWeight.Normal),
    Font(com.resona.music.core.ui.R.font.montserrat_regular, FontWeight.Medium),
    Font(com.resona.music.core.ui.R.font.montserrat_regular, FontWeight.SemiBold),
    Font(com.resona.music.core.ui.R.font.montserrat_regular, FontWeight.Bold),
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = JosefinSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = JosefinSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
        fontFamily = JosefinSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = JosefinSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = JosefinSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = JosefinSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)