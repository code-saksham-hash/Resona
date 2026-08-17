package com.resona.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.Song
import com.resona.music.domain.stats.ArtistStat
import com.resona.music.domain.stats.formatListeningTime

private val cardShape = RoundedCornerShape(16.dp)
private val cardColor = Color.White.copy(alpha = 0.06f)
private val cardBorder = Color.White.copy(alpha = 0.1f)

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    onArtistClick: (String) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(top = 28.dp)
    ) {
        Text(
            text = "Stats",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroStat(
                value = stats.totalPlays.toString(),
                label = "Total Plays",
                icon = Icons.Outlined.PlayCircle,
                modifier = Modifier.weight(1f)
            )
            HeroStat(
                value = formatListeningTime(stats.listeningSecondsAllTime),
                label = "Listening Time",
                icon = Icons.Outlined.Schedule,
                modifier = Modifier.weight(1.2f)
            )
        }

        if (stats.totalPlays > 0) {
            Spacer(modifier = Modifier.height(28.dp))
            SectionHeader("Top Tracks")
            Spacer(modifier = Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(stats.topTracks, key = { _, pair -> pair.first.videoId }) { index, (song, plays) ->
                    TopTrackCard(
                        song = song,
                        rank = index + 1,
                        plays = plays
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionHeader("Top Artists")
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                stats.topArtists.take(4).chunked(2).forEach { rowArtists ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowArtists.forEach { (artist, plays) ->
                            TopArtistCell(
                                artist = artist,
                                plays = plays,
                                onClick = { onArtistClick(artist.name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowArtists.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            SectionHeader("This Period")
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val periodStats = listOf(
                    PeriodStat("Today", formatListeningTime(stats.listeningSecondsToday), Icons.Outlined.Today),
                    PeriodStat("This Week", formatListeningTime(stats.listeningSecondsThisWeek), Icons.Outlined.DateRange),
                    PeriodStat("Day Streak", "${stats.streakDays} days", Icons.Outlined.LocalFireDepartment),
                    PeriodStat("Unique Tracks", stats.uniqueTracks.toString(), Icons.Outlined.MusicNote),
                    PeriodStat("Unique Artists", stats.uniqueArtists.toString(), Icons.Outlined.Person),
                )
                periodStats.chunked(2).forEach { rowStats ->
                    if (rowStats.size == 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowStats.forEach { stat ->
                                PeriodStatTile(
                                    stat = stat,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PeriodStatTile(stat = rowStats.single())
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(28.dp))
            EmptyStateCard()
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun HeroStat(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

@Composable
private fun TopTrackCard(
    song: Song,
    rank: Int,
    plays: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.width(148.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
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
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = playCount(plays),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            minLines = 2
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopArtistCell(
    artist: ArtistStat,
    plays: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(cardShape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (artist.thumbnailUrl.isBlank()) {
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = playCount(plays),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class PeriodStat(
    val label: String,
    val value: String,
    val icon: ImageVector,
)

@Composable
private fun PeriodStatTile(
    stat: PeriodStat,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = stat.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stat.value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stat.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyStateCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .border(0.5.dp, cardBorder, RoundedCornerShape(20.dp))
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No plays yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Play something and your stats will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

private fun playCount(count: Int): String = "$count play" + if (count == 1) "" else "s"
