package com.resona.music.ui.nowplaying

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.Song
import com.resona.music.feature.player.R
import com.resona.music.playback.DownloadState
import com.resona.music.playback.LyricsState
import com.resona.music.playback.PlayerUiState
import com.resona.music.ui.player.AlbumArtBackdrop
import com.resona.music.ui.player.AlbumArtPalette
import com.resona.music.ui.player.MiniPlayerBar
import com.resona.music.ui.player.rememberAlbumArtPalette
import com.resona.music.ui.theme.ResonaTheme

private enum class BottomTab { Queue, Lyrics }

/** Which pane [NowPlayingScreen] is currently showing below the top bar. */
private enum class NowPlayingPane { Player, Queue, Lyrics, Empty }

@Composable
fun NowPlayingScreen(
    uiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onQueueClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onDownloadClick: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onLoadLyrics: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf<BottomTab?>(null) }

    val track = uiState.currentTrack
    // Shared by every pane below; see AlbumArtPalette.kt.
    val palette = rememberAlbumArtPalette(track?.highResThumbnailUrl)
    val pane = when {
        activeTab == BottomTab.Queue -> NowPlayingPane.Queue
        activeTab == BottomTab.Lyrics -> NowPlayingPane.Lyrics
        track == null -> NowPlayingPane.Empty
        else -> NowPlayingPane.Player
    }

    Box(modifier = modifier.fillMaxSize()) {
        AlbumArtBackdrop(
            artworkUrl = track?.highResThumbnailUrl,
            palette = palette,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            NowPlayingTopBar(
                onBack = onBack,
                onQueueClick = {
                    activeTab = if (activeTab == BottomTab.Queue) null else BottomTab.Queue
                    onQueueClick()
                },
                palette = palette,
                headerTitle = if (activeTab == BottomTab.Queue) "Queue" else null,
                downloadState = uiState.downloadState,
                onDownloadClick = onDownloadClick
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (pane) {
                    NowPlayingPane.Queue -> QueueContent(
                        queue = uiState.queue,
                        currentTrack = track,
                        currentDurationMs = uiState.duration,
                        isPlaying = uiState.isPlaying,
                        palette = palette,
                        onSongClick = onSongClick,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onMiniPlayerClick = { activeTab = null },
                    )
                    NowPlayingPane.Lyrics -> LyricsContent(
                        lyricsState = uiState.lyricsState,
                        syncedLyrics = uiState.syncedLyrics,
                        position = uiState.position,
                        palette = palette,
                        onLoad = onLoadLyrics,
                    )
                    NowPlayingPane.Empty -> Text(
                        text = "Nothing is playing",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    NowPlayingPane.Player -> if (track != null) {
                        PlayerContent(
                            track = track,
                            position = uiState.position,
                            duration = uiState.duration,
                            isPlaying = uiState.isPlaying,
                            isLiked = uiState.isLiked,
                            isBuffering = uiState.isBuffering,
                            downloadState = uiState.downloadState,
                            error = uiState.error,
                            palette = palette,
                            onSeek = onSeek,
                            onTogglePlayPause = onTogglePlayPause,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleLike = onToggleLike,
                            onDownloadClick = onDownloadClick
                        )
                    }
                }
            }

            BottomActionRow(
                activeTab = activeTab,
                queueSize = uiState.queue.size,
                palette = palette,
                onQueueClick = {
                    activeTab = if (activeTab == BottomTab.Queue) null else BottomTab.Queue
                    onQueueClick()
                },
                onLyricsClick = {
                    activeTab = if (activeTab == BottomTab.Lyrics) null else BottomTab.Lyrics
                    if (activeTab == BottomTab.Lyrics) onLoadLyrics()
                }
            )
        }
    }
}

