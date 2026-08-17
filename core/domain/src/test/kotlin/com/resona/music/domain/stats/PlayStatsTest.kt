package com.resona.music.domain.stats

import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class PlayStatsTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    // Fixed reference day: 2026-07-15 in UTC.
    private val today: LocalDate = LocalDate.of(2026, 7, 15)
    private val nowMillis: Long = millisOn(today, 12)

    private fun song(
        videoId: String,
        title: String = videoId,
        artist: String = "Artist $videoId",
        duration: String = ""
    ): Song = Song(videoId, title, artist, "https://example.com/$videoId.jpg", duration)

    private fun millisOn(date: LocalDate, hour: Int = 12): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

    private fun entry(
        date: LocalDate,
        videoId: String,
        title: String = videoId,
        artist: String = "Artist $videoId",
        duration: String = ""
    ): PlayHistoryEntry = PlayHistoryEntry(song(videoId, title, artist, duration), millisOn(date))

    // 1. Duration parsing.

    @Test
    fun parseDurationSeconds_handlesBothFormsAndRejectsGarbage() {
        assertEquals(225L, parseDurationSeconds("3:45"))
        assertEquals(3750L, parseDurationSeconds("1:02:30"))
        assertEquals(5L, parseDurationSeconds("0:05"))
        assertNull(parseDurationSeconds(""))
        assertNull(parseDurationSeconds("   "))
        assertNull(parseDurationSeconds("abc"))
        assertNull(parseDurationSeconds("3"))
        assertNull(parseDurationSeconds("1:99"))
        assertNull(parseDurationSeconds("1:02:75"))
    }

    // 2. Time buckets.

    @Test
    fun computePlayStats_bucketsTimeByTodayAndMondayStartWeek() {
        val monday = today.with(DayOfWeek.MONDAY)
        val entries = listOf(
            entry(today, "todayTrack", duration = "1:00:00"),
            entry(today.minusDays(1), "yesterdayTrack", duration = "30:00"),
            entry(monday, "mondayTrack", duration = "20:00"),
            entry(monday.minusDays(1), "lastWeekSunday", duration = "15:00"),
            entry(monday.minusDays(8), "twoWeeksAgo", duration = "10:00")
        )
        val stats = computePlayStats(entries, nowMillis, zone)

        assertEquals(3600L, stats.listeningSecondsToday)
        assertEquals(3600 + 30 * 60 + 20 * 60, stats.listeningSecondsThisWeek)
        assertEquals(3600 + 30 * 60 + 20 * 60 + 15 * 60 + 10 * 60, stats.listeningSecondsAllTime)
        assertEquals(5, stats.totalPlays)
        assertEquals(5, stats.uniqueTracks)
        assertEquals(5, stats.uniqueArtists)
    }

    // 3. Legacy entries.

    @Test
    fun computePlayStats_legacyEntriesCountInTotalsButNotInTimeOrStreak() {
        val legacyA = PlayHistoryEntry(song("legacyTrack", duration = "3:00"), 0L)
        val legacyB = PlayHistoryEntry(song("legacyTrack", duration = "3:00"), 0L)
        val entries = listOf(
            legacyA,
            legacyB,
            entry(today, "modernTrack", duration = "0:10"),
            entry(today.minusDays(1), "otherModern", duration = "0:10")
        )
        val stats = computePlayStats(entries, nowMillis, zone)

        assertEquals(4, stats.totalPlays)
        assertEquals(20L, stats.listeningSecondsAllTime)
        assertEquals(10L, stats.listeningSecondsToday)
        assertEquals(3, stats.uniqueTracks)
        assertEquals(2, stats.streakDays)
    }

    // 4. Top artists and top tracks.

    @Test
    fun computePlayStats_topArtistsOrderByCountThenNameAndSkipBlank() {
        val entries = listOf(
            entry(today, "t1", artist = "Alpha", duration = "0:05"),
            entry(today.minusDays(1), "t2", artist = "Alpha", duration = "0:05"),
            entry(today.minusDays(2), "t3", artist = "Alpha", duration = "0:05"),
            entry(today.minusDays(3), "t4", artist = "Beta", duration = "0:05"),
            entry(today.minusDays(4), "t5", artist = "Beta", duration = "0:05"),
            entry(today.minusDays(5), "t6", artist = "Zed", duration = "0:05"),
            entry(today.minusDays(6), "t7", artist = "Zed", duration = "0:05"),
            entry(today.minusDays(7), "t8", artist = "Gamma", duration = "0:05"),
            entry(today.minusDays(8), "t9", artist = "", duration = "0:05"),
            entry(today.minusDays(9), "t10", artist = "   ", duration = "0:05")
        )
        val stats = computePlayStats(entries, nowMillis, zone)

        assertEquals(4, stats.uniqueArtists)
        // Thumbnail = the artist's most recently played track.
        assertEquals(
            listOf(
                ArtistStat("Alpha", "https://example.com/t1.jpg") to 3,
                ArtistStat("Beta", "https://example.com/t4.jpg") to 2,
                ArtistStat("Zed", "https://example.com/t6.jpg") to 2,
                ArtistStat("Gamma", "https://example.com/t8.jpg") to 1,
            ),
            stats.topArtists
        )
    }

    @Test
    fun computePlayStats_topTracksOrderByCountThenTitleAndKeepLatestSong() {
        val entries = listOf(
            // Two plays of v1 at different times. The later one wins as the
            // representative Song for the videoId.
            entry(today.minusDays(1), "v1", title = "Old Title", duration = "0:05"),
            entry(today, "v1", title = "New Title", duration = "0:05"),
            entry(today, "v2", title = "aaa", duration = "0:05"),
            entry(today, "v2", title = "aaa", duration = "0:05"),
            entry(today, "v3", title = "bbb", duration = "0:05"),
            entry(today, "v3", title = "bbb", duration = "0:05"),
            entry(today, "v4", title = "ccc", duration = "0:05"),
            entry(today, "v5", title = "ddd", duration = "0:05"),
            entry(today, "v6", title = "eee", duration = "0:05")
        )
        val stats = computePlayStats(entries, nowMillis, zone)

        assertEquals(6, stats.uniqueTracks)
        val v1Songs = stats.topTracks.filter { it.first.videoId == "v1" }
        assertEquals(1, v1Songs.size)
        assertEquals("New Title", v1Songs.single().first.title)
        // Count 2 ties are ordered by title asc: "New Title" (v1) before "aaa" (v2).
        assertEquals(
            listOf("New Title", "aaa", "bbb", "ccc", "ddd"),
            stats.topTracks.map { it.first.title }
        )
        assertEquals(5, stats.topTracks.size)
    }

    // 5. Streak.

    @Test
    fun streak_playsTodayYesterdayAndDayBeforeGivesThree() {
        val entries = listOf(
            entry(today, "a", duration = "0:05"),
            entry(today.minusDays(1), "b", duration = "0:05"),
            entry(today.minusDays(2), "c", duration = "0:05")
        )
        assertEquals(3, computePlayStats(entries, nowMillis, zone).streakDays)
    }

    @Test
    fun streak_playTwoDaysAgoWithGapYesterdayGivesOne() {
        val entries = listOf(
            entry(today, "a", duration = "0:05"),
            entry(today.minusDays(2), "b", duration = "0:05")
        )
        assertEquals(1, computePlayStats(entries, nowMillis, zone).streakDays)
    }

    @Test
    fun streak_noPlayTodayButYesterdayAndBeforeGivesTwo() {
        val entries = listOf(
            entry(today.minusDays(1), "a", duration = "0:05"),
            entry(today.minusDays(2), "b", duration = "0:05")
        )
        assertEquals(2, computePlayStats(entries, nowMillis, zone).streakDays)
    }

    @Test
    fun streak_emptyHistoryGivesZero() {
        assertEquals(0, computePlayStats(emptyList(), nowMillis, zone).streakDays)
    }

    @Test
    fun streak_noPlayTodayOrYesterdayGivesZero() {
        val entries = listOf(
            entry(today.minusDays(3), "a", duration = "0:05")
        )
        assertEquals(0, computePlayStats(entries, nowMillis, zone).streakDays)
    }

    @Test
    fun streak_legacyEntriesDoNotCount() {
        val entries = listOf(
            PlayHistoryEntry(song("legacy", duration = "0:05"), 0L),
            entry(today, "modern", duration = "0:05")
        )
        assertEquals(1, computePlayStats(entries, nowMillis, zone).streakDays)
    }

    // 6. Formatting.

    @Test
    fun formatListeningTime_usesDocumentedRules() {
        assertEquals("0m", formatListeningTime(0))
        assertEquals("0m", formatListeningTime(59))
        assertEquals("45m", formatListeningTime(45 * 60))
        assertEquals("1h", formatListeningTime(3600))
        assertEquals("2h 05m", formatListeningTime(2 * 3600 + 5 * 60))
        assertEquals("14h", formatListeningTime(14 * 3600))
        assertEquals("23h 59m", formatListeningTime(23 * 3600 + 59 * 60))
        assertEquals("1d", formatListeningTime(86400))
        assertEquals("3d 02h", formatListeningTime(3 * 86400 + 2 * 3600))
        assertEquals("30d", formatListeningTime(30 * 86400))
    }

    @Test
    fun timeAgo_usesDocumentedThresholds() {
        val now = nowMillis
        assertEquals("Just now", timeAgo(now - 30_000, now, zone))
        assertEquals("Just now", timeAgo(now + 60_000, now, zone))
        assertEquals("45m ago", timeAgo(now - 45 * 60_000, now, zone))
        assertEquals("3h ago", timeAgo(now - 3 * 3_600_000, now, zone))
        // 26 hours earlier lands on the previous calendar day.
        assertEquals("Yesterday", timeAgo(now - 26 * 3_600_000, now, zone))
        // 2 to 29 calendar days ago.
        assertEquals("5d ago", timeAgo(millisOn(today.minusDays(5)), now, zone))
        assertEquals("2d ago", timeAgo(millisOn(today.minusDays(2)), now, zone))
        assertEquals("29d ago", timeAgo(millisOn(today.minusDays(29)), now, zone))
        // 30 days and up use 30-day months.
        assertEquals("1mo ago", timeAgo(millisOn(today.minusDays(30)), now, zone))
        assertEquals("2mo ago", timeAgo(millisOn(today.minusDays(65)), now, zone))
    }
}
