@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.resona.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Montserrat: Google's variable font (OFL-licensed, github.com/google/fonts;
// same file the Google Fonts CDN itself serves), backs the display voice
// below. Bundled locally here rather than pulled through Google's Font
// Provider at runtime, so this doesn't add a new Play-Services dependency.
//
// Built the same way robotoFlex() below is, through an explicit
// FontVariation.Settings weight rather than registering one FontFamily with
// several Font(resId, FontWeight) entries at the same resId: this file's
// *unvaried* default happens to resolve to its lightest named instance
// ("Montserrat Thin"), and letting Android's plain weight-matching pick an
// instance off that axis silently produced Thin text at every requested
// weight, SemiBold/Bold included -- pinning the axis explicitly is what
// actually forces real weight.
private fun montserrat(weight: Int) = FontFamily(
    Font(
        resId = com.resona.music.core.ui.R.font.montserrat_regular,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight))
    )
)

// SemiBold, deliberately not the heavier 700 used elsewhere in this file.
// Bold at this size, stretched 50% wider (textGeometricTransform below) and
// tracked this tight, turns visibly chunky/distorted; SemiBold is what
// actually holds together at the stretch.
private val MontserratDisplay = montserrat(weight = 600)

// Roboto Flex: Google's variable font (OFL-licensed, github.com/google/fonts),
// bundled locally for the same reason Montserrat is above. Backs every tier
// below the display voice, FontVariation.width()/weight() driving each
// style's exact look off that one file.
private fun robotoFlex(width: Float, weight: Int) = FontFamily(
    Font(
        resId = com.resona.music.core.ui.R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.width(width),
            FontVariation.weight(weight),
        )
    )
)

// Leaned heavier across the board (weight numbers pushed up from a plainer
// 400/500/600 scale) -- thin type reads as unfinished/low-confidence at the
// sizes this app actually uses these at, both on page titles and on
// song/artist rows.
private val RobotoFlexBody = robotoFlex(width = 100f, weight = 500)
private val RobotoFlexLabel = robotoFlex(width = 100f, weight = 600)
private val RobotoFlexTitleWide = robotoFlex(width = 110f, weight = 700)
private val RobotoFlexBold = robotoFlex(width = 100f, weight = 800)

/**
 * A Material 3 Expressive type system: Montserrat for the big, wide,
 * tightly-tracked display voice; Roboto Flex for everything else, its
 * width/weight axes driving each tier's exact look off one file, bundled
 * locally rather than through Google's Font Provider (a Play-Services
 * runtime dependency this app deliberately has nowhere else). Every tier
 * pushed bolder than the usual Material defaults: thin type at these sizes
 * reads as unfinished, not clean.
 *
 * [SectionHeaderTextStyle] used to be a separate Inter-based style; it's
 * just [Typography.headlineSmall] now, at 24sp/32sp.
 */
private fun displayVoice(fontSize: TextUnit) = TextStyle(
    fontFamily = MontserratDisplay,
    fontWeight = FontWeight.SemiBold,
    fontSize = fontSize,
    lineHeight = 0.8.em,
    letterSpacing = (-0.05).em,
    textGeometricTransform = TextGeometricTransform(scaleX = 1.5f),
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

private fun headlineVoice(fontSize: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontFamily = RobotoFlexTitleWide,
    fontWeight = FontWeight.Bold,
    fontSize = fontSize,
    lineHeight = lineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

val Typography = Typography(
    displayLarge = displayVoice(57.sp),
    displayMedium = displayVoice(45.sp),
    displaySmall = displayVoice(36.sp),
    headlineLarge = headlineVoice(32.sp, 38.sp),
    headlineMedium = headlineVoice(28.sp, 34.sp),
    headlineSmall = headlineVoice(24.sp, 32.sp),
    titleLarge = TextStyle(
        fontFamily = RobotoFlexBold,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoFlexTitleWide,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleSmall = TextStyle(
        fontFamily = RobotoFlexTitleWide,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = RobotoFlexBody,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = RobotoFlexBody,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp
    ),
    bodySmall = TextStyle(
        fontFamily = RobotoFlexBody,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = RobotoFlexLabel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFlexLabel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoFlexLabel,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/** @see Typography's kdoc -- this is now just [Typography.headlineSmall]; kept as a
 *  named alias so existing call sites don't all need touching. */
val SectionHeaderTextStyle = Typography.headlineSmall
