package com.resona.music.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.Contributor
import com.resona.music.domain.repository.AppLinksRepository
import com.resona.music.domain.repository.AppUpdateInfo
import com.resona.music.domain.repository.AppUpdateRepository
import com.resona.music.domain.repository.ContributorsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Available(val info: AppUpdateInfo) : UpdateCheckState
    /** Covers "already latest", "dismissed this version", and "couldn't
     *  reach GitHub" alike -- see [AppUpdateRepository.checkForUpdate]'s
     *  kdoc, which returns null for all three. There's nothing actionable
     *  to tell those three apart here, so the copy stays neutral. */
    data object NoUpdate : UpdateCheckState
}

sealed interface ContributorsUiState {
    data object Loading : ContributorsUiState
    data class Loaded(val contributors: List<Contributor>) : ContributorsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val contributorsRepository: ContributorsRepository,
    private val appLinksRepository: AppLinksRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _contributorsState = MutableStateFlow<ContributorsUiState>(ContributorsUiState.Loading)
    val contributorsState: StateFlow<ContributorsUiState> = _contributorsState.asStateFlow()

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    /** Seeded with the bundled default so the row is clickable immediately;
     *  [AppLinksRepository.getDiscordInviteUrl] resolves to the current
     *  value (cached or freshly fetched) once init's launch completes. */
    private val _discordUrl = MutableStateFlow(AppLinksRepository.DEFAULT_DISCORD_INVITE_URL)
    val discordUrl: StateFlow<String> = _discordUrl.asStateFlow()

    /** Resona has no update server/store of its own to ask -- this is just
     *  the version this exact build was compiled with, same lookup
     *  [com.resona.music.data.update.GitHubAppUpdateRepository] uses to
     *  decide whether a GitHub release is newer. */
    val appVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "unknown"

    init {
        viewModelScope.launch {
            _contributorsState.value = ContributorsUiState.Loaded(contributorsRepository.getContributors())
        }
        viewModelScope.launch {
            _discordUrl.value = appLinksRepository.getDiscordInviteUrl()
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.Checking
            _updateCheckState.value = try {
                val info = appUpdateRepository.checkForUpdate()
                if (info != null) UpdateCheckState.Available(info) else UpdateCheckState.NoUpdate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateCheckState.NoUpdate
            }
        }
    }
}
