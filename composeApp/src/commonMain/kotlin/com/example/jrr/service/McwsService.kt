package com.example.jrr.service

import co.touchlab.kermit.Logger
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single

@Single
class McwsService(
    private val mcwsClient: JRiverMcwsClient
) {
    private val logger = Logger.withTag("McwsService")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _playerStatus = MutableStateFlow<PlayerStatus?>(null)
    val playerStatus: StateFlow<PlayerStatus?> = _playerStatus.asStateFlow()

    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<PlayingNowItem>>(emptyList())
    val currentQueue: StateFlow<List<PlayingNowItem>> = _currentQueue.asStateFlow()

    private var activeZoneId: String? = null
    private var lastChangeCounter: Int = -1
    private var playbackPollingJob: Job? = null
    private var zonesPollingJob: Job? = null

    fun start() {
        if (zonesPollingJob?.isActive == true) {
            logger.d { "MCWS service already running" }
            return
        }
        startZonesPolling()
    }

    fun stop() {
        stopPlaybackPolling()
        zonesPollingJob?.cancel()
        zonesPollingJob = null
    }

    fun setActiveZone(zoneId: String) {
        if (activeZoneId == zoneId && playbackPollingJob?.isActive == true) return

        logger.i { "Setting active MCWS zone to $zoneId" }
        activeZoneId = zoneId
        lastChangeCounter = -1
        startPlaybackPolling()
    }

    fun clearActiveZone() {
        activeZoneId = null
        stopPlaybackPolling()
    }

    fun play(zoneId: String) = scope.launch { mcwsClient.play(zoneId).logFailure("play") }
    fun pause(zoneId: String) = scope.launch { mcwsClient.pause(zoneId).logFailure("pause") }
    fun playPause(zoneId: String) = scope.launch { mcwsClient.playPause(zoneId).logFailure("playPause") }
    fun next(zoneId: String) = scope.launch { mcwsClient.next(zoneId).logFailure("next") }
    fun previous(zoneId: String) = scope.launch { mcwsClient.previous(zoneId).logFailure("previous") }
    fun setVolume(zoneId: String, level: Float) = scope.launch { mcwsClient.setVolume(zoneId, level).logFailure("setVolume") }
    fun seek(zoneId: String, positionMs: Int) = scope.launch { mcwsClient.seek(zoneId, positionMs).logFailure("seek") }
    fun playTrack(zoneId: String, track: Track) = scope.launch {
        mcwsClient.playByKey(zoneId, track.fileKey.toString()).logFailure("playByKey")
    }
    fun setQueuePosition(zoneId: String, index: Int) = scope.launch {
        mcwsClient.setQueuePosition(zoneId, index).logFailure("setQueuePosition")
    }
    fun reorderQueue(zoneId: String, from: Int, to: Int) = scope.launch {
        mcwsClient.reorderQueue(zoneId, from, to).logFailure("reorderQueue")
    }
    fun removeFromQueue(zoneId: String, index: Int) = scope.launch {
        mcwsClient.removeFromQueue(zoneId, index).logFailure("removeFromQueue")
    }
    fun linkZones(zoneId: String, targetZoneIds: List<String>) = scope.launch {
        mcwsClient.linkZones(zoneId, targetZoneIds).logFailure("linkZones")
    }
    fun unlinkZone(zoneId: String) = scope.launch { mcwsClient.unlinkZone(zoneId).logFailure("unlinkZone") }

    suspend fun browseChildren(id: String = "-1"): List<BrowseItem> {
        return mcwsClient.browseChildren(id).fold(
            onSuccess = { it },
            onFailure = {
                logger.e { "Failed to browse children for ID $id: ${it.message}" }
                emptyList()
            }
        )
    }

    suspend fun browseFiles(id: String): List<Track> {
        return mcwsClient.browseFiles(id).fold(
            onSuccess = { it },
            onFailure = {
                logger.e { "Failed to browse files for ID $id: ${it.message}" }
                emptyList()
            }
        )
    }

    suspend fun search(query: String, limit: Int = -1): List<Track> {
        return mcwsClient.searchFiles(query, limit = limit).fold(
            onSuccess = { it },
            onFailure = {
                logger.e { "Failed to search for '$query': ${it.message}" }
                emptyList()
            }
        )
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

    private fun startPlaybackPolling() {
        playbackPollingJob?.cancel()
        playbackPollingJob = scope.launch {
            while (isActive) {
                pollPlaybackInfo()
                val interval = when (_playerStatus.value?.state) {
                    PlaybackState.PLAYING -> 1000L
                    PlaybackState.PAUSED, PlaybackState.STOPPED -> 5000L
                    else -> 5000L
                }
                delay(interval)
            }
        }
    }

    private fun stopPlaybackPolling() {
        playbackPollingJob?.cancel()
        playbackPollingJob = null
    }

    private suspend fun pollZones() {
        logger.v { "Polling zones..." }
        mcwsClient.getZones().fold(
            onSuccess = { zonesFromServer ->
                logger.d { "Successfully polled ${zonesFromServer.size} zones from server" }
                _zones.value = zonesFromServer
            },
            onFailure = {
                logger.w { "Failed to poll zones: ${it.message}" }
                _zones.value = emptyList()
            }
        )
    }

    private suspend fun pollPlaybackInfo() {
        val zoneId = activeZoneId ?: return
        mcwsClient.getPlaybackInfo(zoneId).fold(
            onSuccess = { status ->
                _playerStatus.value = status
                if (status.playingNowChangeCounter != lastChangeCounter) {
                    logger.d { "Change counter changed (${lastChangeCounter} -> ${status.playingNowChangeCounter}). Tracks in server info: ${status.playingNowTracks}. Fetching queue." }
                    lastChangeCounter = status.playingNowChangeCounter
                    fetchQueue(status.zoneId)
                }
            },
            onFailure = {
                logger.w { "Failed to poll playback info: ${it.message}" }
            }
        )
    }

    private suspend fun fetchQueue(zoneId: String) {
        logger.d { "Fetching queue for zone $zoneId" }
        mcwsClient.getPlayingNow(zoneId).fold(
            onSuccess = {
                logger.d { "Successfully fetched queue (${it.size} items)" }
                _currentQueue.value = it
            },
            onFailure = {
                logger.e(it) { "Failed to fetch queue: ${it.message}" }
            }
        )
    }

    private fun Result<Unit>.logFailure(command: String) {
        onFailure { logger.w { "MCWS command '$command' failed: ${it.message}" } }
    }
}
