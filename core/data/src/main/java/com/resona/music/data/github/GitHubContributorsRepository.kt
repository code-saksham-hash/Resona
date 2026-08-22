package com.resona.music.data.github

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.resona.music.domain.model.Contributor
import com.resona.music.domain.repository.ContributorsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GitHubContributor(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    val contributions: Int = 0,
    val type: String = "User"
)

/** Resona's own repo's contributors, read straight from GitHub -- same
 *  "no server of Resona's own" reasoning as [com.resona.music.data.update.GitHubAppUpdateRepository]. */
@Singleton
class GitHubContributorsRepository @Inject internal constructor(
    private val httpClient: HttpClient,
    @ApplicationContext private val context: Context,
) : ContributorsRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun getContributors(): List<Contributor> {
        val fetched = fetchContributors()
        if (fetched != null) {
            prefs.edit().putString(KEY_CACHED, json.encodeToString(listSerializer, fetched)).apply()
            return fetched.toContributors()
        }
        return cachedContributors()?.toContributors().orEmpty()
    }

    private suspend fun fetchContributors(): List<GitHubContributor>? = runCatching {
        val response = httpClient.get(CONTRIBUTORS_URL) {
            header("Accept", "application/vnd.github+json")
        }
        if (!response.status.isSuccess()) return@runCatching null
        response.body<List<GitHubContributor>>()
    }.getOrElse { e ->
        Log.w(TAG, "fetchContributors: couldn't reach GitHub", e)
        null
    }

    private fun cachedContributors(): List<GitHubContributor>? =
        prefs.getString(KEY_CACHED, null)?.let {
            runCatching { json.decodeFromString(listSerializer, it) }.getOrNull()
        }

    /** Real people only -- bot/automation accounts GitHub itself flags via
     *  [GitHubContributor.type], plus anything that reads as AI-authored
     *  rather than a human contributor (this app credits the people who
     *  built it, not the tools they used). */
    private fun List<GitHubContributor>.toContributors(): List<Contributor> = this
        .filter { it.type.equals("User", ignoreCase = true) }
        .filterNot { EXCLUDED_LOGIN_PATTERN.containsMatchIn(it.login) }
        .sortedByDescending { it.contributions }
        .map {
            Contributor(
                username = it.login,
                displayName = null,
                avatarUrl = it.avatarUrl,
                profileUrl = it.htmlUrl,
                contributions = it.contributions
            )
        }

    private companion object {
        const val TAG = "ContributorsRepository"
        const val CONTRIBUTORS_URL = "https://api.github.com/repos/code-saksham-hash/Resona/contributors?per_page=100"
        const val PREFS_NAME = "resona_contributors"
        const val KEY_CACHED = "cached_contributors"

        val EXCLUDED_LOGIN_PATTERN = Regex("bot|claude|anthropic", RegexOption.IGNORE_CASE)
        val listSerializer = ListSerializer(GitHubContributor.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
