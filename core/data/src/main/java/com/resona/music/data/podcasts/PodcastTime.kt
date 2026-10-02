package com.resona.music.data.podcasts

import java.time.LocalDate
import java.time.ZonedDateTime

/** Turns YouTube's human dates and durations into numbers we can sort and compare. */
internal object PodcastTime {

    // "4d ago", "12h ago", "3mo ago", and the spelled out "2 days ago" too.
    private val relative = Regex(
        """^(\d+)\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|wks?|w|months?|mos?|years?|yrs?|y)\s+ago$""",
        RegexOption.IGNORE_CASE
    )

    // "Sep 23" this year, or "Nov 30, 2021".
    private val absolute = Regex("""^([A-Za-z]{3})[A-Za-z]*\.?\s+(\d{1,2})(?:,\s*(\d{4}))?$""")

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun parsePublished(text: String, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val trimmed = text.trim()
        when (trimmed.lowercase()) {
            "" -> return null
            "today", "just now" -> return now.toInstant().toEpochMilli()
            "yesterday" -> return now.minusDays(1).toInstant().toEpochMilli()
        }

        relative.matchEntire(trimmed)?.let { match ->
            val amount = match.groupValues[1].toLong()
            val unit = match.groupValues[2].lowercase()
            val then = when {
                unit.startsWith("mo") -> now.minusMonths(amount)
                unit.startsWith("s") -> now.minusSeconds(amount)
                unit.startsWith("m") -> now.minusMinutes(amount)
                unit.startsWith("h") -> now.minusHours(amount)
                unit.startsWith("d") -> now.minusDays(amount)
                unit.startsWith("w") -> now.minusWeeks(amount)
                else -> now.minusYears(amount)
            }
            return then.toInstant().toEpochMilli()
        }

        absolute.matchEntire(trimmed)?.let { match ->
            val month = months.indexOf(match.groupValues[1].lowercase()) + 1
            if (month == 0) return null
            val day = match.groupValues[2].toInt()
            val year = match.groupValues[3].toIntOrNull()
            var date = runCatching { LocalDate.of(year ?: now.year, month, day) }.getOrNull() ?: return null
            // No year means this year, unless that lands in the future ("Dec 30" read in January).
            if (year == null && date.isAfter(now.toLocalDate())) date = date.minusYears(1)
            return date.atStartOfDay(now.zone).toInstant().toEpochMilli()
        }
        return null
    }

    private val clock = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""")
    private val part = Regex(
        """(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\b""",
        RegexOption.IGNORE_CASE
    )

    /** "55 min", "1 hr 1 min", "45 sec", or a clock style "1:02:03". 0 if it doesn't parse. */
    fun parseDuration(text: String): Long {
        val trimmed = text.trim()
        clock.matchEntire(trimmed)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            return ((hours * 60 + minutes) * 60 + seconds) * 1000
        }
        var seconds = 0L
        part.findAll(trimmed).forEach { match ->
            val amount = match.groupValues[1].toLong()
            val unit = match.groupValues[2].lowercase()
            seconds += when {
                unit.startsWith("h") -> amount * 3600
                unit.startsWith("m") -> amount * 60
                else -> amount
            }
        }
        return seconds * 1000
    }
}
