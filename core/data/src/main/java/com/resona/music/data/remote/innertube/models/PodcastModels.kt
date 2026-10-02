package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Shapes below were read off live WEB_REMIX responses: a podcasts-filtered
// search, an episodes-filtered search, a show page ("MPSP" + playlist id)
// and that page's episode continuation. Trimmed copies live in
// core/data/src/test/resources/podcasts.

data class InnerTubePodcastShow(
    val browseId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String
)

/** [showTitle]/[showBrowseId] are blank for show page rows, which don't repeat the show. */
data class InnerTubePodcastEpisode(
    val videoId: String,
    val title: String,
    val showTitle: String,
    val showBrowseId: String,
    val thumbnailUrl: String,
    val description: String,
    val publishedText: String,
    val durationText: String
)

data class InnerTubePodcastHeader(
    val title: String,
    val author: String,
    val description: String,
    val thumbnailUrl: String
)

internal const val PODCAST_SHOW_PAGE_TYPE = "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
internal const val PODCAST_EPISODE_PAGE_TYPE = "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE"
private const val EPISODE_BROWSE_PREFIX = "MPED"

/** Rows of a podcasts-filtered search. The row itself links to the show page. */
fun SearchResponse.extractPodcastShows(): List<InnerTubePodcastShow> =
    (continuationContents ?: contents)
        ?.collectAll("musicResponsiveListItemRenderer")
        ?.mapNotNull { runCatching { parseShowRow(it.jsonObject) }.getOrNull() }
        ?.distinctBy { it.browseId }
        .orEmpty()

