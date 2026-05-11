package com.example.jrr.service

import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.*
import com.example.jrr.player.LocalPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single

@Single
class JRiverService(
    private val mcwsClient: JRiverMcwsClient,
    private val localPlayer: LocalPlayer
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val localZone = Zone(id = "local", name = "This Device", guid = "local_guid", isDLNA = false, isLocal = true)

    private val _playerStatus = MutableStateFlow<PlayerStatus?>(null)
    val playerStatus: StateFlow<PlayerStatus?> = _playerStatus.asStateFlow()

    private val _zones = MutableStateFlow<List<Zone>>(listOf(localZone))
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<PlayingNowItem>>(emptyList())
    val currentQueue: StateFlow<List<PlayingNowItem>> = _currentQueue.asStateFlow()

    private var activeZoneId: String? = "local"
    private var lastChangeCounter: Int = -1
    private var pollingJob: Job? = null
    private var zonesPollingJob: Job? = null
    
    private var currentLocalTrack: Track? = null

    fun start() {
        println("JRiverService: Starting service")
        updateLocalStatus() // Initialize with local status
        startPlaybackPolling()
        startZonesPolling()
        
        // Watch local player state to update PlayerStatus when active
        scope.launch {
            localPlayer.playbackState.collect { updateLocalStatus() }
        }
        scope.launch {
            localPlayer.currentPositionMs.collect { updateLocalStatus() }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        zonesPollingJob?.cancel()
        localPlayer.stop()
    }

    fun setActiveZone(zoneId: String) {
        println("JRiverService: Setting active zone to $zoneId")
        activeZoneId = zoneId
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
                if (activeZoneId != "local") {
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
        println("JRiverService: Polling zones...")
        mcwsClient.getZones().fold(
            onSuccess = { zones ->
                println("JRiverService: Successfully polled ${zones.size} zones from server")
                _zones.value = zones + localZone
                if (activeZoneId == null && zones.isNotEmpty()) {
                    println("JRiverService: No active zone, selecting first: ${zones.first().name}")
                    setActiveZone(zones.first().id)
                }
            },
            onFailure = { 
                println("JRiverService: Failed to poll zones: ${it.message}")
                _zones.value = listOf(localZone)
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
        return mcwsClient.browseChildren(id).getOrDefault(emptyList())
    }

    suspend fun browseFiles(id: String): List<Track> {
        return mcwsClient.browseFiles(id).getOrDefault(emptyList())
    }

    suspend fun search(query: String, limit: Int = -1): List<Track> {
        return mcwsClient.searchFiles(query, limit = limit).getOrDefault(emptyList())
    }
}
