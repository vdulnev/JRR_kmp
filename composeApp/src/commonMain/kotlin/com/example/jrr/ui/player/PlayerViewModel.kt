package com.example.jrr.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jrr.domain.model.*
import com.example.jrr.service.JRiverService
import kotlinx.coroutines.flow.*

data class PlayerUiState(
    val status: PlayerStatus? = null,
    val zones: List<Zone> = emptyList(),
    val queue: List<PlayingNowItem> = emptyList(),
    val activeZoneId: String? = null
)

class PlayerViewModel(
    private val jRiverService: JRiverService
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(
        jRiverService.playerStatus,
        jRiverService.zones,
        jRiverService.currentQueue
    ) { status, zones, queue ->
        PlayerUiState(
            status = status,
            zones = zones,
            queue = queue,
            activeZoneId = status?.zoneId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUiState())

    fun play() = jRiverService.play()
    fun pause() = jRiverService.pause()
    fun playPause() = jRiverService.playPause()
    fun next() = jRiverService.next()
    fun previous() = jRiverService.previous()
    
    fun setVolume(level: Float) = jRiverService.setVolume(level)
    fun seek(positionMs: Int) = jRiverService.seek(positionMs)

    fun setQueuePosition(index: Int) = jRiverService.setQueuePosition(index)
    fun reorderQueue(from: Int, to: Int) = jRiverService.reorderQueue(from, to)
    fun removeFromQueue(index: Int) = jRiverService.removeFromQueue(index)

    fun linkZones(targetZoneIds: List<String>) = jRiverService.linkZones(targetZoneIds)
    fun unlinkZone(zoneId: String) = jRiverService.unlinkZone(zoneId)

    fun selectZone(zoneId: String) {
        jRiverService.setActiveZone(zoneId)
    }
}
