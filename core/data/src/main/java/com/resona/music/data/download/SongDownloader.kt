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

        val response: HttpResponse = httpClient.get(streamSource.url) {
            header("User-Agent", streamSource.userAgent)
            // Ask for the whole stream via a range request: servers then
            // answer with Content-Range "bytes 0-.../TOTAL", which is the one
            // place the total size survives modern transports -- plain
            // Content-Length is stripped by HTTP/2 (it travels in DATA frame
            // headers, not the header map), and over plain HTTP/1.1 it's
            // frequently absent for chunked media streams.
            header(HttpHeaders.Range, "bytes=0-")
        }
        Log.d(TAG, "download: response status=${response.status}")
        if (!response.status.isSuccess()) {
            throw IOException("Download failed for ${song.videoId}: HTTP ${response.status.value}")
        }
        // When the server tells us the size up front (Content-Length), the
        // progress ring can be determinate; without it the UI falls back to
        // an indeterminate spinner. Prefer Content-Range's total (see the
        // Range header comment above) over Content-Length.
        val totalBytes = response.headers[HttpHeaders.ContentRange]
            ?.substringAfter('/', missingDelimiterValue = "")
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: response.headers[HttpHeaders.ContentLength]?.toLongOrNull()

        // File writes are blocking -- keep them off whatever dispatcher the
        // caller happens to be on (PlayerViewModel calls this from
        // viewModelScope, i.e. Main by default).
        withContext(Dispatchers.IO) {
            val dirReady = musicDir.mkdirs() || musicDir.isDirectory
            Log.d(TAG, "download: musicDir=$musicDir dirReady=$dirReady")
            var bytesWritten = 0L
            var lastReportedPercent = -1
            try {
                val channel = response.bodyAsChannel()
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            bytesWritten += read
                            // Throttle progress callbacks to ~1% steps -- the
                            // buffer is 8 KiB, so a full report per buffer
                            // would spam the StateFlow for no visible gain.
                            if (totalBytes != null && totalBytes > 0) {
                                val percent = ((bytesWritten * 100) / totalBytes).toInt()
                                if (percent in 0..100 && percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
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
    }
}
