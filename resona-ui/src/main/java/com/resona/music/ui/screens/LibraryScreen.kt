package com.resona.music.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resona.music.ui.theme.ResonaLogoIcon
import com.resona.music.ui.theme.ResonaTheme

data class DownloadTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val imageUrl: String
)

private val mockTracks = listOf(
    DownloadTrack("1", "Ether Drift", "Vanish In Dust", "Monolith", "3:42", "https://picsum.photos/seed/dltrack1/400/400"),
    DownloadTrack("2", "Monolith IV", "Structural Integrity", "Breakdown", "5:18", "https://picsum.photos/seed/dltrack2/400/400"),
    DownloadTrack("3", "Surface Tension", "Kinesis", "Kinesis EP", "4:07", "https://picsum.photos/seed/dltrack3/400/400"),
    DownloadTrack("4", "Glass Ceiling", "AELOS", "AELOS", "6:01", "https://picsum.photos/seed/dltrack4/400/400"),
)

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onTrackClick: (DownloadTrack) -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    LibraryScreenContent(
        onTrackClick = onTrackClick,
        onProfileClick = onProfileClick,
        modifier = modifier
    )
}

@Composable
private fun LibraryScreenContent(
    onTrackClick: (DownloadTrack) -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LibraryTopBar(onProfileClick = onProfileClick)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            item { LibraryHeader() }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { DownloadToggle(isDownloadedOnly = false, onToggle = {}) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { DownloadTracksSection(tracks = mockTracks, onTrackClick = onTrackClick) }
            item { StatsFooter(trackCount = 4, storageUsed = "42.5 MB") }
            item { Spacer(modifier = Modifier.height(68.dp)) }
        }
    }
}

@Composable
private fun LibraryTopBar(
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 17.dp)
            .height(61.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = ResonaLogoIcon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(59.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { }
                .padding(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        IconButton(onClick = onProfileClick) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(27.dp)
            )
        }
    }
}

@Composable
private fun LibraryHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 17.dp)) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Downloads",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DownloadToggle(
    isDownloadedOnly: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Downloaded Only",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(41.dp)
                .height(20.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onToggle)
                .padding(2.dp),
            contentAlignment = if (isDownloadedOnly) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun DownloadTracksSection(
    tracks: List<DownloadTrack>,
    onTrackClick: (DownloadTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        tracks.forEach { track ->
            DownloadTrackRow(track = track, onClick = { onTrackClick(track) })
        }
    }
}

@Composable
private fun DownloadTrackRow(
    track: DownloadTrack,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
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
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${track.artist} \u2022 ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = track.duration,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Icon(
                imageVector = Icons.Outlined.DownloadDone,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.width(7.dp))

        Icon(
            imageVector = Icons.Outlined.MoreHoriz,
            contentDescription = "More",
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun StatsFooter(
    trackCount: Int,
    storageUsed: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$trackCount TRACKS \u2022 $storageUsed USED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color.Transparent)
                    .clickable { }
                    .padding(horizontal = 17.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "MANAGE STORAGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenLightPreview() {
    ResonaTheme {
        LibraryScreenContent()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryScreenDarkPreview() {
    ResonaTheme {
        LibraryScreenContent()
    }
}
