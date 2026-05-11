package com.example.jrr.service

import co.touchlab.kermit.Logger
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.*
import com.example.jrr.player.LocalPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single

@Single
class JRiverService(
    private val mcwsClient: JRiverMcwsClient,
    private val localPlayer: LocalPlayer,
    private val settings: JRiverSettings
) {
    private val logger = Logger.withTag("JRiverService")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val localZone = Zone(id = "local", name = "This Device", guid = "local_guid", isDLNA = false, isLocal = true)

    private val _playerStatus = MutableStateFlow<PlayerStatus?>(null)
    val playerStatus: StateFlow<PlayerStatus?> = _playerStatus.asStateFlow()

    private val _zones = MutableStateFlow<List<Zone>>(listOf(localZone))
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<PlayingNowItem>>(emptyList())
    val currentQueue: StateFlow<List<PlayingNowItem>> = _currentQueue.asStateFlow()

    private var activeZoneId: String? = null // Start as null to trigger initial restoration
    private var lastChangeCounter: Int = -1
    private var pollingJob: Job? = null
    private var zonesPollingJob: Job? = null
    
    private var currentLocalTrack: Track? = null

    fun start() {
        logger.i { "Starting service" }
        
        // Initial restoration attempt
        scope.launch {
            val savedGuid = settings.lastZoneGuid.first()
            logger.d { "Startup - Saved zone GUID: $savedGuid" }
            if (savedGuid == "local_guid" || savedGuid == null) {
                logger.i { "Restoring 'local' zone on startup" }
                activeZoneId = "local"
                updateLocalStatus()
            } else {
                // We'll wait for pollZones to find the GUID
                logger.d { "Waiting for pollZones to restore $savedGuid" }
            }
        }

        startPlaybackPolling()
        startZonesPolling()
        
        // Watch local player state to update PlayerStatus when active
        scope.launch {
            localPlayer.playbackState.collect { 
                logger.v { "Local player state changed: $it" }
                updateLocalStatus() 
            }
        }
        scope.launch {
            localPlayer.currentPositionMs.collect { updateLocalStatus() }
        }
    }

    fun stop() {
        logger.i { "Stopping service" }
        pollingJob?.cancel()
        zonesPollingJob?.cancel()
        localPlayer.stop()
    }

    fun setActiveZone(zoneId: String) {
        logger.i { "Setting active zone to $zoneId" }
        activeZoneId = zoneId
        
        // Persist GUID
        scope.launch {
            val zone = _zones.value.find { it.id == zoneId }
            if (zone != null) {
                logger.d { "Saving last zone GUID: ${zone.guid}" }
                settings.saveLastZone(zone.guid)
            }
        }

        if (zoneId == "local") {
            updateLocalStatus()
        } else {
            // Trigger immediate poll
            scope.launch { pollPlaybackInfo() }
        }
    }

    private fun startPlaybackPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                if (activeZoneId != null && activeZoneId != "local") {
                    pollPlaybackInfo()
                }
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

    private suspend fun pollZones() {
        logger.v { "Polling zones..." }
        mcwsClient.getZones().fold(
            onSuccess = { zonesFromServer ->
                logger.d { "Successfully polled ${zonesFromServer.size} zones from server" }
                val allZones = zonesFromServer + localZone
                _zones.value = allZones
                
                if (activeZoneId == null) {
                    val savedGuid = settings.lastZoneGuid.first()
                    val restoredZone = allZones.find { it.guid == savedGuid }
                    
                    if (restoredZone != null) {
                        logger.i { "Restored last selected zone: ${restoredZone.name} (id: ${restoredZone.id})" }
                        setActiveZone(restoredZone.id)
                    } else if (zonesFromServer.isNotEmpty()) {
                        logger.i { "Last selected zone not found, selecting first: ${zonesFromServer.first().name}" }
                        setActiveZone(zonesFromServer.first().id)
                    } else {
                        logger.i { "No server zones found, selecting local" }
                        setActiveZone("local")
                    }
                }
            },
            onFailure = { 
                logger.w { "Failed to poll zones: ${it.message}" }
                _zones.value = listOf(localZone)
                if (activeZoneId == null) {
                    setActiveZone("local")
                }
            }
        )
    }

    private fun updateLocalStatus() {
        if (activeZoneId != "local") return
        
        val trackInfo = currentLocalTrack?.let { 
            TrackInfo(
                fileKey = it.fileKey.toString(),
                name = it.name,
                artist = it.artist,
                album = it.album,
                imageUrl = it.imageUrl,
                bitrate = it.bitrate,
                bitDepth = it.bitDepth,
                sampleRate = it.sampleRate,
                channels = it.channels
            )
        }

        _playerStatus.value = PlayerStatus(
            zoneId = "local",
            zoneName = "This Device",
            state = localPlayer.playbackState.value,
            trackInfo = trackInfo,
            positionMs = localPlayer.currentPositionMs.value,
            durationMs = localPlayer.durationMs.value,
            positionDisplay = formatTime(localPlayer.currentPositionMs.value),
            volume = localPlayer.volume.value,
            volumeDisplay = "${(localPlayer.volume.value * 100).toInt()}%",
            isMuted = false,
            shuffleMode = ShuffleMode.OFF,
            repeatMode = RepeatMode.OFF,
            playingNowPosition = 0,
            playingNowTracks = _currentQueue.value.size,
            playingNowPositionDisplay = "",
            playingNowChangeCounter = 0
        )
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes}:${seconds.toString().padStart(2, '0')}"
    }

    private suspend fun fetchQueue(zoneId: String) {
        if (zoneId == "local") return // Local queue is managed client-side or from a server-side list
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

    // Transport proxy methods
    fun play() = scope.launch { 
        if (activeZoneId == "local") localPlayer.resume() 
        else activeZoneId?.let { mcwsClient.play(it) } 
    }
    fun pause() = scope.launch { 
        if (activeZoneId == "local") localPlayer.pause()
        else activeZoneId?.let { mcwsClient.pause(it) } 
    }
    fun playPause() = scope.launch { 
        if (activeZoneId == "local") {
            if (localPlayer.playbackState.value == PlaybackState.PLAYING) localPlayer.pause() else localPlayer.resume()
        } else activeZoneId?.let { mcwsClient.playPause(it) } 
    }
    fun next() = scope.launch { activeZoneId?.let { mcwsClient.next(it) } }
    fun previous() = scope.launch { activeZoneId?.let { mcwsClient.previous(it) } }

    fun setVolume(level: Float) = scope.launch { 
        if (activeZoneId == "local") localPlayer.setVolume(level)
        else activeZoneId?.let { mcwsClient.setVolume(it, level) } 
    }
    fun seek(positionMs: Int) = scope.launch { 
        if (activeZoneId == "local") localPlayer.seekTo(positionMs)
        else activeZoneId?.let { mcwsClient.seek(it, positionMs) } 
    }

    fun playTrack(track: Track) {
        logger.i { "Playing track: ${track.name} (zone: $activeZoneId)" }
        if (activeZoneId == "local") {
            currentLocalTrack = track
            val url = mcwsClient.buildStreamUrl(track.fileKey.toString())
            localPlayer.play(url)
            updateLocalStatus()
        } else {
            scope.launch {
                activeZoneId?.let { zoneId ->
                    mcwsClient.playByKey(zoneId, track.fileKey.toString())
                }
            }
        }
    }

    fun setQueuePosition(index: Int) = scope.launch { activeZoneId?.let { mcwsClient.setQueuePosition(it, index) } }
    fun reorderQueue(from: Int, to: Int) = scope.launch { activeZoneId?.let { mcwsClient.reorderQueue(it, from, to) } }
    fun removeFromQueue(index: Int) = scope.launch { activeZoneId?.let { mcwsClient.removeFromQueue(it, index) } }

    fun linkZones(targetZoneIds: List<String>) = scope.launch { activeZoneId?.let { mcwsClient.linkZones(it, targetZoneIds) } }
    fun unlinkZone(zoneId: String) = scope.launch { mcwsClient.unlinkZone(zoneId) }

    // Library Operations
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
}
