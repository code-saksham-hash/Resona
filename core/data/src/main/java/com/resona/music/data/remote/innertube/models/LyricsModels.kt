package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class NextRequest(
    val context: InnerTubeContext,
    val videoId: String
)

/** Backs the "Up next" watch panel -- [extractLyricsBrowseId] only cares about
 *  one specific tab in it. Same "keep raw, walk what's needed" reasoning as
 *  [SearchResponse]/[BrowseResponse]. */
@Serializable
data class NextResponse(
    val contents: JsonElement? = null
)

private const val LYRICS_TAB_TITLE = "Lyrics"

/**
 * A video's watch-next response lists a fixed set of tabs (Up next, Lyrics,
 * Related); this finds the Lyrics one and returns the browseId that actually
 * fetches its content (a separate request -- see [extractLyricsText]).
 * Verified against a live response.
 */
fun NextResponse.extractLyricsBrowseId(): String? {
    val tabs = contents
        ?.jsonObject?.get("singleColumnMusicWatchNextResultsRenderer")
        ?.jsonObject?.get("tabbedRenderer")
        ?.jsonObject?.get("watchNextTabbedResultsRenderer")
        ?.jsonObject?.get("tabs")
        ?.jsonArray ?: return null

    for (tab in tabs) {
        val tabRenderer = tab.jsonObject["tabRenderer"]?.jsonObject ?: continue
        val title = tabRenderer["title"]?.jsonPrimitive?.contentOrNull
        if (title == LYRICS_TAB_TITLE) {
            return tabRenderer["endpoint"]
                ?.jsonObject?.get("browseEndpoint")
                ?.jsonObject?.get("browseId")
                ?.jsonPrimitive?.contentOrNull
        }
    }
    return null
}

/**
 * Parses a browse response for the Lyrics tab's browseId. The documented
 * success shape (per publicly available InnerTube client references) is a
 * musicDescriptionShelfRenderer with the lyrics text as its description --
 * *not independently verified live in this codebase*, because every track
 * tested against anonymously (several different popular songs, across
 * multiple client identities) came back reporting lyrics as unavailable, a
 * gate InnerTube applies well beyond what search/browse/streaming need.
 * Anything that isn't recognizably that shape -- including the verified
 * "not available" messageRenderer/elementRenderer responses -- is treated as
 * "no lyrics" rather than guessed at, consistent with how [SearchModels.kt]
 * defensively skips shapes it doesn't recognize instead of failing outright.
 */
fun BrowseResponse.extractLyricsText(): String? = try {
    val runs = contents
        ?.jsonObject?.get("sectionListRenderer")
        ?.jsonObject?.get("contents")?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("musicDescriptionShelfRenderer")
        ?.jsonObject?.get("description")
        ?.jsonObject?.get("runs")?.jsonArray

    runs?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString(separator = "")
        ?.ifBlank { null }
} catch (e: Exception) {
    null
}
