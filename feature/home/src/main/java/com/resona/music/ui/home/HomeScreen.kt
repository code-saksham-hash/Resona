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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.resona.music.ui.theme.JosefinSansFontFamily
import com.resona.music.ui.theme.NocturneSurface
import com.resona.music.ui.theme.ResonaLogoIcon
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
    val imageUrl: String,
    val videoId: String = ""
)

data class HomeTrack(
    val id: String,
    val number: Int,
    val title: String,
    val artist: String,
    val duration: String,
    val imageUrl: String
)

private val mockRecommended = listOf(
    HomeAlbum("1", "Eternal Waves", "Ambient", "https://picsum.photos/seed/rec1/400/400"),
    HomeAlbum("2", "Neon Dusk", "Electronic", "https://picsum.photos/seed/rec2/400/400"),
    HomeAlbum("3", "Fractal Dreams", "Experimental", "https://picsum.photos/seed/rec3/400/400"),
)

private val mockTrending = listOf(
    HomeAlbum("4", "Midnight Signal", "Synthwave", "https://picsum.photos/seed/trend1/400/400"),
    HomeAlbum("5", "Glacier", "Ambient", "https://picsum.photos/seed/trend2/400/400"),
    HomeAlbum("6", "Pulse", "Techno", "https://picsum.photos/seed/trend3/400/400"),
)

private val mockArtists = listOf(
    HomeArtist("1", "Vanish In Dust", "https://picsum.photos/seed/artist1/400/400"),
    HomeArtist("2", "Kinesis", "https://picsum.photos/seed/artist2/400/400"),
    HomeArtist("3", "AELOS", "https://picsum.photos/seed/artist3/400/400"),
    HomeArtist("4", "OBSCURA", "https://picsum.photos/seed/artist4/400/400"),
)

private val mockTracks = listOf(
    HomeTrack("1", 1, "Ether Drift", "Vanish In Dust", "3:42", "https://picsum.photos/seed/track1/400/400"),
    HomeTrack("2", 2, "Monolith IV", "Structural Integrity", "5:18", "https://picsum.photos/seed/track2/400/400"),
    HomeTrack("3", 3, "Surface Tension", "Kinesis", "4:07", "https://picsum.photos/seed/track3/400/400"),
    HomeTrack("4", 4, "Glass Ceiling", "AELOS", "6:01", "https://picsum.photos/seed/track4/400/400"),
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSearchQuery: (String) -> Unit = {},
    onExploreClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onTrackClick: (HomeTrack) -> Unit = {},
) {
    HomeScreenContent(
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
    onSearchQuery: (String) -> Unit = {},
    onExploreClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAlbumClick: (HomeAlbum) -> Unit = {},
    onArtistClick: (HomeArtist) -> Unit = {},
    onTrackClick: (HomeTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                delay(1500)
                isRefreshing = false
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(onSearchQuery = onSearchQuery, onProfileClick = onProfileClick)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
            item { Spacer(modifier = Modifier.height(7.dp)) }
            item { QuickPicksRow() }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { RecommendedSection(albums = mockRecommended, onAlbumClick = onAlbumClick) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { TrendingSection(albums = mockTrending, onAlbumClick = onAlbumClick) }
            item { Spacer(modifier = Modifier.height(27.dp)) }
            item { TopArtistsSection(artists = mockArtists, onArtistClick = onArtistClick) }
            item { Spacer(modifier = Modifier.height(27.dp)) }
            item { NewForYouSection(tracks = mockTracks, onTrackClick = onTrackClick) }
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
    }
}

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
            modifier = Modifier.size(66.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { onSearchQuery("") }) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
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
private fun QuickPicksRow(modifier: Modifier = Modifier) {
    val quickPicks = listOf("Electronic", "Ambient", "Jazz", "Hip-Hop", "Classical", "Lo-Fi", "Techno")
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        quickPicks.forEach { genre ->
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { }
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
                text = "Recommended For You",
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
                text = "Trending",
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
                    text = "Your Top Artists",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = JosefinSansFontFamily,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Based on your listening habits from the last 30 days.",
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
                text = "New for You",
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

        Text(
            text = track.duration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Icon(
            imageVector = Icons.Outlined.MoreVert,
            contentDescription = "More",
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    ResonaTheme {
        HomeScreenContent(onTrackClick = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    ResonaTheme {
        HomeScreenContent(onTrackClick = {})
    }
}
