package com.resona.music.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.resona.music.domain.model.Song
import com.resona.music.playback.PlayerViewModel
import com.resona.music.ui.home.HomeAlbum
import com.resona.music.ui.home.HomeArtist
import com.resona.music.ui.home.HomeScreen
import com.resona.music.ui.home.HomeTrack
import com.resona.music.ui.library.LibraryScreen
import com.resona.music.ui.nowplaying.NowPlayingScreen
import com.resona.music.ui.player.MiniPlayerBar
import com.resona.music.ui.search.ExploreScreen
import com.resona.music.ui.search.SearchPage
import com.resona.music.ui.search.SearchScreen

@Composable
fun ResonaNavGraph() {
    val navController = rememberNavController()

    // Obtained here, above the NavHost, so it resolves against the Activity
    // (not a per-destination back stack entry) and survives navigation --
    // every screen shares the same player instead of each owning its own.
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Without this, the bar stays pinned to the physical bottom of
            // the screen while the soft keyboard draws on top of it -- the
            // mini-player and its play/pause button end up fully covered
            // (and untappable) any time the keyboard is open, e.g. right
            // after tapping a search result without dismissing it first.
            Column(modifier = Modifier.imePadding()) {
                // Redundant next to the full player, so it's hidden on that
                // screen specifically rather than gated on currentTrack alone.
                if (currentRoute != ResonaDestination.NowPlaying.route) {
                    playerUiState.currentTrack?.let { track ->
                        MiniPlayerBar(
                            track = track,
                            isPlaying = playerUiState.isPlaying,
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onClick = { navController.navigateToTopLevel(ResonaDestination.NowPlaying.route) }
                        )
                    }
                }
                ResonaBottomBar(navController, currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ResonaDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ResonaDestination.Home.route) {
                HomeScreen(
                    onTrackClick = { track ->
                        playerViewModel.play(
                            Song(
                                videoId = track.id,
                                title = track.title,
                                artist = track.artist,
                                thumbnailUrl = track.imageUrl
                            )
                        )
                    },
                    onAlbumClick = { album ->
                        if (album.videoId.isNotBlank()) {
                            playerViewModel.play(
                                Song(
                                    videoId = album.videoId,
                                    title = album.title,
                                    artist = album.subtitle,
                                    thumbnailUrl = album.imageUrl
                                )
                            )
                        }
                    },
                    onArtistClick = { artist ->
                        if (artist.videoId.isNotBlank()) {
                            playerViewModel.play(
                                Song(
                                    videoId = artist.videoId,
                                    title = artist.name,
                                    artist = artist.name,
                                    thumbnailUrl = artist.imageUrl
                                )
                            )
                        }
                    },
                    onExploreClick = {
                        navController.navigateToTopLevel(ResonaDestination.Explore.route)
                    },
                    onSearchQuery = {
                        navController.navigate(ResonaDestination.Search.route)
                    },
                )
            }
            composable(ResonaDestination.Explore.route) {
                ExploreScreen(
                    onSearchClick = {
                        navController.navigate(ResonaDestination.Search.route)
                    },
                    onProfileClick = {
                        // TODO: navigate to profile
                    }
                )
            }
            composable(ResonaDestination.Search.route) {
                SearchPage(
                    onBack = { navController.popBackStack() },
                    onSongClick = playerViewModel::play,
                )
            }
            composable(ResonaDestination.NowPlaying.route) {
                NowPlayingScreen(
                    uiState = playerUiState,
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onSeek = playerViewModel::seekTo,
                    onSkipNext = playerViewModel::skipToNext,
                    onSkipPrevious = playerViewModel::skipToPrevious,
                    // Collapses back to whatever tab sits under it in the
                    // top-level back stack, with the mini-player reappearing
                    // once this route is no longer current -- same idiom the
                    // bottom bar itself uses to switch tabs.
                    onQueueClick = {},
                    onDownloadClick = {
                        // TODO: wire real download logic here
                    },
                    onToggleLike = {
                        // TODO: wire real like/unlike logic here
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ResonaDestination.Library.route) { LibraryScreen() }
        }
    }
}

@Composable
private fun ResonaBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        bottomNavDestinations.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateToTopLevel(destination.route) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) }
            )
        }
    }
}

/**
 * Shared by the bottom bar and the mini-player's tap-through to Now Playing
 * -- both land on a top-level destination, so both need the same "avoid
 * piling up copies, keep tab state when switching back and forth" options.
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
