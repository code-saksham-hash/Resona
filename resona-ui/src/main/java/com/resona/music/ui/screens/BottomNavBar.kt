package com.resona.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.resona.music.ui.theme.NocturneSurfaceContainerLowest

data class BottomNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val destinations = listOf(
    BottomNavDestination("home", "Home", Icons.Outlined.Home),
    BottomNavDestination("explore", "Explore", Icons.Outlined.Explore),
    BottomNavDestination("downloads", "Downloads", Icons.Outlined.Download),
    BottomNavDestination("library", "Library", Icons.Outlined.LibraryMusic),
)

@Composable
fun ResonaBottomBar(
    currentRoute: String? = "home",
    hasMiniPlayer: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.imePadding()) {
        if (hasMiniPlayer) {
            MiniPlayerBar()
        }
        NavigationBar(
            containerColor = NocturneSurfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp
        ) {
            destinations.forEach { destination ->
                NavigationBarItem(
                    selected = currentRoute == destination.route,
                    onClick = { },
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = destination.label,
                            tint = if (currentRoute == destination.route)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentRoute == destination.route)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }
        }
    }
}
