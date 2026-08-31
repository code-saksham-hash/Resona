package com.resona.music.domain.repository

/** External links that might need to change after release (e.g. a Discord
 *  server getting recreated). Resona has no update server of its own -- same
 *  reasoning as [AppUpdateRepository] -- so these are fetched from a small
 *  file in Resona's own GitHub repo instead of being compiled in, letting an
 *  already-installed build pick up a changed link without an app update. */
interface AppLinksRepository {
    /** Never null -- falls back to the last known-good value, or
     *  [DEFAULT_DISCORD_INVITE_URL] if this is the very first launch with
     *  no network yet. */
    suspend fun getDiscordInviteUrl(): String

    companion object {
        /** Single source of truth for the bundled fallback, shared by the
         *  GitHub-backed impl and the UI's initial state so they can't
         *  silently drift apart. */
        const val DEFAULT_DISCORD_INVITE_URL = "https://discord.gg/Xvs72VVChB"
    }
}
