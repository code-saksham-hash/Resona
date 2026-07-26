package com.resona.music.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.ArtistSpotlight
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.model.Song
import com.resona.music.ui.theme.JosefinSansFontFamily
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.NocturneSurface
import com.resona.music.ui.theme.ResonaLogoIcon
import com.resona.music.ui.theme.ResonaSearchEntryBar
import com.resona.music.ui.theme.ResonaTheme

data class HomeAlbum(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val videoId: String = ""
)

data class HomeArtist(
    val id: String,
    val name: String,
    val imageUrl: String
)

data class HomeTrack(
    val id: String,
    val number: Int,
    val title: String,
    val artist: String,
    val duration: String,
    val imageUrl: String
)

private val quickPickGenres =
    listOf("Electronic", "Ambient", "Jazz", "Hip-Hop", "Classical", "Lo-Fi", "Techno")

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSearchQuery: (String) -> Unit = {},
    onExploreClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onTrackClick: (HomeTrack) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onSearchQuery = onSearchQuery,
        onExploreClick = onExploreClick,
        onProfileClick = onProfileClick,
        onAlbumClick = onAlbumClick,
        onArtistClick = onArtistClick,
        onTrackClick = onTrackClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    uiState: HomeUiState,
    onRefresh: () -> Unit = {},
    onSearchQuery: (String) -> Unit = {},
    onExploreClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onTrackClick: (HomeTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(onSearchQuery = onSearchQuery, onProfileClick = onProfileClick)

            val feed = uiState.feed
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.weight(1f).fillMaxWidth())
                feed == null -> ErrorState(
                    message = uiState.errorMessage ?: "Couldn't load your feed",
                    onRetry = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                else -> HomeFeedList(
                    feed = feed,
                    onGenreClick = onSearchQuery,
                    onExploreClick = onExploreClick,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            NocturneOutlinedButton(
                text = "Retry",
                onClick = onRetry,
                borderColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HomeFeedList(
    feed: HomeFeed,
    onGenreClick: (String) -> Unit,
    onExploreClick: () -> Unit,
    onAlbumClick: (HomeAlbum) -> Unit,
    onArtistClick: (HomeArtist) -> Unit,
    onTrackClick: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val recommended = feed.songsFor("recommended")
    val trending = feed.songsFor("trending")
    val newForYou = feed.songsFor("new")

    LazyColumn(modifier = modifier) {
        item { Spacer(modifier = Modifier.height(7.dp)) }
        item { QuickPicksRow(onGenreClick = onGenreClick) }
        if (recommended.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                RecommendedSection(
                    title = feed.titleFor("recommended", "Recommended For You"),
                    albums = recommended.map { it.toHomeAlbum() },
                    onAlbumClick = onAlbumClick
                )
            }
        }
        if (trending.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                TrendingSection(
                    title = feed.titleFor("trending", "Trending"),
                    albums = trending.map { it.toHomeAlbum() },
                    onAlbumClick = onAlbumClick
                )
            }
        }
        if (feed.popularArtists.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(27.dp)) }
            item {
                TopArtistsSection(
                    artists = feed.popularArtists.mapIndexed { index, artist -> artist.toHomeArtist(index) },
                    onArtistClick = onArtistClick
                )
            }
        }
        if (newForYou.isNotEmpty()) {
            item { Spacer(modifier = Modifier.height(27.dp)) }
            item {
                NewForYouSection(
                    title = feed.titleFor("new", "New for You"),
                    tracks = newForYou.mapIndexed { index, song -> song.toHomeTrack(index) },
                    onTrackClick = onTrackClick
                )
            }
        }
        item { Spacer(modifier = Modifier.height(14.dp)) }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Don't see what you want here? Go to Explore",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .clickable(onClick = onExploreClick)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "EXPLORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(27.dp)) }
    }
}

private fun HomeFeed.songsFor(sectionId: String): List<Song> =
    sections.find { it.id == sectionId }?.songs.orEmpty()

private fun HomeFeed.titleFor(sectionId: String, fallback: String): String =
    sections.find { it.id == sectionId }?.title?.takeIf { it.isNotBlank() } ?: fallback

private fun Song.toHomeAlbum() = HomeAlbum(
    id = videoId,
    title = title,
    subtitle = artist,
    imageUrl = highResThumbnailUrl,
    videoId = videoId
)

private fun Song.toHomeTrack(index: Int) = HomeTrack(
    id = videoId,
    number = index + 1,
    title = title,
    artist = artist,
    duration = duration,
    imageUrl = highResThumbnailUrl
)

private fun ArtistSpotlight.toHomeArtist(index: Int) = HomeArtist(
    id = "popular_artist_$index",
    name = name,
    imageUrl = thumbnailUrl
)

