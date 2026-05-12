package com.example.jrr.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.data.remote.lookup.JRiverLookupService
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.SavedServer
import com.example.jrr.domain.model.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class SetupUiState(
    val accessKey: String = "",
    val host: String = "",
    val port: String = "52199",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverInfo: ServerInfo? = null,
    val isSuccess: Boolean = false,
    val recentServers: List<SavedServer> = emptyList()
)

@KoinViewModel
class SetupViewModel(
    private val lookupService: JRiverLookupService,
    private val mcwsClient: JRiverMcwsClient,
    private val settings: JRiverSettings
) : ViewModel() {

    private val logger = Logger.withTag("SetupViewModel")
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Load history for pre-filling
            val lastAccessKey = settings.accessKey.first()
            val lastUsername = settings.username.first()
            val lastPassword = settings.password.first()
            
            // If we have a saved address but it's not 'active' (which is why we are here), 
            // try to extract host and port
            val lastAddress = settings.serverAddress.first()
            var lastHost = ""
            var lastPort = "52199"
            
            if (!lastAddress.isNullOrBlank()) {
                val clean = lastAddress.removePrefix("http://").removePrefix("https://")
                if (clean.contains(":")) {
                    lastHost = clean.substringBefore(":")
                    lastPort = clean.substringAfter(":")
                } else {
                    lastHost = clean
                }
            }

            _uiState.value = _uiState.value.copy(
                accessKey = lastAccessKey ?: "",
                host = lastHost,
                port = lastPort,
                username = lastUsername ?: "",
                password = lastPassword ?: ""
            )

            // Also keep collecting recent servers for the list
            settings.recentServers.collect { servers ->
                _uiState.value = _uiState.value.copy(recentServers = servers)
            }
        }
    }

    fun onAccessKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(accessKey = value, error = null)
    }

    fun onHostChange(value: String) {
        _uiState.value = _uiState.value.copy(host = value, error = null)
    }

    fun onPortChange(value: String) {
        _uiState.value = _uiState.value.copy(port = value, error = null)
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onConnect() {
        val state = _uiState.value
        if (state.accessKey.isBlank() && state.host.isBlank()) {
            _uiState.value = state.copy(error = "Please enter an Access Key or Host address")
            return
        }

        viewModelScope.launch {
            logger.i { "Attempting to connect (accessKey: ${state.accessKey}, host: ${state.host})" }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val address = if (state.accessKey.isNotBlank()) {
                lookupService.lookup(state.accessKey).fold(
                    onSuccess = { response ->
                        if (response.ip != null && response.port != null) {
                            "http://${response.ip}:${response.port}"
                        } else {
                            logger.e { "Could not resolve Access Key: $response" }
                            _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not resolve Access Key")
                            return@launch
                        }
                    },
                    onFailure = {
                        logger.e(it) { "Lookup failed for key ${state.accessKey}" }
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Lookup failed: ${it.message}")
                        return@launch
                    }
                )
            } else {
                "http://${state.host}:${state.port}"
            }

            logger.d { "Resolved server address: $address. Testing with Alive." }
            mcwsClient.alive(address).fold(
                onSuccess = { serverInfo ->
                    logger.i { "Server reached successfully: ${serverInfo.name} (${serverInfo.version})" }
                    _uiState.value = _uiState.value.copy(serverInfo = serverInfo)
                    authenticateAndSave(address, serverInfo, if (state.accessKey.isNotBlank()) state.accessKey else null)
                },
                onFailure = {
                    logger.e(it) { "Server unreachable at $address" }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Server unreachable: ${it.message}")
                }
            )
        }
    }

    fun connectToSaved(server: SavedServer) {
        viewModelScope.launch {
            logger.i { "Connecting to saved server: ${server.name} at ${server.address}" }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // For saved servers, we use the cached address but we should still verify it's Alive
            mcwsClient.alive(server.address).fold(
                onSuccess = { serverInfo ->
                    _uiState.value = _uiState.value.copy(serverInfo = serverInfo)
                    authenticateAndSave(server.address, serverInfo, server.accessKey)
                },
                onFailure = {
                    logger.e(it) { "Saved server unreachable at ${server.address}" }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Server unreachable: ${it.message}")
                }
            )
        }
    }

    private suspend fun authenticateAndSave(address: String, serverInfo: ServerInfo, accessKey: String?) {
        val state = _uiState.value
        logger.i { "Authenticating for user '${state.username}' at $address" }
        mcwsClient.authenticate(address, state.username, state.password).fold(
            onSuccess = { token ->
                logger.i { "Authentication successful. Saving settings." }
                settings.saveAuthenticatedServer(address, accessKey, token)
                settings.saveCredentials(state.username, state.password)
                
                // Add to recent servers
                settings.addRecentServer(SavedServer(
                    address = address,
                    accessKey = accessKey,
                    name = serverInfo.name,
                    lastUsed = 0 // Time is not easily available in commonMain without extra deps, or we use a timestamp lib
                ))

                mcwsClient.updateConfig(address, token)
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            },
            onFailure = {
                logger.e(it) { "Authentication failed" }
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Authentication failed: ${it.message}")
            }
        )
    }
}
