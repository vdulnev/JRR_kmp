package com.example.jrr.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.jrr.navigation.LibraryRoute
import com.example.jrr.navigation.NowPlayingRoute
import com.example.jrr.navigation.PlayerNavHost
import com.example.jrr.navigation.QueueRoute
import com.example.jrr.navigation.SettingsRoute
import com.example.jrr.navigation.ZonesRoute
import com.example.jrr.ui.theme.Gold

@Composable
fun NowPlayingContainer(
    viewModel: PlayerViewModel,
    serverAddress: String
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Gold
            ) {
                PlayerTabItem(
                    selected = currentDestination?.hasRoute<NowPlayingRoute>() == true,
                    onClick = { navController.navigatePlayerTab(NowPlayingRoute) },
                    icon = Icons.Default.PlayCircle,
                    label = "Playing",
                    contentDescription = "Now Playing"
                )
                PlayerTabItem(
                    selected = currentDestination?.hasRoute<QueueRoute>() == true,
                    onClick = { navController.navigatePlayerTab(QueueRoute) },
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "Queue",
                    contentDescription = "Queue"
                )
                PlayerTabItem(
                    selected = currentDestination?.hasRoute<LibraryRoute>() == true,
                    onClick = { navController.navigatePlayerTab(LibraryRoute) },
                    icon = Icons.Default.LibraryMusic,
                    label = "Library",
                    contentDescription = "Library"
                )
                PlayerTabItem(
                    selected = currentDestination?.hasRoute<ZonesRoute>() == true,
                    onClick = { navController.navigatePlayerTab(ZonesRoute) },
                    icon = Icons.Default.SettingsInputComponent,
                    label = "Zones",
                    contentDescription = "Zones"
                )
                PlayerTabItem(
                    selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                    onClick = { navController.navigatePlayerTab(SettingsRoute) },
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    contentDescription = "Settings"
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            PlayerNavHost(
                navController = navController,
                viewModel = viewModel,
                serverAddress = serverAddress,
                onSettingsBack = { navController.navigatePlayerTab(NowPlayingRoute) }
            )
        }
    }
}

@Composable
private fun RowScope.PlayerTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    contentDescription: String
) {
    val color = if (selected) Gold else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color
        )
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun androidx.navigation.NavHostController.navigatePlayerTab(route: Any) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}
