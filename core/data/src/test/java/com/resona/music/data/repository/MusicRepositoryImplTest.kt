package com.resona.music.data.repository

import com.resona.music.data.download.CachedLyrics
import com.resona.music.data.download.DownloadedSongsStore
import com.resona.music.data.download.SongDownloader
import com.resona.music.data.extractor.InnerTubeExtractionClient
import com.resona.music.data.extractor.JsEngine
import com.resona.music.data.extractor.YouTubeStreamExtractor
import com.resona.music.data.history.PlayHistoryStore
import com.resona.music.data.history.SearchHistoryStore
import com.resona.music.data.likes.LikedSongsStore
import com.resona.music.data.playlists.UserPlaylistsStore
import com.resona.music.data.extractor.decipher.DecipherService
import com.resona.music.data.extractor.decipher.NParamDecipherer
import com.resona.music.data.extractor.decipher.PlayerJsRepository
import com.resona.music.data.extractor.decipher.SignatureDecipherer
import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.LyricsLine
import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Playlist
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.StreamSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class MusicRepositoryImplTest {

    // Trimmed to the exact nesting InnerTube uses for search results
    // (verified against a live response): one Song, one Video, and one
    // Artist result, to confirm only the Song entry survives.
    private val fakeSearchResponseJson = """
    {
      "contents": {
        "tabbedSearchResultsRenderer": {
          "tabs": [
            {
              "tabRenderer": {
                "content": {
                  "sectionListRenderer": {
                    "contents": [
                      {
                        "itemSectionRenderer": {
                          "contents": [
                            {
                              "musicResponsiveListItemRenderer": {
                                "thumbnail": {
                                  "musicThumbnailRenderer": {
                                    "thumbnail": {
                                      "thumbnails": [
                                        { "url": "https://example.com/small.jpg", "width": 60, "height": 60 },
                                        { "url": "https://example.com/large.jpg", "width": 120, "height": 120 }
                                      ]
                                    }
                                  }
                                },
                                "flexColumns": [
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": {
                                        "runs": [
                                          {
                                            "text": "Test Song Title",
                                            "navigationEndpoint": {
                                              "watchEndpoint": { "videoId": "abc123XYZ" }
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  },
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": {
                                        "runs": [
                                          { "text": "Song" },
                                          { "text": " • " },
                                          { "text": "Test Artist" }
                                        ]
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      },
                      {
                        "itemSectionRenderer": {
                          "contents": [
                            {
                              "musicResponsiveListItemRenderer": {
                                "flexColumns": [
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": {
                                        "runs": [
                                          {
                                            "text": "Some Video",
                                            "navigationEndpoint": { "watchEndpoint": { "videoId": "videoOnly1" } }
                                          }
                                        ]
                                      }
                                    }
                                  },
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": {
                                        "runs": [
                                          { "text": "Video" },
                                          { "text": " • " },
                                          { "text": "Some Channel" },
                                          { "text": " • " },
                                          { "text": "1M views" }
                                        ]
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      },
                      {
                        "itemSectionRenderer": {
                          "contents": [
                            {
                              "musicResponsiveListItemRenderer": {
                                "flexColumns": [
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": { "runs": [ { "text": "Some Artist" } ] }
                                    }
                                  },
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": {
                                        "runs": [
                                          { "text": "Artist" },
                                          { "text": " • " },
                                          { "text": "1.2M subscribers" }
                                        ]
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
              }
            }
          ]
        }
      }
    }
    """.trimIndent()

    private fun repositoryWithMockedSearchResponse(
        json: String,
        recentPlays: List<Song> = emptyList()
    ): MusicRepositoryImpl {
        val mockEngine = MockEngine {
            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return repositoryWithHttpClient(
            HttpClient(mockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            recentPlays,
        )
    }

    /** Everything around the HTTP client is the same whichever way the
     *  responses are canned, so both helpers above share this. */
    private fun repositoryWithHttpClient(
        httpClient: HttpClient,
        recentPlays: List<Song> = emptyList()
    ): MusicRepositoryImpl {
        // just needs to be constructible here, so a no-op JsEngine fake is
        // fine -- the real WebView-backed one needs a live Context this
        // plain JVM test doesn't have
        val jsEngine = object : JsEngine {
            override suspend fun execute(functionCode: String, argument: String) = null
            override suspend fun executeWithPlayerJs(playerJs: String, discoveryScript: String) = null
        }
        val streamExtractor = YouTubeStreamExtractor(
            client = InnerTubeExtractionClient(httpClient),
            playerJsRepo = PlayerJsRepository(httpClient),
            decipherService = DecipherService(
                playerJsRepo = PlayerJsRepository(httpClient),
                nParamDecipherer = NParamDecipherer(jsEngine),
                signatureDecipherer = SignatureDecipherer(jsEngine),
            ),
        )
        // Neither is exercised by the search-focused tests below -- just
        // needs to be constructible here, same reasoning as the JsEngine
        // fake above (the real implementations need a live Context this
        // plain JVM test doesn't have).
        val songDownloader = object : SongDownloader {
            override suspend fun download(
                song: Song,
                streamSource: StreamSource,
                onProgress: (Float) -> Unit,
            ) = File("/unused")
        }
        val downloadedSongsStore = object : DownloadedSongsStore {
            override val downloads = MutableStateFlow(emptyList<DownloadedSong>())
            override fun filePathFor(videoId: String): String? = null
            override suspend fun markDownloaded(song: Song, filePath: String) = Unit
            override suspend fun remove(videoId: String) = Unit
            override fun cachedLyrics(videoId: String): CachedLyrics? = null
            override suspend fun cacheLyrics(videoId: String, plain: String?, synced: List<LyricsLine>?) = Unit
        }
        val likedSongsStore = object : LikedSongsStore {
            override val likedSongs = MutableStateFlow(emptyList<Song>())
            override fun isLiked(videoId: String) = false
            override suspend fun toggle(song: Song) = Unit
        }
        val playHistoryStore = object : PlayHistoryStore {
            override val entries = MutableStateFlow(recentPlays.map { PlayHistoryEntry(song = it, playedAtMillis = 0L) })
            override suspend fun recordPlay(song: Song) = Unit
        }
        val userPlaylistsStore = object : UserPlaylistsStore {
            override val playlists = MutableStateFlow(emptyList<Playlist>())
            override suspend fun createPlaylist(name: String, songs: List<Song>) = Playlist(
                id = "playlist_test",
                name = name,
                createdAtMillis = 0L,
                songs = songs
            )
            override suspend fun addSongToPlaylist(playlistId: String, song: Song) = Unit
            override suspend fun removeSongFromPlaylist(playlistId: String, videoId: String) = Unit
            override suspend fun deletePlaylist(playlistId: String) = Unit
        }
        val searchHistoryStore = object : SearchHistoryStore {
            override val recentSearches = MutableStateFlow(emptyList<String>())
            override suspend fun record(query: String) = Unit
            override suspend fun remove(query: String) = Unit
            override suspend fun clear() = Unit
        }
        return MusicRepositoryImpl(
            InnerTubeApi(httpClient, PlayerJsRepository(httpClient)),
            streamExtractor,
            songDownloader,
            downloadedSongsStore,
            likedSongsStore,
            playHistoryStore,
            userPlaylistsStore,
            searchHistoryStore,
            httpClient
        )
    }

    @Test
    fun searchReturnsOnlySongResultsWithCorrectFields() = runTest {
        val repository = repositoryWithMockedSearchResponse(fakeSearchResponseJson)

        val songs = repository.search("test query")

        assertEquals(1, songs.size)
        val song = songs.first()
        assertEquals("abc123XYZ", song.videoId)
        assertEquals("Test Song Title", song.title)
        assertEquals("Test Artist", song.artist)
        assertEquals("https://example.com/large.jpg", song.thumbnailUrl)
        assertEquals("", song.duration)
    }

    // Reproduces a shape seen in a live search response for a query that
    // names the artist directly ("daft punk"): InnerTube omits the artist
    // run entirely and leaves only ["Song", "5:38"], which the old
    // position-based parser (subtitleTexts.getOrNull(1)) mislabeled as the
    // artist name instead of recognizing it as the duration.
    private val songWithDurationButNoArtistJson = """
    {
      "contents": {
        "tabbedSearchResultsRenderer": {
          "tabs": [
            {
              "tabRenderer": {
                "content": {
                  "sectionListRenderer": {
                    "contents": [
                      {
                        "itemSectionRenderer": {
                          "contents": [
                            {
                              "musicResponsiveListItemRenderer": {
                                "flexColumns": [
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": {
                                        "runs": [
                                          {
                                            "text": "Instant Crush",
                                            "navigationEndpoint": {
                                              "watchEndpoint": { "videoId": "xyz789" }
                                            }
                                          }
                                        ]
                                      }
                                    }
                                  },
                                  {
                                    "musicResponsiveListItemFlexColumnRenderer": {
                                      "text": {
                                        "runs": [
                                          { "text": "Song" },
                                          { "text": " • " },
                                          { "text": "5:38" }
                                        ]
                                      }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
              }
            }
          ]
        }
      }
    }
    """.trimIndent()

    @Test
    fun searchDoesNotMistakeDurationForArtistWhenArtistIsOmitted() = runTest {
        val repository = repositoryWithMockedSearchResponse(songWithDurationButNoArtistJson)

        val songs = repository.search("daft punk")

        assertEquals(1, songs.size)
        val song = songs.first()
        assertEquals("xyz789", song.videoId)
        assertEquals("Instant Crush", song.title)
        assertEquals("5:38", song.duration)
        assertEquals("", song.artist)
    }

    @Test
    fun getHomeFeedFillsInRecommendedArtistOmittedByInnerTube() = runTest {
        // One artist dominates recentPlays, so recommendedQueryFor has a
        // single candidate ("Daft Punk radio") -- no random() involved, so
        // this is deterministic. The mocked response is reused for every
        // section's search (MockEngine ignores the request), so trending/new
        // get the exact same blank-artist row back with no hint to fix it.
        val repository = repositoryWithMockedSearchResponse(
            json = songWithDurationButNoArtistJson,
            recentPlays = listOf(Song("prior1", "One More Time", "Daft Punk", ""))
        )

        val feed = repository.getHomeFeed()

        val recommended = feed.sections.first { it.id == "recommended" }.songs.first()
        assertEquals("Daft Punk", recommended.artist)
        val trending = feed.sections.first { it.id == "trending" }.songs.first()
        assertEquals("", trending.artist)
    }

    // Shape trimmed from a live next() response fetched with
    // playlistId="RDAMVM<videoId>" -- the "Up next" panel whose entries are
    // the similar-songs radio mix. The tapped track is the first entry.
    private val fakeRadioNextResponseJson = """
    {
      "contents": {
        "singleColumnMusicWatchNextResultsRenderer": {
          "tabbedRenderer": {
            "watchNextTabbedResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "content": {
                      "musicQueueRenderer": {
                        "content": {
                          "playlistPanelRenderer": {
                            "contents": [
                              {
                                "playlistPanelVideoRenderer": {
                                  "title": { "runs": [ { "text": "As It Was" } ] },
                                  "videoId": "nujn6wbr-e8",
                                  "longBylineText": { "runs": [ { "text": "Harry Styles" } ] },
                                  "thumbnail": { "thumbnails": [ { "url": "https://example.com/radio1.jpg" } ] },
                                  "lengthText": { "runs": [ { "text": "2:48" } ] }
                                }
                              },
                              {
                                "playlistPanelVideoRenderer": {
                                  "title": { "runs": [ { "text": "Watermelon Sugar" } ] },
                                  "videoId": "KPM_BYl-EaQ",
                                  "longBylineText": { "runs": [ { "text": "Harry Styles" } ] },
                                  "thumbnail": { "thumbnails": [ { "url": "https://example.com/radio2.jpg" } ] },
                                  "lengthText": { "runs": [ { "text": "2:54" } ] }
                                }
                              },
                              {
                                "playlistPanelVideoRenderer": {
                                  "title": { "runs": [ { "text": "Viva La Vida" } ] },
                                  "videoId": "ALsvdSA9tOU",
                                  "longBylineText": { "runs": [ { "text": "Coldplay" } ] },
                                  "thumbnail": { "thumbnails": [ { "url": "https://example.com/radio3.jpg" } ] },
                                  "lengthText": { "runs": [ { "text": "4:01" } ] }
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  }
                }
              ]
            }
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun getSongRadioReturnsSimilarSongsFromTheUpNextPanel() = runTest {
        val repository = repositoryWithMockedSearchResponse(fakeRadioNextResponseJson)

        val radio = repository.getSongRadio("nujn6wbr-e8")

        assertEquals(3, radio.size)
        // First entry is the tapped track itself, matching what the panel
        // actually returns.
        assertEquals("nujn6wbr-e8", radio[0].videoId)
        assertEquals("As It Was", radio[0].title)
        assertEquals("Harry Styles", radio[0].artist)
        assertEquals("https://example.com/radio1.jpg", radio[0].thumbnailUrl)
        assertEquals("2:48", radio[0].duration)
        assertEquals("KPM_BYl-EaQ", radio[1].videoId)
        assertEquals("ALsvdSA9tOU", radio[2].videoId)
    }

    // Shape trimmed from a live browse("VL<playlistId>") response (fetched
    // directly against InnerTube to confirm this, since the app had no
    // playlist-title parsing to verify it against before): the header
    // renderer carrying the playlist's own title lives under
    // twoColumnBrowseResultsRenderer.tabs[...], as a sibling of
    // secondaryContents.sectionListRenderer...musicPlaylistShelfRenderer.contents[],
    // which is where the actual track rows live.
    private val fakePlaylistBrowseResponseJson = """
    {
      "contents": {
        "twoColumnBrowseResultsRenderer": {
          "tabs": [
            {
              "tabRenderer": {
                "content": {
                  "sectionListRenderer": {
                    "contents": [
                      {
                        "musicResponsiveHeaderRenderer": {
                          "title": { "runs": [ { "text": "My Imported Mix" } ] }
                        }
                      }
                    ]
                  }
                }
              }
            }
          ],
          "secondaryContents": {
            "sectionListRenderer": {
              "contents": [
                {
                  "musicPlaylistShelfRenderer": {
                    "contents": [
                      {
                        "musicResponsiveListItemRenderer": {
                          "flexColumns": [
                            {
                              "musicResponsiveListItemFlexColumnRenderer": {
                                "text": {
                                  "runs": [
                                    {
                                      "text": "Imported Track",
                                      "navigationEndpoint": { "watchEndpoint": { "videoId": "impVid1" } }
                                    }
                                  ]
                                }
                              }
                            },
                            {
                              "musicResponsiveListItemFlexColumnRenderer": {
                                "text": {
                                  "runs": [
                                    { "text": "Imported Artist" },
                                    { "text": " • " },
                                    { "text": "3:33" }
                                  ]
                                }
                              }
                            }
                          ]
                        }
                      }
                    ]
                  }
                }
              ]
            }
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun importPlaylistFromUrlCreatesALocalPlaylistNamedAndPopulatedFromTheSource() = runTest {
        val repository = repositoryWithMockedSearchResponse(fakePlaylistBrowseResponseJson)

        val playlist = repository.importPlaylistFromUrl(
            "https://music.youtube.com/watch?v=xyz&list=PLOHoVaTp8R7dWeCQrKfh7a1a_Gu6KvfWP"
        )

        assertEquals("My Imported Mix", playlist.name)
        assertEquals(1, playlist.songs.size)
        assertEquals("impVid1", playlist.songs.first().videoId)
        assertEquals("Imported Track", playlist.songs.first().title)
        assertEquals("Imported Artist", playlist.songs.first().artist)
        assertEquals("3:33", playlist.songs.first().duration)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importPlaylistFromUrlRejectsAUrlWithNoRecognizablePlaylistId() = runTest {
        val repository = repositoryWithMockedSearchResponse(fakePlaylistBrowseResponseJson)

        repository.importPlaylistFromUrl("https://example.com/not-a-youtube-link")
    }

    // Shape verified live: an inaccessible browseId (private, requires
    // signing in, deleted, or just malformed) comes back HTTP 200 but with
    // no "contents" key at all, rather than a clean 4xx.
    private val fakeUnavailablePlaylistBrowseResponseJson = """
    { "responseContext": {}, "trackingParams": "unused" }
    """.trimIndent()

    @Test(expected = IllegalStateException::class)
    fun importPlaylistFromUrlThrowsWhenThePlaylistHasNoAccessibleTracks() = runTest {
        val repository = repositoryWithMockedSearchResponse(fakeUnavailablePlaylistBrowseResponseJson)

        repository.importPlaylistFromUrl("https://www.youtube.com/playlist?list=PLdoesnotexist12345")
    }

    // One row of a category-filtered shelf: same renderer the live Songs and
    // Videos filters both return, which (unlike a mixed-search row) carries
    // no "Song"/"Video" type label. [detail] is where a song row puts its
    // album and a video row puts its view count.
    private fun filteredRowJson(title: String, videoId: String, artist: String, detail: String) = """
    {
      "musicResponsiveListItemRenderer": {
        "thumbnail": {
          "musicThumbnailRenderer": {
            "thumbnail": { "thumbnails": [ { "url": "https://example.com/$videoId.jpg" } ] }
          }
        },
        "flexColumns": [
          {
            "musicResponsiveListItemFlexColumnRenderer": {
              "text": {
                "runs": [
                  {
                    "text": "$title",
                    "navigationEndpoint": { "watchEndpoint": { "videoId": "$videoId" } }
                  }
                ]
              }
            }
          },
          {
            "musicResponsiveListItemFlexColumnRenderer": {
              "text": {
                "runs": [
                  { "text": "$artist" },
                  { "text": " • " },
                  { "text": "$detail" },
                  { "text": " • " },
                  { "text": "4:34" }
                ]
              }
            }
          }
        ]
      }
    }
    """.trimIndent()

    // The nesting a real filtered search response wraps its shelf in,
    // verified against a live one (contents/tabbedSearchResultsRenderer/
    // tabs[0]/tabRenderer/content/sectionListRenderer/contents[0]/
    // musicShelfRenderer/contents).
    private fun filteredShelfJson(rows: List<String>) = """
    {
      "contents": {
        "tabbedSearchResultsRenderer": {
          "tabs": [
            {
              "tabRenderer": {
                "content": {
                  "sectionListRenderer": {
                    "contents": [
                      { "musicShelfRenderer": { "contents": [ ${rows.joinToString(",")} ] } }
                    ]
                  }
                }
              }
            }
          ]
        }
      }
    }
    """.trimIndent()

    /**
     * searchSongsPage fires a Songs-filtered and a Videos-filtered request
     * for the same query, so unlike [repositoryWithMockedSearchResponse] (one
     * canned body for every request) this has to answer them differently. It
     * tells them apart by the params value in the serialized request body,
     * which is the only thing that differs between the two.
     */
    private fun repositoryWithSeparateSongAndVideoShelves(
        songsJson: String,
        videosJson: String,
    ): MusicRepositoryImpl {
        val mockEngine = MockEngine { request ->
            val body = (request.body as TextContent).text
            respond(
                content = if (VIDEOS_PARAMS in body) videosJson else songsJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return repositoryWithHttpClient(
            HttpClient(mockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
    }

    @Test
    fun searchSongsPageSurfacesAVideoOnlyTrackByAnArtistTheSongShelfEstablished() = runTest {
        // The live "asma njk" case in miniature: the artist's own upload of
        // the requested track exists only as a video, alongside a karaoke
        // version from an unrelated channel and a video duplicate of a track
        // the catalog already has.
        val repository = repositoryWithSeparateSongAndVideoShelves(
            songsJson = filteredShelfJson(
                listOf(
                    filteredRowJson("Kholai Khola", "songKholai", "Neetesh Jung Kunwar", "Kholai Khola"),
                    filteredRowJson("Flirty Maya", "songFlirty", "Neetesh Jung Kunwar", "NJK 2018"),
                )
            ),
            videosJson = filteredShelfJson(
                listOf(
                    filteredRowJson("Ashma (A Confession)", "vidAshma", "Neetesh Jung Kunwar", "29M views"),
                    filteredRowJson("Ashma karaoke with lyrics", "vidKaraoke", "Karaoke Nepal", "2.9K views"),
                    filteredRowJson("Flirty Maya", "vidFlirty", "Neetesh Jung Kunwar", "38M views"),
                )
            ),
        )

        val page = repository.searchSongsPage("asma njk")

        // The video-only track is in, at the rank its own shelf gave it
        // rather than appended below every song. The karaoke upload is out
        // (no song row establishes that channel as an artist) and so is the
        // video of "Flirty Maya" (same recording as a song row already here,
        // under a different videoId).
        assertEquals(
            listOf("Kholai Khola", "Ashma (A Confession)", "Flirty Maya"),
            page.songs.map { it.title }
        )
        assertEquals(
            listOf("songKholai", "vidAshma", "songFlirty"),
            page.songs.map { it.videoId }
        )
    }

    @Test
    fun searchSongsPageFallsBackToVideoUploadsWhenTheCatalogHasNothingAtAll() = runTest {
        // No song rows means no artist the query has established, so the
        // uploader check has nothing to test against. Returning the videos
        // anyway beats the alternative, which is an empty screen.
        val repository = repositoryWithSeparateSongAndVideoShelves(
            songsJson = filteredShelfJson(emptyList()),
            videosJson = filteredShelfJson(
                listOf(
                    filteredRowJson("Obscure Local Track", "vidLocal", "Tiny Channel", "4K views"),
                )
            ),
        )

        val page = repository.searchSongsPage("obscure local track")

        assertEquals(listOf("Obscure Local Track"), page.songs.map { it.title })
        assertEquals("Tiny Channel", page.songs.single().artist)
    }


    private companion object {
        // Mirrors InnerTubeApi.SEARCH_VIDEOS_PARAMS, which is private to it.
        const val VIDEOS_PARAMS = "EgWKAQIQAWoKEAoQAxAEEAkQBQ%3D%3D"
    }
}
