package com.resona.music.data.podcasts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class PodcastTimeTest {

    private val now = ZonedDateTime.of(2026, 10, 2, 15, 0, 0, 0, ZoneOffset.UTC)

    private fun millis(value: ZonedDateTime) = value.toInstant().toEpochMilli()
    private fun day(year: Int, month: Int, day: Int) = millis(LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC))

    @Test
    fun `short relative dates`() {
        assertEquals(millis(now.minusDays(4)), PodcastTime.parsePublished("4d ago", now))
        assertEquals(millis(now.minusHours(12)), PodcastTime.parsePublished("12h ago", now))
        assertEquals(millis(now.minusMinutes(5)), PodcastTime.parsePublished("5m ago", now))
        assertEquals(millis(now.minusMonths(3)), PodcastTime.parsePublished("3mo ago", now))
    }

    @Test
    fun `spelled out relative dates`() {
        assertEquals(millis(now.minusDays(2)), PodcastTime.parsePublished("2 days ago", now))
        assertEquals(millis(now.minusWeeks(1)), PodcastTime.parsePublished("1 week ago", now))
        assertEquals(millis(now.minusDays(1)), PodcastTime.parsePublished("Yesterday", now))
    }

    @Test
    fun `month and day means this year`() {
        assertEquals(day(2026, 9, 23), PodcastTime.parsePublished("Sep 23", now))
    }

    @Test
    fun `month and day in the future means last year`() {
        assertEquals(day(2025, 12, 30), PodcastTime.parsePublished("Dec 30", now))
    }

    @Test
    fun `full dates`() {
        assertEquals(day(2021, 11, 30), PodcastTime.parsePublished("Nov 30, 2021", now))
    }

    @Test
    fun `junk does not parse`() {
        assertNull(PodcastTime.parsePublished("", now))
        assertNull(PodcastTime.parsePublished("964K views", now))
        assertNull(PodcastTime.parsePublished("Foo 12", now))
    }

    @Test
    fun durations() {
        assertEquals(55 * 60_000L, PodcastTime.parseDuration("55 min"))
        assertEquals(61 * 60_000L, PodcastTime.parseDuration("1 hr 1 min"))
        assertEquals(2 * 3_600_000L, PodcastTime.parseDuration("2 hr"))
        assertEquals(45_000L, PodcastTime.parseDuration("45 sec"))
        assertEquals(3_723_000L, PodcastTime.parseDuration("1:02:03"))
        assertEquals(272_000L, PodcastTime.parseDuration("4:32"))
        assertEquals(0L, PodcastTime.parseDuration(""))
    }
}
