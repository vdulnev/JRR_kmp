package com.example.jrr.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.data.remote.lookup.JRiverLookupService
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.ServerInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isSuccess: Boolean = false
)

@KoinViewModel
class SetupViewModel(
    private val lookupService: JRiverLookupService,
    private val mcwsClient: JRiverMcwsClient,
    private val settings: JRiverSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val address = if (state.accessKey.isNotBlank()) {
                lookupService.lookup(state.accessKey).fold(
                    onSuccess = { response ->
                        if (response.ip != null && response.port != null) {
                            "http://${response.ip}:${response.port}"
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not resolve Access Key")
                            return@launch
                        }
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Lookup failed: ${it.message}")
                        return@launch
                    }
                )
            } else {
                "http://${state.host}:${state.port}"
            }

            mcwsClient.alive(address).fold(
                onSuccess = { serverInfo ->
                    _uiState.value = _uiState.value.copy(serverInfo = serverInfo)
                    authenticateAndSave(address, serverInfo)
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Server unreachable: ${it.message}")
                }
            )
        }
    }

    private suspend fun authenticateAndSave(address: String, serverInfo: ServerInfo) {
        val state = _uiState.value
        mcwsClient.authenticate(address, state.username, state.password).fold(
            onSuccess = { token ->
                settings.saveServerDetails(address, if (state.accessKey.isNotBlank()) state.accessKey else null)
                settings.saveCredentials(state.username, state.password)
                settings.saveAuthToken(token)
                mcwsClient.updateConfig(address, token)
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            },
            onFailure = {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Authentication failed: ${it.message}")
            }
        )
    }
}
