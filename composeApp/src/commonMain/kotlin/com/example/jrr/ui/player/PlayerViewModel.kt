package com.example.jrr.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.example.jrr.domain.model.PlayerStatus
import com.example.jrr.domain.model.PlayingNowItem
import com.example.jrr.domain.model.Zone
import com.example.jrr.service.JRiverService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

data class PlayerUiState(
    val status: PlayerStatus? = null,
    val zones: List<Zone> = emptyList(),
    val queue: List<PlayingNowItem> = emptyList(),
    val activeZoneId: String? = null
)

@KoinViewModel
class PlayerViewModel(
    private val jRiverService: JRiverService
) : ViewModel() {

    private val logger = Logger.withTag("PlayerViewModel")

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
    }.onEach { state ->
        logger.v { "UI State updated: zone=${state.activeZoneId}, state=${state.status?.state}, tracks=${state.queue.size}" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUiState())

    fun play() {
        logger.d { "Play requested" }
        jRiverService.play()
    }

    fun pause() {
        logger.d { "Pause requested" }
        jRiverService.pause()
    }

    fun playPause() {
        logger.d { "PlayPause requested" }
        jRiverService.playPause()
    }

    fun next() {
        logger.d { "Next requested" }
        jRiverService.next()
    }

    fun previous() {
        logger.d { "Previous requested" }
        jRiverService.previous()
    }

    fun setVolume(level: Float) {
        logger.d { "Set volume to $level" }
        jRiverService.setVolume(level)
    }

    fun seek(positionMs: Int) {
        logger.d { "Seek to $positionMs" }
        jRiverService.seek(positionMs)
    }

    fun setQueuePosition(index: Int) {
        logger.d { "Set queue position to $index" }
        jRiverService.setQueuePosition(index)
    }

    fun reorderQueue(from: Int, to: Int) {
        logger.d { "Reorder queue from $from to $to" }
        jRiverService.reorderQueue(from, to)
    }

    fun removeFromQueue(index: Int) {
        logger.d { "Remove from queue index $index" }
        jRiverService.removeFromQueue(index)
    }

    fun linkZones(targetZoneIds: List<String>) {
        logger.i { "Linking zones: $targetZoneIds" }
        jRiverService.linkZones(targetZoneIds)
    }

    fun unlinkZone(zoneId: String) {
        logger.i { "Unlinking zone: $zoneId" }
        jRiverService.unlinkZone(zoneId)
    }

    fun selectZone(zoneId: String) {
        logger.i { "Selecting zone: $zoneId" }
        jRiverService.setActiveZone(zoneId)
    }
}
