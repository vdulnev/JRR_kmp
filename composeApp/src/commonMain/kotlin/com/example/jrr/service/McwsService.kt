package com.example.jrr.service

import arrow.core.Either
import arrow.resilience.Schedule
import co.touchlab.kermit.Logger
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.BrowseItem
import com.example.jrr.domain.model.McwsError
import com.example.jrr.domain.model.PlaybackState
import com.example.jrr.domain.model.PlayerStatus
import com.example.jrr.domain.model.PlayingNowItem
import com.example.jrr.domain.model.Track
import com.example.jrr.domain.model.Zone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds

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
    fun playPause(zoneId: String) =
        scope.launch { mcwsClient.playPause(zoneId).logFailure("playPause") }

    fun next(zoneId: String) = scope.launch { mcwsClient.next(zoneId).logFailure("next") }
    fun previous(zoneId: String) =
        scope.launch { mcwsClient.previous(zoneId).logFailure("previous") }

    fun setVolume(zoneId: String, level: Float) =
        scope.launch { mcwsClient.setVolume(zoneId, level).logFailure("setVolume") }

    fun seek(zoneId: String, positionMs: Int) =
        scope.launch { mcwsClient.seek(zoneId, positionMs).logFailure("seek") }

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

    fun unlinkZone(zoneId: String) =
        scope.launch { mcwsClient.unlinkZone(zoneId).logFailure("unlinkZone") }

    suspend fun browseChildren(id: String = "-1"): Either<McwsError, List<BrowseItem>> =
        mcwsClient.browseChildren(id).onLeft { logger.e { "browseChildren($id): ${it.message}" } }

    suspend fun browseFiles(id: String): Either<McwsError, List<Track>> =
        mcwsClient.browseFiles(id).onLeft { logger.e { "browseFiles($id): ${it.message}" } }

    suspend fun search(query: String, limit: Int = -1): Either<McwsError, List<Track>> =
        mcwsClient.searchFiles(query, limit = limit)
            .onLeft { logger.e { "search($query): ${it.message}" } }

    private fun startZonesPolling() {
        zonesPollingJob?.cancel()
        zonesPollingJob = scope.launch {
            Schedule.spaced<Unit>(30.seconds).repeat { pollZones() }
        }
    }

    private fun startPlaybackPolling() {
        playbackPollingJob?.cancel()
        playbackPollingJob = scope.launch {
            while (isActive) {
                pollPlaybackInfo()
                val interval = when (_playerStatus.value?.state) {
                    PlaybackState.PLAYING -> 1.seconds
                    else -> 5.seconds
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
        mcwsClient.getZones()
            .onRight { _zones.value = it; logger.d { "Polled ${it.size} zones" } }
            .onLeft {
                logger.w { "Failed to poll zones: ${it.message}" }; _zones.value = emptyList()
            }
    }

    private suspend fun pollPlaybackInfo() {
        val zoneId = activeZoneId ?: return
        mcwsClient.getPlaybackInfo(zoneId)
            .onRight { status ->
                _playerStatus.value = status
                if (status.playingNowChangeCounter != lastChangeCounter) {
                    logger.d { "Change counter changed (${lastChangeCounter} -> ${status.playingNowChangeCounter}). Tracks: ${status.playingNowTracks}. Fetching queue." }
                    lastChangeCounter = status.playingNowChangeCounter
                    fetchQueue(status.zoneId)
                }
            }
            .onLeft { logger.w { "Failed to poll playback info: ${it.message}" } }
    }

    private suspend fun fetchQueue(zoneId: String) {
        logger.d { "Fetching queue for zone $zoneId" }
        mcwsClient.getPlayingNow(zoneId)
            .onRight { _currentQueue.value = it; logger.d { "Fetched queue (${it.size} items)" } }
            .onLeft { logger.e { "Failed to fetch queue: ${it.message}" } }
    }

    private fun Either<McwsError, Unit>.logFailure(command: String) {
        onLeft { logger.w { "MCWS command '$command' failed: ${it.message}" } }
    }
}
