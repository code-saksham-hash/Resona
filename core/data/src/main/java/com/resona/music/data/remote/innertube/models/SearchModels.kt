package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class SearchRequest(
    val context: InnerTubeContext,
    val query: String
)

/**
 * YouTube Music's search response nests results inside a chain of generic
 * section/shelf wrapper renderers (itemSectionRenderer, musicShelfRenderer,
 * musicCardShelfRenderer...) whose exact nesting varies by query and region.
 * Modeling every wrapper type isn't worth it, so [contents] is kept as raw
 * JSON and walked by [extractSongs] instead.
 */
@Serializable
data class SearchResponse(
    val contents: JsonElement? = null
)

/** A song result exactly as InnerTube described it, before mapping to the domain [Song][com.resona.music.domain.model.Song]. */
data class InnerTubeSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String
)

private const val SONG_TYPE_LABEL = "Song"
private const val BULLET_SEPARATOR = " • "

/**
 * Walks the raw search response looking for musicResponsiveListItemRenderer
 * nodes -- the renderer YouTube Music uses for every individual result row,
 * regardless of which shelf wrapper it's nested under -- and keeps only the
 * ones tagged "Song" (as opposed to Video, Artist, Album, Playlist, Podcast,
 * Episode, Profile...). Verified against a live search response rather than
 * assumed from the documented/undocumented schema.
 */
fun SearchResponse.extractSongs(): List<InnerTubeSong> {
    val results = mutableListOf<InnerTubeSong>()
    contents?.let { walkForSongRenderers(it, results) }
    return results
}

private fun walkForSongRenderers(element: JsonElement, results: MutableList<InnerTubeSong>) {
    when (element) {
        is JsonObject -> {
            element["musicResponsiveListItemRenderer"]?.let { renderer ->
                parseSongRenderer(renderer)?.let(results::add)
            }
            element.values.forEach { walkForSongRenderers(it, results) }
        }
        is JsonArray -> element.forEach { walkForSongRenderers(it, results) }
        else -> Unit
    }
}

// Defensively swallows shape mismatches: one malformed/unexpected renderer
// (a shape YouTube changed, a partial entry, an ad slot) should be skipped,
// not fail the entire search.
private fun parseSongRenderer(renderer: JsonElement): InnerTubeSong? = try {
    parseSongRendererOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseSongRendererOrThrow(renderer: JsonElement): InnerTubeSong? {
    val obj = renderer.jsonObject
    val flexColumns = obj["flexColumns"]?.jsonArray ?: return null

    val titleRun = flexColumns.getOrNull(0)?.flexColumnRuns()?.firstOrNull()?.jsonObject ?: return null
    val title = titleRun["text"]?.jsonPrimitive?.contentOrNull ?: return null
    val videoId = titleRun["navigationEndpoint"]
        ?.jsonObject?.get("watchEndpoint")
        ?.jsonObject?.get("videoId")
        ?.jsonPrimitive?.contentOrNull ?: return null

    val subtitleRuns = flexColumns.getOrNull(1)?.flexColumnRuns() ?: return null
    val subtitleTexts = subtitleRuns
        .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        .filter { it != BULLET_SEPARATOR }
    if (subtitleTexts.firstOrNull() != SONG_TYPE_LABEL) return null
    val artist = subtitleTexts.getOrNull(1) ?: return null

    val thumbnailUrl = obj["thumbnail"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeSong(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl)
}

private fun JsonElement.flexColumnRuns(): JsonArray? =
    jsonObject["musicResponsiveListItemFlexColumnRenderer"]
        ?.jsonObject?.get("text")
        ?.jsonObject?.get("runs")
        ?.jsonArray