@Composable
private fun PlayerContent(
    track: Song,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isLiked: Boolean,
    isBuffering: Boolean,
    downloadState: DownloadState,
    error: String?,
    palette: AlbumArtPalette,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = track.videoId,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "album_art"
        ) { _ ->
            AlbumArt(
                thumbnailUrl = track.highResThumbnailUrl,
                palette = palette,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = track.videoId,
            transitionSpec = {
                (slideInHorizontally(animationSpec = tween(300)) { it / 4 } + fadeIn(animationSpec = tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it / 4 } + fadeOut(animationSpec = tween(300)))
            },
            label = "track_info"
        ) { _ ->
            Column {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onBackground.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SeekBar(
            position = position,
            duration = duration,
            palette = palette,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        PlaybackControls(
            isPlaying = isPlaying,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            isLiked = isLiked,
            onToggleLike = onToggleLike,
            downloadState = downloadState,
            onDownloadClick = onDownloadClick
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun QueueContent(
    queue: List<Song>,
    currentTrack: Song?,
    currentDurationMs: Long,
    isPlaying: Boolean,
    palette: AlbumArtPalette,
    onSongClick: (Song) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onMiniPlayerClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        var miniPlayerHeightPx by remember { mutableStateOf(0) }
        val miniPlayerHeightDp = with(LocalDensity.current) { miniPlayerHeightPx.toDp() }

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "No upcoming songs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentPadding = PaddingValues(bottom = miniPlayerHeightDp + 16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onBackground,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                itemsIndexed(queue) { _, song ->
                    val isCurrent = song.videoId == currentTrack?.videoId
                    val rowShape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .then(
                                if (isCurrent) Modifier
                                    .background(palette.accent.copy(alpha = 0.14f), rowShape)
                                    .border(1.dp, palette.accent.copy(alpha = 0.55f), rowShape)
                                else Modifier
                            )
                            .clickable(onClick = { onSongClick(song) })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                placeholder = ColorPainter(palette.onBackground.copy(alpha = 0.1f)),
                                error = ColorPainter(palette.onBackground.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                )
                                SoundWaves(
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onBackground,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (song.duration.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = song.duration,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (isCurrent && currentDurationMs > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatDuration(currentDurationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onBackground.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (currentTrack != null) {
            MiniPlayerBar(
                track = currentTrack,
                isPlaying = isPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onSkipToPrevious = onSkipPrevious,
                onSkipToNext = onSkipNext,
                onClick = onMiniPlayerClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { miniPlayerHeightPx = it.height }
            )
        }
    }
}

/**
 * Animated equalizer glyph (three staggered pulsing bars) over the
 * currently playing track's darkened cover.
 */
@Composable
private fun SoundWaves(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "soundWaves")
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 3) {
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 320, delayMillis = i * 110),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave$i"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(width = 3.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .graphicsLayer { scaleY = scale }
                    .background(color)
            )
        }
    }
}

@Composable
private fun LyricsContent(
    lyricsState: LyricsState,
    syncedLyrics: List<LyricsLine>,
    position: Long,
    palette: AlbumArtPalette,
    onLoad: () -> Unit,
) {
    LaunchedEffect(lyricsState) {
        if (lyricsState == LyricsState.NotLoaded) onLoad()
    }

    AnimatedContent(
        targetState = lyricsState,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
        label = "lyrics_state",
        modifier = Modifier.fillMaxSize(),
    ) { state ->
        when (state) {
            LyricsState.NotLoaded, LyricsState.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    color = palette.accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is LyricsState.Available -> if (syncedLyrics.isNotEmpty()) {
                SyncedLyricsView(
                    lines = syncedLyrics,
                    position = position,
                    palette = palette,
                )
            } else {
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
            LyricsState.Unavailable -> Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Lyrics not available for this track",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Keeps the active lyric line centered, easing each line's size, color,
 * and alpha by its distance from the active line. Persistent LazyColumn
 * so lines ease in place instead of the window re-sliding each cycle.
 */
@Composable
private fun SyncedLyricsView(
    lines: List<LyricsLine>,
    position: Long,
    palette: AlbumArtPalette,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var viewportHeightPx by remember { mutableStateOf(0) }
    val halfLineHeightPx = with(density) { 16.dp.toPx() }

    val currentIndex = remember(position, lines) {
        val idx = lines.indexOfLast { it.timestamp <= position }
        if (idx < 0) 0 else idx
    }

    LaunchedEffect(currentIndex, viewportHeightPx, lines) {
        if (viewportHeightPx <= 0 || lines.isEmpty()) return@LaunchedEffect
        val centerOffset = -(viewportHeightPx / 2f - halfLineHeightPx).toInt()
        // Seeks can jump many lines -- snap close first, then ease the rest.
        val nearestVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        if (kotlin.math.abs(currentIndex - nearestVisible) > 12) {
            listState.scrollToItem((currentIndex - 2).coerceAtLeast(0), centerOffset)
        }
        listState.animateScrollToItem(currentIndex, centerOffset)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height },
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = with(density) { (viewportHeightPx / 2).toDp() }),
    ) {
        itemsIndexed(lines) { index, line ->
            LyricsSlot(
                text = line.text,
                distance = kotlin.math.abs(index - currentIndex),
                palette = palette,
            )
        }
    }
}

/**
 * One lyric line; its distance from the active line drives size, weight,
 * color, and alpha -- active (0) is large, bold, and accent-colored,
 * fading with distance.
 */
@Composable
private fun LyricsSlot(text: String, distance: Int, palette: AlbumArtPalette) {
    val isCurrent = distance == 0
    val targetAlpha = when (distance) {
        0 -> 1f
        1 -> 0.55f
        2 -> 0.3f
        else -> 0.14f
    }
    val color by animateColorAsState(
        if (isCurrent) palette.accent else palette.onBackground,
        tween(300),
        label = "lyricColor"
    )
    val alpha by animateFloatAsState(targetAlpha, tween(300), label = "lyricAlpha")
    val fontSizeSp by animateFloatAsState(if (isCurrent) 23f else 16f, tween(300), label = "lyricSize")

    Text(
        text = text,
        color = color.copy(alpha = alpha),
        fontSize = fontSizeSp.sp,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
    )
}

@Composable
private fun BottomActionRow(
    activeTab: BottomTab?,
    queueSize: Int,
    palette: AlbumArtPalette,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(onClick = onQueueClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_queue),
                contentDescription = null,
                tint = if (activeTab == BottomTab.Queue) palette.accent else palette.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (queueSize > 0) "Queue ($queueSize)" else "Queue",
                style = MaterialTheme.typography.labelLarge,
                color = if (activeTab == BottomTab.Queue) palette.accent else palette.onBackground.copy(alpha = 0.6f)
            )
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(onClick = onLyricsClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lyrics),
                contentDescription = null,
                tint = if (activeTab == BottomTab.Lyrics) palette.accent else palette.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.labelLarge,
                color = if (activeTab == BottomTab.Lyrics) palette.accent else palette.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    palette: AlbumArtPalette,
    headerTitle: String? = null,
    downloadState: DownloadState = DownloadState.Idle,
    onDownloadClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = palette.onBackground
            )
        }

        if (headerTitle != null) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.titleLarge,
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = onQueueClick) {
            Icon(
                painter = painterResource(R.drawable.ic_queue),
                contentDescription = "Queue",
                tint = palette.onBackground
            )
        }

        Box {
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More options",
                    tint = palette.onBackground
                )
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = { overflowExpanded = false }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            when (downloadState) {
                                is DownloadState.Downloading -> "Downloading…"
                                DownloadState.Downloaded -> "Downloaded"
                                else -> "Download"
                            }
                        )
                    },
                    enabled = downloadState !is DownloadState.Downloading && downloadState !is DownloadState.Downloaded,
                    onClick = {
                        onDownloadClick()
                        overflowExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = { overflowExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun AlbumArt(thumbnailUrl: String, palette: AlbumArtPalette, modifier: Modifier = Modifier) {
    AsyncImage(
        model = thumbnailUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, palette.onBackground.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
    )
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    palette: AlbumArtPalette,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragPosition by remember { mutableStateOf<Long?>(null) }
    val safeDuration = duration.coerceAtLeast(1L)
    val displayedMillis = dragPosition ?: position
    val progress = (displayedMillis.toFloat() / safeDuration).coerceIn(0f, 1f)

    val trackColor = palette.accent
    val railColor = palette.onBackground.copy(alpha = 0.25f)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(duration) {
                    if (duration <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fraction = (down.position.x / size.width).coerceIn(0f, 1f)
                        dragPosition = (fraction * safeDuration).toLong()

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                dragPosition = (newFraction * safeDuration).toLong()
                                change.consume()
                            } else {
                                break
                            }
                        } while (true)

                        dragPosition?.let { onSeek(it) }
                        dragPosition = null
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackCenterY = size.height / 2
                val thumbX = size.width * progress

                drawLine(
                    color = railColor,
                    start = Offset(0f, trackCenterY),
                    end = Offset(size.width, trackCenterY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = trackColor,
                    start = Offset(0f, trackCenterY),
                    end = Offset(thumbX, trackCenterY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawCircle(
                    color = trackColor,
                    radius = 6.dp.toPx(),
                    center = Offset(thumbX, trackCenterY)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(displayedMillis),
                style = MaterialTheme.typography.labelSmall,
                color = palette.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = palette.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Deliberately theme-colored, not [AlbumArtPalette]-tinted: transport
 * controls stay monochrome regardless of the album, matching the
 * mini-player. Only backdrop, text, seek fill, and lyrics accent take
 * the album's palette.
 */
@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    downloadState: DownloadState = DownloadState.Idle,
    onDownloadClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleLike,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onTogglePlayPause),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "Pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            DownloadButton(
                downloadState = downloadState,
                onDownloadClick = onDownloadClick
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun DownloadButton(
    downloadState: DownloadState,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(44.dp), contentAlignment = Alignment.Center) {
        val downloading = downloadState is DownloadState.Downloading
        val progress = (downloadState as? DownloadState.Downloading)?.progress

        if (downloading) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress ?: 0f,
                animationSpec = tween(durationMillis = 300),
                label = "downloadProgress"
            )
            if (progress != null) {
                // Determinate ring showing real download progress.
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            } else {
                // No Content-Length from the server -- spin instead.
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            }
        }

        IconButton(
            onClick = onDownloadClick,
            enabled = !downloading && downloadState !is DownloadState.Downloaded,
            modifier = Modifier.size(40.dp)
        ) {
            when (downloadState) {
                is DownloadState.Downloading -> DownloadingIcon()
                DownloadState.Downloaded -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
                is DownloadState.Failed -> Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Download failed, tap to retry",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(26.dp)
                )
                DownloadState.Idle -> Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "Download",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/** Download icon that breathes (1.0f -> 0.8f scale loop) while the download runs. */
@Composable
private fun DownloadingIcon() {
    val pulse = rememberInfiniteTransition(label = "downloadPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "downloadIconScale"
    )
    Icon(
        imageVector = Icons.Outlined.Download,
        contentDescription = "Downloading…",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .size(26.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    )
}

private val previewTrack = Song(
    videoId = "1",
    title = "Midnight City",
    artist = "M83",
    thumbnailUrl = ""
)

@Preview(showBackground = true, name = "Playing")
@Composable
private fun NowPlayingScreenPlayingPreview() {
    ResonaTheme {
        NowPlayingScreen(
            uiState = PlayerUiState(
                currentTrack = previewTrack,
                isPlaying = true,
                position = 87_000L,
                duration = 244_000L
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onQueueClick = {},
            onSongClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Playing (dark)")
@Composable
private fun NowPlayingScreenPlayingDarkPreview() {
    ResonaTheme(darkTheme = true) {
        NowPlayingScreen(
            uiState = PlayerUiState(
                currentTrack = previewTrack,
                isPlaying = true,
                position = 87_000L,
                duration = 244_000L
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onQueueClick = {},
            onSongClick = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty")
@Composable
private fun NowPlayingScreenEmptyPreview() {
    ResonaTheme {
        NowPlayingScreen(
            uiState = PlayerUiState(),
            onTogglePlayPause = {},
            onSeek = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onQueueClick = {},
            onSongClick = {},
            onBack = {}
        )
    }
}
