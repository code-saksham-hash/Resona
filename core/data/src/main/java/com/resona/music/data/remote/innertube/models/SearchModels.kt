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
    val query: String,
    /** Scopes results to one category, e.g. [SEARCH_SONGS_PARAMS] -- the
     *  same mechanism tapping a filter chip in the real app uses. Omitted
     *  (null) for the default mixed-category search. */
    val params: String? = null
)

/** Body for re-requesting a shelf's next page via its continuation token --
 *  no query/params, since the token itself already encodes the original
 *  search (verified live: a continuation request works with nothing else
 *  in the body). */
@Serializable
data class SearchContinuationRequest(
    val context: InnerTubeContext
)

/**
 * YouTube Music's search response nests results inside a chain of generic
 * section/shelf wrapper renderers (itemSectionRenderer, musicShelfRenderer,
 * musicCardShelfRenderer...) whose exact nesting varies by query and region.
 * Modeling every wrapper type isn't worth it, so [contents] is kept as raw
 * JSON and walked by [extractSongs] instead.
 *
 * [continuationContents] is what a *continuation* request (see
 * [SearchContinuationRequest]) hands back instead of [contents] -- a
 * differently-shaped root for the exact same kind of shelf, verified live.
 * [extractFilteredSongs]/[extractSearchContinuation] check both so one
 * extractor works for either an initial page or a later one.
 */
@Serializable
data class SearchResponse(
    val contents: JsonElement? = null,
    val continuationContents: JsonElement? = null,
    val responseContext: ResponseContext? = null
)

/** A song result exactly as InnerTube described it, before mapping to the domain [Song][com.resona.music.domain.model.Song]. */
data class InnerTubeSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val duration: String = ""
)

/** An artist result exactly as InnerTube described it -- unlike a song row,
 *  identified by a real InnerTube channel identity ([browseId]) rather than
 *  just a name string, which is what makes a genuine artist profile page
 *  possible (see ArtistPageModels.kt). */
data class InnerTubeArtist(
    val browseId: String,
    val name: String,
    val thumbnailUrl: String
)

private const val SONG_TYPE_LABEL = "Song"
private const val ARTIST_PAGE_TYPE = "MUSIC_PAGE_TYPE_ARTIST"
internal const val BULLET_SEPARATOR = " • "

// Matches the "m:ss" / "h:mm:ss" shape InnerTube uses for a song's duration
// when it appears as a subtitle run (see parseSongRendererOrThrow). Reused by
// PlaylistModels.kt -- a playlist track row is the same shape minus the
// leading "Song" type label search rows have (every item in a playlist is
// already known to be a song, so InnerTube doesn't bother repeating that).
internal val DURATION_REGEX = Regex("""^\d{1,2}(:\d{2}){1,2}$""")

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

    // Everything after the "Song" label is some mix of artist/album/duration
    // runs, and which ones are present varies by row -- e.g. a bare ["Song",
    // "5:38"] (artist omitted because it matched the search query) is
    // indistinguishable by position from ["Song", "Daft Punk"] (duration
    // omitted instead). Matching duration by shape instead of by index is
    // what tells those apart -- verified against a live search response
    // where both shapes appear side by side for the same query.
    val rest = subtitleTexts.drop(1)
    val duration = rest.lastOrNull { DURATION_REGEX.matches(it) } ?: ""
    val artist = rest.firstOrNull { !DURATION_REGEX.matches(it) } ?: ""

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

/**
 * Walks the raw search response for musicResponsiveListItemRenderer nodes
 * whose *top-level* navigationEndpoint (unlike a song row's, which is empty
 * -- a song's videoId lives nested in its title run instead, see
 * [extractSongs]) points at an artist channel page, *and* for a
 * musicCardShelfRenderer -- the "Top result" hero card InnerTube shows
 * instead for a well-known-enough exact match. Verified live against a
 * search for "Kendrick Lamar": his real artist channel appeared *only* as
 * that top-result card, not as a musicResponsiveListItemRenderer anywhere
 * else in the response, so a parser that only handled the list-row shape
 * (which is all this originally checked) silently found nothing for any
 * artist prominent enough to earn a top result -- i.e. most of them.
 * Keyed on browseEndpointContextMusicConfig.pageType rather than the
 * "Artist" type label text alone since it's unambiguous. distinctBy guards
 * against the (unobserved but plausible) case of the same artist appearing
 * in both places for some query.
 */
fun SearchResponse.extractArtists(): List<InnerTubeArtist> {
    val results = mutableListOf<InnerTubeArtist>()
    contents?.let { walkForArtistRenderers(it, results) }
    return results.distinctBy { it.browseId }
}

private fun walkForArtistRenderers(element: JsonElement, results: MutableList<InnerTubeArtist>) {
    when (element) {
        is JsonObject -> {
            element["musicResponsiveListItemRenderer"]?.let { renderer ->
                parseArtistRenderer(renderer)?.let(results::add)
            }
            element["musicCardShelfRenderer"]?.let { renderer ->
                parseArtistCardRenderer(renderer)?.let(results::add)
            }
            element.values.forEach { walkForArtistRenderers(it, results) }
        }
        is JsonArray -> element.forEach { walkForArtistRenderers(it, results) }
        else -> Unit
    }
}

