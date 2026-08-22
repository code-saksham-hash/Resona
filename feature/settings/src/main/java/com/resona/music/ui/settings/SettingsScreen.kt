package com.resona.music.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.resona.music.domain.model.Contributor
import com.resona.music.domain.repository.AppUpdateInfo
import com.resona.music.ui.theme.ResonaTheme

private const val GITHUB_REPO_URL = "https://github.com/code-saksham-hash/Resona"
private const val LICENSE_URL = "$GITHUB_REPO_URL/blob/main/LICENSE"

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val contributorsState by viewModel.contributorsState.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()

    SettingsScreenContent(
        contributorsState = contributorsState,
        updateCheckState = updateCheckState,
        appVersion = viewModel.appVersion,
        onBack = onBack,
        onCheckForUpdate = viewModel::checkForUpdate,
    )
}

@Composable
private fun SettingsScreenContent(
    contributorsState: ContributorsUiState,
    updateCheckState: UpdateCheckState,
    appVersion: String,
    onBack: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    fun openUrl(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(end = 17.dp)
                .height(61.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "about_header") { SettingsSectionHeader("About") }
            item(key = "version_row") {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    subtitle = appVersion,
                    modifier = Modifier.padding(horizontal = 17.dp)
                )
            }
            item(key = "update_row") {
                SettingsRow(
                    icon = Icons.Outlined.Update,
                    title = "Check for updates",
                    subtitle = when (val state = updateCheckState) {
                        UpdateCheckState.Idle -> "Resona has no store of its own -- checks GitHub directly"
                        UpdateCheckState.Checking -> "Checking…"
                        is UpdateCheckState.Available -> "v${state.info.versionName} is available – tap to view"
                        UpdateCheckState.NoUpdate -> "You're on the latest available version"
                    },
                    onClick = {
                        val state = updateCheckState
                        if (state is UpdateCheckState.Available) openUrl(state.info.releaseUrl) else onCheckForUpdate()
                    },
                    trailing = if (updateCheckState == UpdateCheckState.Checking) {
                        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary) }
                    } else null,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp)
                )
            }
            item(key = "source_row") {
                SettingsRow(
                    icon = Icons.Outlined.Code,
                    title = "Source code",
                    subtitle = "View Resona on GitHub",
                    onClick = { openUrl(GITHUB_REPO_URL) },
                    trailing = { ExternalLinkGlyph() },
                    modifier = Modifier.padding(horizontal = 17.dp)
                )
            }
            item(key = "license_row") {
                SettingsRow(
                    icon = Icons.Outlined.Gavel,
                    title = "License",
                    subtitle = "MIT · free and open-source",
                    onClick = { openUrl(LICENSE_URL) },
                    trailing = { ExternalLinkGlyph() },
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 10.dp)
                )
            }
            item(key = "disclaimer") {
                Text(
                    text = "Resona interfaces with YouTube Music's private, undocumented InnerTube API. " +
                        "For personal, non-commercial use only -- not affiliated with or endorsed by Google or YouTube Music.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 8.dp)
                )
            }

            when (val state = contributorsState) {
                ContributorsUiState.Loading -> Unit
                is ContributorsUiState.Loaded -> if (state.contributors.isNotEmpty()) {
                    item(key = "developers_header") { SettingsSectionHeader("Developers") }
                    items(state.contributors, key = { "dev_${it.username}" }) { contributor ->
                        ContributorRow(
                            contributor = contributor,
                            onClick = { openUrl(contributor.profileUrl) },
                            modifier = Modifier.padding(horizontal = 17.dp)
                        )
                    }
                }
            }
            // Clears the floating bottom chrome (pill nav, plus the
            // mini-player when a track is playing) -- see HomeScreen's
            // matching spacer for why this needs to be this generous now.
            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(160.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier.padding(horizontal = 17.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), MaterialTheme.shapes.large)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun ExternalLinkGlyph(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = modifier.size(16.dp)
    )
}

@Composable
private fun ContributorRow(
    contributor: Contributor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = contributor.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contributor.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${contributor.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        ExternalLinkGlyph()
    }
}

@Composable
@Preview(showBackground = true)
private fun SettingsScreenPreview() {
    ResonaTheme(darkTheme = true) {
        SettingsScreenContent(
            contributorsState = ContributorsUiState.Loaded(
                listOf(
                    Contributor("code-saksham-hash", null, "", "https://github.com/code-saksham-hash", 120),
                    Contributor("RawNuke", null, "", "https://github.com/RawNuke", 40),
                )
            ),
            updateCheckState = UpdateCheckState.Idle,
            appVersion = "1.3.1"
        )
    }
}

@Composable
@Preview(showBackground = true, name = "Update available")
private fun SettingsScreenUpdatePreview() {
    ResonaTheme(darkTheme = true) {
        SettingsScreenContent(
            contributorsState = ContributorsUiState.Loading,
            updateCheckState = UpdateCheckState.Available(
                AppUpdateInfo("1.4.0", "https://github.com", null)
            ),
            appVersion = "1.3.1"
        )
    }
}
