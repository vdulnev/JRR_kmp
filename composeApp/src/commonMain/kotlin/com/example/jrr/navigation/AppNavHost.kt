package com.example.jrr.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.jrr.ui.player.NowPlayingContainer
import com.example.jrr.ui.player.PlayerViewModel
import com.example.jrr.ui.setup.SetupScreen
import com.example.jrr.ui.setup.SetupViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppNavHost(
    hasServer: Boolean,
    serverAddress: String
) {
    val navController = rememberNavController()
    val startDestination = remember { if (hasServer) PlayerRoute else SetupRoute }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<SetupRoute> {
            val setupViewModel: SetupViewModel = koinViewModel()
            SetupScreen(viewModel = setupViewModel)
        }

        composable<PlayerRoute> {
            val playerViewModel: PlayerViewModel = koinViewModel()
            NowPlayingContainer(
                viewModel = playerViewModel,
                serverAddress = serverAddress
            )
        }
    }

    LaunchedEffect(hasServer) {
        val targetRoute = if (hasServer) PlayerRoute else SetupRoute
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }
}
