package com.resona.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resona.music.domain.model.HomeFeed
import com.resona.music.domain.repository.AppUpdateInfo
import com.resona.music.domain.repository.AppUpdateRepository
import com.resona.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [isLoading] only covers the very first load (nothing to show yet);
 * [isRefreshing] covers every load after that (pull-to-refresh), so an
 * already-populated feed never gets replaced by a blank loading screen just
 * because the user pulled to refresh.
 *
 * [updateInfo] is unrelated to the feed load above and never blocks it --
 * Resona has no update server of its own, so this is a one-off GitHub
 * releases check that surfaces a dismissible banner when it finds something.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val feed: HomeFeed? = null,
    val errorMessage: String? = null,
    val updateInfo: AppUpdateInfo? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
        checkForUpdate()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = it.feed == null, isRefreshing = it.feed != null, errorMessage = null)
            }
            try {
                val feed = musicRepository.getHomeFeed()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, feed = feed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = e.message ?: "Couldn't load your feed")
                }
            }
        }
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val info = runCatching { appUpdateRepository.checkForUpdate() }.getOrNull()
            if (info != null) {
                _uiState.update { it.copy(updateInfo = info) }
            }
        }
    }

    /** Dismisses [HomeUiState.updateInfo] for this specific version -- a
     *  release newer than this one will still surface normally later. */
    fun dismissUpdate() {
        val version = _uiState.value.updateInfo?.versionName ?: return
        appUpdateRepository.dismiss(version)
        _uiState.update { it.copy(updateInfo = null) }
    }
}
