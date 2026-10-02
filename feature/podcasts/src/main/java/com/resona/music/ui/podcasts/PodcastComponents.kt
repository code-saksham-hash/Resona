package com.resona.music.ui.podcasts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.resona.music.domain.model.EpisodeProgress
import com.resona.music.domain.model.PodcastEpisode
import com.resona.music.domain.model.PodcastShow
import com.resona.music.ui.theme.NocturneOutlinedButton
import com.resona.music.ui.theme.SectionHeaderTextStyle

// Same glass treatment as Library's quick link cards.
private val GlassFill = Color.White.copy(alpha = 0.06f)
private val GlassBorder = Color.White.copy(alpha = 0.1f)
internal val ScreenPadding = 17.dp

@Composable
internal fun PodcastTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(end = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/** The round frosted button the app's top bar uses. */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = SectionHeaderTextStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = ScreenPadding)
    )
}

@Composable
internal fun SubHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = ScreenPadding)
    )
}

@Composable
internal fun Artwork(
    url: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.clip(shape)
    )
}

@Composable
internal fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/** White play disc. Purely visual when [onClick] is null (the whole card is the button). */
@Composable
internal fun PlayDisc(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
) {
    val disc = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
    Box(
        modifier = if (onClick != null) {
            // 44dp touch target around the smaller visible disc.
            modifier.size(44.dp).clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
        } else {
            modifier
        },
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = disc, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

/** "Sep 23 · 55 min", skipping whichever half YouTube didn't give us. */
internal fun PodcastEpisode.metaLine(includeShow: Boolean): String =
    listOfNotNull(
        showTitle.takeIf { includeShow && it.isNotBlank() },
        publishedText.takeIf { it.isNotBlank() },
        lengthText.takeIf { !includeShow && it.isNotBlank() }
    ).joinToString(" · ")

/** "6 min" or "1 hr 5 min". YouTube's own text throws in seconds ("5 min 37 sec"), which is just noise here. */
internal val PodcastEpisode.lengthText: String
    get() {
        if (durationMillis <= 0L) return durationText
        val minutes = ((durationMillis + 30_000L) / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> "$minutes min"
            rest == 0L -> "$hours hr"
            else -> "$hours hr $rest min"
        }
    }

internal fun formatTimeLeft(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(1L)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "$minutes min left"
        rest == 0L -> "$hours hr left"
        else -> "$hours hr $rest min left"
    }
}

/** Big card for an episode you're partway through. Tapping anywhere resumes it. */
@Composable
internal fun ContinueCard(
    progress: EpisodeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = progress.song
    Row(
        modifier = modifier
            .width(300.dp)
            .clip(MaterialTheme.shapes.large)
            .background(GlassFill)
            .border(0.5.dp, GlassBorder, MaterialTheme.shapes.large)
            .clickable(onClickLabel = "Resume ${song.title}", role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Artwork(url = song.thumbnailUrl, modifier = Modifier.size(104.dp))
        Column(modifier = Modifier.weight(1f).height(104.dp)) {
            Text(
                text = song.displayArtist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    ProgressLine(fraction = progress.fraction, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatTimeLeft(progress.remainingMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                PlayDisc(contentDescription = null)
            }
        }
    }
}

/** A followed show in the Your shows row, with a badge when it has unplayed new episodes. */
@Composable
internal fun ShowTile(
    show: PodcastShow,
    newCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (newCount > 0) "${show.title}, $newCount new" else show.title
    Column(
        modifier = modifier
            .width(104.dp)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
    ) {
        Box {
            Artwork(url = show.thumbnailUrl, modifier = Modifier.size(104.dp), shape = MaterialTheme.shapes.large)
            if (newCount > 0) {
                Text(
                    text = "$newCount NEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = show.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A show found through Browse. Its image is an episode frame, hence 16:9. */
@Composable
internal fun PopularShowCard(
    show: PodcastShow,
    isFollowing: Boolean,
    onClick: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(224.dp)) {
        Column(modifier = Modifier.clickable(onClickLabel = show.title, role = Role.Button, onClick = onClick)) {
            Artwork(
                url = show.thumbnailUrl,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.large
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = show.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        FollowButton(isFollowing = isFollowing, onClick = onFollow, showTitle = show.title)
    }
}

@Composable
internal fun FollowButton(
    isFollowing: Boolean,
    onClick: () -> Unit,
    showTitle: String,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .then(
                if (isFollowing) Modifier.background(MaterialTheme.colorScheme.primary)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
            )
            .clickable(
                onClickLabel = if (isFollowing) "Unfollow $showTitle" else "Follow $showTitle",
                role = Role.Button,
                onClick = onClick
            )
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val content = if (isFollowing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
        Icon(
            imageVector = if (isFollowing) Icons.Filled.Check else Icons.Outlined.Add,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isFollowing) "Following" else "Follow",
            style = MaterialTheme.typography.labelMedium,
            color = content
        )
    }
}

/**
 * One episode with its status: time left while in progress, "Played" once
 * done. The whole row plays it.
 */
@Composable
internal fun EpisodeRow(
    episode: PodcastEpisode,
    artworkUrl: String,
    progress: EpisodeProgress?,
    includeShowInMeta: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Play ${episode.title}", role = Role.Button, onClick = onPlay)
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(url = artworkUrl, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            val meta = episode.metaLine(includeShowInMeta)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            when {
                progress?.finished == true -> StatusText("Played")
                progress != null && progress.positionMillis > 0L -> Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProgressLine(fraction = progress.fraction, modifier = Modifier.width(96.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimeLeft(progress.remainingMillis),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                includeShowInMeta && episode.lengthText.isNotBlank() -> StatusText(episode.lengthText)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        PlayDisc(contentDescription = null)
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Compact numbered row for the Top episodes chart. */
@Composable
internal fun RankedEpisodeRow(
    rank: Int,
    episode: PodcastEpisode,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable(onClickLabel = "Play ${episode.title}", role = Role.Button, onClick = onPlay)
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%02d".format(rank),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Artwork(url = episode.thumbnailUrl, modifier = Modifier.size(44.dp), shape = MaterialTheme.shapes.small)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = episode.metaLine(includeShow = true),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        PlayDisc(contentDescription = null)
    }
}

@Composable
internal fun CategoryChips(
    selected: PodcastCategory,
    onSelect: (PodcastCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(PodcastCategory.entries, key = { it.name }) { category ->
            val isSelected = category == selected
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else GlassFill)
                    .border(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else GlassBorder, MaterialTheme.shapes.large)
                    .clickable(role = Role.Tab, onClick = { onSelect(category) })
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
internal fun HintCard(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .clip(MaterialTheme.shapes.large)
            .background(GlassFill)
            .border(0.5.dp, GlassBorder, MaterialTheme.shapes.large)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun ErrorBlock(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        NocturneOutlinedButton(text = "Retry", onClick = onRetry, borderColor = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun FullScreenMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
