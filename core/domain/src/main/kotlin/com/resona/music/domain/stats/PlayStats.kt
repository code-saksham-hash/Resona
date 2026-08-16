package com.resona.music.domain.stats

import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Song
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Aggregated listening statistics for one history.
 *
 * [PlayStats] holds the totals the Stats and History screens show.
 * [computePlayStats] builds it from the history. It is a pure function: it
 * takes the current time as a parameter and never reads the clock.
 */

/**
 * The aggregate result for one history.
 *
 * [listeningSecondsToday], [listeningSecondsThisWeek], and
 * [listeningSecondsAllTime] sum the parsed duration of each play. Legacy
 * entries (playedAtMillis = 0) do not participate.
 *
 * [topArtists] lists artists by play count. Blank artist names do not
 * participate. [topTracks] lists songs by play count. The most recent Song
 * represents each videoId.
 */
data class PlayStats(
    val totalPlays: Int,
    val listeningSecondsToday: Long,
    val listeningSecondsThisWeek: Long,
    val listeningSecondsAllTime: Long,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val topArtists: List<Pair<String, Int>>,
    val topTracks: List<Pair<Song, Int>>,
    val streakDays: Int
)

/**
 * Parses a duration string into seconds.
 *
 * The accepted forms are "M:SS" and "H:MM:SS". Minutes and seconds may not
 * exceed 59. Any part longer than 6 digits is rejected to avoid overflow.
 * Blank and unparseable input returns null.
 */
fun parseDurationSeconds(duration: String): Long? {
    if (duration.isBlank()) return null
    val minutesSeconds = Regex("""^(\d+):(\d{1,2})$""").matchEntire(duration)
    if (minutesSeconds != null) {
        return secondsFrom(minutesSeconds.groupValues[1], minutesSeconds.groupValues[2])
    }
    val hoursMinutesSeconds = Regex("""^(\d+):(\d{1,2}):(\d{1,2})$""").matchEntire(duration)
    if (hoursMinutesSeconds != null) {
        val hours = partsToLong(hoursMinutesSeconds.groupValues[1]) ?: return null
        val minutes = partsToLong(hoursMinutesSeconds.groupValues[2]) ?: return null
        val seconds = partsToLong(hoursMinutesSeconds.groupValues[3]) ?: return null
        if (minutes > 59 || seconds > 59) return null
        return hours * 3600 + minutes * 60 + seconds
    }
    return null
}

/**
 * Computes [PlayStats] from [entries].
 *
 * Today means the same local date as [nowMillis] in [zoneId]. This week
 * means the Monday-start week in [zoneId]. Legacy entries count toward
 * total plays and unique counts, but not toward time buckets or the streak.
 */
fun computePlayStats(
    entries: List<PlayHistoryEntry>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): PlayStats {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val monday = today.with(DayOfWeek.MONDAY)

    val timed = entries.filter { it.playedAtMillis > 0 }
    val timedWithSeconds = timed.map { entry ->
        entry to (parseDurationSeconds(entry.song.duration) ?: 0L)
    }
    val listeningSecondsAllTime = timedWithSeconds.sumOf { it.second }
    val listeningSecondsToday = timedWithSeconds
        .filter { dayOf(it.first, zoneId) == today }
        .sumOf { it.second }
    val listeningSecondsThisWeek = timedWithSeconds
        .filter { !dayOf(it.first, zoneId).isBefore(monday) }
        .sumOf { it.second }

    val uniqueTracks = entries.map { it.song.videoId }.distinct().size
    val uniqueArtists = entries.map { it.song.artist }
        .filter { it.isNotBlank() }
        .distinct()
        .size

    val topArtists = entries.map { it.song.artist }
        .filter { it.isNotBlank() }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .take(5)

    val topTracks = entries.groupBy { it.song.videoId }
        .map { (_, plays) ->
            val latest = plays.maxBy { it.playedAtMillis }.song
            latest to plays.size
        }
        .sortedWith(compareByDescending<Pair<Song, Int>> { it.second }.thenBy { it.first.title })
        .take(5)

    return PlayStats(
        totalPlays = entries.size,
        listeningSecondsToday = listeningSecondsToday,
        listeningSecondsThisWeek = listeningSecondsThisWeek,
        listeningSecondsAllTime = listeningSecondsAllTime,
        uniqueTracks = uniqueTracks,
        uniqueArtists = uniqueArtists,
        topArtists = topArtists,
        topTracks = topTracks,
        streakDays = streakDays(entries, today, zoneId)
    )
}

/**
 * Formats [totalSeconds] for display.
 *
 * The rules are:
 * - below one hour: "Xm" (minutes only). Zero gives "0m".
 * - one hour to below one day: "Xh" when minutes are zero, else "Xh YYm"
 *   with two-digit minutes (example: "2h 05m").
 * - one day and up: "Xd" when hours are zero, else "Xd YYh" with two-digit
 *   hours (example: "3d 02h").
 */
fun formatListeningTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    if (minutes < 60) return "${minutes}m"
    if (totalSeconds < 86_400L) {
        val hours = totalSeconds / 3_600
        val remainderMinutes = (totalSeconds % 3_600) / 60
        if (remainderMinutes == 0L) return "${hours}h"
        return "${hours}h ${String.format(Locale.ROOT, "%02d", remainderMinutes)}m"
    }
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    if (hours == 0L) return "${days}d"
    return "${days}d ${String.format(Locale.ROOT, "%02d", hours)}h"
}

/**
 * Formats [millis] relative to [nowMillis] in [zoneId].
 *
 * The rules are:
 * - under 60 seconds: "Just now".
 * - under 60 minutes: "Xm ago".
 * - under 24 hours: "Xh ago".
 * - the previous calendar day: "Yesterday".
 * - 2 to 29 calendar days: "Xd ago".
 * - 30 days and up: "Xmo ago" with months = floor(dayCount / 30).
 */
fun timeAgo(millis: Long, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
    val diff = nowMillis - millis
    if (diff < 60_000L) return "Just now"
    if (diff < 3_600_000L) return "${diff / 60_000L}m ago"
    if (diff < 86_400_000L) return "${diff / 3_600_000L}h ago"
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val then = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
    val dayDiff = ChronoUnit.DAYS.between(then, now)
    if (dayDiff <= 0L) return "Just now"
    if (dayDiff == 1L) return "Yesterday"
    if (dayDiff in 2..29) return "${dayDiff}d ago"
    return "${dayDiff / 30}mo ago"
}

private fun secondsFrom(minutesPart: String, secondsPart: String): Long? {
    val minutes = partsToLong(minutesPart) ?: return null
    val seconds = partsToLong(secondsPart) ?: return null
    if (seconds > 59) return null
    return minutes * 60 + seconds
}

private fun partsToLong(part: String): Long? {
    if (part.length > 6) return null
    return part.toLongOrNull()
}

private fun dayOf(entry: PlayHistoryEntry, zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(entry.playedAtMillis).atZone(zoneId).toLocalDate()

private fun streakDays(entries: List<PlayHistoryEntry>, today: LocalDate, zoneId: ZoneId): Int {
    val playDays = entries.filter { it.playedAtMillis > 0 }
        .map { dayOf(it, zoneId) }
        .toSet()
    if (playDays.isEmpty()) return 0
    var day = today
    if (day !in playDays) day = day.minusDays(1)
    var count = 0
    while (day in playDays) {
        count++
        day = day.minusDays(1)
    }
    return count
}
