package com.example.jrr

import androidx.compose.runtime.*
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.service.JRiverService
import com.example.jrr.ui.player.NowPlayingContainer
import com.example.jrr.ui.player.PlayerViewModel
import com.example.jrr.ui.setup.SetupScreen
import com.example.jrr.ui.setup.SetupViewModel
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

@Composable
fun App() {
    KoinContext {
        val settings: JRiverSettings = koinInject()
        val jRiverService: JRiverService = koinInject()
        val mcwsClient: com.example.jrr.data.remote.mcws.JRiverMcwsClient = koinInject()

        val serverAddress by settings.serverAddress.collectAsState(null)
        val authToken by settings.authToken.collectAsState(null)

        var currentScreen by remember { mutableStateOf<Screen>(Screen.Setup) }

        LaunchedEffect(serverAddress, authToken) {
            println("App: LaunchedEffect triggered - serverAddress: $serverAddress, token: ${authToken?.take(5)}...")
            if (serverAddress != null) {
                mcwsClient.updateConfig(serverAddress!!, authToken)
                jRiverService.start()
                currentScreen = Screen.Player
            } else {
                currentScreen = Screen.Setup
            }
        }
        when (currentScreen) {
            Screen.Setup -> {
                val setupViewModel: SetupViewModel = koinViewModel()
                SetupScreen(
                    viewModel = setupViewModel,
                    onSuccess = { currentScreen = Screen.Player }
                )
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
}

sealed class Screen {
    data object Setup : Screen()
    data object Player : Screen()
}
