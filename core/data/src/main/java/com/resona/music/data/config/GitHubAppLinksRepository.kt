package com.resona.music.data.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.resona.music.domain.repository.AppLinksRepository
import com.resona.music.domain.repository.AppLinksRepository.Companion.DEFAULT_DISCORD_INVITE_URL
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class RemoteAppConfig(val discordInviteUrl: String? = null)

/** Backed by a small JSON file living in Resona's own repo rather than
 *  GitHub's REST API -- raw.githubusercontent.com isn't subject to the API's
 *  60-requests/hour-per-IP limit that [com.resona.music.data.update.GitHubAppUpdateRepository]
 *  already has to budget around. It also always serves `text/plain`
 *  regardless of file extension, so the body is decoded by hand instead of
 *  through the shared client's content negotiation (which only matches
 *  `application/json`). */
@Singleton
class GitHubAppLinksRepository @Inject internal constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context,
) : AppLinksRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun getDiscordInviteUrl(): String {
        val now = System.currentTimeMillis()
        val lastChecked = prefs.getLong(KEY_LAST_CHECKED, 0L)
        if (now - lastChecked < CHECK_INTERVAL_MILLIS) return cachedDiscordUrl()

        val fetched = fetchConfig()?.discordInviteUrl
        if (fetched != null) {
            prefs.edit()
                .putLong(KEY_LAST_CHECKED, now)
                .putString(KEY_DISCORD_URL, fetched)
                .apply()
            return fetched
        }
        return cachedDiscordUrl()
    }

    private fun cachedDiscordUrl(): String =
        prefs.getString(KEY_DISCORD_URL, null) ?: DEFAULT_DISCORD_INVITE_URL

    private suspend fun fetchConfig(): RemoteAppConfig? = runCatching {
        val response = httpClient.get(CONFIG_URL)
        if (!response.status.isSuccess()) return@runCatching null
        json.decodeFromString(RemoteAppConfig.serializer(), response.bodyAsText())
    }.getOrElse { e ->
        Log.w(TAG, "fetchConfig: couldn't reach GitHub", e)
        null
    }

    private companion object {
        const val TAG = "AppLinksRepository"
        const val CONFIG_URL = "https://raw.githubusercontent.com/code-saksham-hash/Resona/main/app-config.json"

        const val PREFS_NAME = "resona_app_links"
        const val KEY_LAST_CHECKED = "last_checked_at"
        const val KEY_DISCORD_URL = "discord_url"

        const val CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L

        val json = Json { ignoreUnknownKeys = true }
    }
}
