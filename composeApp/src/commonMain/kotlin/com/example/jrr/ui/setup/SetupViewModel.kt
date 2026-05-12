package com.example.jrr.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import co.touchlab.kermit.Logger
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.data.remote.lookup.JRiverLookupService
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.DomainError
import com.example.jrr.domain.model.LookupError
import com.example.jrr.domain.model.SavedServer
import com.example.jrr.domain.model.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
            val lastAccessKey = settings.accessKey.first()
            val lastUsername = settings.username.first()
            val lastPassword = settings.password.first()

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

            _uiState.update {
                it.copy(
                    accessKey = lastAccessKey ?: "",
                    host = lastHost,
                    port = lastPort,
                    username = lastUsername ?: "",
                    password = lastPassword ?: "",
                )
            }

            settings.recentServers.collect { servers ->
                _uiState.update { it.copy(recentServers = servers) }
            }
        }
    }

    fun onAccessKeyChange(value: String) = _uiState.update { it.copy(accessKey = value, error = null) }
    fun onHostChange(value: String) = _uiState.update { it.copy(host = value, error = null) }
    fun onPortChange(value: String) = _uiState.update { it.copy(port = value, error = null) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }

    fun onConnect() {
        val state = _uiState.value
        if (state.accessKey.isBlank() && state.host.isBlank()) {
            _uiState.update { it.copy(error = "Please enter an Access Key or Host address") }
            return
        }

        viewModelScope.launch {
            logger.i { "Attempting to connect (accessKey: ${state.accessKey}, host: ${state.host})" }
            _uiState.update { it.copy(isLoading = true, error = null) }

            either<DomainError, Pair<String, ServerInfo>> {
                val address = if (state.accessKey.isNotBlank()) {
                    val response = lookupService.lookup(state.accessKey).bind()
                    ensure(response.ip != null && response.port != null) {
                        LookupError.NotFound("Could not resolve Access Key")
                    }
                    "http://${response.ip}:${response.port}"
                } else {
                    "http://${state.host}:${state.port}"
                }
                logger.d { "Resolved server address: $address. Testing with Alive." }
                val info = mcwsClient.alive(address).bind()
                logger.i { "Server reached: ${info.name} (${info.version})" }
                address to info
            }.fold(
                ifLeft = { err ->
                    logger.e { "Connect failed: ${err.message}" }
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                },
                ifRight = { (address, info) ->
                    _uiState.update { it.copy(serverInfo = info) }
                    authenticateAndSave(
                        address = address,
                        serverInfo = info,
                        accessKey = state.accessKey.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
    }

    fun connectToSaved(server: SavedServer) {
        viewModelScope.launch {
            logger.i { "Connecting to saved server: ${server.name} at ${server.address}" }
            _uiState.update { it.copy(isLoading = true, error = null) }
            mcwsClient.alive(server.address).fold(
                ifLeft = { err ->
                    logger.e { "Saved server unreachable at ${server.address}: ${err.message}" }
                    _uiState.update { it.copy(isLoading = false, error = "Server unreachable: ${err.message}") }
                },
                ifRight = { info ->
                    _uiState.update { it.copy(serverInfo = info) }
                    authenticateAndSave(server.address, info, server.accessKey)
                },
            )
        }
    }

    private suspend fun authenticateAndSave(address: String, serverInfo: ServerInfo, accessKey: String?) {
        val state = _uiState.value
        logger.i { "Authenticating for user '${state.username}' at $address" }
        either<DomainError, String> {
            val token = mcwsClient.authenticate(address, state.username, state.password).bind()
            ensureNotNull(token.takeIf { it.isNotBlank() }) {
                LookupError.NotFound("Empty token from authenticate")
            }
        }.fold(
            ifLeft = { err ->
                logger.e { "Authentication failed: ${err.message}" }
                _uiState.update { it.copy(isLoading = false, error = "Authentication failed: ${err.message}") }
            },
            ifRight = { token ->
                logger.i { "Authentication successful. Saving settings." }
                settings.saveAuthenticatedServer(address, accessKey, token)
                settings.saveCredentials(state.username, state.password)
                settings.addRecentServer(
                    SavedServer(
                        address = address,
                        accessKey = accessKey,
                        name = serverInfo.name,
                        lastUsed = 0,
                    )
                )
                mcwsClient.updateConfig(address, token)
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            },
        )
    }
}
