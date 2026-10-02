package com.resona.music.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
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
import com.resona.music.ui.home.AlbumDetailScreen
import com.resona.music.ui.home.AlbumDetailViewModel
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
import com.resona.music.ui.podcasts.PodcastSearchScreen
import com.resona.music.ui.podcasts.PodcastShowScreen
import com.resona.music.ui.podcasts.PodcastShowViewModel
import com.resona.music.ui.podcasts.PodcastsScreen
import com.resona.music.ui.search.SearchPage
import com.resona.music.ui.settings.SettingsScreen

private val navAnimSpec = tween<Float>(450)

// Bottom-tab switches move sideways between siblings, not deeper into a
// stack, so they get their own quick cross-fade instead of borrowing the
// directional slide below: a full-width slide of two heavy screens for
// 450ms is what read as sluggish under repeated taps ("cycling" between
// tabs), and a slide also implies a forward/back relationship that doesn't
// exist between Home/Search/Library in the first place.
//
// 250ms, not shorter -- an early pass tried 200ms here (and on the pill's
// own morph below) and it read as a hard cut rather than a fade: a spatial/
// shape change needs enough frames for the eye to track continuous motion,
// where a pure color or alpha fade can get away with less.
private val bottomTabAnimSpec = tween<Float>(250)
private val bottomNavEnter = fadeIn(animationSpec = bottomTabAnimSpec)
private val bottomNavExit = fadeOut(animationSpec = bottomTabAnimSpec)

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

    // The bottom tab the current screen belongs to. Sub-screens (Podcasts,
    // an artist, a playlist) aren't tabs themselves, so this keeps the last
    // tab root that was shown rather than reading the current route.
    val activeTab = rememberSaveable { mutableStateOf(ResonaDestination.Home.route) }
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val base = destination.route?.substringBefore("?")
            if (base != null && bottomNavDestinations.any { it.route == base }) activeTab.value = base
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // Obtained here, above the NavHost, so it resolves against the Activity
    // (not a per-destination back stack entry) and survives navigation --
    // every screen shares the same player instead of each owning its own.
    val playerViewModel: PlayerViewModel = hiltViewModel()
    // Chrome needs only these slices of uiState, which re-emits every
    // second while playing (the position poll). Collecting it whole here
    // would recompose this root at that cadence.
    val currentTrack by playerViewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

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
    //
    // Raw palette, not animated: animating here would rebuild the app-wide
    // ColorScheme every frame of the fade. Playback surfaces fade their own
    // palettes (rememberAnimatedAlbumArtPalette); this snaps once per
    // track change.
    val nowPlayingPalette = rememberAlbumArtPalette(currentTrack?.highResThumbnailUrl)
    // remember keyed on the actual colors, not a plain if/else: copy()
    // returns a new ColorScheme identity even when nothing changes, and
    // MaterialTheme propagates that identity to every theme-reading
    // composable in the app. Keyed remember only rebuilds the scheme when
    // the extracted color genuinely changes.
    val baseColorScheme = MaterialTheme.colorScheme
    val dynamicColorScheme = remember(baseColorScheme, nowPlayingPalette.isAdaptive, nowPlayingPalette.accent, nowPlayingPalette.onAccent) {
        if (nowPlayingPalette.isAdaptive) {
            baseColorScheme.copy(
                tertiary = nowPlayingPalette.accent,
                onTertiary = nowPlayingPalette.onAccent,
            )
        } else {
            baseColorScheme
        }
    }

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        // Remembered as one stable builder lambda: NavHost keys its graph
        // on the builder's identity, so a fresh lambda per recomposition
        // would rebuild every destination and re-run the visible screen.
        // All captured values here are stable.
        val navBuilder: NavGraphBuilder.() -> Unit = remember {
            {
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
                        navController.selectTab(ResonaDestination.Library.route, activeTab)
                    },
                    onPodcastsClick = {
                        navController.navigate(ResonaDestination.Podcasts.route)
                    },
                )
            }
            composable(
                ResonaDestination.Podcasts.route,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                PodcastsScreen(
                    onBack = { navController.popBackStack() },
                    onSearchClick = { navController.navigate(ResonaDestination.PodcastSearch.route) },
                    onShowClick = { show -> navController.navigate(podcastShowRoute(show.browseId)) },
                    onPlayEpisode = { episode -> playerViewModel.play(episode.toSong()) },
                    onResume = playerViewModel::play,
                )
            }
            composable(
                route = "${ResonaDestination.PodcastShow.route}/{${PodcastShowViewModel.ARG_BROWSE_ID}}",
                arguments = listOf(navArgument(PodcastShowViewModel.ARG_BROWSE_ID) { type = NavType.StringType }),
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                PodcastShowScreen(
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = { episode -> playerViewModel.play(episode.toSong()) },
                )
            }
            composable(
                ResonaDestination.PodcastSearch.route,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                PodcastSearchScreen(
                    onBack = { navController.popBackStack() },
                    onShowClick = { show -> navController.navigate(podcastShowRoute(show.browseId)) },
                    onPlayEpisode = { episode -> playerViewModel.play(episode.toSong()) },
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
                route = SEARCH_ROUTE_PATTERN,
                arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
                enterTransition = { bottomNavEnter },
                exitTransition = { bottomNavExit },
                popEnterTransition = { bottomNavEnter },
                popExitTransition = { bottomNavExit },
            ) { backStackEntry ->
                SearchPage(
                    initialQuery = backStackEntry.arguments?.getString("query").orEmpty(),
                    onSongClick = playerViewModel::play,
                    onArtistClick = { artist ->
                        navController.navigate(artistDetailRoute(artist.name, artist.browseId))
                    },
                )
            }
            composable(
                ResonaDestination.NowPlaying.route,
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                // Collects its own state off the shared playerViewModel;
                // uiState re-emits every ~second while playing.
                NowPlayingScreen(
                    playerViewModel = playerViewModel,
                    // Now Playing sits on top of whatever screen opened it, so
                    // this lands right back there, mini-player and all.
                    onBack = { navController.popBackStack() },
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
                route = "${ResonaDestination.ArtistDetail.route}/{${ArtistDetailViewModel.ARG_NAME}}" +
                    "?${ArtistDetailViewModel.ARG_BROWSE_ID}={${ArtistDetailViewModel.ARG_BROWSE_ID}}",
                arguments = listOf(
                    navArgument(ArtistDetailViewModel.ARG_NAME) { type = NavType.StringType },
                    navArgument(ArtistDetailViewModel.ARG_BROWSE_ID) { type = NavType.StringType; defaultValue = "" }
                ),
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                ArtistDetailScreen(
                    onBack = { navController.popBackStack() },
                    onSongClick = { song, songs -> playerViewModel.play(song, songs) },
                    onShufflePlay = { songs ->
                        if (songs.isNotEmpty()) {
                            playerViewModel.play(songs.random(), songs)
                            playerViewModel.setShuffleEnabled(true)
                        }
                    },
                    onAlbumClick = { album -> navController.navigate(albumDetailRoute(album.browseId)) },
                )
            }
            composable(
                route = "${ResonaDestination.AlbumDetail.route}/{${AlbumDetailViewModel.ARG_BROWSE_ID}}",
                arguments = listOf(navArgument(AlbumDetailViewModel.ARG_BROWSE_ID) { type = NavType.StringType }),
                enterTransition = { slideEnter },
                exitTransition = { slideExit },
                popEnterTransition = { slidePopEnter },
                popExitTransition = { slidePopExit },
            ) {
                AlbumDetailScreen(
                    onBack = { navController.popBackStack() },
                    onSongClick = { song, songs -> playerViewModel.play(song, songs) },
                    onArtistClick = { name, browseId -> navController.navigate(artistDetailRoute(name, browseId)) },
                )
            }
            }
        }

        // A plain Box, not Scaffold: the bottom chrome floats over the feed,
        // so screens scroll full-bleed underneath and pad their last item
        // clear of it.
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ResonaDestination.Home.route,
                // Top only -- Scaffold used to reserve this via innerPadding;
                // the bottom stays unpadded so content scrolls under the chrome.
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                builder = navBuilder
            )

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
                        currentTrack?.let { lastTrack = it }

                        AnimatedVisibility(
                            visible = currentTrack != null,
                            enter = chromeEnter,
                            exit = chromeExit
                        ) {
                            lastTrack?.let { track ->
                                MiniPlayerBar(
                                    track = track,
                                    isPlaying = isPlaying,
                                    modifier = Modifier.offset(y = (-20).dp),
                                    onTogglePlayPause = playerViewModel::togglePlayPause,
                                    onSkipToPrevious = playerViewModel::skipToPrevious,
                                    onSkipToNext = playerViewModel::skipToNext,
                                    // Pushed, not swapped in as a tab, so Back returns to the screen you were on.
                                    onClick = { navController.navigate(ResonaDestination.NowPlaying.route) { launchSingleTop = true } },
                                    onDismiss = playerViewModel::stop,
                                    onSeekBack = { playerViewModel.seekBy(-10_000L) },
                                    onSeekForward = { playerViewModel.seekBy(30_000L) }
                                )
                            }
                        }
                        ResonaBottomBar(
                            activeTab = activeTab.value,
                            onTabClick = { route -> navController.selectTab(route, activeTab) }
                        )
                    }
                }
            }
        }
    }
}

