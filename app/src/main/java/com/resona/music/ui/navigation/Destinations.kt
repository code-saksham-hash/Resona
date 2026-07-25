package com.resona.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector

sealed class ResonaDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Onboarding : ResonaDestination("onboarding", "Onboarding", Icons.Outlined.Home)
    data object Home : ResonaDestination("home", "Home", Icons.Outlined.Home)
    data object Explore : ResonaDestination("explore", "Explore", Icons.Outlined.Explore) {
        fun createRoute(query: String = ""): String =
            if (query.isBlank()) "explore" else "explore/${java.net.URLEncoder.encode(query, "UTF-8")}"
    }
    data object Downloads : ResonaDestination("downloads", "Downloads", Icons.Outlined.Download)
    data object Library : ResonaDestination("library", "Library", Icons.Outlined.LibraryMusic)
    data object NowPlaying : ResonaDestination("now_playing", "Now Playing", Icons.Outlined.PlayArrow)
    data object MyAccount : ResonaDestination("my_account", "My Account", Icons.Outlined.Home)
}

val bottomNavDestinations = listOf(
    ResonaDestination.Home,
    ResonaDestination.Explore,
    ResonaDestination.Downloads,
    ResonaDestination.Library
)
