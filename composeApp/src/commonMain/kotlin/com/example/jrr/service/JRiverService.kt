package com.example.jrr.service

import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class JRiverService(
    private val mcwsClient: JRiverMcwsClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _playerStatus = MutableStateFlow<PlayerStatus?>(null)
    val playerStatus: StateFlow<PlayerStatus?> = _playerStatus.asStateFlow()

    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<PlayingNowItem>>(emptyList())
    val currentQueue: StateFlow<List<PlayingNowItem>> = _currentQueue.asStateFlow()

    private var activeZoneId: String? = null
    private var lastChangeCounter: Int = -1
    private var pollingJob: Job? = null
    private var zonesPollingJob: Job? = null

    fun start() {
        startPlaybackPolling()
        startZonesPolling()
    }

    fun stop() {
        pollingJob?.cancel()
        zonesPollingJob?.cancel()
    }

    fun setActiveZone(zoneId: String) {
        activeZoneId = zoneId
        // Trigger immediate poll
        scope.launch { pollPlaybackInfo() }
    }

    private fun startPlaybackPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                pollPlaybackInfo()
                val currentStatus = _playerStatus.value
                val interval = when (currentStatus?.state) {
                    PlaybackState.PLAYING -> 1000L
                    PlaybackState.PAUSED, PlaybackState.STOPPED -> 5000L
                    else -> 5000L
                }
                delay(interval)
            }
        }
    }

    private fun startZonesPolling() {
        zonesPollingJob?.cancel()
        zonesPollingJob = scope.launch {
            while (isActive) {
                pollZones()
                delay(30000L)
            }
        }
    }

    private suspend fun pollPlaybackInfo() {
        mcwsClient.getPlaybackInfo(activeZoneId).fold(
            onSuccess = { status ->
                _playerStatus.value = status
                if (status.playingNowChangeCounter != lastChangeCounter) {
                    lastChangeCounter = status.playingNowChangeCounter
                    fetchQueue(status.zoneId)
                }
            },
            onFailure = {
                // Log or handle error
            }
        )
    }

    private suspend fun pollZones() {
        mcwsClient.getZones().fold(
            onSuccess = { zones ->
                _zones.value = zones
                if (activeZoneId == null && zones.isNotEmpty()) {
                    setActiveZone(zones.first().id)
                }
            },
            onFailure = { }
        )
    }

    private suspend fun fetchQueue(zoneId: String) {
        mcwsClient.getPlayingNow(zoneId).fold(
            onSuccess = { _currentQueue.value = it },
            onFailure = { }
        )
    }

    // Transport proxy methods
    fun play() = scope.launch { activeZoneId?.let { mcwsClient.play(it) } }
    fun pause() = scope.launch { activeZoneId?.let { mcwsClient.pause(it) } }
    fun playPause() = scope.launch { activeZoneId?.let { mcwsClient.playPause(it) } }
    fun next() = scope.launch { activeZoneId?.let { mcwsClient.next(it) } }
    fun previous() = scope.launch { activeZoneId?.let { mcwsClient.previous(it) } }
}
