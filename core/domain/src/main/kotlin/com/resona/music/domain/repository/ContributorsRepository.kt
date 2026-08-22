package com.resona.music.domain.repository

import com.resona.music.domain.model.Contributor

/** Resona's own repo's contributors, checked through GitHub's public API --
 *  same "no server of Resona's own" reasoning as [AppUpdateRepository]. */
interface ContributorsRepository {
    /** Human contributors only (bots and any automation account filtered
     *  out), ordered by contribution count, most first. Empty if the
     *  network can't be reached -- callers should treat that as "nothing to
     *  show" rather than an error, since this is a nice-to-have credits
     *  list, not core functionality. */
    suspend fun getContributors(): List<Contributor>
}
