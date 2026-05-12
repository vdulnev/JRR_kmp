package com.example.jrr

import androidx.compose.runtime.*
import co.touchlab.kermit.Logger
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.navigation.AppNavHost
import com.example.jrr.service.JRiverService
import kotlinx.coroutines.flow.combine
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

    LaunchedEffect(serverAddress, authToken) {
        logger.i { "LaunchedEffect - serverAddress: $serverAddress, token: ${authToken?.take(5)}..." }
        if (!serverAddress.isNullOrBlank()) {
            logger.d { "Configuring client and starting service with $serverAddress" }
            mcwsClient.updateConfig(serverAddress, authToken)
            jRiverService.start()
        } else {
            logger.d { "No server address found. Showing Setup screen." }
        }
    }

    AppNavHost(
        hasServer = !serverAddress.isNullOrBlank(),
        serverAddress = serverAddress.orEmpty()
    )
}