private fun parseArtistRenderer(renderer: JsonElement): InnerTubeArtist? = try {
    parseArtistRendererOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseArtistRendererOrThrow(renderer: JsonElement): InnerTubeArtist? {
    val obj = renderer.jsonObject
    val browseEndpoint = obj["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject ?: return null
    val pageType = browseEndpoint["browseEndpointContextSupportedConfigs"]
        ?.jsonObject?.get("browseEndpointContextMusicConfig")
        ?.jsonObject?.get("pageType")?.jsonPrimitive?.contentOrNull
    if (pageType != ARTIST_PAGE_TYPE) return null
    val browseId = browseEndpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null

    val flexColumns = obj["flexColumns"]?.jsonArray ?: return null
    val name = flexColumns.getOrNull(0)?.flexColumnRuns()?.firstOrNull()
        ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

    val thumbnailUrl = obj["thumbnail"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeArtist(browseId = browseId, name = name, thumbnailUrl = thumbnailUrl)
}

/**
 * A "Top result" card's title/thumbnail live as direct fields on the card
 * itself, with the navigationEndpoint nested inside the title run -- unlike
 * a list row, where navigationEndpoint sits at the renderer's top level and
 * title lives inside flexColumns. Different shape, same underlying check.
 */
private fun parseArtistCardRenderer(renderer: JsonElement): InnerTubeArtist? = try {
    parseArtistCardRendererOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseArtistCardRendererOrThrow(renderer: JsonElement): InnerTubeArtist? {
    val obj = renderer.jsonObject
    val titleRun = obj["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    val name = titleRun["text"]?.jsonPrimitive?.contentOrNull ?: return null

    val browseEndpoint = titleRun["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject ?: return null
    val pageType = browseEndpoint["browseEndpointContextSupportedConfigs"]
        ?.jsonObject?.get("browseEndpointContextMusicConfig")
        ?.jsonObject?.get("pageType")?.jsonPrimitive?.contentOrNull
    if (pageType != ARTIST_PAGE_TYPE) return null
    val browseId = browseEndpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null

    val thumbnailUrl = obj["thumbnail"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeArtist(browseId = browseId, name = name, thumbnailUrl = thumbnailUrl)
}

internal fun JsonElement.flexColumnRuns(): JsonArray? =
    jsonObject["musicResponsiveListItemFlexColumnRenderer"]
        ?.jsonObject?.get("text")
        ?.jsonObject?.get("runs")
        ?.jsonArray

/**
 * Walks a Songs-filtered search response -- an initial page via [SearchResponse.contents],
 * a later page via [SearchResponse.continuationContents], same row shape either way -- for
 * its song rows. Unlike a mixed/unfiltered search row (see [extractSongs]), a row here
 * carries no "Song" type label: every row in this shelf is already known to be one, the
 * same reasoning [extractPlaylistSongs] documents for playlist tracks (an album name can
 * appear as a middle run alongside artist/duration here too, simply left unmatched by
 * either check below). Verified against a live songs-filtered search response and its
 * continuation.
 */
fun SearchResponse.extractFilteredSongs(): List<InnerTubeSong> {
    val results = mutableListOf<InnerTubeSong>()
    (continuationContents ?: contents)?.let { walkForFilteredSongs(it, results) }
    return results
}

private fun walkForFilteredSongs(element: JsonElement, results: MutableList<InnerTubeSong>) {
    when (element) {
        is JsonObject -> {
            element["musicResponsiveListItemRenderer"]?.let { renderer ->
                parseFilteredSongRenderer(renderer)?.let(results::add)
            }
            element.values.forEach { walkForFilteredSongs(it, results) }
        }
        is JsonArray -> element.forEach { walkForFilteredSongs(it, results) }
        else -> Unit
    }
}

private fun parseFilteredSongRenderer(renderer: JsonElement): InnerTubeSong? = try {
    parseFilteredSongRendererOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseFilteredSongRendererOrThrow(renderer: JsonElement): InnerTubeSong? {
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

    return InnerTubeSong(videoId = videoId, title = title, artist = artist, thumbnailUrl = thumbnailUrl, duration = duration)
}

/**
 * The continuation token for the *next* page of a Songs-filtered search --
 * present alongside both an initial filtered response's shelf and every
 * subsequent continuation response's shelf, verified live for both. Null
 * once the last page is reached (InnerTube simply omits it there).
 */
fun SearchResponse.extractSearchContinuation(): String? {
    val root = continuationContents ?: contents ?: return null
    return findContinuation(root)
}

private fun findContinuation(element: JsonElement): String? = when (element) {
    is JsonObject -> {
        val direct = element["continuations"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("nextContinuationData")
            ?.jsonObject?.get("continuation")?.jsonPrimitive?.contentOrNull
        direct ?: element.values.firstNotNullOfOrNull { findContinuation(it) }
    }
    is JsonArray -> element.firstNotNullOfOrNull { findContinuation(it) }
    else -> null
}
