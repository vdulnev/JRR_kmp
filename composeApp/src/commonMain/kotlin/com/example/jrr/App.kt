package com.example.jrr

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.data.remote.lookup.JRiverLookupService
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.data.remote.mcws.McwsApi
import com.example.jrr.service.JRiverService
import com.example.jrr.ui.player.NowPlayingContainer
import com.example.jrr.ui.player.NowPlayingScreen
import com.example.jrr.ui.player.PlayerViewModel
import com.example.jrr.ui.setup.SetupScreen
import com.example.jrr.ui.setup.SetupViewModel
import com.example.jrr.ui.theme.ObsidianTheme
import io.ktor.client.*
import nl.adaptivity.xmlutil.serialization.XML

@Composable
fun App(dataStore: DataStore<Preferences>) {
    val httpClient = remember { HttpClient() }
    val xml = remember { XML { autoPolymorphic = true } }
    val settings = remember { JRiverSettings(dataStore) }
    val lookupService = remember { JRiverLookupService(httpClient, xml) }
    val mcwsClient = remember { JRiverMcwsClient(httpClient, McwsApi(httpClient), xml) }
    val jRiverService = remember { JRiverService(mcwsClient) }

    val serverAddress by settings.serverAddress.collectAsState(null)
    val authToken by settings.authToken.collectAsState(null)

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Setup) }

    LaunchedEffect(serverAddress, authToken) {
        if (serverAddress != null) {
            mcwsClient.updateConfig(serverAddress!!, authToken)
            jRiverService.start()
            currentScreen = Screen.Player
        } else {
            currentScreen = Screen.Setup
        }
    }

    ObsidianTheme {
        when (val screen = currentScreen) {
            Screen.Setup -> {
                val setupViewModel: SetupViewModel = viewModel {
                    SetupViewModel(lookupService, mcwsClient, settings)
                }
                SetupScreen(
                    viewModel = setupViewModel,
                    onSuccess = {
                        // Navigation handled by LaunchedEffect observing settings
                    }
                )
            }
            Screen.Player -> {
                val playerViewModel: PlayerViewModel = viewModel {
                    PlayerViewModel(jRiverService)
                }
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
    object Setup : Screen()
    object Player : Screen()
}
