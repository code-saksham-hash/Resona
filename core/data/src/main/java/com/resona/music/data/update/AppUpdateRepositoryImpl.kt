package com.resona.music.data.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.resona.music.domain.repository.AppUpdateInfo
import com.resona.music.domain.repository.AppUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false
)

/** Resona's own repo, checked through GitHub's public releases API -- no
 *  server of Resona's own to run or pay for. */
@Singleton
class GitHubAppUpdateRepository @Inject internal constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context,
) : AppUpdateRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun checkForUpdate(): AppUpdateInfo? {
        val latest = latestRelease() ?: return null

        if (!isNewer(candidate = latest.versionName, current = installedVersion())) return null
        if (prefs.getString(KEY_DISMISSED_VERSION, null) == latest.versionName) return null
        return latest
    }

    override fun dismiss(versionName: String) {
        prefs.edit().putString(KEY_DISMISSED_VERSION, versionName).apply()
    }

    /** GitHub's unauthenticated rate limit is 60 requests/hour per IP, shared
     *  by everyone behind the same NAT -- re-checking on every app launch
     *  would burn through that fast for no reason, since a new release lands
     *  at most a few times a month. The last known answer is cached and
     *  reused until it's a day old. Only a successful check starts that
     *  clock -- a failed one (no network at that moment, GitHub briefly
     *  down) shouldn't cost a real day's wait when it wasn't a deliberate
     *  cooldown, just try again next launch. */
    private suspend fun latestRelease(): AppUpdateInfo? {
        val now = System.currentTimeMillis()
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)

        if (now - lastChecked < CHECK_INTERVAL_MILLIS) return cachedRelease()

        val fetched = fetchLatestRelease() ?: return cachedRelease()
        prefs.edit()
            .putLong(KEY_LAST_CHECKED, now)
            .putString(KEY_LATEST_VERSION, fetched.versionName)
            .putString(KEY_LATEST_URL, fetched.releaseUrl)
            .putString(KEY_LATEST_NOTES, fetched.releaseNotes)
            .apply()
        return fetched
    }

    private fun cachedRelease(): AppUpdateInfo? =
        prefs.getString(KEY_LATEST_VERSION, null)?.let {
            AppUpdateInfo(
                versionName = it,
                releaseUrl = prefs.getString(KEY_LATEST_URL, "").orEmpty(),
                releaseNotes = prefs.getString(KEY_LATEST_NOTES, null),
            )
        }

    private suspend fun fetchLatestRelease(): AppUpdateInfo? = runCatching {
        val response = httpClient.get(RELEASES_URL) {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return@runCatching null
        val release = response.body<GitHubRelease>()
        if (release.draft || release.prerelease) return@runCatching null
        AppUpdateInfo(
            versionName = release.tagName.removePrefix("v"),
            releaseUrl = release.htmlUrl,
            releaseNotes = release.body,
        )
    }.getOrElse { e ->
        Log.w(TAG, "fetchLatestRelease: couldn't reach GitHub", e)
        null
    }

    private fun installedVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "0"

    /** Dotted version strings compared component by component as integers,
     *  not lexicographically -- a plain string compare would put "1.10.0"
     *  before "1.2.0". */
    private fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(candidateParts.size, currentParts.size)) {
            val c = candidateParts.getOrElse(i) { 0 }
            val cur = currentParts.getOrElse(i) { 0 }
            if (c != cur) return c > cur
        }
        return false
    }

    private companion object {
        const val TAG = "AppUpdateRepository"
        const val RELEASES_URL = "https://api.github.com/repos/code-saksham-hash/Resona/releases/latest"

        const val PREFS_NAME = "resona_app_update"
        const val KEY_LAST_CHECKED = "last_checked_at"
        const val KEY_LATEST_VERSION = "latest_version"
        const val KEY_LATEST_URL = "latest_url"
        const val KEY_LATEST_NOTES = "latest_notes"
        const val KEY_DISMISSED_VERSION = "dismissed_version"

        const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L
    }
}
