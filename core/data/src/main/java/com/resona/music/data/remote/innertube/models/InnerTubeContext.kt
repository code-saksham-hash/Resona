package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.Serializable

/** The `context` block every InnerTube request body carries, identifying the calling client. */
@Serializable
data class InnerTubeContext(
    val client: ClientInfo
)

@Serializable
data class ClientInfo(
    val clientName: String,
    val clientVersion: String,
    val hl: String = "en",
    val gl: String = "US"
)

/**
 * The `responseContext` block present on every InnerTube response. Only
 * [visitorData] is modeled: YouTube reflects back a current visitor token
 * here (URL-encoded), which the shared visitor store learns so playback's
 * player requests never carry a stale identity (see PlayerJsRepository).
 */
@Serializable
data class ResponseContext(
    val visitorData: String? = null
)
