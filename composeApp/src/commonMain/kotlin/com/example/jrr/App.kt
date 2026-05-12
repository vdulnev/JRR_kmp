package com.example.jrr

import androidx.compose.runtime.*
import co.touchlab.kermit.Logger
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.service.JRiverService
import com.example.jrr.ui.player.NowPlayingContainer
import com.example.jrr.ui.player.PlayerViewModel
import com.example.jrr.ui.setup.SetupScreen
import com.example.jrr.ui.setup.SetupViewModel
import kotlinx.coroutines.flow.combine
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

@Composable
fun App() {
    val logger = remember { Logger.withTag("App") }
    
    val settings: JRiverSettings = koinInject()
    val jRiverService: JRiverService = koinInject()
    val mcwsClient: com.example.jrr.data.remote.mcws.JRiverMcwsClient = koinInject()

    val session by remember(settings) {
        combine(settings.serverAddress, settings.authToken) { address, token ->
            address to token
        }
    }.collectAsState(null to null)
    val serverAddress = session.first
    val authToken = session.second

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Setup) }

    LaunchedEffect(serverAddress, authToken) {
        logger.i { "LaunchedEffect - serverAddress: $serverAddress, token: ${authToken?.take(5)}..." }
        if (!serverAddress.isNullOrBlank()) {
            logger.d { "Configuring client and starting service with $serverAddress" }
            mcwsClient.updateConfig(serverAddress, authToken)
            jRiverService.start()
            currentScreen = Screen.Player
        } else {
            logger.d { "No server address found. Showing Setup screen." }
            currentScreen = Screen.Setup
        }
    }

    when (currentScreen) {
        Screen.Setup -> {
            val setupViewModel: SetupViewModel = koinViewModel()
            SetupScreen(viewModel = setupViewModel)
        }
        Screen.Player -> {
            val playerViewModel: PlayerViewModel = koinViewModel()
            NowPlayingContainer(
                viewModel = playerViewModel,
                jRiverService = jRiverService,
                serverAddress = serverAddress ?: ""
            )
        }
    }
}

sealed class Screen {
    data object Setup : Screen()
    data object Player : Screen()
}
