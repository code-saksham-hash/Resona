package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val ALBUM_PAGE_TYPE = "MUSIC_PAGE_TYPE_ALBUM"

/** An artist channel's hero header exactly as InnerTube described it. */
data class InnerTubeArtistHeader(
    val name: String,
    val thumbnailUrl: String,
    val description: String,
    /** "20.5M subscribers" or "6.65M monthly audience" -- whichever
     *  InnerTube reported for this channel, already unit-suffixed. Blank if
     *  neither was present. */
    val listenerCountText: String
)

/** An album/single card exactly as InnerTube described it -- an artist
 *  channel's Albums/Singles & EPs shelves. */
data class InnerTubeAlbumSummary(
    val browseId: String,
    val title: String,
    val thumbnailUrl: String,
    val year: String
)

/**
 * Walks an artist channel's browse response for its top-level
 * musicImmersiveHeaderRenderer (see [BrowseResponse.header]'s kdoc) -- name,
 * hero image, bio, and subscriber/monthly-listener count. Verified against
 * a live artist browse response.
 */
fun BrowseResponse.extractArtistHeader(): InnerTubeArtistHeader? {
    val renderer = header?.jsonObject?.get("musicImmersiveHeaderRenderer")?.jsonObject ?: return null
    val name = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: return null

    val thumbnailUrl = renderer["thumbnail"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    val description = renderer["description"]?.jsonObject?.get("runs")?.jsonArray
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString(separator = "") ?: ""

    // subscriberCountText's own run is a bare number ("20.5M") -- unlike
    // monthlyListenerCount's, which is already a full self-contained phrase
    // ("6.65M monthly audience") -- so only the first needs a suffix added.
    // Falls back to the second for a channel with subscriptions hidden/off.
    val subscriberCount = renderer["subscriptionButton"]
        ?.jsonObject?.get("subscribeButtonRenderer")
        ?.jsonObject?.get("subscriberCountText")
        ?.jsonObject?.get("runs")?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
    val listenerCountText = if (subscriberCount != null) {
        "$subscriberCount subscribers"
    } else {
        renderer["monthlyListenerCount"]?.jsonObject?.get("runs")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
    }

    return InnerTubeArtistHeader(
        name = name,
        thumbnailUrl = thumbnailUrl,
        description = description,
        listenerCountText = listenerCountText
    )
}

/**
 * Walks an artist channel's browse response for the musicCarouselShelfRenderer
 * whose own header title is exactly [shelfTitle] (e.g. "Albums" or "Singles &
 * EPs" -- an artist page carries both as separate shelves, plus others this
 * intentionally ignores, like "Featured on", which link to albums too but
 * aren't this artist's own discography) and parses its musicTwoRowItemRenderer
 * cards. Verified against a live artist browse response.
 */
fun BrowseResponse.extractArtistDiscographyShelf(shelfTitle: String): List<InnerTubeAlbumSummary> {
    val results = mutableListOf<InnerTubeAlbumSummary>()
    contents?.let { walkForDiscographyShelf(it, shelfTitle, results) }
    return results
}

private fun walkForDiscographyShelf(element: JsonElement, shelfTitle: String, results: MutableList<InnerTubeAlbumSummary>) {
    when (element) {
        is JsonObject -> {
            val carousel = element["musicCarouselShelfRenderer"]?.jsonObject
            val title = carousel?.get("header")
                ?.jsonObject?.get("musicCarouselShelfBasicHeaderRenderer")
                ?.jsonObject?.get("title")
                ?.jsonObject?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            if (carousel != null && title == shelfTitle) {
                carousel["contents"]?.jsonArray?.forEach { item ->
                    item.jsonObject["musicTwoRowItemRenderer"]?.let { renderer ->
                        parseTwoRowAlbum(renderer)?.let(results::add)
                    }
                }
            }
            element.values.forEach { walkForDiscographyShelf(it, shelfTitle, results) }
        }
        is JsonArray -> element.forEach { walkForDiscographyShelf(it, shelfTitle, results) }
        else -> Unit
    }
}

private fun parseTwoRowAlbum(renderer: JsonElement): InnerTubeAlbumSummary? = try {
    parseTwoRowAlbumOrThrow(renderer)
} catch (e: Exception) {
    null
}

private fun parseTwoRowAlbumOrThrow(renderer: JsonElement): InnerTubeAlbumSummary? {
    val obj = renderer.jsonObject
    val titleRun = obj["title"]?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    val title = titleRun["text"]?.jsonPrimitive?.contentOrNull ?: return null

    val browseEndpoint = obj["navigationEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject ?: return null
    val pageType = browseEndpoint["browseEndpointContextSupportedConfigs"]
        ?.jsonObject?.get("browseEndpointContextMusicConfig")
        ?.jsonObject?.get("pageType")?.jsonPrimitive?.contentOrNull
    if (pageType != ALBUM_PAGE_TYPE) return null
    val browseId = browseEndpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null

    val year = obj["subtitle"]?.jsonObject?.get("runs")?.jsonArray
        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""

    val thumbnailUrl = obj["thumbnailRenderer"]
        ?.jsonObject?.get("musicThumbnailRenderer")
        ?.jsonObject?.get("thumbnail")
        ?.jsonObject?.get("thumbnails")
        ?.jsonArray?.lastOrNull()
        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

    return InnerTubeAlbumSummary(browseId = browseId, title = title, thumbnailUrl = thumbnailUrl, year = year)
}
