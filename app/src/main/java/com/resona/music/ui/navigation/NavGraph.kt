package com.resona.music.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
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
import com.resona.music.ui.player.rememberAlbumArtPalette
import com.resona.music.ui.search.SearchPage
import com.resona.music.ui.settings.SettingsScreen

private val navAnimSpec = tween<Float>(450)

private val bottomNavEnter = slideInHorizontally { it } + fadeIn(animationSpec = navAnimSpec)
private val bottomNavExit = slideOutHorizontally { -it } + fadeOut(animationSpec = navAnimSpec)

private val slideEnter = slideInHorizontally { it } + fadeIn(animationSpec = navAnimSpec)
private val slideExit = slideOutHorizontally { -it } + fadeOut(animationSpec = navAnimSpec)
private val slidePopEnter = slideInHorizontally { -it } + fadeIn(animationSpec = navAnimSpec)
private val slidePopExit = slideOutHorizontally { it } + fadeOut(animationSpec = navAnimSpec)

// For the bottom chrome (mini-player + nav pill) showing/hiding -- shorter
// and vertical, since this is UI chrome sliding out of the way rather than a
// screen navigating past it. Without this the whole bottom bar was popping
// in/out on a bare `if`, a hard cut against the screen content sliding
// smoothly behind it.
private val chromeAnimSpec = tween<Float>(300)
private val chromeEnter = fadeIn(animationSpec = chromeAnimSpec) +
    slideInVertically(animationSpec = tween(300)) { it / 2 }
private val chromeExit = fadeOut(animationSpec = chromeAnimSpec) +
    slideOutVertically(animationSpec = tween(300)) { it / 2 }

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

    // The app's one departure from strict monochrome: ColorScheme.tertiary/
    // onTertiary (the selected bottom-nav pill, the top bar's voice-search
    // button, the Search screen's accents) is overridden here, once, with
    // whatever's currently playing's album-art color -- the same extraction
    // the mini-player/Now Playing screens already use (see AlbumArtPalette.kt
    // in :feature:player). Only while isAdaptive is true, though (a real
    // color was actually extracted) -- its own neutral() fallback pairs
    // white with onSurface/surfaceContainer (a near-black Gray900, not true
    // black), which read as low-contrast/washed-out for the pill's bold
    // label text. Skipping the override entirely when nothing's playing (or
    // extraction hasn't finished yet) instead falls through to tertiary/
    // onTertiary's own Color.kt values, which are exactly primary/onPrimary
    // -- true black-on-white, guaranteed legible. Every plain
    // `MaterialTheme.colorScheme.tertiary` read below this point -- in this
    // file, in :core:ui, in the Search screen -- picks either up automatically.
    val nowPlayingPalette = rememberAlbumArtPalette(playerUiState.currentTrack?.highResThumbnailUrl)
    val dynamicColorScheme = if (nowPlayingPalette.isAdaptive) {
        MaterialTheme.colorScheme.copy(
            tertiary = nowPlayingPalette.accent,
            onTertiary = nowPlayingPalette.onAccent,
        )
    } else {
        MaterialTheme.colorScheme
    }

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        // A plain Box, not Scaffold -- the bottom chrome (mini-player + pill
        // nav) is meant to float over the feed, not reserve fixed space for
        // itself that every screen's content has to stop short of. Each
        // screen scrolls full-bleed underneath it and pads its own last item
        // clear of it instead.
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ResonaDestination.Home.route,
                // Top only -- Scaffold used to reserve this via innerPadding
                // (every screen's content implicitly assumed it), but the
                // bottom is deliberately left unpadded here so content can
                // scroll full-bleed under the floating chrome below.
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
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
                    onSettingsClick = {
                        navController.navigate(ResonaDestination.Settings.route)
                    },
                    onDownloadsClick = {
                        navController.navigateToTopLevel(ResonaDestination.Library.route)
                    },
                )
            }
            composable(
                ResonaDestination.Stats.route,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
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
                enterTransition = { bottomNavEnter },
                exitTransition = { bottomNavExit },
                popEnterTransition = { bottomNavEnter },
                popExitTransition = { bottomNavExit },
            ) { backStackEntry ->
                SearchPage(
                    initialQuery = backStackEntry.arguments?.getString("query").orEmpty(),
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
                    playlists = playerViewModel.playlists.collectAsStateWithLifecycle().value,
                    onAddToPlaylist = playerViewModel::addCurrentTrackToPlaylist,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                ResonaDestination.History.route,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
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
                    onUserPlaylistClick = { playlist ->
                        navController.navigate(playlistDetailRoute(playlist.id, playlist.name))
                    },
                    onSearchQuery = { query ->
                        navController.navigate(searchRoute(query))
                    },
                    onSettingsClick = {
                        navController.navigate(ResonaDestination.Settings.route)
                    },
                    onStatsClick = {
                        navController.navigate(ResonaDestination.Stats.route)
                    },
                    onHistoryClick = {
                        navController.navigate(ResonaDestination.History.route)
                    },
                )
            }
            composable(
                ResonaDestination.Settings.route,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                SettingsScreen(onBack = { navController.popBackStack() })
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

            // The floating bottom chrome: mini-player above the pill nav,
            // both bottom-aligned over the NavHost content rather than
            // pushing it up. imePadding() here (not on the Box above) keeps
            // just this chrome clear of the soft keyboard -- the mini-player
            // and its play/pause button would otherwise end up fully covered
            // (and untappable) any time the keyboard is open, e.g. right
            // after tapping a search result without dismissing it first.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            ) {
                // Redundant next to the full player, so it's hidden on that
                // screen specifically rather than gated on currentTrack alone.
                AnimatedVisibility(
                    visible = currentRoute != ResonaDestination.NowPlaying.route,
                    enter = chromeEnter,
                    exit = chromeExit
                ) {
                    Column {
                        // Read through lastTrack, not currentTrack directly, so
                        // the exit animation below has a track to render while
                        // it slides away instead of the content vanishing out
                        // from under it the instant currentTrack goes null.
                        var lastTrack by remember { mutableStateOf<Song?>(null) }
                        playerUiState.currentTrack?.let { lastTrack = it }

                        AnimatedVisibility(
                            visible = playerUiState.currentTrack != null,
                            enter = chromeEnter,
                            exit = chromeExit
                        ) {
                            lastTrack?.let { track ->
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
                        }
                        ResonaBottomBar(navController, currentRoute)
                    }
                }
            }
        }
    }
}

