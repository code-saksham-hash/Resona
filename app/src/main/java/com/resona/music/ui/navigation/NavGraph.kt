package com.resona.music.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.resona.music.domain.model.Song
import com.resona.music.playback.PlayerViewModel
import com.resona.music.ui.home.HomeScreen
import com.resona.music.ui.home.HomeTrack
import com.resona.music.ui.home.HomeAlbum
import com.resona.music.ui.home.HomeArtist
import com.resona.music.ui.home.ArtistDetailScreen
import com.resona.music.ui.home.ArtistDetailViewModel
import com.resona.music.ui.home.HistoryScreen
import com.resona.music.ui.home.StatsScreen
import com.resona.music.ui.library.LibraryScreen
import com.resona.music.ui.library.PlaylistDetailScreen
import com.resona.music.ui.library.PlaylistDetailViewModel
import com.resona.music.ui.nowplaying.NowPlayingScreen
import com.resona.music.ui.player.MiniPlayerBar
import com.resona.music.ui.search.SearchPage
import com.resona.music.ui.search.SearchScreen

private val navAnimSpec = tween<Float>(450)

private val bottomNavEnter = slideInHorizontally { it } + fadeIn(animationSpec = navAnimSpec)
private val bottomNavExit = slideOutHorizontally { -it } + fadeOut(animationSpec = navAnimSpec)

private val slideEnter = slideInHorizontally { it } + fadeIn(animationSpec = navAnimSpec)
private val slideExit = slideOutHorizontally { -it } + fadeOut(animationSpec = navAnimSpec)
private val slidePopEnter = slideInHorizontally { -it } + fadeIn(animationSpec = navAnimSpec)
private val slidePopExit = slideOutHorizontally { it } + fadeOut(animationSpec = navAnimSpec)

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
                            modifier = Modifier.offset(y = (-20).dp),
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onSkipToPrevious = playerViewModel::skipToPrevious,
                            onSkipToNext = playerViewModel::skipToNext,
                            onClick = { navController.navigateToTopLevel(ResonaDestination.NowPlaying.route) }
                        )
                    }
                    ResonaBottomBar(navController, currentRoute)
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ResonaDestination.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                ResonaDestination.Home.route,
                enterTransition = { bottomNavEnter },
                exitTransition = { bottomNavExit },
                popEnterTransition = { bottomNavEnter },
                popExitTransition = { bottomNavExit },
            ) {
                HomeScreen(
                    onTrackClick = { track ->
                        playerViewModel.play(
                            Song(
                                videoId = track.id,
                                title = track.title,
                                artist = track.artist,
                                thumbnailUrl = track.imageUrl,
                                duration = track.duration
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
                        navController.navigate(artistDetailRoute(artist.name))
                    },
                    onSearchClick = {
                        navController.navigate(searchRoute(""))
                    },
                    onSearchQuery = { query ->
                        navController.navigate(searchRoute(query))
                    },
                )
            }
            composable(
                ResonaDestination.Stats.route,
                enterTransition = { bottomNavEnter },
                exitTransition = { bottomNavExit },
                popEnterTransition = { bottomNavEnter },
                popExitTransition = { bottomNavExit },
            ) {
                StatsScreen(
                    onArtistClick = { artist ->
                        navController.navigate(artistDetailRoute(artist))
                    }
                )
            }
            composable(
                route = "${ResonaDestination.Search.route}?query={query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) { backStackEntry ->
                SearchPage(
                    initialQuery = backStackEntry.arguments?.getString("query").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onSongClick = playerViewModel::play,
                )
            }
            composable(
                ResonaDestination.NowPlaying.route,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
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
                    onSongClick = { song -> playerViewModel.play(song, playerUiState.queue) },
                    onDownloadClick = playerViewModel::download,
                    onToggleLike = playerViewModel::toggleLike,
                    onLoadLyrics = playerViewModel::loadLyrics,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                ResonaDestination.History.route,
                enterTransition = { bottomNavEnter },
                exitTransition = { bottomNavExit },
                popEnterTransition = { bottomNavEnter },
                popExitTransition = { bottomNavExit },
            ) {
                HistoryScreen(onSongClick = playerViewModel::play)
            }
            composable(
                ResonaDestination.Library.route,
                enterTransition = { bottomNavEnter },
                exitTransition = { bottomNavExit },
                popEnterTransition = { bottomNavEnter },
                popExitTransition = { bottomNavExit },
            ) {
                LibraryScreen(
                    onDownloadedSongClick = playerViewModel::play,
                    onLikedSongClick = playerViewModel::play,
                    onSearchClick = {
                        navController.navigate(ResonaDestination.Search.route)
                    },
                )
            }
            composable(
                route = "${ResonaDestination.PlaylistDetail.route}/{${PlaylistDetailViewModel.ARG_BROWSE_ID}}" +
                    "?${PlaylistDetailViewModel.ARG_TITLE}={${PlaylistDetailViewModel.ARG_TITLE}}",
                arguments = listOf(
                    navArgument(PlaylistDetailViewModel.ARG_BROWSE_ID) { type = NavType.StringType },
                    navArgument(PlaylistDetailViewModel.ARG_TITLE) { type = NavType.StringType; defaultValue = "" }
                ),
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                PlaylistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onSongClick = { song, songs -> playerViewModel.play(song, songs) },
                )
            }
            composable(
                route = "${ResonaDestination.ArtistDetail.route}/{${ArtistDetailViewModel.ARG_NAME}}",
                arguments = listOf(navArgument(ArtistDetailViewModel.ARG_NAME) { type = NavType.StringType }),
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                ArtistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onSongClick = { song, songs -> playerViewModel.play(song, songs) },
                )
            }
        }
    }
}

@Composable
private fun ResonaBottomBar(navController: NavHostController, currentRoute: String?) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.1f))
                .align(Alignment.TopCenter)
        )
        NavigationBar(
            containerColor = Color.Black.copy(alpha = 0.7f),
            tonalElevation = 0.dp
        ) {
            bottomNavDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { navController.navigateToTopLevel(destination.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.White.copy(alpha = 0.12f),
                        selectedIconColor = Color.White,
                        unselectedIconColor = Color.White.copy(alpha = 0.4f),
                        selectedTextColor = Color.White,
                        unselectedTextColor = Color.White.copy(alpha = 0.4f)
                    )
                )
            }
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

/** [ResonaDestination.Search]'s route, optionally pre-filled (e.g. tapping a genre chip on Home). */
private fun searchRoute(query: String): String =
    if (query.isBlank()) ResonaDestination.Search.route
    else "${ResonaDestination.Search.route}?query=${Uri.encode(query)}"

/** [ResonaDestination.PlaylistDetail]'s route for one specific playlist. */
private fun playlistDetailRoute(browseId: String, title: String): String =
    "${ResonaDestination.PlaylistDetail.route}/${Uri.encode(browseId)}?title=${Uri.encode(title)}"

/** [ResonaDestination.ArtistDetail]'s route for one specific artist. */
private fun artistDetailRoute(name: String): String =
    "${ResonaDestination.ArtistDetail.route}/${Uri.encode(name)}"