private fun parseShowRow(row: JsonObject): InnerTubePodcastShow? {
    val endpoint = row["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject ?: return null
    if (endpoint.pageType() != PODCAST_SHOW_PAGE_TYPE) return null
    val browseId = endpoint.string("browseId") ?: return null
    val flexColumns = row["flexColumns"]?.jsonArray ?: return null
    val title = flexColumns.getOrNull(0)?.flexColumnRuns()?.joinedText()?.takeIf { it.isNotBlank() } ?: return null
    val authorRuns = flexColumns.getOrNull(1)?.flexColumnRuns()
    // Usually just the channel run. Prefer it if anything else ever rides along.
    val author = authorRuns?.firstOrNull { it.jsonObject.browseEndpoint() != null }?.text()
        ?: authorRuns?.textsWithoutBullets()?.lastOrNull()
        ?: ""
    return InnerTubePodcastShow(browseId, title.trim(), author.trim(), row.thumbnailUrl())
}

/** Rows of an episodes-filtered search: title, then "date • show". No duration here. */
fun SearchResponse.extractEpisodeResults(): List<InnerTubePodcastEpisode> =
    (continuationContents ?: contents)
        ?.collectAll("musicResponsiveListItemRenderer")
        ?.mapNotNull { runCatching { parseEpisodeResultRow(it.jsonObject) }.getOrNull() }
        ?.distinctBy { it.videoId }
        .orEmpty()

private fun parseEpisodeResultRow(row: JsonObject): InnerTubePodcastEpisode? {
    val flexColumns = row["flexColumns"]?.jsonArray ?: return null
    val titleRuns = flexColumns.getOrNull(0)?.flexColumnRuns() ?: return null
    val titleEndpoint = titleRuns.firstOrNull()?.jsonObject?.browseEndpoint()
    if (titleEndpoint?.pageType() != PODCAST_EPISODE_PAGE_TYPE) return null
    val videoId = row["playlistItemData"]?.jsonObject?.string("videoId")
        ?: titleEndpoint.string("browseId")?.removePrefix(EPISODE_BROWSE_PREFIX)
        ?: return null
    val title = titleRuns.joinedText().takeIf { it.isNotBlank() } ?: return null

    val metaRuns = flexColumns.getOrNull(1)?.flexColumnRuns().orEmpty()
    val showRun = metaRuns.firstOrNull { it.jsonObject.browseEndpoint()?.pageType() == PODCAST_SHOW_PAGE_TYPE }?.jsonObject
    val published = metaRuns.firstOrNull { it.jsonObject["navigationEndpoint"] == null && it.text() != BULLET_SEPARATOR }
        ?.text().orEmpty()

    return InnerTubePodcastEpisode(
        videoId = videoId,
        title = title,
        showTitle = showRun?.text().orEmpty().trim(),
        showBrowseId = showRun?.browseEndpoint()?.string("browseId").orEmpty(),
        thumbnailUrl = row.thumbnailUrl(),
        description = "",
        publishedText = published,
        durationText = ""
    )
}

/** A show page's header: title, author strapline, description and square cover. */
fun BrowseResponse.extractPodcastHeader(): InnerTubePodcastHeader? {
    val header = contents?.collectAll("musicResponsiveHeaderRenderer")?.firstOrNull()?.jsonObject ?: return null
    val title = header["title"]?.runs()?.joinedText()?.takeIf { it.isNotBlank() } ?: return null
    val author = header["straplineTextOne"]?.runs()?.joinedText().orEmpty().trim()
    val description = header["description"]?.collectAll("musicDescriptionShelfRenderer")?.firstOrNull()
        ?.jsonObject?.get("description")?.runs()?.joinedText().orEmpty()
    return InnerTubePodcastHeader(title.trim(), author, description, header.thumbnailUrl())
}

/** Episode rows of a show page, or of its continuation. Newest first, as YouTube orders them. */
fun BrowseResponse.extractShowEpisodes(): List<InnerTubePodcastEpisode> =
    (continuationContents ?: contents)
        ?.collectAll("musicMultiRowListItemRenderer")
        ?.mapNotNull { runCatching { parseShowEpisodeRow(it.jsonObject) }.getOrNull() }
        ?.distinctBy { it.videoId }
        .orEmpty()

private fun parseShowEpisodeRow(row: JsonObject): InnerTubePodcastEpisode? {
    val titleRuns = row["title"]?.runs() ?: return null
    val title = titleRuns.joinedText().takeIf { it.isNotBlank() } ?: return null
    val videoId = row["onTap"]?.jsonObject?.get("watchEndpoint")?.jsonObject?.string("videoId")
        ?: titleRuns.firstOrNull()?.jsonObject?.browseEndpoint()?.string("browseId")?.removePrefix(EPISODE_BROWSE_PREFIX)
        ?: return null

    // "964K views • 4d ago". The date comes last; a lone run could be either.
    val published = row["subtitle"]?.runs()?.textsWithoutBullets()?.lastOrNull()
        ?.takeUnless { it.endsWith("views") || it.endsWith("view") }
        .orEmpty()
    val duration = row["playbackProgress"]?.jsonObject?.get("musicPlaybackProgressRenderer")?.jsonObject
        ?.get("durationText")?.runs()?.textsWithoutBullets()?.firstOrNull()
        .orEmpty()

    return InnerTubePodcastEpisode(
        videoId = videoId,
        title = title,
        showTitle = "",
        showBrowseId = "",
        thumbnailUrl = row.thumbnailUrl(),
        description = row["description"]?.runs()?.joinedText().orEmpty(),
        publishedText = published,
        durationText = duration
    )
}

/** Token for a show's next page of episodes, null once there are no more. */
fun BrowseResponse.extractEpisodesContinuation(): String? =
    (continuationContents ?: contents)?.let { findNextContinuation(it) }

private fun findNextContinuation(element: JsonElement): String? = when (element) {
    is JsonObject -> element["continuations"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("nextContinuationData")
        ?.jsonObject?.string("continuation")
        ?: element.values.firstNotNullOfOrNull { findNextContinuation(it) }
    is JsonArray -> element.firstNotNullOfOrNull { findNextContinuation(it) }
    else -> null
}

private fun JsonElement.collectAll(key: String): List<JsonElement> {
    val found = mutableListOf<JsonElement>()
    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                element[key]?.let(found::add)
                element.values.forEach(::walk)
            }
            is JsonArray -> element.forEach(::walk)
            else -> Unit
        }
    }
    walk(this)
    return found
}

private fun JsonElement.runs(): JsonArray? = (this as? JsonObject)?.get("runs")?.jsonArray

private fun JsonElement.text(): String? = (this as? JsonObject)?.string("text")

private fun JsonArray.joinedText(): String = mapNotNull { it.text() }.joinToString("")

private fun JsonArray.textsWithoutBullets(): List<String> =
    mapNotNull { it.text() }.filter { it != BULLET_SEPARATOR && it.isNotBlank() }

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.browseEndpoint(): JsonObject? =
    this["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject

private fun JsonObject.pageType(): String? =
    this["browseEndpointContextSupportedConfigs"]?.jsonObject
        ?.get("browseEndpointContextMusicConfig")?.jsonObject
        ?.string("pageType")

private fun JsonObject.thumbnailUrl(): String =
    this["thumbnail"]?.jsonObject
        ?.get("musicThumbnailRenderer")?.jsonObject
        ?.get("thumbnail")?.jsonObject
        ?.get("thumbnails")?.jsonArray
        ?.lastOrNull()?.jsonObject
        ?.string("url")
        .orEmpty()
