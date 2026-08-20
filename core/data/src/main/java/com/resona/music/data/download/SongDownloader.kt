package com.resona.music.data.download

import android.content.Context
import android.os.Environment
import android.util.Log
import com.resona.music.domain.model.Song
import com.resona.music.domain.repository.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Saves a song's resolved audio stream to a local file. Separate interface so
 *  tests can fake it without a live Context -- same reasoning as JsEngine
 *  (see ExtractorModule). [onProgress] reports download fraction (0f..1f);
 *  callers that don't care about progress can ignore it. */
internal interface SongDownloader {
    suspend fun download(
        song: Song,
        streamSource: StreamSource,
        onProgress: (Float) -> Unit = {},
    ): File
}

internal class HttpSongDownloader @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context,
) : SongDownloader {

    override suspend fun download(
        song: Song,
        streamSource: StreamSource,
        onProgress: (Float) -> Unit,
    ): File {
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val destination = File(musicDir, "${song.videoId.sanitizeForFileName()}.audio")
        // Written under a temp name and renamed on success so a failed/killed
        // download never leaves a file behind that looks downloaded.
        val tempFile = File(musicDir, "${destination.name}.part")
        Log.d(TAG, "download: GET ${streamSource.url.take(80)}... -> $destination")

        // File writes are blocking, so keep them off whatever dispatcher the
        // caller happens to be on (PlayerViewModel calls this from
        // viewModelScope, which defaults to Main).
        withContext(Dispatchers.IO) {
            val dirReady = musicDir.mkdirs() || musicDir.isDirectory
            Log.d(TAG, "download: musicDir=$musicDir dirReady=$dirReady")

            var position = 0L
            var totalBytes: Long? = null
            var bytesWritten = 0L
            var lastReportedPercent = -1

            try {
                tempFile.outputStream().use { output ->
                    while (true) {
                        // A single request for the whole file (`Range: bytes=0-`,
                        // what this used to send) gets rejected immediately, the
                        // same way ExoPlayer's open ended read does (see
                        // RangedHttpDataSource in core/player, which measured
                        // this directly). Fetching in bounded slices avoids
                        // that, and the size below mirrors the one it settled
                        // on for the same reason.
                        val rangeEnd = position + RANGE_SIZE_BYTES - 1
                        val response: HttpResponse = httpClient.get(streamSource.url) {
                            header("User-Agent", streamSource.userAgent)
                            header(HttpHeaders.Range, "bytes=$position-$rangeEnd")
                        }
                        Log.d(TAG, "download: range bytes=$position-$rangeEnd status=${response.status}")

                        if (!response.status.isSuccess()) {
                            if (position == 0L) {
                                throw IOException("Download failed for ${song.videoId}: HTTP ${response.status.value}")
                            }
                            // Got the first slice fine, but a later one was not
                            // honored. Ought to be rare now that resolving goes
                            // through a client the CDN doesn't cut off past the
                            // first megabyte (see YouTubeStreamExtractor), but
                            // if a video ever resolves through one of the more
                            // heavily-watched clients anyway, this can still
                            // happen. Surfacing it plainly beats quietly keeping
                            // a partial file around that looks downloaded but
                            // will cut out partway through when played back
                            // offline.
                            throw IOException(
                                "Could only save part of ${song.videoId}: got $bytesWritten of " +
                                    "${totalBytes ?: "an unknown number of"} bytes before the source stopped answering."
                            )
                        }

                        if (totalBytes == null) {
                            // When the server tells us the size up front, the
                            // progress ring can be determinate; without it the
                            // UI falls back to an indeterminate spinner. Prefer
                            // Content-Range's total over Content-Length, since
                            // Content-Length is stripped by HTTP/2 (it travels
                            // in DATA frame headers, not the header map) and is
                            // frequently absent over plain HTTP/1.1 for chunked
                            // media anyway.
                            totalBytes = response.headers[HttpHeaders.ContentRange]
                                ?.substringAfter('/', missingDelimiterValue = "")
                                ?.toLongOrNull()
                                ?.takeIf { it > 0 }
                                ?: response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                        }

                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                        var bytesInThisRange = 0L
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read == -1) break
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                bytesWritten += read
                                bytesInThisRange += read
                                // Throttled to ~1% steps so a small buffer size
                                // doesn't spam the StateFlow for no visible gain.
                                totalBytes?.let { total ->
                                    val percent = ((bytesWritten * 100) / total).toInt()
                                    if (percent in 0..100 && percent != lastReportedPercent) {
                                        lastReportedPercent = percent
                                        onProgress(percent / 100f)
                                    }
                                }
                            }
                        }
                        position += bytesInThisRange

                        val doneByTotal = totalBytes != null && position >= totalBytes!!
                        // A range that came back successful but empty isn't
                        // expected to happen, but treat it as the end rather
                        // than looping forever if it ever does.
                        if (doneByTotal || bytesInThisRange == 0L) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "download: failed after $bytesWritten bytes, deleting partial file", e)
                tempFile.delete()
                throw e
            }
            Log.d(TAG, "download: wrote $bytesWritten bytes to $tempFile")

            if (bytesWritten == 0L) {
                tempFile.delete()
                throw IOException("Downloaded 0 bytes for ${song.videoId} -- stream URL likely expired or empty")
            }
            if (!tempFile.renameTo(destination)) {
                Log.w(TAG, "download: renameTo failed ($tempFile -> $destination)")
                tempFile.delete()
                throw IOException("Could not save downloaded file for ${song.videoId}")
            }
        }
        return destination
    }

    private fun String.sanitizeForFileName(): String = filter { it.isLetterOrDigit() || it == '-' || it == '_' }

    private companion object {
        const val TAG = "SongDownloader"
        const val DOWNLOAD_BUFFER_BYTES = 8 * 1024

        // Mirrors RangedHttpDataSource.RANGE_SIZE_BYTES in core/player: same
        // CDN, same measured ceiling, same margin under it. Duplicated rather
        // than shared because core/data doesn't depend on core/player.
        const val RANGE_SIZE_BYTES = 900_000L
    }
}
