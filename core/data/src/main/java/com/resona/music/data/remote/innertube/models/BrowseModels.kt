package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BrowseRequest(
    val context: InnerTubeContext,
    val browseId: String
)

/**
 * Shape varies a lot by what browseId was requested (the home feed's
 * carousels vs. a single playlist's track list use entirely different
 * wrapper renderers), so -- same reasoning as [SearchResponse] -- [contents]
 * is kept as raw JSON and walked by whichever extractor matches what was
 * actually requested (see [extractFeaturedPlaylists], [extractPlaylistSongs]).
 */
@Serializable
data class BrowseResponse(
    val contents: JsonElement? = null
)
