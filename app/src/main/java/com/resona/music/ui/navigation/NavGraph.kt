package com.resona.music.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.resona.music.playback.PlayerViewModel
import com.resona.music.ui.account.MyAccountScreen
import com.resona.music.ui.home.HomeScreen
import com.resona.music.ui.library.LibraryScreen
import com.resona.music.ui.nowplaying.NowPlayingScreen
import com.resona.music.ui.onboarding.OnboardingScreen
import com.resona.music.ui.player.MiniPlayerBar
import com.resona.music.ui.search.SearchScreen
import com.resona.music.ui.theme.NocturneSurfaceContainerLowest

@Composable
fun ResonaNavGraph() {
    val navController = rememberNavController()

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomNavDestinations.map { it.route }
    val showPlayer = currentRoute !in listOf(
        ResonaDestination.NowPlaying.route,
        ResonaDestination.Onboarding.route,
        ResonaDestination.MyAccount.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column(modifier = Modifier.imePadding()) {
                    if (showPlayer) {
                        playerUiState.currentTrack?.let { track ->
                            MiniPlayerBar(
                                track = track,
                                isPlaying = playerUiState.isPlaying,
                                onTogglePlayPause = playerViewModel::togglePlayPause,
                                onClick = {
                                    navController.navigateToTopLevel(ResonaDestination.NowPlaying.route)
                                },
                                error = playerUiState.error
                            )
                        }
                    }
                    ResonaBottomBar(navController, currentRoute, showPlayer && playerUiState.currentTrack != null)
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ResonaDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ResonaDestination.Onboarding.route) {
                OnboardingScreen(
                    onGetStarted = {
                        navController.navigate(ResonaDestination.Home.route) {
                            popUpTo(ResonaDestination.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(ResonaDestination.Home.route) {
                HomeScreen(
                    onSearchClick = { navController.navigateToTopLevel(ResonaDestination.Explore.route) },
                    onExploreClick = { navController.navigateToTopLevel(ResonaDestination.Explore.route) },
                    onProfileClick = { navController.navigate(ResonaDestination.MyAccount.route) },
                    onAlbumClick = { album ->
                        playerViewModel.play(
                            com.resona.music.domain.model.Song(
                                videoId = album.videoId,
                                title = album.title,
                                artist = album.subtitle,
                                thumbnailUrl = album.imageUrl
                            )
                        )
                    },
                    onArtistClick = { artist ->
                        playerViewModel.play(
                            com.resona.music.domain.model.Song(
                                videoId = artist.videoId,
                                title = artist.name,
                                artist = artist.name,
                                thumbnailUrl = artist.imageUrl
                            )
                        )
                    },
                    onTrackClick = { track ->
                        playerViewModel.play(
                            com.resona.music.domain.model.Song(
                                videoId = track.id,
                                title = track.title,
                                artist = track.artist,
                                thumbnailUrl = track.imageUrl
                            )
                        )
                    }
                )
            }
            composable(ResonaDestination.Explore.route) {
                SearchScreen(
                    onSongClick = playerViewModel::play,
                    onGenreClick = { },
                    onProfileClick = { navController.navigate(ResonaDestination.MyAccount.route) }
                )
            }
            composable(ResonaDestination.Downloads.route) {
                LibraryScreen(
                    onTrackClick = { track ->
                        playerViewModel.play(
                            com.resona.music.domain.model.Song(
                                videoId = track.id,
                                title = track.title,
                                artist = track.artist,
                                thumbnailUrl = track.imageUrl
                            )
                        )
                    },
                    onProfileClick = { navController.navigate(ResonaDestination.MyAccount.route) }
                )
            }
            composable(ResonaDestination.Library.route) {
                LibraryScreen(
                    onTrackClick = { track ->
                        playerViewModel.play(
                            com.resona.music.domain.model.Song(
                                videoId = track.id,
                                title = track.title,
                                artist = track.artist,
                                thumbnailUrl = track.imageUrl
                            )
                        )
                    },
                    onProfileClick = { navController.navigate(ResonaDestination.MyAccount.route) }
                )
            }
            composable(ResonaDestination.NowPlaying.route) {
                NowPlayingScreen(
                    uiState = playerUiState,
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onSeek = playerViewModel::seekTo,
                    onSkipNext = playerViewModel::skipToNext,
                    onSkipPrevious = playerViewModel::skipToPrevious,
                    onQueueClick = {},
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ResonaDestination.MyAccount.route) {
                MyAccountScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun ResonaBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    hasMiniPlayer: Boolean
) {
    NavigationBar(
        containerColor = NocturneSurfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        bottomNavDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateToTopLevel(destination.route) },
                icon = {
                    Icon(
                        destination.icon,
                        contentDescription = destination.label,
                        tint = if (currentRoute == destination.route)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                },
                label = {
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (currentRoute == destination.route)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    }
}

private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
