package com.resona.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Wraps a [DefaultHttpDataSource] so every request against a resolved stream
 * url stays under a small explicit byte range, instead of the single
 * open-ended request ExoPlayer would otherwise make.
 *
 * While chasing a "song still won't play" report on 2026-08-20, resolving
 * one stream url and fetching it two different ways showed the real shape
 * of the problem. A `Range` request for the first megabyte or so came back
 * 206 with exactly the requested bytes, every time. Asking for more than
 * about 1.08 MB of that same url, whether as a bigger explicit range or no
 * range at all (which reads as "give me everything"), came back an
 * immediate 403 with zero bytes, every time. Same url, same token, same
 * client: only the size of the window changed the outcome. ExoPlayer's
 * progressive source asks for position 0 with no known length on a fresh
 * MediaItem, which is exactly the shape that failed, so this class exists
 * to keep that request from ever going out, and to keep every later
 * request under the same ceiling.
 *
 * One honest limitation: this only fixes the "never starts at all"
 * failure. Asking for bytes starting partway through the file, once the
 * first range is used up, isn't reliably honored either, even with a
 * brand new token, based on the same day's testing. When that happens,
 * ExoPlayer sees it as a normal playback error, and PlayerViewModel's
 * existing retry recovers by resolving the track again from scratch. That
 * can sound like a long track jumping back to the beginning once or twice
 * rather than continuing smoothly, and it's worth another look if that
 * turns out to be common on a real phone rather than just this test setup.
 *
 * [open] always issues a bounded request comfortably under the ceiling
 * (see [RANGE_SIZE_BYTES]), and [read] opens the next one once the current
 * range runs dry, using the server's own `Content-Range` header to learn
 * how much it actually served and how long the whole resource is. The
 * ceiling is well under any real track's size, so rolling over to another
 * request is the normal case for basically every song, not a rare
 * fallback.
 */
@UnstableApi
class RangedHttpDataSource private constructor(
    private val delegate: DefaultHttpDataSource,
) : DataSource {

    /**
     * [httpDataSourceFactory] is the same factory PlayerViewModel calls
     * `setUserAgent` on before every track (see PlaybackDataSourceModule).
     * Wrapping it here, instead of building an independent one, means
     * every ranged request this opens still goes out under whichever
     * InnerTube client actually resolved the current track.
     */
    class Factory(
        private val httpDataSourceFactory: DefaultHttpDataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            RangedHttpDataSource(httpDataSourceFactory.createDataSource())
    }

    private var pendingSpec: DataSpec? = null

    /** Absolute position of the next byte [read] will hand back. */
    private var position = 0L

    /** Exclusive end of what the caller actually asked for, or [UNSET] when
     *  it asked for everything from [position] on. */
    private var requestEnd = UNSET

    /** Exclusive end of the whole resource, learned the first time a
     *  `Content-Range` response reveals it. [UNSET] until then. */
    private var resourceEnd = UNSET

    /** How many more bytes the currently open ranged request still owes. */
    private var bytesLeftInRequest = 0L

    /** Whether the currently open ranged request has yielded at least one
     *  byte yet. Guards against looping forever if a freshly opened range
     *  answers with an immediate, empty end of stream. */
    private var requestYieldedData = false

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        pendingSpec = dataSpec
        position = dataSpec.position
        requestEnd = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.position + dataSpec.length
        } else {
            UNSET
        }
        resourceEnd = UNSET
        openNextRange()

        return when {
            dataSpec.length != C.LENGTH_UNSET.toLong() ->
                if (resourceEnd != UNSET) minOf(dataSpec.length, resourceEnd - dataSpec.position) else dataSpec.length
            resourceEnd != UNSET -> resourceEnd - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesLeftInRequest <= 0L) {
            val end = knownEnd()
            if (end != UNSET && position >= end) return C.RESULT_END_OF_INPUT
            delegate.close()
            openNextRange()
            if (bytesLeftInRequest <= 0L) return C.RESULT_END_OF_INPUT
        }

        val readLength = minOf(length.toLong(), bytesLeftInRequest).toInt()
        val bytesRead = delegate.read(buffer, offset, readLength)
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            // The server closed out this range before delivering everything
            // it originally promised. If it gave nothing at all for this
            // range, there's nothing left to try. Otherwise treat the range
            // as done early and let the recursive call open a fresh one
            // starting from wherever we actually got to.
            if (!requestYieldedData) return C.RESULT_END_OF_INPUT
            bytesLeftInRequest = 0L
            return read(buffer, offset, length)
        }
        requestYieldedData = true
        position += bytesRead
        bytesLeftInRequest -= bytesRead
        return bytesRead
    }

    override fun getUri(): Uri? = delegate.uri ?: pendingSpec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() {
        pendingSpec = null
        bytesLeftInRequest = 0L
        delegate.close()
    }

    /** The tighter of "what the caller actually asked for" and "how long
     *  the resource actually is", once at least one of those is known. */
    private fun knownEnd(): Long = when {
        requestEnd != UNSET && resourceEnd != UNSET -> minOf(requestEnd, resourceEnd)
        requestEnd != UNSET -> requestEnd
        else -> resourceEnd
    }

    /**
     * Opens a `Range` request for the next slice starting at [position].
     * Never opens without an explicit end. See the class kdoc for why that
     * matters here.
     */
    private fun openNextRange() {
        val spec = checkNotNull(pendingSpec)
        val end = knownEnd()
        val remaining = if (end != UNSET) end - position else Long.MAX_VALUE
        val rangeLength = minOf(RANGE_SIZE_BYTES, remaining)
        requestYieldedData = false
        if (rangeLength <= 0L) {
            bytesLeftInRequest = 0L
            return
        }

        val rangeSpec = spec.buildUpon().setPosition(position).setLength(rangeLength).build()
        try {
            delegate.open(rangeSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // The position landed on or past the resource's true end. This
            // is the clean way that shows up when the caller's own
            // requested end was only ever a guess, from an unknown length
            // DataSpec.
            if (e.responseCode == 416) {
                bytesLeftInRequest = 0L
                return
            }
            throw e
        }

        val servedRange = delegate.responseHeaders[CONTENT_RANGE_HEADER]?.firstOrNull()
            ?.let { CONTENT_RANGE.find(it) }
        if (servedRange != null) {
            val (_, lastByte, total) = servedRange.destructured
            bytesLeftInRequest = lastByte.toLong() - position + 1
            total.toLongOrNull()?.let { resourceEnd = it }
        } else {
            // No Content-Range means the response was a plain 200, not a
            // 206. The delegate already skipped ahead to `position`
            // internally in that case and will deliver up to rangeLength.
            bytesLeftInRequest = rangeLength
        }
    }

    private companion object {
        /** Kept safely under the roughly 1.05 to 1.08 MB window where the
         *  CDN flips from serving a range to instantly rejecting it (see
         *  the class kdoc). 900 KB leaves a real margin below the observed
         *  cutoff instead of hugging it, since that number came from one
         *  afternoon's measurement and not a documented guarantee. Sitting
         *  right at the edge would be one bad measurement away from
         *  landing back on instant 403s for every track. */
        const val RANGE_SIZE_BYTES = 900_000L

        val UNSET = C.LENGTH_UNSET.toLong()

        const val CONTENT_RANGE_HEADER = "Content-Range"
        val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
    }
}
