package com.resona.music.domain.model

/** One GitHub account that has contributed code to Resona's repo, shown on
 *  the Settings screen's Developers section. */
data class Contributor(
    val username: String,
    val displayName: String?,
    val avatarUrl: String,
    val profileUrl: String,
    val contributions: Int
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}
