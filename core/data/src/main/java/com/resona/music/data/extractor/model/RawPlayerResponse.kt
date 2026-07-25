package com.resona.music.data.extractor.model

import kotlinx.serialization.Serializable

// Response shape for InnerTube's /player endpoint. Separate from the
// existing single-client PlayerResponse model since the multi-client
// extraction flow needs a couple of fields that one never did.
@Serializable
internal data class RawPlayerResponse(
    val playabilityStatus: RawPlayabilityStatus? = null,
    val streamingData: RawStreamingData? = null,
)

@Serializable
internal data class RawPlayabilityStatus(
    val status: String? = null,
    val reason: String? = null,
)

@Serializable
internal data class RawStreamingData(
    val expiresInSeconds: String? = null,
    val formats: List<RawFormat> = emptyList(),
    val adaptiveFormats: List<RawFormat> = emptyList(),
)

@Serializable
internal data class RawFormat(
    val itag: Int,
    val url: String? = null,
    val signatureCipher: String? = null,
    val mimeType: String,
    val bitrate: Long = 0,
    val audioSampleRate: String? = null,
)
