package com.example.jrr.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jrr.service.JRiverService
import com.example.jrr.ui.library.LibraryBrowseScreen
import com.example.jrr.ui.library.LibraryViewModel
import com.example.jrr.ui.theme.Gold

@Composable
fun NowPlayingContainer(
    viewModel: PlayerViewModel,
    jRiverService: JRiverService, // Added to provide to LibraryViewModel
    serverAddress: String
) {
    var selectedTab by remember { mutableStateOf(PlayerTab.Player) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Gold
            ) {
                NavigationBarItem(
                    selected = selectedTab == PlayerTab.Player,
                    onClick = { selectedTab = PlayerTab.Player },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = "Player") },
                    label = { Text("Player") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Gold,
                        selectedTextColor = Gold,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == PlayerTab.Library,
                    onClick = { selectedTab = PlayerTab.Library },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                    label = { Text("Library") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Gold,
                        selectedTextColor = Gold,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == PlayerTab.Queue,
                    onClick = { selectedTab = PlayerTab.Queue },
                    icon = { Icon(Icons.Default.List, contentDescription = "Queue") },
                    label = { Text("Queue") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Gold,
                        selectedTextColor = Gold,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == PlayerTab.Zones,
                    onClick = { selectedTab = PlayerTab.Zones },
                    icon = { Icon(Icons.Default.SettingsInputComponent, contentDescription = "Zones") },
                    label = { Text("Zones") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Gold,
                        selectedTextColor = Gold,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                PlayerTab.Player -> NowPlayingScreen(viewModel, serverAddress)
                PlayerTab.Library -> {
                    val libraryViewModel: LibraryViewModel = viewModel {
                        LibraryViewModel(jRiverService)
                    }
                    LibraryBrowseScreen(viewModel = libraryViewModel)
                }
                PlayerTab.Queue -> PlayQueueScreen(viewModel)
                PlayerTab.Zones -> ZoneControllerScreen(viewModel)
            }
        }
    }
}

enum class PlayerTab {
    Player, Library, Queue, Zones
}
