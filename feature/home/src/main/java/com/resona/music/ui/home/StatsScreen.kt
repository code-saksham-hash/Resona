package com.resona.music.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.Song
import com.resona.music.domain.stats.ArtistStat
import com.resona.music.domain.stats.PlayStats
import com.resona.music.domain.stats.formatListeningTime
import com.resona.music.ui.theme.ResonaTheme
import com.resona.music.ui.theme.SectionHeaderTextStyle

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onTrackClick: (Song) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    StatsScreenContent(
        stats = stats,
        onBack = onBack,
        onTrackClick = onTrackClick,
        onArtistClick = onArtistClick,
        modifier = modifier
    )
}

/**
 * Your charts: a centered hero with the all-time totals, a recent-activity
 * strip, a spotlight poster for the No. 1 track, then Billboard-style
 * ranked charts for the rest. Rank numerals are oversized on purpose --
 * they're the visual voice of the screen, the way artwork is on Home.
 *
 * Stateless so it's directly previewable without standing up Hilt --
 * [StatsScreen] above owns the [StatsViewModel] and threads its state
 * through, the same split Search and the detail screens use.
 */
@Composable
private fun StatsScreenContent(
    stats: PlayStats,
    onBack: () -> Unit = {},
    onTrackClick: (Song) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        StatsTopBar(onBack = onBack)

        if (stats.totalPlays == 0) {
            StatsEmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                TotalsHero(stats = stats)
                Spacer(modifier = Modifier.height(12.dp))
                ActivityStrip(stats = stats)

                if (stats.topTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    StatsSectionHeader("Top Tracks")
                    Spacer(modifier = Modifier.height(14.dp))
                    TopTrackSpotlight(
                        song = stats.topTracks.first().first,
                        plays = stats.topTracks.first().second,
                        onClick = { onTrackClick(stats.topTracks.first().first) }
                    )
                    if (stats.topTracks.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ChartCard {
                            stats.topTracks.drop(1).forEachIndexed { index, (song, plays) ->
                                TrackRankRow(
                                    rank = index + 2,
                                    song = song,
                                    plays = plays,
                                    onClick = { onTrackClick(song) }
                                )
                            }
                        }
                    }
                }

                if (stats.topArtists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))
                    StatsSectionHeader("Top Artists")
                    Spacer(modifier = Modifier.height(14.dp))
                    ChartCard {
                        stats.topArtists.forEachIndexed { index, (artist, plays) ->
                            ArtistRankRow(
                                rank = index + 1,
                                artist = artist,
                                plays = plays,
                                onClick = { onArtistClick(artist.name) }
                            )
                        }
                    }
                }

                // Clears the floating bottom chrome (pill nav, plus the
                // mini-player when a track is playing).
                Spacer(modifier = Modifier.height(160.dp))
            }
        }
    }
}

/**
 * Back + title bar, the same 61dp pattern Settings and the detail screens
 * use.
 */
@Composable
private fun StatsTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(end = 17.dp)
            .height(61.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Stats",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Section titles read exactly like Home/Search section headers. */
@Composable
private fun StatsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = SectionHeaderTextStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 17.dp)
    )
}

/**
 * The all-time totals as one centered hero: the play count set big in the
 * display voice, everything else collapsed into a single summary line
 * underneath. Centered against the left-aligned charts below it so the
 * top of the screen feels like a masthead, not another tile.
 */