/** Spring shared by every size/position morph in the pill nav -- snappy and
 *  non-bouncy, since a visible bounce on a tab you tap constantly reads as
 *  sluggish rather than lively. Generic since it drives a Dp, an IntSize
 *  (expand/shrink, content size), and a Float (fade) at different call sites. */
private fun <T> navMorphSpec(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val navColorSpec = tween<Color>(200)

@Composable
private fun ResonaBottomBar(navController: NavHostController, currentRoute: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.7f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search's registered route carries an optional "?query=..."
                // suffix (see the Search composable() below) -- currentRoute
                // reflects that full pattern while on the screen, so a bare
                // equality check against destination.route would never match
                // and the pill would never show selected.
                val currentBaseRoute = currentRoute?.substringBefore("?")
                bottomNavDestinations.forEach { destination ->
                    val selected = currentBaseRoute == destination.route
                    NavPillItem(
                        label = destination.label,
                        icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        selected = selected,
                        onClick = { navController.navigateToTopLevel(destination.route) }
                    )
                }
            }
        }
    }
}

/**
 * One tab of the floating pill nav: icon-only circle when unselected, morphs
 * into a wider pill with the label alongside the icon when selected. Colors
 * stay within the app's monochrome white-on-black-glass palette -- same
 * tones the old flat NavigationBar used, just applied per-pill instead of as
 * one static indicator.
 */
@Composable
private fun NavPillItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 20.dp else 12.dp,
        animationSpec = navMorphSpec(),
        label = "navPillPadding"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.tertiary else Color.Transparent,
        animationSpec = navColorSpec,
        label = "navPillContainer"
    )
    // Computed locally rather than trusting onTertiary -- that's tuned for
    // AlbumArtPalette's broader use (mini-player backdrops, seek bars) with
    // a middling luminance cutoff for choosing black vs. white text. This
    // pill is small, bold, and meant to be read at a glance, so it leans
    // harder toward black -- only a genuinely dark extracted fill falls
    // back to white -- rather than splitting evenly.
    val contentColor by animateColorAsState(
        targetValue = when {
            !selected -> Color.White.copy(alpha = 0.4f)
            MaterialTheme.colorScheme.tertiary.luminance() > 0.3f -> Color.Black
            else -> Color.White
        },
        animationSpec = navColorSpec,
        label = "navPillContent"
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .animateContentSize(animationSpec = navMorphSpec()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(24.dp))
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(navMorphSpec()) + expandHorizontally(navMorphSpec()),
                exit = fadeOut(navMorphSpec()) + shrinkHorizontally(navMorphSpec())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
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
