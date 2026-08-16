package com.resona.music.domain.model

/**
 * One entry in the on-device play history.
 *
 * [playedAtMillis] is the epoch-millis time the play started. A value of 0
 * means the record predates timestamps (legacy). Legacy records still count
 * toward totals, but not toward listening time or the streak.
 */
data class PlayHistoryEntry(
    val song: Song,
    val playedAtMillis: Long
)
