package com.resona.music.ui.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Song
import com.resona.music.domain.stats.timeAgo
import com.resona.music.ui.theme.ResonaTheme
import kotlinx.coroutines.delay

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    HistoryScreenContent(
        history = history,
        nowMillis = nowMillis,
        onBack = onBack,
        onSongClick = onSongClick,
        modifier = modifier
    )
}

/**
 * Stateless so it's directly previewable without standing up Hilt --
 * [HistoryScreen] above owns the [HistoryViewModel] and threads its state
 * through, the same split Search and the detail screens use.
 */
@Composable
private fun HistoryScreenContent(
    history: List<PlayHistoryEntry>,
    nowMillis: Long,
    onBack: () -> Unit = {},
    onSongClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HistoryTopBar(onBack = onBack)

        Text(
            text = if (history.isEmpty()) "Recently played tracks"
            else "${history.size} ${if (history.size == 1) "play" else "plays"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 17.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            HistoryEmptyState(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 160.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Keyed on videoId + timestamp + index, not videoId alone:
                // the same track replayed appears more than once, and
                // duplicate keys break item identity/animations.
                itemsIndexed(
                    history,
                    key = { index, entry ->
                        "${entry.song.videoId}_${entry.playedAtMillis}_$index"
                    }
                ) { _, entry ->
                    HistoryRow(
                        entry = entry,
                        nowMillis = nowMillis,
                        onClick = { onSongClick(entry.song) }
                    )
                }
            }
        }
    }
}

/**
 * Back + title bar, the same 61dp pattern Settings and the detail screens
 * use -- History previously had no way back at all, and its oversized
 * headlineMedium title matched nothing else in the app.
 */
@Composable
private fun HistoryTopBar(
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
            text = "History",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A plain song row -- thumbnail, title, artist, trailing recency -- reading
 * exactly like Search/Library/Album rows (same 48dp shapes.small thumb,
 * same titleMedium/labelSmall pairing, no card background). The old card
 * with a White-alpha fill and border turned a long scrolling list into
 * visual noise and washed out entirely in light mode.
 */
@Composable
private fun HistoryRow(
    entry: PlayHistoryEntry,
    nowMillis: Long,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (entry.song.thumbnailUrl.isBlank()) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                AsyncImage(
                    model = entry.song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.song.displayArtist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                // A legacy record has playedAtMillis = 0, which would format as
                // a timestamp from 1970. Show a neutral label for those instead.
                text = if (entry.playedAtMillis > 0) timeAgo(entry.playedAtMillis, nowMillis) else "Recently",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (entry.song.duration.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.song.duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(modifier: Modifier = Modifier) {
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
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No listening history yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tracks you play will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

private const val PreviewNow = 1_700_000_000_000L

private val previewHistory = listOf(
    PlayHistoryEntry(
        song = Song(videoId = "1", title = "Midnight City", artist = "M83", thumbnailUrl = "", duration = "4:04"),
        playedAtMillis = PreviewNow - 5 * 60_000L
    ),
    PlayHistoryEntry(
        song = Song(videoId = "2", title = "Nightcall", artist = "Kavinsky", thumbnailUrl = "", duration = "4:18"),
        playedAtMillis = PreviewNow - 2 * 3_600_000L
    ),
    PlayHistoryEntry(
        song = Song(videoId = "1", title = "Midnight City", artist = "M83", thumbnailUrl = "", duration = "4:04"),
        playedAtMillis = PreviewNow - 26 * 3_600_000L
    ),
    PlayHistoryEntry(
        song = Song(
            videoId = "3",
            title = "Instant Crush",
            artist = "Daft Punk ft. Julian Casablancas",
            thumbnailUrl = "",
            duration = "3:44"
        ),
        playedAtMillis = PreviewNow - 4 * 86_400_000L
    ),
    PlayHistoryEntry(
        song = Song(videoId = "4", title = "Ether Drift", artist = "", thumbnailUrl = ""),
        playedAtMillis = 0L
    ),
)

@Preview(showBackground = true, name = "Populated")
@Composable
private fun HistoryScreenPopulatedPreview() {
    ResonaTheme(darkTheme = true) {
        HistoryScreenContent(history = previewHistory, nowMillis = PreviewNow)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, name = "Populated Light")
@Composable
private fun HistoryScreenPopulatedLightPreview() {
    ResonaTheme(darkTheme = false) {
        HistoryScreenContent(history = previewHistory, nowMillis = PreviewNow)
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun HistoryScreenEmptyPreview() {
    ResonaTheme(darkTheme = true) {
        HistoryScreenContent(history = emptyList(), nowMillis = PreviewNow)
    }
}
