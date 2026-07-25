package com.resona.music.ui.home

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.resona.music.ui.theme.ResonaPlaceholderScreenContent
import com.resona.music.ui.theme.ResonaTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    ResonaPlaceholderScreenContent(
        icon = Icons.Outlined.Home,
        title = "Home",
        caption = "Your library and recommendations will live here.",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    ResonaTheme {
        HomeScreen()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    ResonaTheme(darkTheme = true) {
        HomeScreen()
    }
}
