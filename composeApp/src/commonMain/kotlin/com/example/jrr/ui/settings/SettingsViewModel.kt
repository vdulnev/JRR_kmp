package com.example.jrr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.SavedServer
import com.example.jrr.service.JRiverService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class SettingsUiState(
    val serverAddress: String = "",
    val serverName: String = "",
    val bitPerfect: Boolean = true,
    val crossfadeDuration: Float = 4.5f,
    val showVuMeter: Boolean = true,
    val spectrumComplexity: Int = 64,
    val version: String = "1.0.0",
    val isLoading: Boolean = false
)

@KoinViewModel
class SettingsViewModel(
    private val settings: JRiverSettings,
    private val jRiverService: JRiverService,
    private val mcwsClient: JRiverMcwsClient
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.serverAddress,
        settings.bitPerfect,
        settings.crossfadeDuration,
        settings.showVuMeter,
        settings.spectrumComplexity
    ) { address, bitPerfect, crossfade, vuMeter, complexity ->
        SettingsUiState(
            serverAddress = address ?: "",
            bitPerfect = bitPerfect,
            crossfadeDuration = crossfade,
            showVuMeter = vuMeter,
            spectrumComplexity = complexity
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setBitPerfect(enabled: Boolean) {
        viewModelScope.launch { settings.setBitPerfect(enabled) }
    }

    fun setCrossfadeDuration(seconds: Float) {
        viewModelScope.launch { settings.setCrossfadeDuration(seconds) }
    }

    fun setShowVuMeter(enabled: Boolean) {
        viewModelScope.launch { settings.setShowVuMeter(enabled) }
    }

    fun setSpectrumComplexity(bands: Int) {
        viewModelScope.launch { settings.setSpectrumComplexity(bands) }
    }

    fun logout() {
        viewModelScope.launch {
            // Clear only the active session to trigger navigation to Setup screen
            // but keep credentials/host history for pre-filling
            settings.saveServerDetails("", null)
            settings.saveAuthToken(null)
            jRiverService.stop()
        }
    }
}
