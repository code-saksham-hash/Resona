package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** An album page's own header exactly as InnerTube described it. */
data class InnerTubeAlbumHeader(
    val title: String,
    val artistName: String,
    /** Lets the album page link back to the artist -- null if InnerTube
     *  didn't attach a channel to the byline (a various-artists compilation). */
    val artistBrowseId: String?,
    val thumbnailUrl: String,
    val year: String
)

/**
 * Walks an album's browse response for its musicResponsiveHeaderRenderer
 * (nested inside [BrowseResponse.contents] here, unlike an artist channel's
 * -- see [BrowseResponse.header]'s kdoc) -- title, cover art, year, and the
 * artist byline (straplineTextOne). Verified against a live album browse
 * response.
 */
fun BrowseResponse.extractAlbumHeader(): InnerTubeAlbumHeader? = contents?.let { findAlbumHeader(it) }

private fun findAlbumHeader(element: JsonElement): InnerTubeAlbumHeader? = when (element) {
    is JsonObject -> {
        val renderer = element["musicResponsiveHeaderRenderer"]?.jsonObject
        renderer?.let(::parseAlbumHeader) ?: element.values.firstNotNullOfOrNull { findAlbumHeader(it) }
    }
    is JsonArray -> element.firstNotNullOfOrNull { findAlbumHeader(it) }
    else -> null
}

private fun parseAlbumHeader(renderer: JsonObject): InnerTubeAlbumHeader? {
    val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

    val artistRun = renderer["straplineTextOne"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
    val artistName = artistRun?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
    val artistBrowseId = artistRun?.get("navigationEndpoint")
        ?.jsonObject?.get("browseEndpoint")
        ?.jsonObject?.get("browseId")?.jsonPrimitive?.contentOrNull

    // subtitle runs are shaped like ["Album", " • ", "2025"] -- the year is
    // whichever run is purely numeric, not fixed by position (a compilation
    // or various-artists album can shift what else appears alongside it).
    val year = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.lastOrNull { it.toIntOrNull() != null } ?: ""

    val thumbnailUrl = renderer["thumbnail"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeAlbumHeader(
        title = title,
        artistName = artistName,
        artistBrowseId = artistBrowseId,
        thumbnailUrl = thumbnailUrl,
        year = year
    )
}

/**
 * Walks an album's browse response for its tracklist. Shaped like
 * [extractPlaylistSongs]'s rows (title/artist in flexColumns, no "Song"
 * label) with one difference verified against a live response: a track's
 * duration lives in its own fixedColumns slot instead of mixed into
 * flexColumns, and there's no per-row thumbnail at all (every row shares the
 * album's own cover -- callers should fill [InnerTubeSong.thumbnailUrl] in
 * themselves from [InnerTubeAlbumHeader.thumbnailUrl]).
 */
fun BrowseResponse.extractAlbumTracks(): List<InnerTubeSong> {
    val results = mutableListOf<InnerTubeSong>()
    contents?.let { walkForAlbumTracks(it, results) }
    return results
}

private fun walkForAlbumTracks(element: JsonElement, results: MutableList<InnerTubeSong>) {
    when (element) {
        is JsonObject -> {
            element["musicResponsiveListItemRenderer"]?.let { renderer ->
                parseAlbumTrack(renderer)?.let(results::add)
            }
            element.values.forEach { walkForAlbumTracks(it, results) }
        }
        is JsonArray -> element.forEach { walkForAlbumTracks(it, results) }
        else -> Unit
    }
}

private fun parseAlbumTrack(renderer: JsonElement): InnerTubeSong? = try {
    parseAlbumTrackOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseAlbumTrackOrThrow(renderer: JsonElement): InnerTubeSong? {
    val obj = renderer.jsonObject
    val flexColumns = obj["flexColumns"]?.jsonArray ?: return null

    val titleRun = flexColumns.getOrNull(0)?.flexColumnRuns()?.firstOrNull()?.jsonObject ?: return null
    val title = titleRun["text"]?.jsonPrimitive?.contentOrNull ?: return null
    val videoId = titleRun["navigationEndpoint"]
        ?.jsonObject?.get("watchEndpoint")
        ?.jsonObject?.get("videoId")
        ?.jsonPrimitive?.contentOrNull ?: return null

    val artist = flexColumns.getOrNull(1)?.flexColumnRuns()
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.firstOrNull { !DURATION_REGEX.matches(it) } ?: ""

    val duration = obj["fixedColumns"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("musicResponsiveListItemFixedColumnRenderer")
        ?.jsonObject?.get("text")?.jsonObject?.get("runs")?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeSong(videoId = videoId, title = title, artist = artist, thumbnailUrl = "", duration = duration)
}
