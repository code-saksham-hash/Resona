package com.resona.music.domain.model

/** A [Song] saved for offline playback, and where its audio file lives on disk. */
data class DownloadedSong(
    val song: Song,
    val filePath: String
)
