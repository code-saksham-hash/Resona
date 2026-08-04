package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The similar-songs mix YouTube Music builds for a video. Asking for it via
 * `next()` with playlistId "RDAMVM<videoId>" (see InnerTubeApi.next's
 * playlistId param) makes the watch-next response's "Up next" panel a full
 * radio queue instead of just the tapped track -- verified against a live
 * response. The panel itself lives under the first watch-next tab:
 *
 *   contents...watchNextTabbedResultsRenderer.tabs[0]
 *     .tabRenderer.content.musicQueueRenderer.content
 *     .playlistPanelRenderer.contents[] -> playlistPanelVideoRenderer
 *
 * Every row in that panel is already a playable song (same videoId shape as
 * a search result row), so unlike [SearchResponse.extractSongs] there's no
 * "Song" type label to gate on -- the first panel entry is the tapped track
 * itself, and the rest are the similar-songs radio.
 */
fun NextResponse.extractRadioSongs(): List<InnerTubeSong> {
    val results = mutableListOf<InnerTubeSong>()
    contents?.let { walkForRadioTracks(it, results) }
    return results
}

private fun walkForRadioTracks(element: JsonElement, results: MutableList<InnerTubeSong>) {
    when (element) {
        is JsonObject -> {
            element["playlistPanelVideoRenderer"]?.let { renderer ->
                parseRadioTrack(renderer)?.let(results::add)
            }
            element.values.forEach { walkForRadioTracks(it, results) }
        }
        is JsonArray -> element.forEach { walkForRadioTracks(it, results) }
        else -> Unit
    }
}

// Defensively swallows shape mismatches, same reasoning as the other
// extractors: one malformed row should be skipped, not fail the whole mix.
private fun parseRadioTrack(renderer: JsonElement): InnerTubeSong? = try {
    parseRadioTrackOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseRadioTrackOrThrow(renderer: JsonElement): InnerTubeSong? {
    val obj = renderer.jsonObject

    val title = obj["title"]
        ?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("text")?.jsonPrimitive?.contentOrNull ?: return null
    val videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull ?: return null

    // longBylineText is "Artist • Album • Year" runs; the artist is whatever
    // precedes the first bullet separator, same run shape as elsewhere.
    val artist = obj["longBylineText"]
        ?.jsonObject?.get("runs")?.jsonArray
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.firstOrNull { it != BULLET_SEPARATOR } ?: ""

    val duration = obj["lengthText"]
        ?.jsonObject?.get("runs")?.jsonArray
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString(separator = "") ?: ""

    val thumbnailUrl = obj["thumbnail"]
        ?.jsonObject?.get("thumbnails")?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeSong(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnailUrl,
        duration = duration
    )
}
