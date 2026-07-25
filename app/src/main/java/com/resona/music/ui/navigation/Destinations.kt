package com.resona.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level destinations reachable from the bottom navigation bar. */
sealed class ResonaDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : ResonaDestination("home", "Home", Icons.Outlined.Home)
    data object Search : ResonaDestination("search", "Search", Icons.Outlined.Search)
    data object NowPlaying : ResonaDestination("now_playing", "Now Playing", Icons.Outlined.PlayArrow)
    data object Library : ResonaDestination("library", "Library", Icons.AutoMirrored.Outlined.List)
}

val bottomNavDestinations = listOf(
    ResonaDestination.Home,
    ResonaDestination.Search,
    ResonaDestination.NowPlaying,
    ResonaDestination.Library
)
