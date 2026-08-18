package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A real playlist/mix card exactly as InnerTube described it, before mapping
 *  to the domain [FeaturedPlaylist][com.resona.music.domain.model.FeaturedPlaylist]. */
data class InnerTubePlaylist(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String
)

private const val PLAYLIST_PAGE_TYPE = "MUSIC_PAGE_TYPE_PLAYLIST"

/**
 * Walks a browse response (browseId "FEmusic_home") for musicTwoRowItemRenderer
 * nodes -- the card renderer YouTube Music uses for playlist/mix/album
 * carousels on the home feed -- and keeps only the ones that link to a
 * playlist page (as opposed to an album or artist, which use the same
 * renderer shape). Verified against a live browse response.
 */
fun BrowseResponse.extractFeaturedPlaylists(): List<InnerTubePlaylist> {
    val results = mutableListOf<InnerTubePlaylist>()
    contents?.let { walkForTwoRowItems(it, results) }
    return results
}

private fun walkForTwoRowItems(element: JsonElement, results: MutableList<InnerTubePlaylist>) {
    when (element) {
        is JsonObject -> {
            element["musicTwoRowItemRenderer"]?.let { renderer ->
                parseTwoRowPlaylist(renderer)?.let(results::add)
            }
            element.values.forEach { walkForTwoRowItems(it, results) }
        }
        is JsonArray -> element.forEach { walkForTwoRowItems(it, results) }
        else -> Unit
    }
}

private fun parseTwoRowPlaylist(renderer: JsonElement): InnerTubePlaylist? = try {
    parseTwoRowPlaylistOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseTwoRowPlaylistOrThrow(renderer: JsonElement): InnerTubePlaylist? {
    val obj = renderer.jsonObject
    val titleRun = obj["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    val title = titleRun["text"]?.jsonPrimitive?.contentOrNull ?: return null

    val browseEndpoint = titleRun["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject ?: return null
    val pageType = browseEndpoint["browseEndpointContextSupportedConfigs"]
        ?.jsonObject?.get("browseEndpointContextMusicConfig")
        ?.jsonObject?.get("pageType")?.jsonPrimitive?.contentOrNull
    if (pageType != PLAYLIST_PAGE_TYPE) return null
    val browseId = browseEndpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null

    val subtitle = obj["subtitle"]?.jsonObject?.get("runs")?.jsonArray
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString(separator = "") ?: ""

    val thumbnailUrl = obj["thumbnailRenderer"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubePlaylist(browseId = browseId, title = title, subtitle = subtitle, thumbnailUrl = thumbnailUrl)
}

/**
 * Walks a browse response for a single playlist (browseId "VL<playlistId>")
 * for its track list. Reuses [InnerTubeSong]/musicResponsiveListItemRenderer
 * parsing from SearchModels.kt, but *without* gating on a "Song" type label
 * in the subtitle -- unlike a search results page, every row here is already
 * known to be a track, and InnerTube doesn't repeat that label. Verified
 * against a live playlist browse response.
 */
fun BrowseResponse.extractPlaylistSongs(): List<InnerTubeSong> {
    val results = mutableListOf<InnerTubeSong>()
    contents?.let { walkForPlaylistTracks(it, results) }
    return results
}

/**
 * Walks a single playlist's browse response for its own title
 * (musicResponsiveHeaderRenderer.title), for a caller that needs to name a
 * playlist after its source rather than just list its tracks (see
 * MusicRepositoryImpl.importPlaylistFromUrl). Same defensive recursive walk
 * as extractPlaylistSongs, verified against a live playlist browse response.
 */
fun BrowseResponse.extractPlaylistTitle(): String? = contents?.let { findPlaylistTitle(it) }

private fun findPlaylistTitle(element: JsonElement): String? = when (element) {
    is JsonObject -> {
        val ownTitle = element["musicResponsiveHeaderRenderer"]?.jsonObject
            ?.get("title")?.jsonObject?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
        ownTitle ?: element.values.firstNotNullOfOrNull { findPlaylistTitle(it) }
    }
    is JsonArray -> element.firstNotNullOfOrNull { findPlaylistTitle(it) }
    else -> null
}

private fun walkForPlaylistTracks(element: JsonElement, results: MutableList<InnerTubeSong>) {
    when (element) {
        is JsonObject -> {
            element["musicResponsiveListItemRenderer"]?.let { renderer ->
                parsePlaylistTrack(renderer)?.let(results::add)
            }
            element.values.forEach { walkForPlaylistTracks(it, results) }
        }
        is JsonArray -> element.forEach { walkForPlaylistTracks(it, results) }
        else -> Unit
    }
}

private fun parsePlaylistTrack(renderer: JsonElement): InnerTubeSong? = try {
    parsePlaylistTrackOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parsePlaylistTrackOrThrow(renderer: JsonElement): InnerTubeSong? {
    val obj = renderer.jsonObject
    val flexColumns = obj["flexColumns"]?.jsonArray ?: return null

    val titleRun = flexColumns.getOrNull(0)?.flexColumnRuns()?.firstOrNull()?.jsonObject ?: return null
    val title = titleRun["text"]?.jsonPrimitive?.contentOrNull ?: return null
    val videoId = titleRun["navigationEndpoint"]
        ?.jsonObject?.get("watchEndpoint")
        ?.jsonObject?.get("videoId")
        ?.jsonPrimitive?.contentOrNull ?: return null

    val subtitleTexts = flexColumns.getOrNull(1)?.flexColumnRuns()
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.filter { it != BULLET_SEPARATOR }
        ?: emptyList()
    val duration = subtitleTexts.lastOrNull { DURATION_REGEX.matches(it) } ?: ""
    val artist = subtitleTexts.firstOrNull { !DURATION_REGEX.matches(it) } ?: ""

    val thumbnailUrl = obj["thumbnail"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeSong(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        duration = duration
    )
}
