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
        println("JRiverService: Starting service")
        startPlaybackPolling()
        startZonesPolling()
    }

    fun stop() {
        pollingJob?.cancel()
        zonesPollingJob?.cancel()
    }

    fun setActiveZone(zoneId: String) {
        println("JRiverService: Setting active zone to $zoneId")
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
                    println("JRiverService: Change counter changed (${lastChangeCounter} -> ${status.playingNowChangeCounter}). Tracks in server info: ${status.playingNowTracks}. Fetching queue.")
                    lastChangeCounter = status.playingNowChangeCounter
                    fetchQueue(status.zoneId)
                }
            },
            onFailure = {
                println("JRiverService: Failed to poll playback info: ${it.message}")
            }
        )
    }

    private suspend fun pollZones() {
        mcwsClient.getZones().fold(
            onSuccess = { zones ->
                _zones.value = zones
                if (activeZoneId == null && zones.isNotEmpty()) {
                    println("JRiverService: No active zone, selecting first: ${zones.first().name}")
                    setActiveZone(zones.first().id)
                }
            },
            onFailure = { 
                println("JRiverService: Failed to poll zones: ${it.message}")
            }
        )
    }

    private suspend fun fetchQueue(zoneId: String) {
        println("JRiverService: Fetching queue for zone $zoneId")
        mcwsClient.getPlayingNow(zoneId).fold(
            onSuccess = { 
                println("JRiverService: Successfully fetched queue (${it.size} items)")
                _currentQueue.value = it 
            },
            onFailure = { 
                println("JRiverService: Failed to fetch queue: ${it.message}")
                it.printStackTrace()
            }
        )
    }

    // Transport proxy methods
    fun play() = scope.launch { activeZoneId?.let { mcwsClient.play(it) } }
    fun pause() = scope.launch { activeZoneId?.let { mcwsClient.pause(it) } }
    fun playPause() = scope.launch { activeZoneId?.let { mcwsClient.playPause(it) } }
    fun next() = scope.launch { activeZoneId?.let { mcwsClient.next(it) } }
    fun previous() = scope.launch { activeZoneId?.let { mcwsClient.previous(it) } }

    fun setVolume(level: Float) = scope.launch { activeZoneId?.let { mcwsClient.setVolume(it, level) } }
    fun seek(positionMs: Int) = scope.launch { activeZoneId?.let { mcwsClient.seek(it, positionMs) } }

    fun setQueuePosition(index: Int) = scope.launch { activeZoneId?.let { mcwsClient.setQueuePosition(it, index) } }
    fun reorderQueue(from: Int, to: Int) = scope.launch { activeZoneId?.let { mcwsClient.reorderQueue(it, from, to) } }
    fun removeFromQueue(index: Int) = scope.launch { activeZoneId?.let { mcwsClient.removeFromQueue(it, index) } }

    fun linkZones(targetZoneIds: List<String>) = scope.launch { activeZoneId?.let { mcwsClient.linkZones(it, targetZoneIds) } }
    fun unlinkZone(zoneId: String) = scope.launch { mcwsClient.unlinkZone(zoneId) }

    // Library Operations
    suspend fun browseChildren(id: String = "-1"): List<BrowseItem> {
        return mcwsClient.browseChildren(id).getOrDefault(emptyList())
    }

    suspend fun browseFiles(id: String): List<Track> {
        return mcwsClient.browseFiles(id).getOrDefault(emptyList())
    }

    suspend fun search(query: String, limit: Int = -1): List<Track> {
        return mcwsClient.searchFiles(query, limit = limit).getOrDefault(emptyList())
    }
}
