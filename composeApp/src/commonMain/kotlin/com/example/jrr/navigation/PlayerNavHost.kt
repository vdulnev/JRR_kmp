package com.example.jrr.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.jrr.ui.library.LibraryBrowseScreen
import com.example.jrr.ui.library.LibraryViewModel
import com.example.jrr.ui.player.NowPlayingScreen
import com.example.jrr.ui.player.PlayQueueScreen
import com.example.jrr.ui.player.PlayerViewModel
import com.example.jrr.ui.player.ZoneControllerScreen
import com.example.jrr.ui.settings.SettingsScreen
import com.example.jrr.ui.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlayerNavHost(
    navController: NavHostController,
    viewModel: PlayerViewModel,
    serverAddress: String,
    onSettingsBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NowPlayingRoute,
        modifier = modifier
    ) {
        composable<NowPlayingRoute> {
            NowPlayingScreen(viewModel, serverAddress)
        }

        composable<QueueRoute> {
            PlayQueueScreen(viewModel)
        }

        composable<LibraryRoute> {
            val libraryViewModel: LibraryViewModel = koinViewModel()
            LibraryBrowseScreen(viewModel = libraryViewModel)
        }

        composable<ZonesRoute> {
            ZoneControllerScreen(viewModel)
        }

        composable<SettingsRoute> {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = onSettingsBack
            )
        }
    }
}
