package com.resona.music.data.repository

import com.resona.music.data.extractor.InnerTubeExtractionClient
import com.resona.music.data.extractor.JsEngine
import com.resona.music.data.extractor.YouTubeStreamExtractor
import com.resona.music.data.extractor.decipher.DecipherService
import com.resona.music.data.extractor.decipher.NParamDecipherer
import com.resona.music.data.extractor.decipher.PlayerJsRepository
import com.resona.music.data.extractor.decipher.SignatureDecipherer
import com.resona.music.data.remote.innertube.InnerTubeApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

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
        return MusicRepositoryImpl(InnerTubeApi(httpClient), streamExtractor)
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
    }
}
