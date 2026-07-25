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
    val androidSdkVersion: Int? = null,
    val osVersion: String? = null,
    val hl: String = "en",
    val gl: String = "US"
)