/** Shared by every size/position morph in the pill nav, and pinned to the
 *  same 250ms as [navColorSpec] (and the bottom-tab screen fade above) so a
 *  tap resolves as one motion instead of several pieces -- padding, color,
 *  label reveal, screen content -- each visibly settling at a different
 *  moment. This used to be a spring, which (being physics-driven rather
 *  than fixed-duration) settled later than the color tween on the same
 *  pill and never lined up with the screen transition at all. Generic since
 *  it drives a Dp, an IntSize (expand/shrink), and a Float (fade) at
 *  different call sites. */
private fun <T> navMorphSpec(): androidx.compose.animation.core.FiniteAnimationSpec<T> = tween(durationMillis = 250)
private val navColorSpec = tween<Color>(250)

@Composable
private fun ResonaBottomBar(activeTab: String, onTabClick: (route: String) -> Unit) {
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
                bottomNavDestinations.forEach { destination ->
                    val selected = activeTab == destination.route
                    NavPillItem(
                        label = destination.label,
                        icon = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        selected = selected,
                        onClick = { onTabClick(destination.route) }
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
            // No animateContentSize here -- the animated padding above and
            // the label's own expandHorizontally/shrinkHorizontally below
            // already animate this Row's width between them. Adding a third
            // animator on top of two that already cover it doesn't smooth
            // anything further; it layers a second easing curve onto a
            // width that's already mid-animation, which reads as a wobble
            // rather than one clean motion.
            modifier = Modifier.padding(horizontal = horizontalPadding),
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
 * A bottom bar tap (or Home's downloads shortcut into Library). Tapping the
 * tab you're already in goes back to its root, the way most apps behave;
 * without this it just restored the same sub-screen and seemed to do nothing.
 */
private fun NavHostController.selectTab(route: String, activeTab: MutableState<String>) {
    if (route == activeTab.value) {
        val root = if (route == ResonaDestination.Search.route) SEARCH_ROUTE_PATTERN else route
        if (runCatching { getBackStackEntry(root) }.isSuccess) {
            popBackStack(root, inclusive = false)
            return
        }
    }
    activeTab.value = route
    navigateToTopLevel(route)
}

/** Switches tabs while keeping each tab's own back stack, so coming back to one picks up where you left it. */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

// Search registers with an optional query arg, so this is the pattern its back stack entry carries.
private val SEARCH_ROUTE_PATTERN = "${ResonaDestination.Search.route}?query={query}"

/** [ResonaDestination.Search]'s route, optionally pre-filled (e.g. tapping a genre chip on Home). */
private fun searchRoute(query: String): String =
    if (query.isBlank()) ResonaDestination.Search.route
    else "${ResonaDestination.Search.route}?query=${Uri.encode(query)}"

/** [ResonaDestination.PlaylistDetail]'s route for one specific playlist. */
private fun playlistDetailRoute(browseId: String, title: String): String =
    "${ResonaDestination.PlaylistDetail.route}/${Uri.encode(browseId)}?title=${Uri.encode(title)}"

/** [ResonaDestination.ArtistDetail]'s route for one specific artist.
 *  [browseId] is only ever known upfront for a Search result (see
 *  SearchModels.extractArtists()) -- every other caller (Home, Stats,
 *  History) only has [name], and ArtistDetailViewModel resolves a browseId
 *  for those itself. */
private fun artistDetailRoute(name: String, browseId: String? = null): String {
    val base = "${ResonaDestination.ArtistDetail.route}/${Uri.encode(name)}"
    return if (browseId.isNullOrBlank()) base else "$base?${ArtistDetailViewModel.ARG_BROWSE_ID}=${Uri.encode(browseId)}"
}

/** [ResonaDestination.AlbumDetail]'s route for one specific album. */
private fun albumDetailRoute(browseId: String): String =
    "${ResonaDestination.AlbumDetail.route}/${Uri.encode(browseId)}"

private fun podcastShowRoute(browseId: String): String =
    "${ResonaDestination.PodcastShow.route}/${Uri.encode(browseId)}"
