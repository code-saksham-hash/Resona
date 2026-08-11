package com.resona.music.data.repository

import com.resona.music.data.download.DownloadedSongsStore
import com.resona.music.data.download.SongDownloader
import com.resona.music.data.extractor.InnerTubeExtractionClient
import com.resona.music.data.extractor.JsEngine
import com.resona.music.data.extractor.YouTubeStreamExtractor
import com.resona.music.data.history.PlayHistoryStore
import com.resona.music.data.likes.LikedSongsStore
import com.resona.music.data.extractor.decipher.DecipherService
import com.resona.music.data.extractor.decipher.NParamDecipherer
import com.resona.music.data.extractor.decipher.PlayerJsRepository
import com.resona.music.data.extractor.decipher.SignatureDecipherer
import com.resona.music.data.remote.innertube.InnerTubeApi
import com.resona.music.domain.model.DownloadedSong
import com.resona.music.domain.model.PlayHistoryEntry
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.StreamSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

    private fun repositoryWithMockedSearchResponse(json: String): MusicRepositoryImpl {
        val mockEngine = MockEngine {
            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
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
            override suspend fun download(song: Song, streamSource: StreamSource) = File("/unused")
        }
        val downloadedSongsStore = object : DownloadedSongsStore {
            override val downloads = MutableStateFlow(emptyList<DownloadedSong>())
            override fun filePathFor(videoId: String): String? = null
            override suspend fun markDownloaded(song: Song, filePath: String) = Unit
            override suspend fun remove(videoId: String) = Unit
        }
        val likedSongsStore = object : LikedSongsStore {
            override val likedSongs = MutableStateFlow(emptyList<Song>())
            override fun isLiked(videoId: String) = false
            override suspend fun toggle(song: Song) = Unit
        }
        val playHistoryStore = object : PlayHistoryStore {
            override val entries = MutableStateFlow(emptyList<PlayHistoryEntry>())
            override suspend fun recordPlay(song: Song) = Unit
        }
        return MusicRepositoryImpl(
            InnerTubeApi(httpClient, PlayerJsRepository(httpClient)),
            streamExtractor,
            songDownloader,
            downloadedSongsStore,
            likedSongsStore,
            playHistoryStore,
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
}