@Composable
private fun HomeTopBar(
    onSearchQuery: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 17.dp, end = 17.dp)
            .height(61.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = ResonaLogoIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        ResonaSearchEntryBar(
            onClick = { onSearchQuery("") },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onProfileClick) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun QuickPicksRow(onGenreClick: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        quickPickGenres.forEach { genre ->
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onGenreClick(genre) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun RecommendedSection(
    albums: List<HomeAlbum>,
    title: String = "Recommended For You",
    onAlbumClick: (HomeAlbum) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = JosefinSansFontFamily,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            albums.forEach { album ->
                RecommendedCard(
                    title = album.title,
                    subtitle = album.subtitle,
                    imageUrl = album.imageUrl,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
private fun RecommendedCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(238.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                NocturneSurface.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TrendingSection(
    albums: List<HomeAlbum>,
    title: String = "Trending",
    onAlbumClick: (HomeAlbum) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = JosefinSansFontFamily,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            albums.forEach { album ->
                TrendingCard(
                    title = album.title,
                    subtitle = album.subtitle,
                    imageUrl = album.imageUrl,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
private fun TrendingCard(
    title: String,
    subtitle: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(204.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                NocturneSurface.copy(alpha = 0.95f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TopArtistsSection(
    artists: List<HomeArtist>,
    onArtistClick: (HomeArtist) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
        ) {
            Column {
                Text(
                    text = "Popular Artists",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = JosefinSansFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    // There's no account/listening history in this app to
                    // personalize against -- this is deliberately framed as
                    // "popular right now", not "yours".
                    text = "Trending across YouTube Music right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 17.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            artists.forEach { artist ->
                ArtistCard(name = artist.name, imageUrl = artist.imageUrl, onClick = { onArtistClick(artist) })
            }
        }
    }
}

@Composable
private fun ArtistCard(
    name: String,
    imageUrl: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(122.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .size(122.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NewForYouSection(
    tracks: List<HomeTrack>,
    title: String = "New for You",
    onTrackClick: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
                .padding(bottom = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = JosefinSansFontFamily,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 17.dp)
                .clip(MaterialTheme.shapes.large)
        ) {
            tracks.forEach { track ->
                NewForYouRow(track = track, onClick = { onTrackClick(track) })
            }
        }
    }
}

@Composable
private fun NewForYouRow(
    track: HomeTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 20.dp)
            .height(68.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format("%02d", track.number),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(20.dp)
        )

        Spacer(modifier = Modifier.width(20.dp))

        Box(
            modifier = Modifier
                .size(41.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = track.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        if (track.duration.isNotBlank()) {
            Text(
                text = track.duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.width(14.dp))
        }

        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "More",
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

private val previewHomeFeed = HomeFeed(
    sections = listOf(
        com.resona.music.domain.model.HomeFeedSection(
            id = "recommended",
            title = "Recommended For You",
            songs = listOf(
                Song("1", "Eternal Waves", "Ambient Collective", "https://picsum.photos/seed/rec1/400/400", "3:12"),
                Song("2", "Neon Dusk", "Night Drive", "https://picsum.photos/seed/rec2/400/400", "4:01"),
            )
        ),
        com.resona.music.domain.model.HomeFeedSection(
            id = "trending",
            title = "Trending Now",
            songs = listOf(
                Song("3", "Midnight Signal", "Synthwave Union", "https://picsum.photos/seed/trend1/400/400", "3:45"),
            )
        ),
        com.resona.music.domain.model.HomeFeedSection(
            id = "new",
            title = "New For You",
            songs = listOf(
                Song("4", "Ether Drift", "Vanish In Dust", "https://picsum.photos/seed/track1/400/400", "3:42"),
                Song("5", "Monolith IV", "Structural Integrity", "https://picsum.photos/seed/track2/400/400", "5:18"),
            )
        ),
    ),
    popularArtists = listOf(
        ArtistSpotlight("Vanish In Dust", "https://picsum.photos/seed/artist1/400/400"),
        ArtistSpotlight("Kinesis", "https://picsum.photos/seed/artist2/400/400"),
    )
)

@Preview(showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    ResonaTheme {
        HomeScreenContent(uiState = HomeUiState(isLoading = false, feed = previewHomeFeed))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    ResonaTheme {
        HomeScreenContent(uiState = HomeUiState(isLoading = false, feed = previewHomeFeed))
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun HomeScreenLoadingPreview() {
    ResonaTheme {
        HomeScreenContent(uiState = HomeUiState(isLoading = true))
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun HomeScreenErrorPreview() {
    ResonaTheme {
        HomeScreenContent(
            uiState = HomeUiState(isLoading = false, errorMessage = "Unable to resolve host \"music.youtube.com\"")
        )
    }
}
