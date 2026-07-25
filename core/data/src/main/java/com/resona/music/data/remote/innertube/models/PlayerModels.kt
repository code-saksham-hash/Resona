package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class PlayerRequest(
    val context: InnerTubeContext,
    val videoId: String
)

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null
)

@Serializable
data class PlayabilityStatus(
    val status: String? = null,
    val reason: String? = null
)

@Serializable
data class StreamingData(
    val adaptiveFormats: List<AdaptiveFormat> = emptyList()
)

/**
 * One entry from streamingData.adaptiveFormats. Either [url] is a directly
 * playable link, or [signatureCipher]/[cipher] holds a URL-encoded blob
 * whose `s` parameter must be run through YouTube's obfuscated player-JS
 * decipher function before it's usable -- not implemented here (see
 * StreamCipherRequiredException).
 */
@Serializable
data class AdaptiveFormat(
    val itag: Int? = null,
    val mimeType: String? = null,
    val bitrate: Long? = null,
    val audioQuality: String? = null,
    val url: String? = null,
    val signatureCipher: String? = null,
    val cipher: String? = null
)
