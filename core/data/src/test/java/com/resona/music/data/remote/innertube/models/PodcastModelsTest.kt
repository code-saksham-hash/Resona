package com.resona.music.data.remote.innertube.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fixtures are trimmed copies of live responses (menus, tracking params and
// most rows stripped, nesting untouched).
class PodcastModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> fixture(name: String): T {
        val text = javaClass.classLoader!!.getResource("podcasts/$name")!!.readText()
        return json.decodeFromString(text)
    }

    @Test
    fun `podcast search rows become shows linking to their show page`() {
        val shows = fixture<SearchResponse>("search_shows.json").extractPodcastShows()

        assertEquals(2, shows.size)
        val first = shows.first()
        assertEquals("MPSPPLdgCQwmpVIWA", first.browseId)
        assertEquals("The Rest Is Science", first.title)
        assertEquals("The Rest Is Science", first.author)
        assertTrue(first.thumbnailUrl.contains("studio_square_thumbnail"))
        assertEquals("Science for Sleep", shows[1].title)
    }

    @Test
    fun `episode search rows carry the date and the show they belong to`() {
        val episodes = fixture<SearchResponse>("search_episodes.json").extractEpisodeResults()

        assertEquals(2, episodes.size)
        val first = episodes.first()
        assertEquals("4I5uY_V4ATo", first.videoId)
        assertEquals("Reacting To Crazy Science Experiments", first.title)
        assertEquals("Nov 30, 2021", first.publishedText)
        assertEquals("Good Mythical Morning with Rhett & Link", first.showTitle)
        assertEquals("MPSPPLJ49NV73ttrvdqf3b1icOXusZabXitci9", first.showBrowseId)
        assertEquals("", first.durationText)
    }

    @Test
    fun `episode search ignores show rows`() {
        val episodes = fixture<SearchResponse>("search_shows.json").extractEpisodeResults()
        assertTrue(episodes.isEmpty())
    }

    @Test
    fun `show page header has title, author, description and cover`() {
        val header = fixture<BrowseResponse>("show_page.json").extractPodcastHeader()

        assertNotNull(header)
        assertEquals("The Rest Is Science", header!!.title)
        assertEquals("The Rest Is Science", header.author)
        assertTrue(header.description.startsWith("Whether you"))
        assertTrue(header.thumbnailUrl.contains("studio_square_thumbnail"))
    }

    @Test
    fun `show page episodes have date, duration and description but no views`() {
        val response = fixture<BrowseResponse>("show_page.json")
        val episodes = response.extractShowEpisodes()

        assertEquals(3, episodes.size)
        val first = episodes.first()
        assertEquals("7sJLgNxGWu8", first.videoId)
        assertEquals("The Importance Of \"Soft Fascination\"", first.title)
        assertEquals("4d ago", first.publishedText)
        assertEquals("55 min", first.durationText)
        assertTrue(first.description.startsWith("What is fire"))
        assertTrue(first.thumbnailUrl.contains("7sJLgNxGWu8"))
        assertEquals("Sep 23", episodes[1].publishedText)
        assertEquals("1 hr 1 min", episodes[1].durationText)

        assertNotNull(response.extractEpisodesContinuation())
    }

    @Test
    fun `continuation page parses the same rows and ends the chain`() {
        val response = fixture<BrowseResponse>("show_page_continuation.json")
        val episodes = response.extractShowEpisodes()

        assertEquals(2, episodes.size)
        assertEquals("Nov 25, 2025", episodes.last().publishedText)
        assertNull(response.extractEpisodesContinuation())
    }
}
