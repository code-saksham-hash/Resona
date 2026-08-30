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
 *
 * [header] is a separate top-level field InnerTube only populates for some
 * browse pages -- an artist channel's hero (musicImmersiveHeaderRenderer)
 * lives here, verified against a live artist browse response, while a
 * playlist/album page's header renderer lives inside [contents] instead
 * (see [extractArtistHeader] vs. [extractAlbumHeader]). Null wherever
 * InnerTube didn't send one, which [extractSongs]/[extractPlaylistSongs]-style
 * whole-tree walks over [contents] never needed to care about.
 */
@Serializable
data class BrowseResponse(
    val contents: JsonElement? = null,
    val header: JsonElement? = null,
    val responseContext: ResponseContext? = null
)
