package com.resona.music.domain.repository

/** A release that's newer than the one currently installed. */
data class AppUpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val releaseNotes: String?
)

/** Checks whether a newer Resona release exists than the one currently
 *  installed. Resona has no update server of its own and isn't distributed
 *  through a store that could push updates -- this is the whole mechanism. */
interface AppUpdateRepository {
    /** Null when already on the latest version, the check couldn't reach
     *  the network, or the user already dismissed this specific version. */
    suspend fun checkForUpdate(): AppUpdateInfo?

    /** Stops [checkForUpdate] from returning [versionName] again. A newer
     *  release than that one will still surface normally. */
    fun dismiss(versionName: String)
}
