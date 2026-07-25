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
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Saves a song's resolved audio stream to a local file. Separate interface so
 *  tests can fake it without a live Context -- same reasoning as JsEngine
 *  (see ExtractorModule). */
internal interface SongDownloader {
    suspend fun download(song: Song, streamSource: StreamSource): File
}

internal class HttpSongDownloader @Inject constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context,
) : SongDownloader {

    override suspend fun download(song: Song, streamSource: StreamSource): File {
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val destination = File(musicDir, "${song.videoId.sanitizeForFileName()}.audio")
        // Written under a temp name and renamed on success so a failed/killed
        // download never leaves a file behind that looks downloaded.
        val tempFile = File(musicDir, "${destination.name}.part")
        Log.d(TAG, "download: GET ${streamSource.url.take(80)}... -> $destination")

        val response: HttpResponse = httpClient.get(streamSource.url) {
            header("User-Agent", streamSource.userAgent)
        }
        Log.d(TAG, "download: response status=${response.status}")
        if (!response.status.isSuccess()) {
            throw IOException("Download failed for ${song.videoId}: HTTP ${response.status.value}")
        }

        // File writes are blocking -- keep them off whatever dispatcher the
        // caller happens to be on (PlayerViewModel calls this from
        // viewModelScope, i.e. Main by default).
        withContext(Dispatchers.IO) {
            val dirReady = musicDir.mkdirs() || musicDir.isDirectory
            Log.d(TAG, "download: musicDir=$musicDir dirReady=$dirReady")
            var bytesWritten = 0L
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