@Composable
private fun TotalsHero(
    stats: PlayStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stats.totalPlays.toString(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "TOTAL PLAYS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "${formatListeningTime(stats.listeningSecondsAllTime)} listening · " +
                "${plural(stats.uniqueTracks, "track")} · ${plural(stats.uniqueArtists, "artist")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The recent window -- today, this week, day streak -- as three centered
 * stats in the same card the hero uses, so the top of the screen reads as
 * one totals block.
 */
@Composable
private fun ActivityStrip(
    stats: PlayStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        ActivityStat(
            value = formatListeningTime(stats.listeningSecondsToday),
            label = "Today",
            modifier = Modifier.weight(1f)
        )
        ActivityStat(
            value = formatListeningTime(stats.listeningSecondsThisWeek),
            label = "This Week",
            modifier = Modifier.weight(1f)
        )
        ActivityStat(
            value = "${stats.streakDays}d",
            label = "Day Streak",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActivityStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1
        )
    }
}

/**
 * The No. 1 track as a poster: full-bleed 16:9 artwork (which is the
 * thumbnail's native shape, so nothing is cropped away) with a scrim and
 * overlay type, the same overlay treatment Home's Recommended cards use.
 * Tapping it plays the track.
 */
@Composable
private fun TopTrackSpotlight(
    song: Song,
    plays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp)
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.highResThumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxSize()
        )
        // Scrim, not a theme surface: it sits on artwork, where a
        // monochrome surface fill would wash out either mode.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
        Text(
            text = "Your top track".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .padding(end = 100.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = playCount(plays),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

/**
 * The container both charts share: one surfaceContainerLowest card holding
 * plain rows, exactly like Home's New For You section.
 */
@Composable
private fun ChartCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 6.dp),
        content = { content() }
    )
}

@Composable
private fun TrackRankRow(
    rank: Int,
    song: Song,
    plays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(44.dp)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = song.highResThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = playCount(plays),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ArtistRankRow(
    rank: Int,
    artist: ArtistStat,
    plays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString().padStart(2, '0'),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(44.dp)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (artist.thumbnailUrl.isBlank()) {
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = playCount(plays),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun StatsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Leaderboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No stats yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Play something and your stats will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

private fun plural(count: Int, singular: String): String =
    "$count $singular" + if (count == 1) "" else "s"

private fun playCount(count: Int): String = plural(count, "play")

private val previewSongs = listOf(
    Song(videoId = "1", title = "Midnight City", artist = "M83", thumbnailUrl = "", duration = "4:04"),
    Song(videoId = "2", title = "Nightcall", artist = "Kavinsky", thumbnailUrl = "", duration = "4:18"),
    Song(videoId = "3", title = "Instant Crush", artist = "Daft Punk", thumbnailUrl = "", duration = "3:44"),
    Song(videoId = "4", title = "Ether Drift", artist = "Vanish In Dust", thumbnailUrl = "", duration = "3:42"),
    Song(videoId = "5", title = "Neon Dusk", artist = "Night Drive", thumbnailUrl = "", duration = "4:01"),
)

private val previewStats = PlayStats(
    totalPlays = 47,
    listeningSecondsToday = 3_600L,
    listeningSecondsThisWeek = 12_340L,
    listeningSecondsAllTime = 200_000L,
    uniqueTracks = 18,
    uniqueArtists = 9,
    topArtists = listOf(
        ArtistStat("Daft Punk", "") to 14,
        ArtistStat("M83", "") to 9,
        ArtistStat("Kavinsky", "") to 6,
        ArtistStat("Night Drive", "") to 4,
        ArtistStat("Vanish In Dust", "") to 3,
    ),
    topTracks = previewSongs.mapIndexed { index, song -> song to (12 - index * 2) },
    streakDays = 6
)

@Preview(showBackground = true, name = "Populated")
@Composable
private fun StatsScreenPopulatedPreview() {
    ResonaTheme(darkTheme = true) {
        StatsScreenContent(stats = previewStats)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Populated Light")
@Composable
private fun StatsScreenPopulatedLightPreview() {
    ResonaTheme(darkTheme = false) {
        StatsScreenContent(stats = previewStats)
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun StatsScreenEmptyPreview() {
    ResonaTheme(darkTheme = true) {
        StatsScreenContent(
            stats = PlayStats(
                totalPlays = 0,
                listeningSecondsToday = 0L,
                listeningSecondsThisWeek = 0L,
                listeningSecondsAllTime = 0L,
                uniqueTracks = 0,
                uniqueArtists = 0,
                topArtists = emptyList(),
                topTracks = emptyList(),
                streakDays = 0
            )
        )
    }
}
