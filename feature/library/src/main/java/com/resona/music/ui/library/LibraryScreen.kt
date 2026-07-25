package com.resona.music.ui.library

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.resona.music.ui.theme.ResonaPlaceholderScreenContent
import com.resona.music.ui.theme.ResonaTheme

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    ResonaPlaceholderScreenContent(
        icon = Icons.AutoMirrored.Outlined.List,
        title = "Library",
        caption = "Saved albums, artists, and playlists.",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    ResonaTheme {
        LibraryScreen()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LibraryScreenDarkPreview() {
    ResonaTheme(darkTheme = true) {
        LibraryScreen()
    }
}
