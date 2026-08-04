package com.resona.music.data.extractor.decipher

import com.resona.music.data.extractor.model.RawFormat
import com.resona.music.domain.repository.StreamCipherRequiredException
import javax.inject.Inject

// Turns one RawFormat into an actually-playable URL: deciphers
// signatureCipher if there's no direct url, then applies the n-param
// transform every adaptive format needs regardless. From yt-dlp-android
// (see NOTICE.md).
//
// playerJsUrl is nullable -- fetching it can fail on its own (watch page
// blocked/changed) even when the format already has a direct url, and in
// that case we'd rather serve the url without the n-param fix (maybe
// throttled) than refuse to play at all.
internal class DecipherService @Inject constructor(
    private val playerJsRepo: PlayerJsRepository,
    private val nParamDecipherer: NParamDecipherer,
    private val signatureDecipherer: SignatureDecipherer,
) {
    suspend fun buildPlayableUrl(format: RawFormat, playerJsUrl: String?): String {
        val playerJs = playerJsUrl?.let { runCatching { playerJsRepo.fetchPlayerJs(it) }.getOrNull() }

        // Direct urls come from the Android/iOS/TV clients that win the
        // extraction chain, and those are playable exactly as returned -- their
        // `n` token is the CDN's own, not the web player's obfuscated nsig
        // token. Running it through the nsig transform can only replace it with
        // a wrong value and turn a working url into a 403 (handed to the
        // player as a source error), so the transform is only applied to urls
        // that were rebuilt from a signatureCipher (i.e. web-style formats).
        val rawUrl = format.url
            ?: format.signatureCipher?.let { cipher ->
                playerJs?.let { signatureDecipherer.decrypt(cipher, it) }
                    ?: throw StreamCipherRequiredException(
                        "itag ${format.itag} is signature-ciphered but player JS is unavailable"
                    )
            }
            ?: throw StreamCipherRequiredException(
                "itag ${format.itag} has neither url nor signatureCipher"
            )

        return if (format.url != null) {
            rawUrl
        } else {
            playerJs?.let { nParamDecipherer.transform(rawUrl, it) } ?: rawUrl
        }
    }
}
