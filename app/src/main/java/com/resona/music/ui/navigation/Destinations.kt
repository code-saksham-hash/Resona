package com.resona.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations reachable from the bottom navigation bar. Each has
 * its own distinct filled/outlined icon pair -- [selectedIcon] for the
 * active tab, [unselectedIcon] otherwise -- instead of one static icon
 * regardless of state (and instead of Explore quietly reusing Search's
 * icon, which is genuinely confusable with the Search tab itself).
 */
sealed class ResonaDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : ResonaDestination("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Explore : ResonaDestination("explore", "Explore", Icons.Filled.Explore, Icons.Outlined.Explore)
    data object Search : ResonaDestination("search", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    data object NowPlaying : ResonaDestination(
        "now_playing", "Now Playing", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircleOutline
    )
    data object Library : ResonaDestination("library", "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)

    /** Reached from a Library playlist card, not the bottom bar -- its icon is unused. */
    data object PlaylistDetail : ResonaDestination(
        "playlist", "Playlist", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic
    )

    /** Reached from a Home artist card, not the bottom bar -- its icon is unused. */
    data object ArtistDetail : ResonaDestination("artist", "Artist", Icons.Filled.Explore, Icons.Outlined.Explore)
}

val bottomNavDestinations = listOf(
    ResonaDestination.Home,
    ResonaDestination.Explore,
    ResonaDestination.NowPlaying,
    ResonaDestination.Library
)
